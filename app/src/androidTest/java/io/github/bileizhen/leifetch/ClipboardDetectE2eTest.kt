// 剪贴板识别端到端验证：真实设备上把链接写入剪贴板，模拟退后台再打开，
// 断言 MainViewModel.clipboardSuggest 收到该链接（即"是否下载"弹窗已触发）。
// 注意：若 PC 端 O+ 互联（OplusRemoteService）正在同步剪贴板，手机剪贴板会在
// 写入后约 250ms 内被系统清空（见 "Clearing clipboard" 日志），本测试会因此失败。
package io.github.bileizhen.leifetch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardDetectE2eTest {
    private val url = "https://dragonwell.oss-cn-shanghai.aliyuncs.com/11.0.31.28.11/Alibaba_Dragonwell_Extended_11.0.31.28.11_x64_linux.tar.gz"

    @Test fun copiedLinkPromptsDownloadDialog() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val cm = instr.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var first: ActivityScenario<MainActivity>? = null
        var second: ActivityScenario<MainActivity>? = null
        try {
            // 实例 A 获得窗口焦点后，本进程才有剪贴板写入权限。
            first = ActivityScenario.launch(MainActivity::class.java)
            Thread.sleep(1500)
            instr.runOnMainSync { cm.setPrimaryClip(ClipData.newPlainText("url", url)) }
            // 回到桌面触发 onStop（重置提示去重），随后重新打开触发 onWindowFocusChanged。
            instr.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME)
            Thread.sleep(800)
            second = ActivityScenario.launch(MainActivity::class.java)
            var seen: String? = null
            val deadline = System.currentTimeMillis() + 8000
            while (System.currentTimeMillis() < deadline && seen != url) {
                Thread.sleep(300)
                second.onActivity { a -> seen = a.vm.clipboardSuggest.value }
            }
            assertEquals(url, seen)
        } finally {
            first?.close(); second?.close()
        }
    }
}
