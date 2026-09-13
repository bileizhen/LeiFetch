package io.github.bileizhen.leifetch

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bileizhen.leifetch.nsfx.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val payload = ByteArray(3 * 1024 * 1024 + 37) { (it % 251).toByte() }
    private fun server(data: ByteArray, range: Boolean = true, tag: String = "\"v1\"", slow: Boolean = false,
                       seen: MutableList<String> = CopyOnWriteArrayList()): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val header = request.getHeader("Range")
                seen += header.orEmpty()
                val response = MockResponse().setHeader("ETag", tag)
                if (range && header != null) {
                    val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(header)!!
                    val start = m.groupValues[1].toInt(); val end = m.groupValues[2].toInt()
                    response.setResponseCode(206).setHeader("Content-Range", "bytes $start-$end/${data.size}")
                        .setBody(Buffer().write(data, start, end - start + 1))
                } else response.setBody(Buffer().write(data))
                if (slow && header != "bytes=0-0") response.throttleBody(32 * 1024, 20, TimeUnit.MILLISECONDS)
                return response
            }
        }
        start()
    }
    @Test fun multiThreadResultMatchesEveryByte() = runBlocking {
        server(payload).use { s ->
            val task = Task(url = s.url("/file.bin").toString(), name = "file.bin")
            try { assertArrayEquals(payload, NsfxDownloadEngine(context, NsfxConfig(threads = 4, mode = "threads_only")).download(task, { _, _, _ -> }).readBytes()) }
            finally { workDir(context, task.id).deleteRecursively() }
        }
    }
    @Test fun ignoringRangeFallsBackToOneWholeBody() = runBlocking {
        val seen = CopyOnWriteArrayList<String>()
        server(payload, range = false, seen = seen).use { s ->
            val task = Task(url = s.url("/file.bin").toString(), name = "file.bin")
            try {
                assertArrayEquals(payload, NsfxDownloadEngine(context, NsfxConfig(threads = 8, mode = "threads_only")).download(task, { _, _, _ -> }).readBytes())
                assertEquals(listOf("bytes=0-0", ""), seen.toList())
            } finally { workDir(context, task.id).deleteRecursively() }
        }
    }
    @Test fun knownSmallFileSkipsProbe() = runBlocking {
        val seen = CopyOnWriteArrayList<String>()
        server(payload, seen = seen).use { s ->
            val task = Task(url = s.url("/small.bin").toString(), name = "small.bin",
                expectedSize = payload.size.toLong())
            try {
                assertArrayEquals(payload, NsfxDownloadEngine(context, NsfxConfig(threads = 8))
                    .download(task, { _, _, _ -> }).readBytes())
                assertEquals(listOf(""), seen.toList())
            } finally { workDir(context, task.id).deleteRecursively() }
        }
    }
    @Test fun lastModifiedEnablesSafeSegmentedDownloadWithoutEtag() = runBlocking {
        val modified = "Mon, 23 Apr 2018 08:32:25 GMT"
        val validators = CopyOnWriteArrayList<String>()
        val s = MockWebServer()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                validators += request.getHeader("If-Range").orEmpty()
                val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(request.getHeader("Range")!!)!!
                val start = m.groupValues[1].toInt()
                val end = m.groupValues[2].toInt()
                return MockResponse().setResponseCode(206)
                    .setHeader("Last-Modified", modified)
                    .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                    .setBody(Buffer().write(payload, start, end - start + 1))
            }
        }
        s.start()
        s.use {
            val task = Task(url = s.url("/modified.bin").toString(), name = "modified.bin")
            try {
                assertArrayEquals(payload, NsfxDownloadEngine(context,
                    NsfxConfig(threads = 1, mode = "threads_only")).download(task, { _, _, _ -> }).readBytes())
                assertEquals(listOf("", modified), validators.toList())
            } finally { workDir(context, task.id).deleteRecursively() }
        }
    }
    @Test fun cancellationThenResumeUsesNonzeroRangeOffset() = runBlocking {
        val seen = CopyOnWriteArrayList<String>()
        server(payload, slow = true, seen = seen).use { s ->
            val task = Task(url = s.url("/resume.bin").toString(), name = "resume.bin")
            try {
                val engine = NsfxDownloadEngine(context, NsfxConfig(threads = 1, mode = "threads_only"))
                val job = launch(Dispatchers.IO) { engine.download(task, { _, _, _ -> }) }
                delay(400); job.cancelAndJoin()
                val checkpoint = java.io.File(workDir(context, task.id), "0.offset").readText().toLong()
                assertTrue(checkpoint > 0)
                assertArrayEquals(payload, engine.download(task, { _, _, _ -> }).readBytes())
                assertTrue(seen.contains("bytes=$checkpoint-${payload.lastIndex}"))
            } finally { workDir(context, task.id).deleteRecursively() }
        }
    }
    @Test fun changedValidatorDoesNotReuseOldParts() = runBlocking {
        server(payload).use { s ->
            val task = Task(url = s.url("/change.bin").toString(), name = "change.bin")
            try {
                val engine = NsfxDownloadEngine(context, NsfxConfig(threads = 1, mode = "threads_only"))
                engine.download(task, { _, _, _ -> })
                val changed = ByteArray(payload.size) { 17 }
                s.dispatcher = object : Dispatcher() {
                    override fun dispatch(r: RecordedRequest): MockResponse {
                        val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(r.getHeader("Range")!!)!!
                        val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
                        return MockResponse().setResponseCode(206).setHeader("ETag", "\"v2\"")
                            .setHeader("Content-Range", "bytes $a-$b/${changed.size}")
                            .setBody(Buffer().write(changed, a, b - a + 1))
                    }
                }
                assertArrayEquals(changed, engine.download(task, { _, _, _ -> }).readBytes())
            } finally { workDir(context, task.id).deleteRecursively() }
        }
    }
    @Test fun rotatingSignedRedirectsStillDownloadAndResume() = runBlocking {
        val counter = java.util.concurrent.atomic.AtomicInteger()
        val s = MockWebServer()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/jump") {
                    val n = counter.incrementAndGet()
                    return MockResponse().setResponseCode(302).setHeader("Location", "/edge-$n/data.apk?sign=token-$n")
                }
                val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(request.getHeader("Range")!!)!!
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
                val r = MockResponse().setResponseCode(206).setHeader("ETag", "\"stable\"")
                    .setHeader("Content-Range", "bytes $a-$b/${payload.size}")
                    .setBody(Buffer().write(payload, a, b - a + 1))
                if (a != 0 || b != 0) r.throttleBody(32 * 1024, 20, TimeUnit.MILLISECONDS)
                return r
            }
        }
        s.start()
        s.use {
            val task = Task(url = s.url("/jump").toString(), name = "sgame.apk")
            try {
                val engine = NsfxDownloadEngine(context, NsfxConfig(threads = 1, mode = "threads_only"))
                val job = launch(Dispatchers.IO) { engine.download(task, { _, _, _ -> }) }
                delay(400); job.cancelAndJoin()
                val checkpoint = java.io.File(workDir(context, task.id), "0.offset").readText().toLong()
                assertTrue("transfer must survive differing redirect targets", checkpoint > 0)
                assertTrue(counter.get() >= 2)
                assertArrayEquals(payload, engine.download(task, { _, _, _ -> }).readBytes())
                assertTrue(counter.get() >= 3)
            } finally { workDir(context, task.id).deleteRecursively() }
        }
    }
    @Test fun liveNotificationHasPromotableCharacteristics() {
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            val n = Notices.progress(context, Task(url = "https://example.invalid/a", name = "测试下载", done = 25, total = 100, state = "下载中"))
            assertTrue(n.hasPromotableCharacteristics())
        }
    }
    @Test fun dynamicTailSplitPreservesEveryByte() = runBlocking {
        val data = ByteArray(40 * 1024 * 1024) { (it % 239).toByte() }
        val s = MockWebServer()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(request.getHeader("Range")!!)!!
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
                val r = MockResponse().setResponseCode(206).setHeader("ETag", "\"tail\"")
                    .setHeader("Content-Range", "bytes $a-$b/${data.size}")
                    .setBody(Buffer().write(data, a, b - a + 1))
                if (a >= data.size / 2) r.throttleBody(128 * 1024, 30, TimeUnit.MILLISECONDS)
                return r
            }
        }
        s.start()
        s.use {
            val task = Task(url = s.url("/tail.bin").toString(), name = "tail.bin")
            try {
                val engine = NsfxDownloadEngine(context, NsfxConfig(threads = 2, segments = 2, mode = "manual", maxRetries = 2))
                val file = withTimeout(60_000) { engine.download(task, { _, _, _ -> }) }
                assertArrayEquals(data, file.readBytes())
                val journal = org.json.JSONObject(java.io.File(workDir(context, task.id), "segments.json").readText())
                assertTrue("Expected an actual tail split", journal.getJSONArray("segments").length() > 2)
            } finally { workDir(context, task.id).deleteRecursively() }
        }
    }
}
