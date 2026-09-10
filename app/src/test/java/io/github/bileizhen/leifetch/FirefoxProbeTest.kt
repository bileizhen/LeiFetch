package io.github.bileizhen.leifetch

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class FirefoxProbeTest {
    // 响应头复刻 speedtest.zju.edu.cn/1000M 的实测结果：206、强 ETag、Content-Range 总长 1000 MB。
    private val zjuTotal = 1_048_576_000L
    private val zjuEtag = "\"3e800000-56a7fe0a01cd3\""

    // GeckoView WebResponse.headers：服务器原始响应头（大小写保留）。
    private fun zjuGeckoHeaders(etag: String? = zjuEtag, length: Long? = zjuTotal) = buildMap {
        etag?.let { put("ETag", it) }
        put("Accept-Ranges", "bytes")
        put("Last-Modified", "Mon, 23 Apr 2018 08:32:25 GMT")
        length?.let { put("Content-Length", it.toString()) }
    }

    private fun zju206(code: Int = 206, etag: String? = zjuEtag, contentRange: String? = "bytes 0-0/$zjuTotal") =
        MockResponse().setResponseCode(code).apply {
            setHeader("Server", "none")
            etag?.let { setHeader("ETag", it) }
            setHeader("Accept-Ranges", "bytes")
            setHeader("Last-Modified", "Mon, 23 Apr 2018 08:32:25 GMT")
            contentRange?.let { setHeader("Content-Range", it) }
            setHeader("Content-Length", 1)
            setBody("0")
        }

    @Test fun takesOverZjuSpeedtestLink() {
        MockWebServer().use { server ->
            server.enqueue(zju206())
            val verified = FirefoxProbe.verify(server.url("/1000M").toString(), zjuGeckoHeaders())
            assertNotNull(verified)
            assertEquals(zjuEtag, verified!!.etag)
            assertEquals(zjuTotal, verified.totalLength)
            val request = server.takeRequest(2, TimeUnit.SECONDS)
            assertNotNull(request)
            assertEquals("bytes=0-0", request!!.getHeader("Range"))
            assertEquals("identity", request.getHeader("Accept-Encoding"))
        }
    }

    @Test fun acceptsUnknownReportedLength() {
        MockWebServer().use { server ->
            server.enqueue(zju206())
            assertNotNull(FirefoxProbe.verify(server.url("/1000M").toString(), zjuGeckoHeaders(length = null)))
        }
    }

    @Test fun matchesHeaderNamesCaseInsensitively() {
        MockWebServer().use { server ->
            server.enqueue(zju206())
            assertNotNull(FirefoxProbe.verify(server.url("/1000M").toString(),
                mapOf("etag" to zjuEtag, "content-length" to zjuTotal.toString())))
        }
    }

    @Test fun acceptsServerWithoutRangeSupport() {
        MockWebServer().use { server ->
            server.enqueue(zju206(code = 200, contentRange = null))
            assertNotNull(FirefoxProbe.verify(server.url("/file").toString(), zjuGeckoHeaders(length = 1)))
        }
    }

    @Test fun acceptsMissingGeckoEtag() {
        MockWebServer().use { server ->
            server.enqueue(zju206())
            assertNotNull(FirefoxProbe.verify(server.url("/1000M").toString(), zjuGeckoHeaders(etag = null)))
        }
    }

    @Test fun acceptsMissingProbeEtag() {
        MockWebServer().use { server ->
            server.enqueue(zju206(etag = null))
            assertNotNull(FirefoxProbe.verify(server.url("/1000M").toString(), zjuGeckoHeaders()))
        }
    }

    @Test fun rejectsEtagMismatch() {
        MockWebServer().use { server ->
            server.enqueue(zju206(etag = "\"different-etag\""))
            assertNull(FirefoxProbe.verify(server.url("/1000M").toString(), zjuGeckoHeaders()))
        }
    }

    @Test fun acceptsWeakEtagWithoutTreatingItAsStrong() {
        MockWebServer().use { server ->
            server.enqueue(zju206(etag = "W/$zjuEtag"))
            val result = FirefoxProbe.verify(server.url("/1000M").toString(), zjuGeckoHeaders(etag = "W/$zjuEtag"))
            assertNotNull(result)
            assertEquals("", result!!.etag)
        }
    }

    @Test fun rejectsLengthMismatch() {
        MockWebServer().use { server ->
            server.enqueue(zju206())
            assertNull(FirefoxProbe.verify(server.url("/1000M").toString(), zjuGeckoHeaders(length = 999)))
        }
    }

    @Test fun rejectsWildcardTotal() {
        MockWebServer().use { server ->
            server.enqueue(zju206(contentRange = "bytes 0-0/*"))
            assertNull(FirefoxProbe.verify(server.url("/1000M").toString(), zjuGeckoHeaders()))
        }
    }

    @Test fun rejectsRedirectToUnavailableDownload() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/elsewhere"))
            server.enqueue(MockResponse().setResponseCode(404))
            assertNull(FirefoxProbe.verify(server.url("/1000M").toString(), zjuGeckoHeaders()))
        }
    }

    @Test fun rejectsNonHttpScheme() {
        assertNull(FirefoxProbe.verify("ftp://speedtest.zju.edu.cn/1000M", zjuGeckoHeaders()))
    }

    @Test fun rejectsUserInfoUrl() {
        assertNull(FirefoxProbe.verify("http://user:pass@speedtest.zju.edu.cn/1000M", zjuGeckoHeaders()))
    }

    @Test fun returnsNullOnConnectionFailure() {
        assertNull(FirefoxProbe.verify("http://127.0.0.1:1/1000M", zjuGeckoHeaders(), connectTimeoutMs = 1000))
    }
}
