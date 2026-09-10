package io.github.bileizhen.leifetch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.URL

/** GitHub 下载加速：识别 GitHub 直链，经「镜像前缀 + 原链接」转发；实测各镜像首字节延迟择优。 */
object GithubMirrors {
    val builtin = listOf(
        "https://gh.dpik.top",
        "https://ghfast.top",
        "https://gh-proxy.com",
        "https://ghfile.geekertao.top",
        "https://gh-proxy.net",
    )

    /** 镜像测速用的稳定小文件；只取 Range 首字节，不消费正文。 */
    const val speedTestTarget = "https://raw.githubusercontent.com/torvalds/linux/master/README"
    private val githubHosts = setOf(
        "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com",
        "raw.githubusercontent.com", "gist.githubusercontent.com", "codeload.github.com",
    )

    fun isGithubUrl(url: String): Boolean {
        val u = runCatching { URL(url) }.getOrNull() ?: return false
        if (u.protocol !in setOf("http", "https") || u.userInfo != null) return false
        return u.host.lowercase() in githubHosts
    }

    /** 自定义镜像以空白 / 逗号 / 分号分隔；校验 HTTP(S) 与主机，去尾部斜杠并去重。 */
    fun parseCustom(raw: String): List<String> = raw.split(Regex("[\\s,;]+"))
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotEmpty() }
        .filter { candidate ->
            runCatching {
                val u = URL(candidate)
                u.protocol in setOf("http", "https") && u.host.isNotBlank() && u.userInfo == null
            }.getOrDefault(false)
        }
        .distinct()

    fun effectiveList(customRaw: String): List<String> = (builtin + parseCustom(customRaw)).distinct()

    fun rewrite(url: String, mirror: String): String = "${mirror.trimEnd('/')}/$url"

    /** 去掉已知镜像前缀，还原原始 GitHub 地址。 */
    fun strip(url: String, customRaw: String = ""): String {
        for (mirror in effectiveList(customRaw)) {
            if (url.startsWith("$mirror/")) return url.removePrefix("$mirror/")
        }
        return url
    }

    /**
     * 下载开始前解析最终地址：指定镜像优先（不在列表或失效时回退自动），
     * 自动模式并行实测各镜像首字节延迟择优；全部不可用时保持原链接。
     */
    suspend fun resolve(url: String, enabled: Boolean, pick: String, customRaw: String): String {
        if (!enabled || !isGithubUrl(url)) return url
        val mirrors = effectiveList(customRaw)
        if (pick != "auto" && pick in mirrors) return rewrite(url, pick)
        val fastest = measure(mirrors, url).filterValues { it != null }.minByOrNull { it.value!! }?.key
        return fastest?.let { rewrite(url, it) } ?: url
    }

    /** 并行实测各镜像；值为 null 表示该镜像不可用。 */
    suspend fun measure(mirrors: List<String>, target: String, timeoutMs: Int = 4000): Map<String, Long?> =
        coroutineScope { mirrors.map { async(Dispatchers.IO) { it to probe(it, target, timeoutMs) } }.awaitAll().toMap() }

    /** Range 首字节探测：2xx/3xx 计为可用，返回首字节毫秒数。 */
    fun probe(mirror: String, target: String, timeoutMs: Int = 4000): Long? {
        var connection: HttpURLConnection? = null
        return try {
            val c = (URL(rewrite(target, mirror)).openConnection() as HttpURLConnection).also { connection = it }
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", "LeiFetch-NSFX/0.1")
            c.setRequestProperty("Accept", "*/*")
            c.setRequestProperty("Range", "bytes=0-0")
            val start = System.nanoTime()
            val code = c.responseCode
            if (code in 200..399) (System.nanoTime() - start) / 1_000_000L else null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
