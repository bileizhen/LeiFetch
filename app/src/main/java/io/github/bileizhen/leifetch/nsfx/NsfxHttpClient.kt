// Android transport for the NSFX Kotlin port; no external HTTP/download runtime.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.bileizhen.leifetch.nsfx

import io.github.bileizhen.leifetch.Task
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class HttpFailure(val status: Int) : IOException("HTTP $status")
class RangeFailure(message: String) : IOException(message)
class SizeMismatch(message: String) : IOException(message)

class NsfxHttpClient(private val config: NsfxConfig) {
    private val connections = Semaphore(config.globalMaxConnections.coerceIn(1, 128))
    class Response(val connection: HttpURLConnection, private val release: (Boolean) -> Unit) : Closeable {
        private val closed = AtomicBoolean(false)
        private var body: InputStream? = null
        private var reusable = false
        val code get() = connection.responseCode
        val url get() = connection.url.toExternalForm()
        val length get() = header("Content-Length")?.toLongOrNull() ?: -1L
        fun header(name: String): String? = connection.getHeaderField(name)
        val stream: InputStream get() = body ?: connection.inputStream.also { body = it }
        /** 仅在已读到 EOF 后调用，此时 HttpURLConnection 才可安全复用连接。 */
        fun markConsumed() { reusable = true }
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { body?.close() }
            release(reusable)
        }
    }
    @OptIn(InternalCoroutinesApi::class)
    suspend fun get(task: Task, range: String? = null, validator: String? = null): Response {
        var url = URL(task.url)
        require(url.protocol in setOf("http", "https") && url.userInfo == null)
        var headers = task.headers.filterKeys { it.lowercase() in setOf("authorization", "cookie", "user-agent", "referer", "accept", "accept-language") }
        repeat(6) {
            connections.acquire()
            var connection: HttpURLConnection? = null
            var cancellation: DisposableHandle? = null
            var handedOff = false
            try {
                val c = (url.openConnection() as HttpURLConnection).also { connection = it }
                c.instanceFollowRedirects = false; c.useCaches = false
                c.requestMethod = "GET"; c.connectTimeout = config.connectionTimeoutMs; c.readTimeout = config.readTimeoutMs
                c.setRequestProperty("User-Agent", "LeiFetch-NSFX/0.1")
                c.setRequestProperty("Accept", "*/*")
                headers.forEach { (k, v) -> require('\r' !in v && '\n' !in v); c.setRequestProperty(k, v) }
                c.setRequestProperty("Accept-Encoding", "identity")
                range?.let { c.setRequestProperty("Range", it) }
                validator?.let { c.setRequestProperty("If-Range", it) }
                cancellation = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
                    if (it != null) c.disconnect()
                }
                currentCoroutineContext().ensureActive()
                val status = c.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val next = URL(url, c.getHeaderField("Location") ?: throw IOException("重定向缺少 Location"))
                    require(next.protocol in setOf("http", "https") && next.userInfo == null)
                    require(!(url.protocol == "https" && next.protocol == "http")) { "拒绝 HTTPS 降级" }
                    if (origin(url) != origin(next)) headers = headers.filterKeys { it.lowercase() !in setOf("authorization", "cookie", "referer") }
                    url = next
                } else {
                    val handle = cancellation
                    handedOff = true
                    return Response(c) { reusable ->
                        handle.dispose()
                        if (!reusable) c.disconnect()
                        connections.release()
                    }
                }
            } finally {
                if (!handedOff) { cancellation?.dispose(); connection?.disconnect(); connections.release() }
            }
        }
        throw IOException("重定向超过上限")
    }
    private fun origin(url: URL) = "${url.protocol}://${url.host}:${if (url.port >= 0) url.port else url.defaultPort}"
}
