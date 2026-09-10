package io.github.bileizhen.leifetch

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ServiceFlowTest {
    @Test fun servicePauseResumeAndCompletionNotification() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val data = ByteArray(16 * 1024 * 1024) { (it % 173).toByte() }
        val server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(request.getHeader("Range").orEmpty())!!
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
                return MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $a-$b/${data.size}")
                    .setHeader("ETag", "\"service\"").setBody(Buffer().write(data, a, b - a + 1))
                    .throttleBody(64 * 1024, 200, TimeUnit.MILLISECONDS)
            }
        }
        server.start()
        val task = Task(url = server.url("/NSFX-service-test.bin").toString(), name = "NSFX-service-test.bin")
        try {
            context.app.ready.await()
            context.app.store.put(task)
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            delay(1000)
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).putExtra("id", task.id).putExtra("op", "start"))
            }
            withTimeout(20_000) { while ((context.app.store.get(task.id)?.done ?: 0) == 0L) delay(200) }
            withTimeout(5000) { while (manager.activeNotifications.none { it.id == Notices.ACTIVE_ID }) delay(100) }
            val live = manager.activeNotifications.first { it.id == Notices.ACTIVE_ID }.notification
            assertTrue(live.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
            if (android.os.Build.VERSION.SDK_INT >= 36) assertTrue(live.hasPromotableCharacteristics())
            println("LEIFETCH_DEVICE ${Notices.diagnostic(context)} notificationFlags=${live.flags}")
            context.startService(Intent(context, DownloadService::class.java).putExtra("id", task.id).putExtra("op", "pause"))
            withTimeout(10_000) { while (context.app.store.get(task.id)?.state != "已暂停") {
                val current = context.app.store.get(task.id)!!
                check(current.state !in setOf("已完成", "失败")) { "Pause reached ${current.state}: ${current.error}" }
                delay(100)
            } }
            assertTrue(context.app.store.get(task.id)!!.done > 0)
            delay(500)
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).putExtra("id", task.id).putExtra("op", "start"))
            }
            withTimeout(40_000) {
                while (context.app.store.get(task.id)?.state != "已完成") {
                    check(context.app.store.get(task.id)?.state != "失败") { context.app.store.get(task.id)!!.error }
                    delay(200)
                }
            }
            val done = context.app.store.get(task.id)!!
            assertArrayEquals(data, context.contentResolver.openInputStream(Uri.parse(done.uri))!!.use { it.readBytes() })
            withTimeout(5000) { while (manager.activeNotifications.none { it.tag == task.id }) delay(100) }
            val notification = manager.activeNotifications.first { it.tag == task.id }.notification
            assertEquals(listOf("分享", "打开"), notification.actions.map { it.title.toString() })
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                assertTrue(notification.hasPromotableCharacteristics())
                assertTrue(notification.extras.getBoolean("android.requestPromotedOngoing"))
            }
        } finally {
            context.app.store.get(task.id)?.uri?.takeIf { it.isNotEmpty() }?.let { context.contentResolver.delete(Uri.parse(it), null, null) }
            context.app.store.remove(task.id)
            manager.cancel(task.id, 1)
            context.stopService(Intent(context, DownloadService::class.java))
            server.shutdown()
        }
    }
}
