// Kotlin adaptation of NSFX's engine, policies and checkpoint design.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.bileizhen.leifetch.nsfx

import android.content.Context
import io.github.bileizhen.leifetch.Task
import io.github.bileizhen.leifetch.workDir
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URL
import javax.net.ssl.SSLHandshakeException
import java.util.concurrent.atomic.AtomicLong

class EngineTelemetry(val connections: Int, val pieceSize: Long, val fills: ByteArray, val speed: Long)

class NsfxDownloadEngine(private val context: Context, private val config: NsfxConfig,
                         private val http: NsfxHttpClient = NsfxHttpClient(config)) {
    private val hostFile = AtomicFile(File(context.filesDir, "nsfx-host-strategies.json"))
    private val hostLock = Any()
    private val hostHints = runCatching { JSONObject(String(hostFile.readFully())) }.getOrDefault(JSONObject())
    private val limiter = RateLimiter(config.globalSpeedLimit)

    suspend fun download(task: Task, progress: (Long, Long, Long) -> Unit,
                         telemetry: (EngineTelemetry) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val info = probe(task)
        val storage = NsfxStorage(workDir(context, task.id))
        if (!info.supportsRange || info.size <= 0 || info.etag.isEmpty()) {
            storage.reset()
            return@withContext single(task, info, storage, progress, telemetry)
        }
        val (threads, count) = SegmentPlanner.calculate(info.size, config)
        val segments = storage.load(info) ?: run {
            storage.reset()
            RandomAccessFile(storage.partial, "rw").use { it.setLength(info.size); it.fd.sync() }
            (0 until count).map { i -> Segment(i, info.size * i / count, info.size * (i + 1) / count) }.toMutableList()
                .also { storage.save(info, it) }
        }
        var concurrency = hostCap(info.url, threads)
        val pieceSize = PiecePolicy.pieceSize(info.size)
        telemetry(EngineTelemetry(concurrency, pieceSize, PiecePolicy.coverage(segments, info.size, pieceSize), 0))
        var round = 0
        val bytes = AtomicLong(segments.sumOf { it.downloaded })
        while (true) {
            try {
                runSegments(task, info, storage, segments, concurrency, bytes, progress, telemetry, pieceSize)
                break
            } catch (e: HttpFailure) {
                if (e.status !in setOf(429, 503) || concurrency <= 1 || round++ >= 6) throw e
                concurrency = maxOf(1, concurrency / 2)
                rememberHost(info.url, concurrency)
                delay(NsfxRetryPolicy.delayMs(round))
            }
        }
        NsfxStorage.verifyCoverage(segments, info.size)
        require(segments.all { it.downloaded == it.size }) { "分段尚未完成" }
        RandomAccessFile(storage.partial, "rw").use { require(it.length() == info.size); it.fd.sync() }
        progress(info.size, info.size, 0)
        storage.partial
    }

    private suspend fun probe(task: Task): FileInfo {
        var retries = 0
        var useRange = true
        while (true) {
            try {
                val info = http.get(task, if (useRange) "bytes=0-0" else null).use { r ->
                    if (useRange && r.code in setOf(400, 405, 416) &&
                        !(r.code == 416 && r.header("Content-Range") == "bytes */0")) {
                        useRange = false
                        return@use null
                    }
                    requireIdentity(r)
                    val size = when (r.code) {
                        206 -> parseRange(r.header("Content-Range"), 0, 0)
                        200 -> r.length
                        416 -> if (r.header("Content-Range") == "bytes */0") 0 else throw HttpFailure(416)
                        else -> throw HttpFailure(r.code)
                    }
                    val tag = r.header("ETag").orEmpty().trim().takeIf { it.startsWith('"') && it.endsWith('"') }.orEmpty()
                    FileInfo(task.url, size, tag, r.code == 206)
                }
                if (info != null) return info
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                if (!retryable(e) || retries >= NsfxRetryPolicy.maxRetries(config.maxRetries)) throw e
                delay(NsfxRetryPolicy.delayMs(++retries))
            }
        }
    }

    private suspend fun runSegments(task: Task, info: FileInfo, storage: NsfxStorage, segments: MutableList<Segment>,
                                    concurrency: Int, bytes: AtomicLong, progress: (Long, Long, Long) -> Unit,
                                    telemetry: (EngineTelemetry) -> Unit, pieceSize: Long) = supervisorScope {
        val workers = mutableMapOf<Int, Deferred<Unit>>()
        var lastBytes = bytes.get()
        var lastTime = System.nanoTime()
        var lastReport = 0L
        var smoothed = 0.0
        val samples = mutableMapOf<Int, Long>()
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                workers.filterValues { it.isCompleted }.keys.toList().forEach { id -> workers.remove(id)!!.await() }
                segments.filter { it.remaining > 0 && it.index !in workers }.take((concurrency - workers.size).coerceAtLeast(0)).forEach { s ->
                    workers[s.index] = async(Dispatchers.IO) { transfer(task, info, storage, s, bytes) }
                }
                if (workers.isEmpty()) break
                delay(200)
                val now = System.nanoTime()
                val elapsed = (now - lastTime) / 1e9
                val current = bytes.get()
                val instant = ((current - lastBytes) / elapsed).coerceAtLeast(0.0)
                smoothed = if (smoothed <= 0) instant else smoothed * 0.7 + instant * 0.3
                for (s in segments) {
                    val old = samples.put(s.index, s.downloaded) ?: s.downloaded
                    s.speed = (s.downloaded - old).coerceAtLeast(0) / elapsed
                }
                lastTime = now; lastBytes = current
                if (now - lastReport >= 1_000_000_000) {
                    progress(current, info.size, smoothed.toLong()); lastReport = now
                    telemetry(EngineTelemetry(workers.size, pieceSize,
                        PiecePolicy.coverage(segments, info.size, pieceSize), smoothed.toLong()))
                }
                if (config.enableDynamicSegments && workers.size < concurrency && segments.none { it.remaining > 0 && it.index !in workers }) {
                    val snapshots = segments.filter { it.index in workers && it.remaining > 0 }.map {
                        SplitSnapshot(it.index, it.remaining, it.speed, (now - it.lastProgressNs) / 1_000_000,
                            if (it.lastSplitNs == 0L) Long.MAX_VALUE else (now - it.lastSplitNs) / 1_000_000)
                    }
                    val plans = DynamicSegmentPolicy.plan(snapshots, concurrency, segments.size)
                    for (plan in plans) {
                        val worker = workers.remove(plan.index) ?: continue
                        worker.cancelAndJoin()
                        val s = segments.first { it.index == plan.index }
                        if (s.remaining < DynamicSegmentPolicy.MIN_SPLIT_BYTES * 2) continue
                        val steal = plan.stealBytes.coerceIn(DynamicSegmentPolicy.MIN_SPLIT_BYTES, s.remaining - DynamicSegmentPolicy.MIN_SPLIT_BYTES)
                        val split = s.end - steal
                        val next = Segment(segments.maxOf { it.index } + 1, split, s.end)
                        s.end = split; s.lastSplitNs = now; next.lastSplitNs = now
                        segments.add(next)
                        storage.save(info, segments)
                    }
                }
            }
        } finally {
            workers.values.forEach { it.cancel() }
            withContext(NonCancellable) { workers.values.forEach { it.join() } }
        }
    }

    private suspend fun transfer(task: Task, info: FileInfo, storage: NsfxStorage, segment: Segment, bytes: AtomicLong) {
        val segmentLimiter = RateLimiter(config.segmentSpeedLimit)
        var retries = 0
        while (segment.remaining > 0) {
            try {
                val start = segment.start + segment.downloaded
                val end = segment.end - 1
                http.get(task, "bytes=$start-$end", info.etag).use { response ->
                    // 签名 CDN(如腾讯 cdntips)每次请求都 302 到不同的边缘 URL,
                    // 身份只认 ETag 与总长,请求已带 If-Range 兜底;200 即校验失败。
                    if (response.code == 200) throw RangeFailure("RANGE_RESPONSE_INVALID：资源已变化")
                    if (response.code != 206) throw HttpFailure(response.code)
                    if (parseRange(response.header("Content-Range"), start, end) != info.size || response.header("ETag") != info.etag) {
                        throw RangeFailure("RANGE_RESPONSE_INVALID：资源已变化")
                    }
                    requireIdentity(response)
                    response.stream.use { input ->
                        RandomAccessFile(storage.partial, "rw").use { output ->
                            output.seek(start)
                            val buffer = ByteArray(128 * 1024)
                            var lastSync = System.nanoTime()
                            try {
                                while (segment.remaining > 0) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), segment.remaining).toInt())
                                    if (n < 0) throw IOException("incomplete transfer")
                                    limiter.consume(n); segmentLimiter.consume(n)
                                    output.write(buffer, 0, n)
                                    segment.downloaded += n; bytes.addAndGet(n.toLong()); segment.lastProgressNs = System.nanoTime()
                                    if (System.nanoTime() - lastSync > 1_000_000_000) {
                                        output.fd.sync(); storage.checkpoint(segment); lastSync = System.nanoTime()
                                    }
                                }
                                if (input.read() != -1) throw RangeFailure("分片响应超出范围")
                            } finally { output.fd.sync(); storage.checkpoint(segment) }
                        }
                    }
                }
                return
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                if (e is RangeFailure) {
                    bytes.addAndGet(-segment.downloaded)
                    segment.downloaded = 0
                    storage.checkpoint(segment)
                    throw e
                }
                if (e is HttpFailure && e.status in setOf(429, 503)) throw e
                if (!retryable(e) || retries >= NsfxRetryPolicy.maxRetries(config.maxRetries)) throw e
                delay(NsfxRetryPolicy.delayMs(++retries))
            }
        }
    }

    private suspend fun single(task: Task, info: FileInfo, storage: NsfxStorage, progress: (Long, Long, Long) -> Unit,
                               telemetry: (EngineTelemetry) -> Unit): File {
        if (info.size == 0L) { storage.partial.writeBytes(byteArrayOf()); progress(0, 0, 0); return storage.partial }
        var retries = 0
        while (true) {
            try {
                http.get(task).use { response ->
                    if (response.code != 200) throw HttpFailure(response.code)
                    requireIdentity(response)
                    var done = 0L
                    var previous = 0L
                    var last = System.nanoTime()
                    val total = response.length
                    val pieceSize = maxOf(1L, total)
                    progress(0, total, 0)
                    telemetry(EngineTelemetry(1, pieceSize, ByteArray(1), 0))
                    response.stream.use { input ->
                        RandomAccessFile(storage.partial, "rw").use { out ->
                            out.setLength(0)
                            val buffer = ByteArray(128 * 1024)
                            val segmentLimiter = RateLimiter(config.segmentSpeedLimit)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                limiter.consume(n); segmentLimiter.consume(n)
                                out.write(buffer, 0, n); done += n
                                val now = System.nanoTime()
                                if (now - last > 1_000_000_000) {
                                    val instant = ((done - previous) * 1e9 / (now - last)).toLong()
                                    progress(done, total, instant)
                                    telemetry(EngineTelemetry(1, pieceSize,
                                        byteArrayOf(((done * 255) / pieceSize).coerceIn(0L, 255L).toByte()), instant))
                                    last = now; previous = done
                                }
                            }
                            out.fd.sync()
                        }
                    }
                    if (total >= 0 && done != total) throw IOException("incomplete transfer")
                    progress(done, done, 0)
                    return storage.partial
                }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                if (!retryable(e) || retries >= NsfxRetryPolicy.maxRetries(config.maxRetries)) throw e
                delay(NsfxRetryPolicy.delayMs(++retries))
            }
        }
    }

    private fun hostCap(url: String, requested: Int): Int = synchronized(hostLock) {
        val value = hostHints.optJSONObject(URL(url).host)
        if (value == null || value.optLong("expires") < System.currentTimeMillis()) requested
        else minOf(requested, value.optInt("cap", requested).coerceAtLeast(1))
    }
    private fun rememberHost(url: String, cap: Int) = synchronized(hostLock) {
        hostHints.put(URL(url).host, JSONObject().put("cap", cap).put("expires", System.currentTimeMillis() + 45 * 60_000))
        NsfxStorage.atomic(hostFile, hostHints.toString())
    }
    private fun requireIdentity(response: NsfxHttpClient.Response) {
        val encoding = response.header("Content-Encoding")
        if (encoding != null && !encoding.equals("identity", true)) throw RangeFailure("不支持压缩的分段表示")
    }
    private fun retryable(e: IOException): Boolean = e !is RangeFailure && e !is SSLHandshakeException &&
        !(e is HttpFailure && e.status in NsfxRetryPolicy.permanentHttp)
    companion object {
        fun parseRange(value: String?, start: Long, end: Long): Long {
            val m = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value.orEmpty()) ?: throw RangeFailure("无效 Content-Range")
            if (m.groupValues[1].toLong() != start || m.groupValues[2].toLong() != end) throw RangeFailure("Content-Range 不匹配")
            return m.groupValues[3].toLong().also { if (it <= end || end < start) throw RangeFailure("Content-Range 总长不正确") }
        }
    }
}

private class RateLimiter(private val rate: Long) {
    private val lock = Mutex()
    private var nextNs = 0L
    suspend fun consume(bytes: Int) {
        if (rate <= 0) return
        val wait = lock.withLock {
            val now = System.nanoTime()
            nextNs = maxOf(now, nextNs) + (bytes * 1e9 / rate).toLong()
            ((nextNs - now) / 1_000_000).coerceAtLeast(0)
        }
        if (wait > 0) delay(wait)
    }
}
