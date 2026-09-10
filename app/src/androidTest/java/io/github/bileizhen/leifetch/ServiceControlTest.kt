package io.github.bileizhen.leifetch

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** 通过同 uid 的 instrumentation 启动下载服务（绕过跨应用服务启动限制），供端到端验证。 */
@RunWith(AndroidJUnit4::class)
class ServiceControlTest {
    @Test fun startNewestPending() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val op = args.getString("op") ?: "start"
        // id 可由外部指定：instrumentation 进程与主进程的 SQLite 可见性存在差异，
        // 而服务在主进程启动，此处只需任务 ID 字符串。
        val id = args.getString("id")
            ?: context.app.store.tasks.value.filter { it.state == "待确认" }.maxByOrNull { it.created }?.id
        checkNotNull(id) { "未提供 id 且没有待确认任务" }
        context.startForegroundService(Intent(context, DownloadService::class.java)
            .putExtra("id", id).putExtra("op", op))
        // instrumentation 结束时 AMS 会强停目标进程；保持进程存活以便外部观察下载中的通知。
        val duration = args.getString("duration")?.toLong() ?: 0L
        if (duration > 0) Thread.sleep(duration)
    }
}
