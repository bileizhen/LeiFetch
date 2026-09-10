// Kotlin adaptation of Hanabi-Download-Manager-X NSFX, commit 5df83d3.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.bileizhen.leifetch.nsfx

import kotlin.math.roundToLong

data class NsfxConfig(
    val threads: Int = 4,
    val segments: Int? = null,
    val mode: String = "auto",
    val maxConcurrentTasks: Int = 3,
    val globalMaxConnections: Int = 16,
    val maxRetries: Int = 32,
    val enableDynamicSegments: Boolean = true,
    val globalSpeedLimit: Long = 0,
    val segmentSpeedLimit: Long = 0,
    val connectionTimeoutMs: Int = 30_000,
    val readTimeoutMs: Int = 30_000
)

object SegmentPlanner {
    fun calculate(size: Long, config: NsfxConfig): Pair<Int, Int> {
        val maxThreads = config.threads.coerceIn(1, 64)
        if (size <= 0) return 1 to 1
        val maxSegments = minOf(256L, size).toInt()
        when (config.mode) {
            "manual" -> (config.segments ?: config.threads).coerceIn(1, maxSegments).let { return minOf(maxThreads, it) to it }
            "threads_only" -> config.threads.coerceIn(1, minOf(32, maxSegments)).let { return minOf(maxThreads, it) to it }
            "segments_only" -> (config.segments ?: 16).coerceIn(1, maxSegments).let { return minOf(maxThreads, it) to it }
        }
        val mb = 1024L * 1024
        var count = when {
            size < 5 * mb -> 1
            size < 20 * mb -> 2
            size < 50 * mb -> 4
            size < 200 * mb -> 8
            size < 500 * mb -> 12
            size < 1024 * mb -> 16
            size < 2048 * mb -> 20
            else -> 24
        }
        if (config.enableDynamicSegments && count > 1 && size / count < 5 * mb) {
            count = (size / (5 * mb)).toInt().coerceIn(1, count)
        }
        count = count.coerceIn(1, 32)
        return minOf(maxThreads, count) to count
    }
}

data class SplitSnapshot(val index: Int, val remaining: Long, val speed: Double, val idleMs: Long, val sinceSplitMs: Long)
data class SplitDecision(val index: Int, val stealBytes: Long, val reason: String, val score: Double)

object DynamicSegmentPolicy {
    const val MIN_SPLIT_BYTES = 8L * 1024 * 1024
    fun plan(snapshots: List<SplitSnapshot>, maxConcurrent: Int, totalSegments: Int): List<SplitDecision> {
        val active = snapshots.filter { it.remaining > 0 }
        if (active.isEmpty()) return emptyList()
        val maxPlans = minOf(256 - totalSegments, maxConcurrent - active.size, 2)
        if (maxPlans <= 0) return emptyList()
        val average = active.map { it.remaining.toDouble() }.average()
        val speeds = active.map { it.speed }.filter { it > 0 }.sorted()
        val median = if (speeds.isEmpty()) 0.0 else speeds[speeds.size / 2]
        val single = active.size == 1
        return active.mapNotNull { s ->
            if (s.remaining < MIN_SPLIT_BYTES * 2 || s.sinceSplitMs < 8000) return@mapNotNull null
            val ratio = if (average <= 0) 1.0 else s.remaining / average
            val stalled = s.idleMs >= 4000
            val slow = median > 0 && s.speed > 0 && s.speed < median * 0.7
            val heavy = ratio >= if (single) 1.0 else 1.35
            if (!single && !stalled && !(slow && heavy)) return@mapNotNull null
            val stealRatio = if (stalled) 0.65 else if (single) 0.60 else 0.50
            val steal = (s.remaining * stealRatio).roundToLong().coerceIn(MIN_SPLIT_BYTES, s.remaining - MIN_SPLIT_BYTES)
            val score = ratio + (if (single) 1 else 0) + (if (stalled) 2 else 0) +
                if (median > 0 && s.speed > 0) (1 - s.speed / median).coerceIn(0.0, 1.5) else 0.0
            SplitDecision(s.index, steal, if (stalled) "stall-tail-steal" else if (single) "tail-steal" else "throughput-tail-steal", score)
        }.sortedByDescending { it.score }.take(maxPlans)
    }
}

object NsfxRetryPolicy {
    fun delayMs(retry: Int): Long {
        val attempt = retry.coerceAtLeast(1)
        val exponent = if (attempt > 6) 5 else attempt - 1
        return (500L * (1 shl exponent) + (attempt * 137) % 250).coerceIn(500, 15000)
    }
    fun maxRetries(configured: Int) = (if (configured < 1) 32 else configured).coerceIn(1, 32)
    val permanentHttp = setOf(400, 401, 403, 404, 405, 410, 416, 451)
}
