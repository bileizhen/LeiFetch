package io.github.bileizhen.leifetch

import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebView
import com.crossbowffs.remotepreferences.RemotePreferences
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.util.Collections
import java.util.concurrent.*

class CaptureProvider : ContentProvider() {
    private val recent = mutableMapOf<Int, Long>()
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(method == "capture")
        val c = requireNotNull(context)
        val uid = Binder.getCallingUid()
        val pref = c.getSharedPreferences("hook", Context.MODE_PRIVATE)
        val allowed = pref.getString("packages", "").orEmpty().split(Regex("[\\s,;]+"))
        val caller = c.packageManager.getPackagesForUid(uid).orEmpty().firstOrNull { it in allowed }
            ?: throw SecurityException("来源未授权")
        check(pref.getBoolean("enabled", false))
        synchronized(recent) {
            val now = SystemClock.elapsedRealtime()
            check(now - (recent[uid] ?: -1000) >= 500) { "请求过于频繁" }
            recent[uid] = now
        }
        val raw = requireNotNull(extras?.getString("request"))
        require(raw.length <= 32768)
        val j = JSONObject(raw)
        val url = j.getString("url")
        require(url.length <= 8192 && Uri.parse(url).scheme in setOf("http", "https"))
        val h = j.optJSONObject("headers") ?: JSONObject()
        val headers = h.keys().asSequence().filter {
            it.lowercase() in setOf("authorization", "cookie", "user-agent", "referer", "accept", "accept-language")
        }.associateWith { h.getString(it).also { v -> require(!v.contains('\r') && !v.contains('\n')) } }
        val identity = Binder.clearCallingIdentity()
        try {
            runBlockingReady(c)
            val task = c.app.store.add(Task(url = url, name = safeName(j.getString("name")),
                headers = headers, source = "$caller · ${j.optString("entry")}", tree = c.app.settings.state.value.tree))
            Notices.refreshCandidate(c)
            return Bundle().apply { putBoolean("accepted", true); putString("id", task.id) }
        } finally { Binder.restoreCallingIdentity(identity) }
    }
    private fun runBlockingReady(c: Context) = kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.withTimeout(5000) { c.app.ready.await() }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}

class HookEntry : IXposedHookLoadPackage {
    companion object {
        // libxposed API 101 与 legacy 入口可能都被加载时，保证同一包只安装一次钩子。
        private val handled = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    }
    private val installed = Collections.newSetFromMap(ConcurrentHashMap<Method, Boolean>())
    private val executor = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(32))
    private var context: Context? = null
    private var preferences: RemotePreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    @Volatile private var enabled = false
    @Volatile private var genericEnabled = true
    @Volatile private var firefoxEnabled = false
    @Volatile private var systemEnabled = false
    private val probing = object : ThreadLocal<Boolean>() { override fun initialValue() = false }
    private var lastUiRedirect = 0L
    private val genericActive get() = enabled && genericEnabled && probing.get() != true
    private val observeHeaders = setOf("authorization", "cookie", "user-agent", "referer", "accept", "accept-language")

    override fun handleLoadPackage(p: XC_LoadPackage.LoadPackageParam) = handle(p.packageName, p.classLoader)

    /** legacy 与 libxposed API 101 两个入口共用的安装逻辑。 */
    fun handle(packageName: String, classLoader: ClassLoader) {
        if (!handled.add(packageName)) return
        if (packageName == PKG || packageName == "android" || packageName == "com.android.systemui") return
        XposedHelpers.findAndHookMethod(Application::class.java, "attach", Context::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (context != null) return
                context = (param.args[0] as Context).applicationContext ?: param.args[0] as Context
                queue {
                    val c = requireNotNull(context)
                    // 提供者冷启动或包可见性刷新可能让首次查询失败，退避重试。
                    var attempt = 0
                    while (preferences == null && attempt < 5) {
                        runCatching {
                            preferences = RemotePreferences(c, "$PKG.preferences", "hook", true).also { pref ->
                                fun refresh() {
                                    val plugins = pref.getString("plugins", "generic").orEmpty().split(',')
                                    genericEnabled = "generic" in plugins
                                    firefoxEnabled = "firefox" in plugins && packageName in setOf("org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix")
                                    systemEnabled = "system" in plugins && packageName in SystemDownloads.packages
                                    enabled = pref.getBoolean("enabled", false) && packageName in
                                        pref.getString("packages", "").orEmpty().split(Regex("[\\s,;]+"))
                                }
                                preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                                    runCatching { refresh() }.onFailure { enabled = false }
                                }.also(pref::registerOnSharedPreferenceChangeListener)
                                refresh()
                            }
                        }.onFailure {
                            attempt++
                            log("配置读取失败（第 $attempt/5 次）", it)
                            if (attempt < 5) runCatching { Thread.sleep(1000) }
                        }
                    }
                    if (preferences == null) enabled = false
                }
            }
        })
        // 系统下载器插件的两类进程只装专用钩子；通用钩子集中在提供器进程会与入队捕获重复。
        if (packageName in SystemDownloads.packages) {
            installSystemDownloads(packageName, classLoader)
            return
        }
        installDownloadManager()
        installWebView()
        listOf("org.mozilla.geckoview.GeckoSession", "okhttp3.RealCall", "okhttp3.internal.connection.RealCall",
            "com.android.okhttp.internal.huc.HttpURLConnectionImpl",
            "com.android.okhttp.internal.huc.HttpsURLConnectionImpl").forEach { name ->
            runCatching { classLoader.loadClass(name) }.getOrNull()?.let(::installNetworkClass)
        }
        XposedHelpers.findAndHookMethod(ClassLoader::class.java, "loadClass", String::class.java,
            Boolean::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val name = param.args[0] as String
                    if (name == "org.mozilla.geckoview.GeckoSession" || name == "okhttp3.RealCall" || name == "okhttp3.internal.connection.RealCall" ||
                        name == "com.android.okhttp.internal.huc.HttpURLConnectionImpl" ||
                        name == "com.android.okhttp.internal.huc.HttpsURLConnectionImpl") {
                        (param.result as? Class<*>)?.let(::installNetworkClass)
                    }
                }
            })
    }

    private fun installDownloadManager() {
        XposedHelpers.findAndHookMethod(DownloadManager::class.java, "enqueue", DownloadManager.Request::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!genericActive || param.hasThrowable()) return
                    runCatching {
                        val request = param.args[0]
                        val url = XposedHelpers.getObjectField(request, "mUri").toString()
                        val pairs = XposedHelpers.getObjectField(request, "mRequestHeaders") as? List<*>
                        val h = pairs.orEmpty().mapNotNull { pair ->
                            val k = XposedHelpers.getObjectField(pair, "first") as? String ?: return@mapNotNull null
                            val v = XposedHelpers.getObjectField(pair, "second") as? String ?: return@mapNotNull null
                            k to v
                        }.toMap()
                        submit(url, h, null, "DownloadManager（原任务保留）")
                    }.onFailure { log("DownloadManager", it) }
                }
            })
    }

    // 系统下载器接管：提供器进程集中捕获 DownloadManager 入队，界面进程重定向下载列表入口。
    private fun installSystemDownloads(packageName: String, classLoader: ClassLoader) {
        if (packageName == SystemDownloads.UI) { installDownloadsUiTakeover(); return }
        val provider = runCatching { classLoader.loadClass("${SystemDownloads.PROVIDER}.DownloadProvider") }
            .onFailure { XposedBridge.log("LeiFetch 系统下载器：未找到 DownloadProvider，入队捕获未安装") }
            .getOrNull() ?: return
        XposedHelpers.findAndHookMethod(provider, "insert", Uri::class.java, ContentValues::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!enabled || !systemEnabled || param.hasThrowable()) return
                runCatching {
                    val values = param.args[1] as? ContentValues ?: return
                    val request = SystemDownloads.insertRequest(values.getAsString("uri"),
                        values.getAsString("title"), values.getAsString("hint")) ?: return
                    // 通用插件已在入队应用内上报（可带请求头）时不重复捕获。
                    if (coveredByGenericHook()) return
                    queue { capture(request.first, emptyMap(), request.second, "系统下载器（原任务保留）") }
                }.onFailure { log("系统下载器入队捕获", it) }
            }
        })
        XposedBridge.log("LeiFetch 系统下载器：DownloadProvider.insert 钩子已安装")
    }

    /** 入队调用方（Binder 调用 uid）是否已被通用插件在自己的进程内接管上报。 */
    private fun coveredByGenericHook(): Boolean {
        if (!enabled || !genericEnabled) return false
        val scoped = preferences?.getString("packages", "").orEmpty().split(Regex("[\\s,;]+")).toSet()
        val callers = context?.packageManager?.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        return callers.any { it in scoped }
    }

    private fun installDownloadsUiTakeover() {
        XposedHelpers.findAndHookMethod(Activity::class.java, "onCreate", Bundle::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled || !systemEnabled) return
                val activity = param.thisObject as? Activity ?: return
                val action = activity.intent?.action ?: return
                if (action !in SystemDownloads.takeOverActions) return
                runCatching {
                    // TrampolineActivity 可能继续转发到 DownloadList，短窗口内只发起一次跳转。
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUiRedirect >= 1500) {
                        lastUiRedirect = now
                        activity.startActivity(Intent(Intent.ACTION_MAIN).setClassName(PKG, "$PKG.MainActivity")
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra("page", 1))
                    }
                    activity.finish()
                }.onFailure { log("系统下载界面接管", it) }
            }
        })
        XposedBridge.log("LeiFetch 系统下载器：下载界面入口接管已安装")
    }

    private fun installWebView() {
        XposedHelpers.findAndHookMethod(WebView::class.java, "setDownloadListener", DownloadListener::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val original = param.args[0] as? DownloadListener ?: return
                    val web = param.thisObject as WebView
                    param.args[0] = DownloadListener { url, agent, disposition, mime, length ->
                        if (!genericActive || Uri.parse(url).scheme !in setOf("http", "https")) {
                            original.onDownloadStart(url, agent, disposition, mime, length)
                        } else {
                            val h = mutableMapOf("User-Agent" to agent.orEmpty())
                            CookieManager.getInstance().getCookie(url)?.let { h["Cookie"] = it }
                            web.url?.let { h["Referer"] = it }
                            val accepted = queue {
                                if (!capture(url, h, URLUtil.guessFileName(url, disposition, mime), "WebView 接管")) {
                                    web.post { original.onDownloadStart(url, agent, disposition, mime, length) }
                                }
                            }
                            if (!accepted) original.onDownloadStart(url, agent, disposition, mime, length)
                        }
                    }
                }
            })
    }

    private fun installNetworkClass(clazz: Class<*>) {
        if (clazz.name == "org.mozilla.geckoview.GeckoSession") { installFirefox(clazz); return }
        clazz.declaredMethods.filter { it.name in setOf("execute", "enqueue", "getInputStream") }.forEach { method ->
            if (!installed.add(method)) return@forEach
            runCatching { XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!genericActive) return
                    runCatching {
                        if (method.name == "enqueue" && param.args.size == 1) {
                            val callback = param.args[0] ?: return
                            val type = method.parameterTypes[0]
                            if (!type.isInterface) return
                            param.args[0] = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, m, args ->
                                if (m.name == "onResponse") args?.getOrNull(1)?.let(::observeOkHttp)
                                try { m.invoke(callback, *(args ?: emptyArray())) }
                                catch (e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
                            }
                        } else if (method.name == "getInputStream") {
                            val conn = param.thisObject as? HttpURLConnection ?: return
                            if (conn.requestMethod == "GET") {
                                val h = runCatching { conn.requestProperties.mapValues { it.value.joinToString("; ") } }.getOrDefault(emptyMap())
                                param.setObjectExtra("leifetch.headers", h)
                            }
                        }
                    }.onFailure { log("网络前置识别", it) }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!genericActive || param.hasThrowable()) return
                    runCatching {
                        if (method.name == "execute") param.result?.let(::observeOkHttp)
                        if (method.name == "getInputStream") {
                            val conn = param.thisObject as? HttpURLConnection ?: return
                            if (conn.requestMethod != "GET" || conn.responseCode !in 200..299) return
                            val url = conn.url.toString()
                            val disposition = conn.getHeaderField("Content-Disposition")
                            if (!looksLikeDownload(url, disposition)) return
                            @Suppress("UNCHECKED_CAST") val h = param.getObjectExtra("leifetch.headers") as? Map<String, String> ?: emptyMap()
                            submit(url, h, URLUtil.guessFileName(url, disposition, conn.contentType), "HttpURLConnection（原请求保留）")
                        }
                    }.onFailure { log("网络响应识别", it) }
                }
            }) }.onFailure { log("安装网络 Hook", it) }
        }
    }

    private fun observeOkHttp(response: Any) {
        if (!genericActive || response.javaClass.name != "okhttp3.Response") return
        runCatching {
            val request = XposedHelpers.callMethod(response, "request")
            if (XposedHelpers.callMethod(request, "method") != "GET") return
            val code = XposedHelpers.callMethod(response, "code") as Int
            if (code !in 200..299) return
            val url = XposedHelpers.callMethod(request, "url").toString()
            val disposition = XposedHelpers.callMethod(response, "header", "Content-Disposition") as? String
            if (!looksLikeDownload(url, disposition)) return
            val headers = XposedHelpers.callMethod(request, "headers")
            val size = XposedHelpers.callMethod(headers, "size") as Int
            val h = (0 until size).associate {
                XposedHelpers.callMethod(headers, "name", it) as String to
                    XposedHelpers.callMethod(headers, "value", it) as String
            }
            submit(url, h, URLUtil.guessFileName(url, disposition, null), "OkHttp（原请求保留）")
        }.onFailure { log("OkHttp", it) }
    }

    private fun looksLikeDownload(url: String, disposition: String?): Boolean =
        disposition?.contains("attachment", true) == true ||
            Uri.parse(url).path.orEmpty().substringAfterLast('.').lowercase() in
            setOf("apk", "zip", "7z", "rar", "pdf", "iso", "exe", "mp4", "mp3", "bin")
    private fun installFirefox(clazz: Class<*>) {
        val setter = clazz.declaredMethods.firstOrNull { it.name == "setContentDelegate" && it.parameterTypes.size == 1 }
        if (setter == null) { XposedBridge.log("LeiFetch Firefox：未找到 setContentDelegate，接管未安装"); return }
        if (!installed.add(setter)) return
        XposedBridge.hookMethod(setter, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val original = param.args[0] ?: return
                val type = setter.parameterTypes[0]
                param.args[0] = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                    fun forward(): Any? = try { method.invoke(original, *(args ?: emptyArray())) }
                    catch (e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
                    if (method.name != "onExternalResponse" || !enabled || !firefoxEnabled) forward()
                    else {
                        // 当前 GeckoView 传入 WebResponse：uri/headers/body 均为公有字段。
                        val response = args?.getOrNull(1)
                        val captured = if (response == null) false else queue {
                            val url = runCatching { XposedHelpers.getObjectField(response, "uri") as? String }.getOrNull()
                            val accepted = if (url.isNullOrEmpty()) false else runCatching {
                                @Suppress("UNCHECKED_CAST")
                                val headers = XposedHelpers.getObjectField(response, "headers") as? Map<String, String> ?: emptyMap()
                                probing.set(true)
                                try {
                                    val verified = FirefoxProbe.verify(url, headers, onRejected = { reason ->
                                        XposedBridge.log("LeiFetch Firefox：$reason，保留 Firefox 下载")
                                    }) ?: return@runCatching false
                                    val name = URLUtil.guessFileName(url, FirefoxProbe.header(headers, "Content-Disposition"),
                                        FirefoxProbe.header(headers, "Content-Type"))
                                    val ok = capture(verified.url, emptyMap(), name, "Firefox 插件接管")
                                    XposedBridge.log(if (ok) "LeiFetch Firefox：已接管下载" else "LeiFetch Firefox：任务入库失败，保留 Firefox 下载")
                                    ok
                                } finally { probing.set(false) }
                            }.getOrDefault(false)
                            if (accepted) {
                                runCatching { (XposedHelpers.getObjectField(response, "body") as? java.io.InputStream)?.close() }
                            } else Handler(Looper.getMainLooper()).post { forward() }
                        }
                        if (!captured) forward() else null
                    }
                }
            }
        })
        XposedBridge.log("LeiFetch Firefox：onExternalResponse 钩子已安装")
    }
    private fun submit(url: String, headers: Map<String, String>, name: String?, entry: String) {
        queue { capture(url, headers, name ?: URLUtil.guessFileName(url, null, null), entry) }
    }
    private fun capture(url: String, headers: Map<String, String>, name: String, entry: String): Boolean {
        if (!enabled) return false
        return runCatching {
            val j = JSONObject().put("url", url).put("name", name).put("entry", entry)
                .put("headers", JSONObject(headers.filterKeys { it.lowercase() in observeHeaders }))
            if (j.toString().length > 32768) return false
            context?.contentResolver?.call(Uri.parse("content://$PKG.capture"), "capture", null,
                Bundle().apply { putString("request", j.toString()) })?.getBoolean("accepted") == true
        }.getOrDefault(false)
    }
    private fun queue(action: () -> Unit): Boolean = try {
        executor.execute { runCatching(action).onFailure { log("上报", it) } }; true
    } catch (_: RejectedExecutionException) { false }
    private fun log(where: String, error: Throwable) {
        XposedBridge.log("LeiFetch $where: ${error.javaClass.simpleName}: ${error.message}")
        XposedBridge.log(error)
    }
}
