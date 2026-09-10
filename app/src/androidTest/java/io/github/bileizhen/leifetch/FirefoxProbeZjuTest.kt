package io.github.bileizhen.leifetch

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirefoxProbeZjuTest {
    /** 实链路探测 http://speedtest.zju.edu.cn/1000M：Range 只取 1 字节，验证 Firefox 接管条件成立。 */
    @Test fun probeRealZjuSpeedtestLink() {
        val reachable = runCatching { java.net.InetAddress.getAllByName("speedtest.zju.edu.cn").isNotEmpty() }.getOrDefault(false)
        assumeTrue("设备无法解析 speedtest.zju.edu.cn，跳过实链路探测", reachable)
        // 与 GeckoView WebResponse.headers 对应的服务器响应头（实测值）。
        val geckoHeaders = mapOf(
            "ETag" to "\"3e800000-56a7fe0a01cd3\"",
            "Content-Length" to "1048576000",
            "Accept-Ranges" to "bytes")
        val verified = FirefoxProbe.verify("http://speedtest.zju.edu.cn/1000M", geckoHeaders, connectTimeoutMs = 8000)
        assertNotNull("ZJU 1000M 应满足 206 + 强 ETag 匹配 + 总长一致", verified)
        assertEquals(1_048_576_000L, verified!!.totalLength)
    }
}
