package io.github.bileizhen.leifetch

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** 代理模式，与设置页的四项一一对应。 */
object ProxyMode {
    const val NONE = "none"
    const val SYSTEM = "system"
    const val MANUAL = "manual"
    const val AUTO = "auto"
    val all = listOf(NONE, SYSTEM, MANUAL, AUTO)
    fun label(mode: String): String = when (mode) {
        NONE -> "不使用代理"
        SYSTEM -> "系统代理"
        MANUAL -> "手动配置"
        else -> "自动（推荐）"
    }
    fun summary(mode: String): String = when (mode) {
        NONE -> "所有下载与探测直连，忽略系统代理"
        SYSTEM -> "跟随 Wi-Fi 高级设置或 VPN 应用写入的系统代理"
        MANUAL -> "指定代理服务器，可设账号密码与直连名单"
        else -> "优先系统代理，其次探测本机常见代理端口，都不可用时直连"
    }
}

/** 代理协议；自动探测出的代理也复用同一组取值。 */
object ProxyType {
    const val HTTP = "http"
    const val SOCKS = "socks"
    fun label(type: String): String = if (type == SOCKS) "SOCKS5" else "HTTP"
}

data class ProxySettings(
    /** 默认跟随系统代理：与未引入代理配置时的行为一致，不改变任何既有用户的线路。 */
    val mode: String = ProxyMode.SYSTEM,
    val type: String = ProxyType.HTTP,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    /** 直连名单：域名后缀、`*` 通配，或 `<local>`（localhost 与内网地址）。 */
    val bypass: String = ""
) {
    /** 主机与端口都合法才可用于手动模式。 */
    val usable get() = host.isNotBlank() && port in 1..65535

    /** 手动配置的代理；配置不完整时返回 null，由调用方退回直连。 */
    fun manual(): Proxy? = if (!usable) null else Proxy(
        if (type == ProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP,
        InetSocketAddress.createUnresolved(host, port))

    /** 该地址是否在直连名单内（不走代理）。 */
    fun bypasses(url: URL): Boolean {
        val host = url.host?.lowercase().orEmpty()
        if (host.isEmpty()) return false
        for (entry in bypassEntries()) {
            if (entry == "*") return true
            if (entry == "<local>" && isLocalHost(host)) return true
            if (host == entry || host.endsWith(".$entry")) return true
        }
        return false
    }

    fun bypassEntries(): List<String> = parseBypass(bypass)

    /** hook 进程只能经 RemotePreferences 读到字符串，故用不可见字符拼一个紧凑串。 */
    fun encode(): String =
        listOf(mode, type, host, port.toString(), username, password, bypass).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "\u0001"
        fun parseBypass(raw: String): List<String> = raw.split(Regex("[\\s,;]+"))
            .map { it.trim().trimStart('.').lowercase() }.filter { it.isNotEmpty() }.distinct()

        /** 解析 hook 偏好里的代理串；缺失或损坏时按直连处理，不让宿主进程意外走代理。 */
        fun decode(raw: String?): ProxySettings {
            val parts = raw.orEmpty().split(SEPARATOR)
            if (parts.size < 7) return ProxySettings(mode = ProxyMode.NONE)
            return ProxySettings(
                mode = parts[0].takeIf { it in ProxyMode.all } ?: ProxyMode.SYSTEM,
                type = parts[1].takeIf { it == ProxyType.SOCKS } ?: ProxyType.HTTP,
                host = parts[2], port = parts[3].toIntOrNull() ?: 0,
                username = parts[4], password = parts[5], bypass = parts[6])
        }

        private fun isLocalHost(host: String): Boolean = host == "localhost" || host == "::1" || host == "[::1]" ||
            !host.contains('.') || host.startsWith("127.") || host.startsWith("10.") ||
            host.startsWith("192.168.") || host.startsWith("169.254.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host)
    }
}

/**
 * 代理解析的纯逻辑：直连名单与模式决定用哪个代理，系统代理与自动探测由调用方注入，
 * 这样模式分支可以脱离 Android 环境单测。
 */
object ProxyResolver {
    fun resolve(settings: ProxySettings, url: URL, system: (URL) -> Proxy?, auto: () -> Proxy?): Proxy? {
        if (settings.bypasses(url)) return null
        return when (settings.mode) {
            ProxyMode.NONE -> null
            ProxyMode.MANUAL -> settings.manual()
            ProxyMode.SYSTEM -> system(url)
            else -> system(url) ?: auto()
        }
    }
}

/** 代理的可读描述，如 `HTTP 代理 127.0.0.1:7890`；null 表示直连。 */
fun describeProxy(proxy: Proxy?): String {
    val address = proxy?.address() as? InetSocketAddress ?: return "直连"
    val type = if (proxy.type() == Proxy.Type.SOCKS) ProxyType.SOCKS else ProxyType.HTTP
    return "${ProxyType.label(type)} 代理 ${address.hostString}:${address.port}"
}

/** 当前模式下这次请求实际会走的线路，用于设置页摘要与连接测试结果。 */
fun describeRoute(settings: ProxySettings, proxy: Proxy?): String = when {
    proxy != null -> "${ProxyMode.label(settings.mode)} · ${describeProxy(proxy)}"
    settings.mode == ProxyMode.NONE -> "不使用代理"
    settings.mode == ProxyMode.AUTO -> "未发现可用代理，将直连"
    settings.mode == ProxyMode.MANUAL -> "手动配置不完整，将直连"
    else -> "系统未配置代理，将直连"
}

/**
 * 把 [ProxySettings] 解析成具体代理。系统代理取自默认 [ProxySelector]（系统属性）与
 * ConnectivityManager；自动模式先跟随系统代理，再探测本机常见代理端口。
 * 自动探测结果带 TTL 缓存，探测只做 TCP 连接与协议握手，不产生实际下载流量。
 */
class ProxyManager(private val context: Context, private val settings: () -> ProxySettings = { ProxySettings() }) {
    private val lock = Any()
    private var detected: Proxy? = null
    private var detectedAt = 0L

    fun forUrl(url: URL): Proxy? {
        val config = settings()
        val proxy = runCatching { ProxyResolver.resolve(config, url, ::system, ::auto) }.getOrNull()
        // HttpURLConnection 只在收到 407 时才问 Authenticator，这里按当前手动配置登记凭据。
        if (proxy != null && config.mode == ProxyMode.MANUAL) ProxyAuth.remember(config)
        return proxy
    }

    fun forUrl(url: String): Proxy? = runCatching { URL(url) }.getOrNull()?.let(::forUrl)

    /** 丢弃自动探测缓存：设置变化或用户主动重新检测时调用。 */
    fun invalidate() = synchronized(lock) { detected = null; detectedAt = 0L }

    private fun auto(): Proxy? = synchronized(lock) {
        val now = System.currentTimeMillis()
        if (detectedAt != 0L && now - detectedAt < AUTO_TTL_MS) return detected
        detected = AutoProxy.detect()
        detectedAt = now
        detected
    }

    private fun system(url: URL): Proxy? {
        // 默认 ProxySelector 读的是系统属性（http.proxyHost / socksProxyHost），
        // Wi-Fi 高级设置里的代理与 VPN 应用写入的代理都由此生效。
        val selected = runCatching { ProxySelector.getDefault()?.select(url.toURI())?.firstOrNull() }.getOrNull()
        if (selected != null && selected.type() != Proxy.Type.DIRECT && selected.address() != null) return selected
        // 个别 ROM 只把代理写进 ConnectivityManager，不落系统属性。
        val info = runCatching { context.getSystemService(ConnectivityManager::class.java)?.defaultProxy }.getOrNull()
        if (info != null) {
            val host = info.host
            if (!host.isNullOrBlank() && info.port in 1..65535)
                return Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, info.port))
        }
        return null
    }

    private companion object {
        const val AUTO_TTL_MS = 5 * 60_000L
    }
}

/** 本机代理自动探测：先看端口是否在监听，再按 HTTP CONNECT / SOCKS5 握手确认确实是代理。 */
object AutoProxy {
    const val host = "127.0.0.1"
    /** 常见代理客户端的默认监听端口：Clash / Clash Verge / v2rayN / sing-box / Privoxy / 抓包工具。 */
    private val ports = listOf(7890, 7897, 10809, 10808, 1080, 1081, 8118, 8888, 20171, 8080)
    /** 本机端口要么立刻连上、要么立刻被拒，150 ms 足够，不会拖慢下载启动。 */
    private const val connectTimeoutMs = 150
    private const val handshakeTimeoutMs = 600

    fun detect(): Proxy? = detect(ports)

    /** 按给定端口顺序探测；返回第一个通过握手的代理。 */
    internal fun detect(candidates: List<Int>): Proxy? {
        for (port in candidates) {
            if (!reachable(port)) continue
            if (speaksHttp(port)) return proxy(ProxyType.HTTP, port)
            // Clash 的 mixed-port 同时收 HTTP 与 SOCKS5，HTTP 握手失败时再试 SOCKS。
            if (speaksSocks(port)) return proxy(ProxyType.SOCKS, port)
        }
        return null
    }

    private fun proxy(type: String, port: Int) = Proxy(
        if (type == ProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP,
        InetSocketAddress.createUnresolved(host, port))

    /** HTTP 代理校验：CONNECT 到任意可解析目标，返回 2xx 即认为可用。 */
    internal fun speaksHttp(port: Int): Boolean = withSocket(port, handshakeTimeoutMs) { socket ->
        val request = "CONNECT www.example.com:443 HTTP/1.1\r\nHost: www.example.com:443\r\n\r\n"
        socket.getOutputStream().apply { write(request.toByteArray()); flush() }
        val status = socket.getInputStream().bufferedReader().readLine().orEmpty()
        status.startsWith("HTTP/1.") && status.split(' ').getOrNull(1)?.startsWith("2") == true
    }

    /** SOCKS5 校验：发送无认证握手，服务端回 `05 00` 即为 SOCKS5 代理。 */
    internal fun speaksSocks(port: Int): Boolean = withSocket(port, handshakeTimeoutMs) { socket ->
        socket.getOutputStream().apply { write(byteArrayOf(0x05, 0x01, 0x00)); flush() }
        val reply = ByteArray(2)
        socket.getInputStream().read(reply) == 2 && reply[0] == 0x05.toByte() && reply[1] == 0x00.toByte()
    }

    private fun reachable(port: Int): Boolean = withSocket(port, connectTimeoutMs) { true }

    private inline fun withSocket(port: Int, timeoutMs: Int, block: (Socket) -> Boolean): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            block(socket)
        }
    } catch (_: IOException) {
        false
    }
}

/** 用下载内核同一套探测请求验证当前线路，返回是否连通与可读描述。 */
object ProxyTester {
    suspend fun test(manager: ProxyManager, settings: ProxySettings,
                     url: String = GithubMirrors.speedTestTarget): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val proxy = manager.forUrl(url)
            val route = describeRoute(settings, proxy)
            val probe = runCatching {
                GithubMirrors.probe(url, timeoutMs = 8000, followRedirects = true, proxy = proxy)
            }.getOrNull()
            if (probe != null) true to "$route · 首字节 ${probe.latencyMs} ms" else false to "$route · 连接失败"
        }
}

/**
 * 进程级代理认证：HttpURLConnection 只在收到 407 时才询问 Authenticator，
 * 这里只应答当前手动配置的代理主机与端口，其它请求一律拒绝。
 */
private object ProxyAuth {
    private val installed = AtomicBoolean(false)
    @Volatile private var host = ""
    @Volatile private var port = 0
    @Volatile private var user = ""
    @Volatile private var password = ""

    fun remember(settings: ProxySettings) {
        if (settings.username.isEmpty() || !settings.usable) return
        host = settings.host
        port = settings.port
        user = settings.username
        password = settings.password
        if (!installed.compareAndSet(false, true)) return
        runCatching {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? =
                    if (requestorType == RequestorType.PROXY && requestingHost.equals(host, true) && requestingPort == port)
                        PasswordAuthentication(user, password.toCharArray())
                    else null
            })
        }
    }
}
