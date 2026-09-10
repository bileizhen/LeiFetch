package io.github.bileizhen.leifetch

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

/** Ordinary download responses must not require the speed-test server's range/ETag combination. */
class FirefoxCompatibilityTest {
    @Test fun acceptsOrdinaryDownloadWithoutEtagOrRanges() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("file").setHeader("Content-Type", "application/zip"))
            assertNotNull(FirefoxProbe.verify(server.url("/release.zip").toString(),
                mapOf("Content-Type" to "application/zip", "Content-Length" to "4")))
        }
    }

    @Test fun acceptsChunkedDownloadWithoutKnownSize() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setChunkedBody("file", 2).setHeader("Content-Type", "application/octet-stream"))
            assertNotNull(FirefoxProbe.verify(server.url("/export").toString(),
                mapOf("Content-Type" to "application/octet-stream")))
        }
    }

    @Test fun acceptsWeakEtagWithRangeSupport() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setBody("f")
                .setHeader("ETag", "W/\"release\"").setHeader("Content-Range", "bytes 0-0/4"))
            assertNotNull(FirefoxProbe.verify(server.url("/release.apk").toString(),
                mapOf("ETag" to "W/\"release\"", "Content-Length" to "4")))
        }
    }

    @Test fun followsDownloadRedirect() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/files/release.zip"))
            server.enqueue(MockResponse().setBody("file").setHeader("Content-Type", "application/zip"))
            val verified = FirefoxProbe.verify(server.url("/download").toString(),
                mapOf("Content-Type" to "application/zip", "Content-Length" to "4"))
            assertNotNull(verified)
            assertEquals(server.url("/files/release.zip").toString(), verified!!.url)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun retriesWithoutRangeWhenServerRejectsIt() {
        for (status in listOf(400, 405, 416)) MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(status))
            server.enqueue(MockResponse().setBody("file"))
            assertNotNull(FirefoxProbe.verify(server.url("/download").toString(), mapOf("Content-Length" to "4")))
            assertEquals("bytes=0-0", server.takeRequest().getHeader("Range"))
            val request = server.takeRequest()
            assertNull(request.getHeader("Range"))
            assertEquals("LeiFetch-NSFX/0.1", request.getHeader("User-Agent"))
        }
    }

    @Test fun rejectsLoginPageEvenWhenItsSizeMatches() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("html").setHeader("Content-Type", "text/html; charset=utf-8"))
            var reason = ""
            assertNull(FirefoxProbe.verify(server.url("/private.zip").toString(),
                mapOf("Content-Type" to "application/zip", "Content-Length" to "4"), onRejected = { reason = it }))
            assertTrue(reason.contains("登录状态"))
        }
    }

    @Test fun permitsExplicitHtmlFileDownload() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("html").setHeader("Content-Type", "text/html"))
            assertNotNull(FirefoxProbe.verify(server.url("/page.html").toString(), mapOf("Content-Type" to "text/html")))
        }
    }

    @Test fun rejectsHttpErrorsAndReportsStatus() {
        for (status in listOf(401, 403, 404, 500)) MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(status))
            var reason = ""
            assertNull(FirefoxProbe.verify(server.url("/file.zip").toString(), emptyMap(), onRejected = { reason = it }))
            assertTrue(reason.contains(status.toString()))
        }
    }

    @Test fun boundsRedirectLoops() {
        MockWebServer().use { server ->
            repeat(6) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/again")) }
            assertNull(FirefoxProbe.verify(server.url("/again").toString(), emptyMap()))
            assertEquals(6, server.requestCount)
        }
    }

    @Test fun rejectsInvalidPartialResponse() {
        for (range in listOf("bytes 1-1/4", "bytes 0-3/4", "bytes 0-0/0", "nonsense/4")) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(206).setBody("f").setHeader("Content-Range", range))
                assertNull(FirefoxProbe.verify(server.url("/file.zip").toString(), emptyMap()))
            }
        }
    }

    @Test fun rejectsCompressedRepresentationUnsupportedByDownloader() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("file").setHeader("Content-Encoding", "gzip"))
            assertNull(FirefoxProbe.verify(server.url("/file.zip").toString(), emptyMap()))
        }
    }

    @Test fun acceptsEmptyFileAfterRangeRejection() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */0"))
            server.enqueue(MockResponse().setBody(""))
            val verified = FirefoxProbe.verify(server.url("/empty.txt").toString(), mapOf("Content-Length" to "0"))
            assertNotNull(verified)
            assertEquals(0L, verified!!.totalLength)
        }
    }
}
