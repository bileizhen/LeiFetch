package io.github.bileizhen.leifetch

import android.app.Notification
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Build real platform notifications without publishing or changing the user's download queue. */
@RunWith(AndroidJUnit4::class)
class NoticeDesignTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val c get() = instrumentation.targetContext
    private val task = Task(url = "", name = "旅行影像 · 夏日海岸.zip", state = "下载中",
        done = 42L * 1024 * 1024, total = 100L * 1024 * 1024, speed = 2L * 1024 * 1024)

    private fun ready() = runBlocking { c.app.ready.await() }

    private fun assertPromotable(n: Notification) {
        if (Build.VERSION.SDK_INT >= 36) {
            assertTrue(n.hasPromotableCharacteristics())
            assertTrue(n.extras.getBoolean("android.requestPromotedOngoing"))
        }
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
    }

    @Test fun transferHasOnePercentageAndTwoAuxiliaryFields() {
        ready()
        val n = Notices.progress(c, task)
        assertEquals("正在下载 · 42%", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(task.name, n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("2.0 MB/s · 约剩 29 秒", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertEquals(listOf("暂停", "取消"), n.actions.map { it.title.toString() })
        if (c.app.settings.state.value.fluid) assertPromotable(n)
    }

    @Test fun unknownSizeDoesNotInventPercentageOrEta() {
        ready()
        val n = Notices.progress(c, task.copy(total = -1))
        assertEquals("正在下载", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("2.0 MB/s · 已下载 42.0 MB", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertTrue(n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }

    @Test fun savingAndQueueDoNotLookLikeFinishedTransfers() {
        ready()
        for ((state, title) in listOf("保存中" to "正在保存文件", "排队中" to "等待下载")) {
            val n = Notices.progress(c, task.copy(state = state, done = task.total))
            assertEquals(title, n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(0, n.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
            if (c.app.settings.state.value.fluid) assertPromotable(n)
        }
    }

    @Test fun confirmationHasNoEmptyProgressAndKeepsTaskActions() {
        ready()
        val n = Notices.candidate(c, task.copy(state = "待确认"), 3, true)
        assertEquals("等待确认下载", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("100.0 MB · 另有 2 项待确认", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertEquals(0, n.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        if (Build.VERSION.SDK_INT >= 36) {
            assertEquals(listOf("确认下载", "忽略"), n.actions.map { it.title.toString() })
            assertPromotable(n)
        }
    }

    @Test fun completionHasNoFullProgressAndKeepsOpenShareActions() {
        ready()
        val n = Notices.completed(c, task.copy(done = task.total, state = "已完成"), true)
        assertEquals("下载完成", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(0, n.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(listOf("打开", "分享"), n.actions.map { it.title.toString() })
        assertPromotable(n)
    }

    @Test fun ordinaryNotificationsRemainDismissible() {
        ready()
        for (n in listOf(Notices.candidate(c, task, 1, false), Notices.completed(c, task, false))) {
            assertEquals(0, n.flags and Notification.FLAG_ONGOING_EVENT)
            assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
            assertFalse(n.extras.getBoolean("android.requestPromotedOngoing"))
        }
    }

    /** Native expanded notification previews, NOT screenshots of ColorOS's separate capsule renderer. */
    @Test fun renderNativeTemplatesForVisualReview() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        ready()
        instrumentation.runOnMainSync {
            // OEM framework caches notification colors globally. Render only the device's current
            // theme; a configuration context does not reliably emulate the other theme.
            val dark = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val context = c
            val cases = listOf(
                "downloading" to Notices.progress(context, task),
                "unknown" to Notices.progress(context, task.copy(total = -1)),
                "saving" to Notices.progress(context, task.copy(state = "保存中")),
                "candidate" to Notices.candidate(context, task, 3, true),
                "completed" to Notices.completed(context, task.copy(done = task.total), true),
                "long-name" to Notices.candidate(context, task.copy(name = "2026 年夏日旅行摄影素材与视频原片备份_未经压缩的完整归档文件.zip"), 1, true)
            )
            for ((name, n) in cases) {
                val views = Notification.Builder.recoverBuilder(context, n).createBigContentView()
                assertNotNull(views)
                val parent = FrameLayout(context)
                val view = views.apply(context, parent)
                parent.addView(view)
                val width = (360 * context.resources.displayMetrics.density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((320 * context.resources.displayMetrics.density).toInt(), View.MeasureSpec.AT_MOST))
                view.layout(0, 0, width, view.measuredHeight)
                val bitmap = Bitmap.createBitmap(width, view.measuredHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(if (dark) Color.rgb(32, 32, 32) else Color.WHITE)
                view.draw(canvas)
                val file = File(c.filesDir, "notice-previews/${if (dark) "dark" else "light"}-$name.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val hierarchy = StringBuilder()
                fun describe(v: View, depth: Int) {
                    hierarchy.append("  ".repeat(depth)).append(v.javaClass.simpleName)
                        .append(" ").append(runCatching { v.resources.getResourceEntryName(v.id) }.getOrDefault("-"))
                        .append(" visible=").append(v.visibility).append(" alpha=").append(v.alpha)
                        .append(" size=").append(v.width).append("x").append(v.height)
                        .append(" at=").append(v.x).append(",").append(v.y)
                    if (v is TextView) hierarchy.append(" text=").append(v.text).append(" color=").append(v.currentTextColor)
                    hierarchy.append('\n')
                    if (v is ViewGroup) for (i in 0 until v.childCount) describe(v.getChildAt(i), depth + 1)
                }
                describe(view, 0)
                File(file.parentFile, file.nameWithoutExtension + ".txt").writeText(hierarchy.toString())
                bitmap.recycle()
            }
        }
    }
}
