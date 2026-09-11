// 分享接收入口：Via 浏览器等第三方下载器按 ACTION_SEND + text/plain 显式组件调起，
// EXTRA_TEXT 即下载地址（Via 只传 URL，不带 UA / Cookie / 文件名），契约见
// https://github.com/GopeedLab/gopeed/issues/412。GPL-3.0。
package io.github.bileizhen.leifetch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
        val url = clipboardUrl(text)
        val source = referrer?.host?.let { "分享 · $it" } ?: "外部分享"
        if (url == null) {
            Toast.makeText(this, "未识别到下载链接", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                app.ready.await()
                val task = withContext(Dispatchers.IO) {
                    val u = Uri.parse(url)
                    app.store.add(Task(url = url, name = safeName(u.lastPathSegment ?: "download.bin"),
                        source = source, tree = app.settings.state.value.tree))
                }
                // 分享到下载器本身就是确认动作：可启动的任务立即开始；已在传输中的去重任务不重启。
                if (task.canStart()) {
                    ContextCompat.startForegroundService(applicationContext,
                        Intent(applicationContext, DownloadService::class.java)
                            .putExtra("id", task.id).putExtra("op", "start"))
                    Toast.makeText(this@DownloaderActivity, "已开始下载 · ${task.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@DownloaderActivity, "任务已在列表 · ${task.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DownloaderActivity, e.message ?: "添加下载失败", Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }
}
