package io.github.bileizhen.leifetch

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DiagTest {
    @Test fun pauseProbe() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = ByteArray(16 * 1024 * 1024) { (it % 173).toByte() }
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(request.getHeader("Range").orEmpty())!!
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
                return MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $a-$b/${data.size}")
                    .setHeader("ETag", "\"diag\"").setBody(Buffer().write(data, a, b - a + 1))
                    .throttleBody(64 * 1024, 60, TimeUnit.MILLISECONDS)
            }
        }
        server.start()
        val task = Task(url = server.url("/diag.bin").toString(), name = "diag.bin")
        try {
            context.app.ready.await()
            context.app.store.put(task)
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).putExtra("id", task.id).putExtra("op", "start"))
            }
            withTimeout(20_000) { while ((context.app.store.get(task.id)?.done ?: 0) == 0L) delay(200) }
            delay(500)
            var t = context.app.store.get(task.id)!!
            println("DIAG before pause: state=${t.state} done=${t.done} total=${t.total}")
            context.startService(Intent(context, DownloadService::class.java).putExtra("id", task.id).putExtra("op", "pause"))
            repeat(10) { i ->
                delay(1000)
                t = context.app.store.get(task.id)!!
                println("DIAG t+${i + 1}s: state=${t.state} done=${t.done} speed=${t.speed}")
                if (i == 4) for ((thread, frames) in Thread.getAllStackTraces()) {
                    val busy = frames.any { it.className.contains("leifetch") } ||
                        (thread.name.contains("DefaultDispatcher") && frames.any { it.className.contains("kotlinx") })
                    if (busy) {
                        println("DIAG THREAD ${thread.name} state=${thread.state}:")
                        var count = 0
                        for (frame in frames) { println("DIAG   $frame"); if (++count >= 18) break }
                    }
                }
            }
        } finally {
            context.app.store.get(task.id)?.uri?.takeIf { it.isNotEmpty() }?.let { context.contentResolver.delete(android.net.Uri.parse(it), null, null) }
            context.app.store.remove(task.id)
            context.stopService(Intent(context, DownloadService::class.java))
            server.shutdown()
        }
    }
}
