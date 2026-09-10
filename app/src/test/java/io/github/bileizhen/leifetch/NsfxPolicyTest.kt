package io.github.bileizhen.leifetch

import io.github.bileizhen.leifetch.nsfx.*
import org.junit.Assert.*
import org.junit.Test

class NsfxPolicyTest {
    private val mb = 1024L * 1024
    @Test fun automaticThresholdsMatchUpstream() {
        val config = NsfxConfig(threads = 8)
        assertEquals(1 to 1, SegmentPlanner.calculate(4 * mb, config))
        assertEquals(1 to 1, SegmentPlanner.calculate(5 * mb, config))
        assertEquals(2 to 2, SegmentPlanner.calculate(10 * mb, config))
        assertEquals(4 to 4, SegmentPlanner.calculate(20 * mb, config))
        assertEquals(8 to 12, SegmentPlanner.calculate(200 * mb, config))
        assertEquals(8 to 24, SegmentPlanner.calculate(2048 * mb, config))
    }
    @Test fun threadsAndSegmentCountAreIndependent() {
        assertEquals(3 to 12, SegmentPlanner.calculate(120 * mb, NsfxConfig(threads = 3, mode = "manual", segments = 12)))
    }
    @Test fun tailStealUsesUpstreamRatio() {
        val plan = DynamicSegmentPolicy.plan(listOf(SplitSnapshot(0, 100 * mb, 10.0, 0, 9000)), 4, 4)
        assertEquals(1, plan.size)
        assertEquals(60 * mb, plan[0].stealBytes)
        assertEquals("tail-steal", plan[0].reason)
    }
    @Test fun stalledTailUsesSixtyFivePercent() {
        val plan = DynamicSegmentPolicy.plan(listOf(SplitSnapshot(0, 100 * mb, 0.0, 5000, 9000)), 2, 1)
        assertEquals(65 * mb, plan.single().stealBytes)
    }
    @Test fun respectsCooldownAndAvailableSlots() {
        assertTrue(DynamicSegmentPolicy.plan(listOf(SplitSnapshot(0, 100 * mb, 1.0, 0, 1000)), 4, 1).isEmpty())
        assertTrue(DynamicSegmentPolicy.plan(listOf(SplitSnapshot(0, 100 * mb, 1.0, 0, 9000)), 1, 1).isEmpty())
    }
    @Test fun retryBackoffMatchesUpstream() {
        assertEquals(637, NsfxRetryPolicy.delayMs(1))
        assertEquals(1024, NsfxRetryPolicy.delayMs(2))
        assertEquals(15000, NsfxRetryPolicy.delayMs(32))
    }
    @Test fun coverageRejectsGapsEvenIfByteSumMatches() {
        assertThrows(IllegalArgumentException::class.java) {
            NsfxStorage.verifyCoverage(listOf(Segment(0, 0, 50), Segment(1, 51, 101)), 100)
        }
    }
}
