package io.github.bileizhen.leifetch

import java.net.HttpURLConnection
import java.net.URL

/** Firefox GeckoView 外部响应的独立探测；不依赖 Xposed，可在 JVM 与设备上测试。 */
object FirefoxProbe {
    class Verified(val etag: String, val totalLength: Long, val url: String)

    /**
     * 验证公开 HTTP(S) 下载能独立获取。200 / 未知大小 / 缺失或弱 ETag 均可接管；
     * 分段与续传能力由下载内核判定。已有的版本、长度矛盾，以及登录页、错误响应仍回退。
     * 只探测响应头，不消费文件内容；不读取或转发浏览器 Cookie。
     */
    fun verify(rawUrl: String, geckoHeaders: Map<String, String>, connectTimeoutMs: Int = 4000,
               onRejected: (String) -> Unit = {}): Verified? {
        fun reject(reason: String): Verified? { onRejected(reason); return null }
        var url = runCatching { URL(rawUrl) }.getOrNull() ?: return reject("下载地址无效")
        if (!supported(url)) return reject("非 HTTP(S) 地址或地址含认证信息")
        val etag = header(geckoHeaders, "ETag").orEmpty().trim()
        val reportedLength = header(geckoHeaders, "Content-Length")?.toLongOrNull() ?: -1L
        val deadline = System.nanoTime() + connectTimeoutMs.coerceAtLeast(1).toLong() * 1_000_000
        var useRange = true
        var redirects = 0
        while (true) {
            val remaining = ((deadline - System.nanoTime()) / 1_000_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (remaining <= 0) return reject("下载探测超时")
            var connection: HttpURLConnection? = null
            try {
                val c = (url.openConnection() as HttpURLConnection).also { connection = it }
                c.instanceFollowRedirects = false
                c.useCaches = false
                c.connectTimeout = remaining
                c.readTimeout = remaining
                // 与 NSFX 实际下载请求保持一致，避免探测和下载得到不同内容。
                c.setRequestProperty("User-Agent", "LeiFetch-NSFX/0.1")
                c.setRequestProperty("Accept", "*/*")
                c.setRequestProperty("Accept-Encoding", "identity")
                if (useRange) c.setRequestProperty("Range", "bytes=0-0")
                val code = c.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    if (++redirects > 5) return reject("重定向超过上限")
                    val location = c.getHeaderField("Location") ?: return reject("重定向缺少目标地址")
                    val next = URL(url, location)
                    if (!supported(next) || (url.protocol == "https" && next.protocol == "http"))
                        return reject("不支持的重定向地址")
                    url = next
                    continue
                }
                // 有些下载端点拒绝 Range 头，却支持普通 GET。只重试一次，无需下载完整文件。
                if (useRange && code in setOf(400, 405, 416)) {
                    useRange = false
                    continue
                }
                if (code != 200 && code != 206) return reject("服务器返回 HTTP $code")
                val encoding = c.getHeaderField("Content-Encoding")
                if (!encoding.isNullOrBlank() && !encoding.equals("identity", true)) return reject("服务器返回压缩编码")
                val probeType = mediaType(c.getHeaderField("Content-Type"))
                val geckoType = mediaType(header(geckoHeaders, "Content-Type"))
                if (probeType in setOf("text/html", "application/xhtml+xml", "application/json") && probeType != geckoType)
                    return reject("独立请求返回网页或接口信息，可能需要浏览器登录状态")
                val probeEtag = c.getHeaderField("ETag").orEmpty().trim()
                if (etag.isNotEmpty() && probeEtag.isNotEmpty() && probeEtag != etag) return reject("文件版本与浏览器响应不一致")
                val total = if (code == 206) parseTotalLength(c.getHeaderField("Content-Range"))
                    ?: return reject("分段响应范围无效")
                else c.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 } ?: -1L
                if (reportedLength >= 0 && total >= 0 && total != reportedLength) return reject("文件大小与浏览器响应不一致")
                return Verified(probeEtag.takeIf(::isStrongEtag).orEmpty(), total, url.toExternalForm())
            } catch (_: java.net.SocketTimeoutException) {
                return reject("下载探测超时")
            } catch (_: Exception) {
                return reject("无法独立连接下载地址")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun supported(url: URL) = url.protocol in setOf("http", "https") && url.userInfo == null && url.host.isNotBlank()
    private fun mediaType(value: String?) = value.orEmpty().substringBefore(';').trim().lowercase(java.util.Locale.ROOT)

    internal fun header(headers: Map<String, String>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, true) }?.value

    internal fun isStrongEtag(value: String) = value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")

    /** 解析 "bytes 0-0/1048576000" 的总长度；缺失、通配或不完整时返回 null。 */
    internal fun parseTotalLength(contentRange: String?): Long? {
        val match = Regex("bytes 0-0/([0-9]+)", RegexOption.IGNORE_CASE).matchEntire(contentRange?.trim().orEmpty()) ?: return null
        return match.groupValues[1].toLongOrNull()?.takeIf { it > 0 }
    }
}
