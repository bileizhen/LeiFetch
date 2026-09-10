package io.github.bileizhen.leifetch

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

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
        assertTrue(GithubMirrors.isGithubUrl("https://objects.githubusercontent.com/github-assets/xx"))
        assertTrue(GithubMirrors.isGithubUrl("https://codeload.github.com/user/repo/zip/refs/heads/main"))
        assertTrue(GithubMirrors.isGithubUrl("https://gist.githubusercontent.com/user/gist/raw/abc/file"))
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

    @Test fun 指定镜像时直接前缀重写() = runBlocking {
        assertEquals("https://ghfast.top/$release",
            GithubMirrors.resolve(release, enabled = true, pick = "https://ghfast.top", customRaw = ""))
        assertEquals("https://my.top/$release",
            GithubMirrors.resolve(release, enabled = true, pick = "https://my.top", customRaw = "https://my.top"))
    }

    @Test fun 测速目标使用小文件() {
        assertTrue(GithubMirrors.isGithubUrl(GithubMirrors.speedTestTarget))
    }
}
