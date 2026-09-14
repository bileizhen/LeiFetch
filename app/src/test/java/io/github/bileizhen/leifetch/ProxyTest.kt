package io.github.bileizhen.leifetch

import io.github.bileizhen.leifetch.nsfx.NsfxConfig
import io.github.bileizhen.leifetch.nsfx.NsfxHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.URL
import kotlin.concurrent.thread

class ProxyTest {
    private val target = URL("https://github.com/user/repo/releases/download/v1/app.apk")

    private fun httpProxy(host: String, port: Int) = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
    private fun socksProxy(host: String, port: Int) = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port))
    private fun portOf(proxy: Proxy?): Int = (proxy?.address() as InetSocketAddress).port

    // 不使用代理：即便系统与手动都配置了代理也直连。
    @Test fun 不使用代理始终直连() {
        val settings = ProxySettings(mode = ProxyMode.NONE, host = "127.0.0.1", port = 7890)
        assertNull(ProxyResolver.resolve(settings, target, { httpProxy("10.0.0.1", 8080) }, { httpProxy("127.0.0.1", 7890) }))
    }

    // 系统代理只跟随系统，不会顺带去探测本机端口。
    @Test fun 系统代理只跟随系统设置() {
        val settings = ProxySettings(mode = ProxyMode.SYSTEM)
        assertEquals(8080, portOf(ProxyResolver.resolve(settings, target, { httpProxy("10.0.0.1", 8080) }, { httpProxy("127.0.0.1", 7890) })))
        assertNull(ProxyResolver.resolve(settings, target, { null }, { httpProxy("127.0.0.1", 7890) }))
    }

    // 自动：系统代理优先；系统没有时才探测本机端口。
    @Test fun 自动模式先系统后探测() {
        val settings = ProxySettings(mode = ProxyMode.AUTO)
        assertEquals(8080, portOf(ProxyResolver.resolve(settings, target, { httpProxy("10.0.0.1", 8080) }, { httpProxy("127.0.0.1", 7890) })))
        assertEquals(7890, portOf(ProxyResolver.resolve(settings, target, { null }, { httpProxy("127.0.0.1", 7890) })))
        assertNull(ProxyResolver.resolve(settings, target, { null }, { null }))
    }

    @Test fun 手动模式使用配置的服务器() {
        val http = ProxySettings(mode = ProxyMode.MANUAL, host = "10.0.0.1", port = 3128)
        val resolved = ProxyResolver.resolve(http, target, { null }, { null })
        assertEquals(Proxy.Type.HTTP, resolved?.type())
        assertEquals(3128, portOf(resolved))
        val socks = http.copy(type = ProxyType.SOCKS)
        assertEquals(Proxy.Type.SOCKS, ProxyResolver.resolve(socks, target, { null }, { null })?.type())
    }

    // 手动配置不完整（没填服务器或端口越界）时退回直连，而不是拿一个坏代理去请求。
    @Test fun 手动配置不完整时直连() {
        for (settings in listOf(
            ProxySettings(mode = ProxyMode.MANUAL),
            ProxySettings(mode = ProxyMode.MANUAL, host = "10.0.0.1"),
            ProxySettings(mode = ProxyMode.MANUAL, host = "10.0.0.1", port = 70000),
            ProxySettings(mode = ProxyMode.MANUAL, port = 3128))) {
            assertFalse(settings.usable)
            assertNull(ProxyResolver.resolve(settings, target, { null }, { null }))
        }
    }

    @Test fun 直连名单跳过代理() {
        val base = ProxySettings(mode = ProxyMode.MANUAL, host = "127.0.0.1", port = 7890)
        fun bypassed(raw: String, url: String = target.toExternalForm()) =
            ProxyResolver.resolve(base.copy(bypass = raw), URL(url), { null }, { null }) == null

        assertTrue(bypassed("github.com"))
        assertTrue(bypassed("github.com", "https://api.github.com/repos/x"))   // 子域命中
        assertTrue(bypassed(".github.com"))                                    // 前导点归一化
        assertTrue(bypassed("*"))
        assertTrue(bypassed("<local>", "http://192.168.1.10:8000/a.bin"))
        assertTrue(bypassed("<local>", "http://nas/a.bin"))
        assertFalse(bypassed("example.com"))
        assertFalse(bypassed("<local>"))
        assertFalse(bypassed(""))
    }

    @Test fun 直连名单归一化与去重() {
        assertEquals(listOf("a.com", "b.com", "<local>", "*"),
            ProxySettings.parseBypass(" .a.com, b.com\nA.com ; <local> * "))
        assertTrue(ProxySettings.parseBypass("  ").isEmpty())
    }

    @Test fun 代理配置编码往返() {
        val settings = ProxySettings(ProxyMode.MANUAL, ProxyType.SOCKS, "10.0.0.1", 1080, "user", "pass", "a.com,b.com")
        assertEquals(settings, ProxySettings.decode(settings.encode()))
        // 跨进程通道缺值时按直连处理，避免宿主应用意外走代理。
        assertEquals(ProxyMode.NONE, ProxySettings.decode(null).mode)
        assertEquals(ProxyMode.NONE, ProxySettings.decode("").mode)
        assertEquals(ProxyMode.NONE, ProxySettings.decode("garbage").mode)
    }

    @Test fun 描述当前线路() {
        val manual = ProxySettings(mode = ProxyMode.MANUAL, host = "127.0.0.1", port = 7890)
        assertEquals("手动配置 · HTTP 代理 127.0.0.1:7890", describeRoute(manual, httpProxy("127.0.0.1", 7890)))
        assertEquals("自动（推荐） · SOCKS5 代理 127.0.0.1:1080", describeRoute(ProxySettings(mode = ProxyMode.AUTO), socksProxy("127.0.0.1", 1080)))
        assertEquals("手动配置不完整，将直连", describeRoute(manual, null))
        assertEquals("未发现可用代理，将直连", describeRoute(ProxySettings(mode = ProxyMode.AUTO), null))
        assertEquals("系统未配置代理，将直连", describeRoute(ProxySettings(mode = ProxyMode.SYSTEM), null))
        assertEquals("不使用代理", describeRoute(ProxySettings(mode = ProxyMode.NONE), null))
        assertEquals("直连", describeProxy(null))
    }

    // 自动探测必须真的握手确认：端口在听不等于端口后面是代理。
    @Test fun 自动探测识别HTTP代理() {
        val server = fakeProxy { socket ->
            socket.getInputStream().bufferedReader().readLine()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray()); flush()
            }
            Thread.sleep(300)
        }
        server.use { assertTrue(AutoProxy.speaksHttp(it.localPort)) }
    }

    @Test fun 自动探测识别SOCKS5代理() {
        val server = fakeProxy { socket ->
            val greeting = ByteArray(3)
            if (socket.getInputStream().read(greeting) == 3)
                socket.getOutputStream().apply { write(byteArrayOf(0x05, 0x00)); flush() }
            Thread.sleep(300)
        }
        server.use { assertTrue(AutoProxy.speaksSocks(it.localPort)) }
    }

    // 端口在听但不回代理握手（如普通 Web 服务、直接挂断的端口）不能判为代理。
    @Test fun 自动探测跳过非代理端口() {
        val server = fakeProxy { Thread.sleep(300) }
        server.use {
            assertFalse(AutoProxy.speaksHttp(it.localPort))
            assertFalse(AutoProxy.speaksSocks(it.localPort))
            assertNull(AutoProxy.detect(listOf(it.localPort)))
        }
        val closed = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).also { it.close() }.localPort
        assertNull(AutoProxy.detect(listOf(closed)))
        assertNull(AutoProxy.detect(emptyList()))
    }

    @Test fun 自动探测返回带协议的代理() {
        val server = fakeProxy { socket ->
            val greeting = ByteArray(3)
            if (socket.getInputStream().read(greeting) == 3)
                socket.getOutputStream().apply { write(byteArrayOf(0x05, 0x00)); flush() }
            Thread.sleep(300)
        }
        server.use {
            val detected = AutoProxy.detect(listOf(it.localPort))
            assertEquals(Proxy.Type.SOCKS, detected?.type())
            assertEquals("127.0.0.1", (detected!!.address() as InetSocketAddress).hostString)
            assertEquals(it.localPort, portOf(detected))
        }
    }

    // 代理接线：解析结果必须真的落到 HttpURLConnection 上——指向不可达代理时请求应当失败。
    @Test fun 下载请求按解析结果选择线路(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
            val task = Task(url = server.url("/f").toString(), name = "f")
            NsfxHttpClient(NsfxConfig()) { null }.get(task).use { assertEquals(200, it.code) }
            val blocked = NsfxHttpClient(NsfxConfig()) { httpProxy("127.0.0.1", 1) }
            assertThrows(IOException::class.java) { runBlocking { blocked.get(task).close() } }
        }
    }

    /** 假代理：接受一个连接后交给 [handler]，随后关闭。 */
    private fun fakeProxy(handler: (java.net.Socket) -> Unit): ServerSocket {
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return@thread
                runCatching { handler(socket) }
                runCatching { socket.close() }
            }
        }
        return server
    }
}
