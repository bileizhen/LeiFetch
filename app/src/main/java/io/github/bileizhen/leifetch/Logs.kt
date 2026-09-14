package io.github.bileizhen.leifetch

import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 日志级别。只留三档：够筛，又不至于让筛选本身变成负担。 */
enum class LogLevel(val label: String, val priority: Int) {
    INFO("信息", android.util.Log.INFO),
    WARN("警告", android.util.Log.WARN),
    ERROR("错误", android.util.Log.ERROR);

    companion object {
        fun of(raw: String?): LogLevel = entries.firstOrNull { it.name == raw } ?: INFO
    }
}

/** 日志来源。日志页的来源筛选项由缓冲里实际出现过的来源生成，不写死列表。 */
object LogSource {
    const val DOWNLOAD = "下载"
    const val ENGINE = "内核"
    const val MIRROR = "镜像"
    const val PROXY = "代理"
    const val CAPTURE = "捕获"
    const val FIREFOX = "Firefox"
    const val SYSTEM = "系统下载器"
    const val SERVICE = "服务"
    const val PLUGIN = "插件"
    const val APP = "应用"
}

data class LogEntry(val time: Long, val level: LogLevel, val source: String, val message: String)

/** 日志页的筛选条件。 */
data class LogFilter(val level: LogLevel? = null, val source: String? = null, val query: String = "")

/** 日志页渲染所需的一次性状态：过滤结果、可选来源与当前筛选条件。 */
data class LogUiState(
    val entries: List<LogEntry> = emptyList(),
    val sources: List<String> = emptyList(),
    val total: Int = 0,
    val level: LogLevel? = null,
    val source: String? = null,
    val query: String = "",
)

/** 过滤逻辑独立成函数：缓冲与界面共用同一套判定，也便于单测。 */
fun filterLogs(entries: List<LogEntry>, level: LogLevel? = null, source: String? = null, query: String = ""): List<LogEntry> {
    val text = query.trim()
    return entries.filter { entry ->
        (level == null || entry.level == level) &&
            (source == null || entry.source == source) &&
            (text.isEmpty() || entry.source.contains(text, true) || entry.message.contains(text, true))
    }
}

/** 日志里的地址只保留主机：查询串可能带签名或凭据，与隐私说明一致。 */
fun logHost(url: String): String = runCatching { URL(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "未知主机"

/**
 * 定长环形缓冲：写满后丢最旧的。纯逻辑，脱离 Android 可单测。
 * 快照按需生成并缓存，读取方拿到的永远是不可变列表。
 */
class LogBuffer(val capacity: Int = Logs.CAPACITY) {
    private val entries = ArrayDeque<LogEntry>()
    private var stale = true
    private var cached: List<LogEntry> = emptyList()

    fun add(entry: LogEntry) = synchronized(this) {
        while (entries.size >= capacity) entries.removeFirst()
        entries.addLast(entry)
        stale = true
    }

    fun clear() = synchronized(this) {
        entries.clear()
        stale = true
    }

    fun size(): Int = synchronized(this) { entries.size }

    fun snapshot(): List<LogEntry> = synchronized(this) {
        if (stale) {
            cached = entries.toList()
            stale = false
        }
        cached
    }

    /** 按级别 / 来源 / 关键字过滤；关键字同时匹配来源与正文，忽略大小写。 */
    fun filter(level: LogLevel? = null, source: String? = null, query: String = ""): List<LogEntry> =
        filterLogs(snapshot(), level, source, query)

    /** 缓冲里出现过的来源，按首次出现顺序。 */
    fun sources(): List<String> = snapshot().map { it.source }.distinct()
}

/**
 * 应用进程内的日志总线。下载、内核、镜像、代理与捕获路径都写这里；
 * 插件进程（Firefox、通用捕获、系统下载器）的日志经 CaptureProvider 回传后并入同一份缓冲，
 * 日志页订阅 [entries] 即可实时刷新。
 *
 * 只保留内存中的近期日志，不落盘：进程结束即清空，不产生额外的隐私面。
 * 每次写入都发布一次快照——日志本身是低频事件（进度不写日志），
 * 换来的是订阅方无需节流也看不出延迟。
 */
object Logs {
    /** 单条日志上限，防止异常堆栈或超长错误信息撑爆界面。 */
    private const val MAX_MESSAGE = 400
    /** 保留的日志条数：够看清一次下载的前后经过，又不会无限增长。 */
    const val CAPACITY = 600
    private val buffer = LogBuffer()
    private val mutable = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = mutable.asStateFlow()

    val count: Int get() = buffer.size()

    fun i(source: String, message: String) = append(LogLevel.INFO, source, message)
    fun w(source: String, message: String) = append(LogLevel.WARN, source, message)
    fun e(source: String, message: String) = append(LogLevel.ERROR, source, message)

    fun append(level: LogLevel, source: String, message: String) {
        val line = message.replace('\n', ' ').replace('\r', ' ').trim().take(MAX_MESSAGE)
        if (line.isEmpty()) return
        buffer.add(LogEntry(System.currentTimeMillis(), level, source, line))
        // 同一份内容也交给系统日志，便于用 logcat 排查应用进程自身的问题。
        runCatching { android.util.Log.println(level.priority, "LeiFetch", "$source：$line") }
        mutable.value = buffer.snapshot()
    }

    fun clear() {
        buffer.clear()
        mutable.value = emptyList()
    }
}

/**
 * 诊断包，沿用 XBlocker 的日志上报形态：打包成 zip 便于保存与分享。
 * 内含 logs.txt（本模块日志）、summary.json（版本与设备摘要）、logcat.txt（本进程系统日志片段）。
 * 不含任务 URL（日志里的地址只留主机名）、不请求 root、也不读取 LSPosed 私有日志。
 */
object LogExport {
    private val stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")

    fun text(entries: List<LogEntry>): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
        return buildString {
            append("LeiFetch 日志 · ${entries.size} 条\n\n")
            for (entry in entries) {
                append(format.format(Date(entry.time))).append("  ")
                append(entry.level.label).append("  ")
                append('[').append(entry.source).append("] ").append(entry.message).append('\n')
            }
        }
    }

    suspend fun create(context: Context, entries: List<LogEntry>): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "logs").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(stampFormat)
        val report = File(directory, "LeiFetch_logs_${stamp}_${UUID.randomUUID().toString().take(8)}.zip")
        val tasks = context.app.store.tasks.value
        val config = context.app.settings.state.value
        val summary = org.json.JSONObject().apply {
            put("appVersion", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("createdAt", System.currentTimeMillis())
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("androidVersion", Build.VERSION.RELEASE)
            put("apiLevel", Build.VERSION.SDK_INT)
            put("enabled", config.enabled)
            put("plugins", config.plugins)
            put("scopedPackages", config.packages)
            put("threads", config.threads)
            put("maxTasks", config.maxTasks)
            put("connections", config.connections)
            put("speedLimit", config.speedLimit)
            put("githubMirror", config.githubMirror)
            put("proxyMode", config.proxy.mode)
            put("proxyHost", config.proxy.host)
            put("proxyPort", config.proxy.port)
            put("taskCount", tasks.size)
            put("pendingCount", tasks.count { it.state == "待确认" })
            put("activeCount", tasks.count { it.state == "下载中" || it.state == "排队中" || it.state == "保存中" })
            put("failedCount", tasks.count { it.state == "失败" })
        }
        try {
            ZipOutputStream(report.outputStream().buffered()).use { zip ->
                fun entry(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                entry("README.txt", """
                    LeiFetch 诊断包

                    logs.txt    应用进程内的近期日志（下载、内核、镜像、代理、捕获）。
                    summary.json 版本、设备与设置摘要，以及任务数量统计。
                    logcat.txt  本进程最近的系统日志片段。

                    不含任务下载地址（日志中的地址只保留主机名）、不含已下载文件内容。
                    不需要 root，也读不到 LSPosed 的私有日志。
                """.trimIndent() + "\n")
                entry("logs.txt", text(entries))
                entry("summary.json", summary.toString(2))
                entry("logcat.txt", captureLogcat())
            }
            // 保留一天，够接收方读完授权 URI；再早的顺手清掉。
            directory.listFiles()?.filter {
                it.isFile && it.name.startsWith("LeiFetch_logs_") &&
                    it.lastModified() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            }?.forEach { it.delete() }
            report
        } catch (error: Exception) {
            report.delete()
            throw error
        }
    }

    /**
     * 只取本进程、最近的若干条系统日志。用 available() 轮询 + 截止时间控制，
     * 不用 ProcessBuilder.redirectOutput / Process.waitFor(timeout)（都是 API 26 起）。
     * 超时或不可用都不影响诊断包本身。
     */
    private fun captureLogcat(): String {
        var process: java.lang.Process? = null
        return try {
            process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=${Process.myPid()}", "-t", "400")
                .redirectErrorStream(true).start()
            val input = process.inputStream
            val text = StringBuilder()
            val buffer = ByteArray(16 * 1024)
            val deadline = System.currentTimeMillis() + 4000
            var timedOut = false
            while (true) {
                if (input.available() > 0) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    text.append(String(buffer, 0, n, Charsets.UTF_8))
                    if (text.length >= 1_000_000) break
                } else if (System.currentTimeMillis() > deadline) {
                    timedOut = true
                    break
                } else {
                    Thread.sleep(20)
                }
            }
            (if (timedOut) "日志收集超时，以下为部分内容。\n" else "") +
                text.toString().ifBlank { "本进程没有可用的系统日志。\n" }
        } catch (error: Exception) {
            "系统日志不可用：${error.javaClass.simpleName}\n"
        } finally {
            runCatching { process?.destroy() }
        }
    }
}
