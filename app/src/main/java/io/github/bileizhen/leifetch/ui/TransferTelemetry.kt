// 每任务的分段点阵与速度历史;仅在内存中维护,断点续传时由引擎快照重建。
package io.github.bileizhen.leifetch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SpeedSample(val time: Long, val speed: Long)

data class TransferStatus(
    val connections: Int = 0,
    val pieceSize: Long = 0L,
    val fills: ByteArray = ByteArray(0),
    val history: List<SpeedSample> = emptyList(),
    val activeMs: Long = 0L,
    val peak: Long = 0L,
    val mean: Long = 0L,
) {
    // 覆盖度以无符号解读:Byte 是有符号的,255 会读成 -1。
    val donePieces: Int get() = fills.count { (it.toInt() and 0xFF) >= 255 }
}

class TransferTelemetryRegistry {
    // 样本上限:超过后两两合并(自适应分辨率),生命周期视图可无限延长。
    private val maxSamples = 1440
    private val flows = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<TransferStatus>>()
    private fun mutable(id: String) = flows.computeIfAbsent(id) { MutableStateFlow(TransferStatus()) }
    fun flow(id: String): StateFlow<TransferStatus> = mutable(id).asStateFlow()
    fun record(id: String, connections: Int, pieceSize: Long, fills: ByteArray, speed: Long) {
        mutable(id).update { s ->
            val now = System.currentTimeMillis()
            val last = s.history.lastOrNull()
            val dt = if (last == null || speed <= 0) 0L else (now - last.time).coerceIn(0L, 10_000L)
            var history = s.history + SpeedSample(now, speed)
            if (history.size > maxSamples) history = history.chunked(2)
                .map { pair -> SpeedSample(pair.last().time, pair.map { it.speed }.sum() / pair.size) }
            val active = history.filter { it.speed > 0 }
            TransferStatus(connections, pieceSize, fills, history, s.activeMs + dt,
                maxOf(s.peak, speed), if (active.isEmpty()) 0L else active.sumOf { it.speed } / active.size)
        }
    }
    fun clear(id: String) { flows.remove(id) }
}
