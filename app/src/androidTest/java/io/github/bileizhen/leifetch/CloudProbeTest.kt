package io.github.bileizhen.leifetch

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CloudProbeTest {
    @Test fun completedTaskPublishesLiveActions() = runBlocking {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        c.app.ready.await()
        val task = Task(url = "", name = "LeiFetch-流体云测试.txt", state = "已完成", done = 100, total = 100, finished = System.currentTimeMillis())
        val file = File(c.filesDir, "downloads/${task.id}/${task.name}").apply {
            parentFile!!.mkdirs(); writeText("LeiFetch NSFX — ColorOS 流体云打开 / 分享测试")
        }
        val complete = task.copy(uri = FileProvider.getUriForFile(c, "$PKG.files", file).toString())
        c.app.store.put(complete)
        val manager = c.getSystemService(NotificationManager::class.java)
        try {
            c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            delay(1500)
            Notices.complete(c, complete)
            delay(2000)
            val n = manager.activeNotifications.first { it.tag == task.id }.notification
            if (InstrumentationRegistry.getArguments().getString("highChannel") == "true") {
                manager.createNotificationChannel(android.app.NotificationChannel("cloud_probe_high", "流体云临时探针", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
                manager.notify(task.id, 1, android.app.Notification.Builder.recoverBuilder(c, n).setChannelId("cloud_probe_high").build())
            }
            assertEquals(listOf("打开", "分享"), n.actions.map { it.title.toString() })
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                assertTrue(n.hasPromotableCharacteristics())
                assertTrue(n.extras.getBoolean("android.requestPromotedOngoing"))
            }
            println("LEIFETCH_CLOUD flags=${n.flags} ${Notices.diagnostic(c)}")
            delay(30_000)
        } finally {
            manager.cancel(task.id, 1)
            c.app.store.remove(task.id)
            file.delete()
        }
    }
}
