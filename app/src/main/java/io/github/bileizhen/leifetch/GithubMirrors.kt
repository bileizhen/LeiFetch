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

    /** 纳入镜像评估的 GitHub 主机：规范主机 + release 302 后的签名 CDN 主机。
     *  镜像站转发能力差异很大——规范地址几乎都支持，签名 CDN 地址只有纯透传镜像支持
     *  （实测 gh-proxy.com / gh-proxy.net 可用，gh.dpik.top / ghfile.geekertao.top 返回 404），
     *  故实际可用性由 resolve() 用下载地址本身探测判定，不依赖静态主机白名单。 */
    private val githubHosts = setOf(
        "github.com", "raw.githubusercontent.com", "gist.githubusercontent.com", "codeload.github.com",
        "release-assets.githubusercontent.com", "objects.githubusercontent.com",
    )

    /** GitHub 签名 CDN 主机：链接带约 1 小时时效签名，过期后镜像也救不回，需提前提示。 */
    private val signedAssetHosts = setOf(
        "release-assets.githubusercontent.com", "objects.githubusercontent.com",
    )
    private const val legacyAssetHostPrefix = "github-production-release-asset"

    fun isGithubUrl(url: String): Boolean {
        val u = runCatching { URL(url) }.getOrNull() ?: return false
        if (u.protocol !in setOf("http", "https") || u.userInfo != null) return false
        return u.host.lowercase() in githubHosts
    }

    /** 是否为 GitHub 签名 CDN 资源地址（镜像不可用，且链接带时效签名）。 */
    fun isSignedAsset(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return false
        return host in signedAssetHosts || (host.startsWith(legacyAssetHostPrefix) && host.endsWith(".amazonaws.com"))
    }

    /** 解析签名过期时刻（毫秒）；无 `se` 参数或无法解析时返回 null。 */
    fun assetExpiryMillis(url: String): Long? = runCatching {
        val query = URL(url).query ?: return null
        val encoded = query.split('&').firstOrNull { it.startsWith("se=") }?.substring(3) ?: return null
        val text = java.net.URLDecoder.decode(encoded, "UTF-8")
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(text)?.time
    }.getOrNull()

    /** 签名已过期时返回可操作的提示；未过期或非签名地址返回 null。 */
    fun expiredAssetNotice(url: String, now: Long = System.currentTimeMillis()): String? =
        assetExpiryMillis(url)?.takeIf { it < now }?.let {
            "GitHub 下载链接的签名已过期（有效期约 1 小时），请回浏览器重新发起下载"
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

    /** 探测结果：首字节延迟与资源总长（取自 Content-Range）。 */
    data class Probe(val latencyMs: Long, val total: Long)

    /** 下载线路：实际请求地址与所用镜像主机（未走镜像时 mirror 为空）。 */
    data class Route(val url: String, val mirror: String = "")

    /**
     * 下载开始前解析下载线路：并行探测各镜像与直连基准，取最快且内容一致者。
     * 探测目标就是下载地址本身（Range 0-0），故规范 github.com 地址与签名 CDN 地址都能按
     * 镜像真实转发能力评估；全部不可用时保持原链接直连。
     */
    suspend fun resolve(url: String, enabled: Boolean, pick: String, customRaw: String): Route {
        if (!enabled || !isGithubUrl(url)) return Route(url)
        val mirrors = effectiveList(customRaw)
        val (probes, direct) = survey(mirrors, url)
        val chosen = chooseMirror(mirrors, pick, probes, direct) ?: return Route(url)
        return Route(rewrite(url, chosen), chosen)
    }

    /**
     * 挑选镜像：指定镜像合格则优先（即便不是最快），否则取最快合格者；无合格者返回 null 表示直连。
     * 合格 = 返回 206 分段响应，且直连基准可得时总长与直连一致——
     * 后者防止镜像送来错误页或旧版本文件（实测 gh-proxy.net 对 Range 请求返回 200 + 555 字节错误页）。
     */
    fun chooseMirror(mirrors: List<String>, pick: String, probes: Map<String, Probe?>, direct: Probe?): String? {
        fun usable(mirror: String) = probes[mirror]?.let { direct == null || it.total == direct.total } == true
        if (pick != "auto" && pick in mirrors && usable(pick)) return pick
        return mirrors.filter(::usable).minByOrNull { probes[it]!!.latencyMs }
    }

    /** 并行探测各镜像与直连基准（同一批发出，互不增加等待）。 */
    suspend fun survey(mirrors: List<String>, url: String, timeoutMs: Int = 4000): Pair<Map<String, Probe?>, Probe?> =
        coroutineScope {
            val jobs = mirrors.map { async(Dispatchers.IO) { it to probe(rewrite(url, it), timeoutMs) } }
            val direct = async(Dispatchers.IO) { probe(url, timeoutMs, followRedirects = true) }
            jobs.awaitAll().toMap() to direct.await()
        }

    /** 镜像测速（设置页）：各镜像首字节延迟；null 表示不可用。 */
    suspend fun measure(mirrors: List<String>, target: String, timeoutMs: Int = 4000): Map<String, Long?> =
        coroutineScope {
            mirrors.map { async(Dispatchers.IO) { it to probe(rewrite(target, it), timeoutMs)?.latencyMs } }
                .awaitAll().toMap()
        }

    /**
     * Range 0-0 首字节探测。只接受 206 且 Content-Range 总长有效；仅看状态码会把「返回 200 +
     * 错误页」的镜像误判为可用，从而被选中并让下载拿到错误内容。
     */
    fun probe(rawUrl: String, timeoutMs: Int = 4000, followRedirects: Boolean = false): Probe? {
        var connection: HttpURLConnection? = null
        return try {
            val c = (URL(rawUrl).openConnection() as HttpURLConnection).also { connection = it }
            // 镜像应直接返回文件；直连基准需要跟随 GitHub 到签名 CDN 才能拿到总长。
            c.instanceFollowRedirects = followRedirects
            c.useCaches = false
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", "LeiFetch-NSFX/0.1")
            c.setRequestProperty("Accept", "*/*")
            c.setRequestProperty("Range", "bytes=0-0")
            val start = System.nanoTime()
            if (c.responseCode != 206) return null
            val total = parseTotalLength(c.getHeaderField("Content-Range")) ?: return null
            Probe((System.nanoTime() - start) / 1_000_000L, total)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** 解析 `bytes 0-0/30623092` 的总长；`*` 或无效值返回 null。 */
    private fun parseTotalLength(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/', "")?.trim()?.toLongOrNull()?.takeIf { it > 0 }
}
