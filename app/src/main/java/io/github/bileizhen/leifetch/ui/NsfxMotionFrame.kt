package io.github.bileizhen.leifetch

internal enum class NsfxMotionMode { Idle, Running, Draining }
internal data class NsfxParticle(val position: Float, val opacity: Float = 1f)

internal data class NsfxMotionFrame(
    val mode: NsfxMotionMode = NsfxMotionMode.Idle,
    val particles: List<NsfxParticle> = emptyList(),
) {
    fun sample(progress: Float): List<NsfxParticle> = particles.map { particle ->
        val t = progress.coerceIn(0f, 1f)
        when (mode) {
            NsfxMotionMode.Idle -> particle.copy(opacity = 0f)
            NsfxMotionMode.Running -> NsfxParticle(
                (particle.position + t) % 1f,
                particle.opacity + (1f - particle.opacity) * (t * 6f).coerceAtMost(1f),
            )
            NsfxMotionMode.Draining -> NsfxParticle(
                particle.position + (1f - particle.position) * t,
                particle.opacity * (1f - t),
            )
        }
    }

    fun transition(active: Boolean, lanes: Int, progress: Float): NsfxMotionFrame {
        if (!active && mode == NsfxMotionMode.Idle) return this
        val current = sample(progress)
        val next = List(lanes + 1) { i ->
            current.getOrNull(i) ?: NsfxParticle(if (i == lanes) 0f else i.toFloat() / lanes, 0f)
        }
        return NsfxMotionFrame(if (active) NsfxMotionMode.Running else NsfxMotionMode.Draining, next)
    }
}
