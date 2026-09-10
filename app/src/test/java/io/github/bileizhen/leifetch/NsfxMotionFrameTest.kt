package io.github.bileizhen.leifetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NsfxMotionFrameTest {
    @Test fun drainingFinishesParticlesWithoutWrapping() {
        val running = NsfxMotionFrame(NsfxMotionMode.Running, listOf(NsfxParticle(.7f), NsfxParticle(.2f)))
        val draining = running.transition(active = false, lanes = 1, progress = 0f)
        val end = draining.sample(1f)
        assertEquals(NsfxMotionMode.Draining, draining.mode)
        assertEquals(1f, end.first().position, 0.0001f)
        assertEquals(0f, end.first().opacity, 0.0001f)
    }

    @Test fun startingAgainKeepsExistingParticlesAndAddsNoJumpingPhase() {
        val draining = NsfxMotionFrame(NsfxMotionMode.Draining, listOf(NsfxParticle(.8f, .5f)))
        val running = draining.transition(active = true, lanes = 1, progress = 0f)
        assertEquals(NsfxMotionMode.Running, running.mode)
        assertTrue(running.particles.first().position >= .79f)
    }
}
