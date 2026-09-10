package io.github.bileizhen.leifetch

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bileizhen.leifetch.nsfx.NsfxConfig
import io.github.bileizhen.leifetch.nsfx.NsfxDownloadEngine
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise Firefox's admission probe and the actual downloader with the same endpoint. */
@RunWith(AndroidJUnit4::class)
class FirefoxDownloadTest {
    private val c = InstrumentationRegistry.getInstrumentation().targetContext
    private val payload = ByteArray(32 * 1024 + 17) { (it % 251).toByte() }

    private fun download(kind: String) = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (kind == "redirect" && request.path == "/download")
                        return MockResponse().setResponseCode(302).setHeader("Location", "/file.zip")
                    val ranged = request.getHeader("Range") != null
                    if (kind == "reject-range" && ranged) return MockResponse().setResponseCode(400)
                    val response = MockResponse().setHeader("Content-Type", "application/zip")
                    if (kind == "weak-etag") response.setHeader("ETag", "W/\"v1\"")
                    return when {
                        kind == "weak-etag" && ranged -> response.setResponseCode(206)
                            .setHeader("Content-Range", "bytes 0-0/${payload.size}").setBody(Buffer().write(payload, 0, 1))
                        kind == "chunked" -> response.setChunkedBody(Buffer().write(payload), 4096)
                        else -> response.setBody(Buffer().write(payload))
                    }
                }
            }
            val headers = buildMap {
                put("Content-Type", "application/zip")
                if (kind != "chunked") put("Content-Length", payload.size.toString())
                if (kind == "weak-etag") put("ETag", "W/\"v1\"")
            }
            val verified = FirefoxProbe.verify(server.url("/download").toString(), headers)
            assertNotNull("Firefox admission failed for $kind", verified)
            val task = Task(url = verified!!.url, name = "compat-$kind.zip")
            try {
                val file = NsfxDownloadEngine(c, NsfxConfig(maxRetries = 0)).download(task) { _, _, _ -> }
                assertArrayEquals(payload, file.readBytes())
            } finally { workDir(c, task.id).deleteRecursively() }
        }
    }

    @Test fun ordinaryDownload() = download("ordinary")
    @Test fun unknownLengthDownload() = download("chunked")
    @Test fun weakEtagDownload() = download("weak-etag")
    @Test fun redirectedDownload() = download("redirect")
    @Test fun serverRejectingRangeStillDownloads() = download("reject-range")
}
