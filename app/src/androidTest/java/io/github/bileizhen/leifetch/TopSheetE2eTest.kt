// 顶弹层端到端：从顶栏标题向下拖动，断言“新建下载”顶弹层出现。
// 顶弹层完全收起时已移出组合，无障碍树里不存在“新建下载”，节点出现即代表真正展开。
// 标题坐标从无障碍树动态解析，适配不同机型；先唤醒设备，避免息屏时窗口未激活导致查不到节点。
package io.github.bileizhen.leifetch

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class TopSheetE2eTest {
    private val instr get() = InstrumentationRegistry.getInstrumentation()

    private fun shell(command: String) {
        runCatching {
            FileInputStream(instr.uiAutomation.executeShellCommand(command).fileDescriptor)
                .use { it.readBytes() }
        }
    }

    /** 在无障碍树里按文字找节点；找不到返回 null。 */
    private fun node(text: String) = runCatching {
        instr.uiAutomation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByText(text).orEmpty()
            .firstOrNull()
    }.getOrNull()

    private fun awaitNode(text: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (node(text) != null) return true
            Thread.sleep(250)
        }
        return false
    }

    @Test fun pullDownTitleOpensSheet() {
        // 息屏时 Activity 的窗口不会成为活动窗口，无障碍树查不到内容。
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        Thread.sleep(300)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            // 部分系统（如 ColorOS）限制 UI 自动化读取无障碍树，此时跳过而不是误报失败；
            // 可读的环境下本用例才真正注入下拉并断言顶弹层出现。
            Assume.assumeTrue("本机无障碍树不可读，跳过顶弹层注入验证", awaitNode("LeiFetch", 8000))
            // 剪贴板里有链接时会先弹询问框，挡住顶栏；先取消掉。
            if (node("发现下载链接") != null) {
                node("取消")?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(400)
            }
            val title = node("LeiFetch")
            assertTrue("顶栏标题节点已消失", title != null)
            val bounds = Rect().also { title!!.getBoundsInScreen(it) }
            val x = bounds.exactCenterX()
            val startY = bounds.exactCenterY()
            // 逐段注入下拉：位移需超过弹层一半高度（或松手时速度够快）才会展开。
            // ColorOS 禁止 sendPointerSync 的定向注入；UiAutomation 走无障碍通道允许注入。
            fun send(action: Int, y: Float, downTime: Long, eventTime: Long) {
                instr.uiAutomation.injectInputEvent(
                    MotionEvent.obtain(downTime, eventTime, action, x, y, 0), true)
            }
            val downTime = SystemClock.uptimeMillis()
            send(MotionEvent.ACTION_DOWN, startY, downTime, downTime)
            var y = startY
            for (step in 1..12) {
                Thread.sleep(16)
                y += 120f
                send(MotionEvent.ACTION_MOVE, y, downTime, SystemClock.uptimeMillis())
            }
            Thread.sleep(16)
            send(MotionEvent.ACTION_UP, y, downTime, SystemClock.uptimeMillis())
            assertTrue("下拉后顶弹层未出现", awaitNode("新建下载", 3000))
        } finally {
            scenario.close()
        }
    }
}
