package io.github.bileizhen.leifetch

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class GithubMirrorsTest {
    private val release = "https://github.com/buaoyezz/Hanabi-Download-Manager-X/releases/download/v1/app.apk"

    @Test fun 内置镜像站为用户指定列表() {
        assertEquals(listOf(
            "https://gh.dpik.top",
            "https://ghfast.top",
            "https://gh-proxy.com",
            "https://ghfile.geekertao.top",
            "https://gh-proxy.net",
        ), GithubMirrors.builtin)
    }

    @Test fun 识别GitHub直链() {
        assertTrue(GithubMirrors.isGithubUrl(release))
        assertTrue(GithubMirrors.isGithubUrl("https://github.com/user/repo/archive/refs/heads/main.zip"))
        assertTrue(GithubMirrors.isGithubUrl("https://raw.githubusercontent.com/torvalds/linux/master/README"))
        assertTrue(GithubMirrors.isGithubUrl("https://codeload.github.com/user/repo/zip/refs/heads/main"))
        assertTrue(GithubMirrors.isGithubUrl("https://gist.githubusercontent.com/user/gist/raw/abc/file"))
    }

    // 签名 CDN 地址纳入镜像评估：透传型镜像可用、解析型镜像 404，故由 resolve 用下载地址实测判定。
    @Test fun 签名CDN地址纳入镜像评估() {
        val signed = "https://release-assets.githubusercontent.com/github-production-release-asset/1/abc" +
            "?sp=r&se=2026-09-10T16%3A40%3A04Z&sig=xx"
        assertTrue(GithubMirrors.isGithubUrl(signed))
        assertTrue(GithubMirrors.isGithubUrl("https://objects.githubusercontent.com/github-assets/xx"))
        assertTrue(GithubMirrors.isSignedAsset(signed))
        assertTrue(GithubMirrors.isSignedAsset("https://objects.githubusercontent.com/github-assets/xx"))
        assertTrue(GithubMirrors.isSignedAsset(
            "https://github-production-release-asset-2e65be.s3.amazonaws.com/1/abc?se=2026-09-10T16%3A40%3A04Z"))
        // 规范地址不算签名资源
        assertFalse(GithubMirrors.isSignedAsset(release))
    }

    // 镜像能力差异（实测）：透传型可用、解析型返回 404，据此择优。
    @Test fun 按探测结果选择镜像() {
        val a = "https://gh-proxy.com"; val b = "https://gh.dpik.top"; val c = "https://gh-proxy.net"
        val direct = GithubMirrors.Probe(500, 30623092L)
        fun ok(ms: Long) = GithubMirrors.Probe(ms, 30623092L)
        // 自动：取最快合格者；不可用（null，如 gh-proxy.net 返回 200 错误页）不参与
        assertEquals(b, GithubMirrors.chooseMirror(listOf(a, b, c), "auto", mapOf(a to ok(300), b to ok(120), c to null), direct))
        assertEquals(a, GithubMirrors.chooseMirror(listOf(a, b, c), "auto", mapOf(a to ok(300), b to null, c to null), direct))
        // 全部不可用 → null（调用方直连）
        assertNull(GithubMirrors.chooseMirror(listOf(a, b), "auto", mapOf(a to null, b to null), direct))
        // 直连基准不可得（如 GitHub 被墙）时不比对总长，仍按延迟择优
        assertEquals(b, GithubMirrors.chooseMirror(listOf(a, b), "auto", mapOf(a to ok(300), b to ok(120)), null))
    }

    @Test fun 指定镜像可用时优先于更快的镜像() {
        val a = "https://gh-proxy.com"; val b = "https://gh.dpik.top"
        fun ok(ms: Long) = GithubMirrors.Probe(ms, 30623092L)
        assertEquals(b, GithubMirrors.chooseMirror(listOf(a, b), b, mapOf(a to ok(100), b to ok(900)), null))
        // 指定镜像不可用（如无法转发签名地址）→ 回退自动择优
        assertEquals(a, GithubMirrors.chooseMirror(listOf(a, b), b, mapOf(a to ok(100), b to null), null))
        assertNull(GithubMirrors.chooseMirror(listOf(a, b), b, mapOf(a to null, b to null), null))
    }

    // 镜像送来错误页/旧文件时应被剔除：总长与直连基准不一致即不合格。
    @Test fun 总长与直连不一致的镜像被剔除() {
        val good = "https://gh-proxy.com"; val liar = "https://gh-proxy.net"
        val direct = GithubMirrors.Probe(500, 30623092L)
        // liar 更快但总长不符（如 555 字节错误页）→ 选 good
        assertEquals(good, GithubMirrors.chooseMirror(listOf(good, liar), "auto",
            mapOf(good to GithubMirrors.Probe(300, 30623092L), liar to GithubMirrors.Probe(50, 555L)), direct))
        // 即便用户指定了 liar，也不采纳
        assertEquals(good, GithubMirrors.chooseMirror(listOf(good, liar), liar,
            mapOf(good to GithubMirrors.Probe(300, 30623092L), liar to GithubMirrors.Probe(50, 555L)), direct))
    }

    @Test fun 解析签名过期时刻() {
        val url = "https://release-assets.githubusercontent.com/x?sp=r&se=2026-09-10T16%3A40%3A04Z&sig=a"
        assertEquals(1789058404000L, GithubMirrors.assetExpiryMillis(url))
        assertNull(GithubMirrors.assetExpiryMillis("https://github.com/user/repo/releases/download/v1/a.apk"))
        assertNull(GithubMirrors.assetExpiryMillis("not a url"))
    }

    @Test fun 过期签名给出可操作提示() {
        val url = "https://release-assets.githubusercontent.com/x?sp=r&se=2026-09-10T16%3A40%3A04Z&sig=a"
        val after = 1789058404000L + 1
        assertNotNull(GithubMirrors.expiredAssetNotice(url, now = after))
        assertNull(GithubMirrors.expiredAssetNotice(url, now = 1789058404000L - 1))
        // 无签名的规范地址不拦截
        assertNull(GithubMirrors.expiredAssetNotice(release, now = Long.MAX_VALUE))
    }

    @Test fun 非GitHub或异常地址不加速() {
        assertFalse(GithubMirrors.isGithubUrl("https://example.com/file.apk"))
        assertFalse(GithubMirrors.isGithubUrl("https://api.github.com/repos"))
        assertFalse(GithubMirrors.isGithubUrl("https://github.com.evil.com/file"))
        assertFalse(GithubMirrors.isGithubUrl("ftp://github.com/file"))
        assertFalse(GithubMirrors.isGithubUrl("https://user:pass@github.com/file"))
        assertFalse(GithubMirrors.isGithubUrl(""))
        assertFalse(GithubMirrors.isGithubUrl("not a url"))
    }

    @Test fun 前缀重写与还原() {
        val rewritten = GithubMirrors.rewrite(release, "https://ghfast.top/")
        assertEquals("https://ghfast.top/$release", rewritten)
        assertEquals(release, GithubMirrors.strip(rewritten))
        assertEquals(release, GithubMirrors.strip("https://gh-proxy.net/$release"))
        assertEquals(release, GithubMirrors.strip(release))
        assertEquals(release, GithubMirrors.strip("https://my.mirror.top/$release", "https://my.mirror.top"))
    }

    @Test fun 自定义镜像解析与去重() {
        assertEquals(listOf("https://a.com", "https://b.top"),
            GithubMirrors.parseCustom("https://a.com/ https://b.top/,"))
        assertTrue(GithubMirrors.parseCustom("ftp://x.com http:// nohost").isEmpty())
        // 与内置重复的自定义镜像被去重；新的自定义镜像追加在内置之后
        assertEquals(GithubMirrors.builtin.size, GithubMirrors.effectiveList("https://ghfast.top").size)
        val expanded = GithubMirrors.effectiveList("https://ghfast.top https://extra.top")
        assertEquals(GithubMirrors.builtin.size + 1, expanded.size)
        assertEquals("https://extra.top", expanded.last())
    }

    @Test fun 未启用或非GitHub地址保持原样() = runBlocking {
        assertEquals(release, GithubMirrors.resolve(release, enabled = false, pick = "auto", customRaw = ""))
        assertEquals("https://example.com/a.apk",
            GithubMirrors.resolve("https://example.com/a.apk", enabled = true, pick = "auto", customRaw = ""))
    }

    // resolve = survey(下载地址) + chooseMirror + rewrite；镜像选择与重写为纯逻辑，此处直接组合验证。
    @Test fun 选定镜像产出对应前缀地址() {
        val pick = "https://gh-proxy.net"
        val chosen = GithubMirrors.chooseMirror(GithubMirrors.builtin, pick,
            mapOf(pick to GithubMirrors.Probe(200, 4096)), null)
        assertEquals(pick, chosen)
        assertEquals("$pick/$release", GithubMirrors.rewrite(release, chosen!!))
    }

    // probe 是镜像合格判定的唯一入口，直接复刻实测到的各类镜像行为。
    @Test fun 探测只接受206分段响应() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-0/30623092").setBody("x"))
            val probe = GithubMirrors.probe(server.url("/f").toString())
            assertNotNull(probe)
            assertEquals(30623092L, probe!!.total)
        }
    }

    // 复刻 gh-proxy.net 实测行为：对 Range 请求返回 200 + 555 字节错误页，绝不能判为可用。
    @Test fun 返回200错误页的镜像判为不可用() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("<html>error</html>"))
            assertNull(GithubMirrors.probe(server.url("/f").toString()))
        }
    }

    @Test fun 范围无效或错误状态判为不可用() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-0/*").setBody("x"))
            assertNull(GithubMirrors.probe(server.url("/f").toString()))
            server.enqueue(MockResponse().setResponseCode(404))
            assertNull(GithubMirrors.probe(server.url("/f").toString()))
        }
    }

    @Test fun 探测只取首字节且镜像不跟随重定向() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-0/7").setBody("x"))
            GithubMirrors.probe(server.url("/f").toString())
            val request = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("bytes=0-0", request?.getHeader("Range"))
            // 镜像应直接返回文件；302 到别处不算加速能力
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/next"))
            assertNull(GithubMirrors.probe(server.url("/f").toString()))
        }
    }

    // 直连基准需跟随 GitHub 302 到签名 CDN 才能取到总长，用于校验镜像内容是否一致。
    @Test fun 直连基准可跟随重定向取总长() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/real"))
            server.enqueue(MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-0/30623092").setBody("x"))
            val direct = GithubMirrors.probe(server.url("/f").toString(), followRedirects = true)
            assertNotNull(direct)
            assertEquals(30623092L, direct!!.total)
        }
    }

    @Test fun 测速目标使用小文件() {
        assertTrue(GithubMirrors.isGithubUrl(GithubMirrors.speedTestTarget))
    }
}
