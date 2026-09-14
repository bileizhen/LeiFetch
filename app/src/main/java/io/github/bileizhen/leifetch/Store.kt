package io.github.bileizhen.leifetch

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Binder
import android.os.Process
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.crossbowffs.remotepreferences.RemotePreferenceProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

const val PKG = "io.github.bileizhen.leifetch"
private val Context.dataStore by preferencesDataStore("settings")

data class Config(
    val threads: Int = 4, val tree: String = "", val notices: Boolean = true,
    val fluid: Boolean = true, val enabled: Boolean = false,
    val packages: String = "", val colorMode: Int = 3,
    val maxTasks: Int = 3, val connections: Int = 16, val speedLimit: Long = 0, val dynamic: Boolean = true,
    val blur: Boolean = true, val floatingBar: Boolean = true, val liquidGlass: Boolean = true,
    val predictiveBack: Boolean = true, val scale: Float = 1f, val plugins: String = "generic",
    val githubMirror: Boolean = true, val githubMirrorPick: String = "auto", val githubMirrors: String = "",
    val clipboardDetect: Boolean = true, val clipboardSeen: String = "",
    val proxy: ProxySettings = ProxySettings()
)

class Settings(private val context: Context, scope: CoroutineScope) {
    private val threads = intPreferencesKey("threads")
    private val tree = stringPreferencesKey("tree")
    private val notices = booleanPreferencesKey("notices")
    private val fluid = booleanPreferencesKey("fluid")
    private val enabled = booleanPreferencesKey("enabled")
    private val packages = stringPreferencesKey("packages")
    private val theme = intPreferencesKey("theme")
    private val colorMode = intPreferencesKey("colorMode")
    private val maxTasks = intPreferencesKey("maxTasks")
    private val connections = intPreferencesKey("connections")
    private val speedLimit = longPreferencesKey("speedLimit")
    private val dynamic = booleanPreferencesKey("dynamic")
    private val blur = booleanPreferencesKey("blur")
    private val floatingBar = booleanPreferencesKey("floatingBar")
    private val liquidGlass = booleanPreferencesKey("liquidGlass")
    private val predictiveBack = booleanPreferencesKey("predictiveBack")
    private val scale = floatPreferencesKey("scale")
    private val plugins = stringPreferencesKey("plugins")
    private val githubMirror = booleanPreferencesKey("githubMirror")
    private val githubMirrorPick = stringPreferencesKey("githubMirrorPick")
    private val githubMirrors = stringPreferencesKey("githubMirrors")
    private val clipboardDetect = booleanPreferencesKey("clipboardDetect")
    private val clipboardSeen = stringPreferencesKey("clipboardSeen")
    private val proxyMode = stringPreferencesKey("proxyMode")
    private val proxyType = stringPreferencesKey("proxyType")
    private val proxyHost = stringPreferencesKey("proxyHost")
    private val proxyPort = intPreferencesKey("proxyPort")
    private val proxyUser = stringPreferencesKey("proxyUser")
    private val proxyPassword = stringPreferencesKey("proxyPassword")
    private val proxyBypass = stringPreferencesKey("proxyBypass")
    private fun Preferences.read() = Config(
        p_threads(), p_tree(), p_notices(), p_fluid(), p_enabled(), p_packages(),
        p_colorMode(), p_maxTasks(), p_connections(), p_speedLimit(), p_dynamic(),
        p_blur(), p_floatingBar(), p_liquidGlass(), p_predictiveBack(), p_scale(), this[plugins] ?: "generic",
        this[githubMirror] ?: true, this[githubMirrorPick] ?: "auto", this[githubMirrors] ?: "",
        this[clipboardDetect] ?: true, this[clipboardSeen] ?: "", p_proxy()
    )
    private fun Preferences.p_threads() = this[threads] ?: 4
    private fun Preferences.p_tree() = this[tree] ?: ""
    private fun Preferences.p_notices() = this[notices] ?: true
    private fun Preferences.p_fluid() = this[fluid] ?: true
    private fun Preferences.p_enabled() = this[enabled] ?: false
    private fun Preferences.p_packages() = this[packages] ?: ""
    // 旧版本用 theme 键（0-3）；首次读取时映射到 colorMode。
    private fun Preferences.p_colorMode() = this[colorMode] ?: this[theme] ?: 3
    private fun Preferences.p_maxTasks() = this[maxTasks] ?: 3
    private fun Preferences.p_connections() = this[connections] ?: 16
    private fun Preferences.p_speedLimit() = this[speedLimit] ?: 0
    private fun Preferences.p_dynamic() = this[dynamic] ?: true
    private fun Preferences.p_blur() = this[blur] ?: true
    private fun Preferences.p_floatingBar() = this[floatingBar] ?: true
    private fun Preferences.p_liquidGlass() = this[liquidGlass] ?: true
    private fun Preferences.p_predictiveBack() = this[predictiveBack] ?: true
    private fun Preferences.p_scale() = this[scale] ?: 1f
    private fun Preferences.p_proxy() = ProxySettings(
        mode = this[proxyMode]?.takeIf { it in ProxyMode.all } ?: ProxyMode.SYSTEM,
        type = this[proxyType]?.takeIf { it == ProxyType.SOCKS } ?: ProxyType.HTTP,
        host = this[proxyHost] ?: "", port = this[proxyPort] ?: 0,
        username = this[proxyUser] ?: "", password = this[proxyPassword] ?: "",
        bypass = this[proxyBypass] ?: ""
    )
    val state = context.dataStore.data.map { it.read() }.stateIn(scope, SharingStarted.Eagerly, Config())

    init {
        scope.launch(Dispatchers.IO) {
            context.dataStore.data.collect { p ->
                val config = p.read()
                check(context.getSharedPreferences("hook", Context.MODE_PRIVATE).edit()
                    .putBoolean("enabled", p[enabled] ?: false)
                    .putString("packages", HookPlugins.packages(config).joinToString(","))
                    .putString("plugins", p[plugins] ?: "generic")
                    // Firefox 插件在浏览器进程里独立探测，只能经偏好提供者拿到代理配置。
                    .putString("proxy", config.proxy.encode()).commit())
            }
        }
    }

    suspend fun edit(change: (Config) -> Config) {
        context.dataStore.edit { p ->
            val next = change(p.read())
            p[threads] = next.threads.coerceIn(1, 16)
            p[tree] = next.tree
            p[notices] = next.notices
            p[fluid] = next.fluid
            p[enabled] = next.enabled
            p[packages] = next.packages.trim()
            p[colorMode] = next.colorMode.coerceIn(0, 5)
            p[maxTasks] = next.maxTasks.coerceIn(1, 8)
            p[connections] = next.connections.coerceIn(1, 128)
            p[speedLimit] = next.speedLimit.coerceIn(0, 1024L * 1024 * 1024)
            p[dynamic] = next.dynamic
            p[blur] = next.blur
            p[floatingBar] = next.floatingBar
            p[liquidGlass] = next.liquidGlass
            p[predictiveBack] = next.predictiveBack
            p[scale] = next.scale.coerceIn(0.8f, 1.1f)
            p[plugins] = next.plugins
            p[githubMirror] = next.githubMirror
            p[githubMirrorPick] = next.githubMirrorPick.trim()
            p[githubMirrors] = next.githubMirrors.trim()
            p[clipboardDetect] = next.clipboardDetect
            p[clipboardSeen] = next.clipboardSeen.trim('\n')
            p[proxyMode] = next.proxy.mode.takeIf { it in ProxyMode.all } ?: ProxyMode.SYSTEM
            p[proxyType] = next.proxy.type.takeIf { it == ProxyType.SOCKS } ?: ProxyType.HTTP
            p[proxyHost] = next.proxy.host.trim()
            p[proxyPort] = next.proxy.port.coerceIn(0, 65535)
            p[proxyUser] = next.proxy.username.trim()
            p[proxyPassword] = next.proxy.password
            p[proxyBypass] = ProxySettings.parseBypass(next.proxy.bypass).joinToString(",")
        }
    }
}

class HookPreferenceProvider : RemotePreferenceProvider("$PKG.preferences", arrayOf("hook")) {
    override fun checkAccess(prefFileName: String, prefKey: String, write: Boolean): Boolean =
        prefFileName == "hook" && (!write || Binder.getCallingUid() == Process.myUid())
}

data class Task(
    val id: String = UUID.randomUUID().toString(), val url: String,
    val name: String, val headers: Map<String, String> = emptyMap(),
    val source: String = "手动", val state: String = "待确认",
    val done: Long = 0, val total: Long = -1, val speed: Long = 0,
    val created: Long = System.currentTimeMillis(), val finished: Long = 0,
    val uri: String = "", val error: String = "", val tree: String = "",
    /** 下载时所用的 GitHub 镜像站主机；空表示未走镜像（直连、非 GitHub 或未启用）。 */
    val mirror: String = "",
    /** 捕获来源已观察到的响应长度；仅作为免探测提示，内核仍会校验。 */
    val expectedSize: Long = -1
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("url", url).put("name", name)
        .put("headers", JSONObject(headers)).put("source", source).put("state", state)
        .put("done", done).put("total", total).put("speed", speed).put("created", created)
        .put("finished", finished).put("uri", uri).put("error", error).put("tree", tree)
        .put("mirror", mirror).put("expectedSize", expectedSize)

    companion object {
        fun from(j: JSONObject): Task {
            val h = j.optJSONObject("headers") ?: JSONObject()
            return Task(j.getString("id"), j.getString("url"), j.getString("name"),
                h.keys().asSequence().associateWith { h.getString(it) }, j.getString("source"),
                j.getString("state"), j.optLong("done"), j.optLong("total", -1),
                j.optLong("speed"), j.getLong("created"), j.optLong("finished"),
                j.optString("uri"), j.optString("error"), j.optString("tree"),
                j.optString("mirror"), j.optLong("expectedSize", -1))
        }
    }
}

class TaskStore(context: Context) : SQLiteOpenHelper(context, "tasks.db", null, 1) {
    private val mutable = MutableStateFlow<List<Task>>(emptyList())
    val tasks = mutable.asStateFlow()
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY, payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported schema upgrade: $oldVersion -> $newVersion")
    }
    @Synchronized fun load() {
        val result = mutableListOf<Task>()
        readableDatabase.rawQuery("SELECT payload FROM tasks", null).use { c ->
            while (c.moveToNext()) result += Task.from(JSONObject(c.getString(0)))
        }
        mutable.value = result.sortedByDescending { it.created }
    }
    @Synchronized fun get(id: String): Task? = tasks.value.firstOrNull { it.id == id }
    @Synchronized fun put(task: Task) {
        writableDatabase.insertWithOnConflict("tasks", null, ContentValues().apply {
            put("id", task.id); put("payload", task.json().toString())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        mutable.value = (mutable.value.filterNot { it.id == task.id } + task).sortedByDescending { it.created }
    }
    @Synchronized fun update(id: String, change: (Task) -> Task) { get(id)?.let { put(change(it)) } }
    @Synchronized fun add(task: Task): Task {
        // 忽略来源去重：同一入队可能先后被应用内钩子与系统下载器插件上报。
        val duplicate = tasks.value.firstOrNull {
            it.url == task.url && it.headers == task.headers &&
                it.state !in setOf("已完成", "已取消") && task.created - it.created < 60_000
        }
        if (duplicate != null) {
            // 后到的响应观测可能比先到的 DownloadManager 事件多带一个长度提示。
            if (duplicate.expectedSize < 0 && task.expectedSize >= 0) {
                return duplicate.copy(expectedSize = task.expectedSize).also(::put)
            }
            return duplicate
        }
        require(tasks.value.count { it.state == "待确认" } < 200) { "待确认任务已达上限" }
        put(task)
        return task
    }
    @Synchronized fun remove(id: String) {
        writableDatabase.delete("tasks", "id=?", arrayOf(id))
        mutable.value = mutable.value.filterNot { it.id == id }
    }
}

class LeiFetchApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var store: TaskStore
    lateinit var settings: Settings
    lateinit var proxies: ProxyManager
    val telemetry = TransferTelemetryRegistry()
    val ready = CompletableDeferred<Unit>()
    override fun onCreate() {
        super.onCreate()
        settings = Settings(this, scope)
        // 代理解析按每次请求现读设置：切换模式、自动探测结果与直连名单都立即生效。
        proxies = ProxyManager(this) { settings.state.value.proxy }
        store = TaskStore(this)
        Notices.channels(this)
        scope.launch {
            try {
                store.load()
                store.tasks.value.filter { it.state in setOf("下载中", "排队中", "保存中") }.forEach {
                    store.put(it.copy(state = "已暂停", speed = 0, error = "进程中断，点击继续"))
                }
                ready.complete(Unit)
            } catch (e: Throwable) { ready.completeExceptionally(e) }
        }
    }
}

val Context.app: LeiFetchApp get() = applicationContext as LeiFetchApp
fun safeName(value: String): String = value.substringAfterLast('/').substringAfterLast('\\')
    .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_").take(96).trim().let {
        if (it.isBlank() || it == "." || it == "..") "download.bin" else it
    }
/** 把 SAF 树 URI 转成可读路径（primary:Download → /storage/emulated/0/Download）；无法识别时原样返回。 */
fun displayTree(tree: String): String {
    if (tree.isBlank()) return tree
    val uri = runCatching { Uri.parse(tree) }.getOrNull() ?: return tree
    if (uri.scheme != "content" || uri.authority != "com.android.externalstorage.documents") return tree
    val documentId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
    val path = documentId?.substringAfter(':', "") ?: return tree
    if (path.isBlank()) return tree
    val volume = documentId.substringBefore(':', "")
    val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
    return "$root/${path.trimEnd('/')}"
}
fun workDir(context: Context, id: String): File {
    require(runCatching { UUID.fromString(id) }.isSuccess)
    return File(context.filesDir, "partial/$id").apply { mkdirs() }
}
