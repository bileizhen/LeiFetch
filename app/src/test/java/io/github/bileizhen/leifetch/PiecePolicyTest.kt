package io.github.bileizhen.leifetch

import io.github.bileizhen.leifetch.nsfx.PiecePolicy
import io.github.bileizhen.leifetch.nsfx.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class PiecePolicyTest {
    @Test fun pieceSizeScalesBeyond2048Pieces() {
        assertEquals(1L shl 20, PiecePolicy.pieceSize(2_064_101_061))          // 1.9GB → 1969 片,仍 1MB
        assertEquals(2L shl 20, PiecePolicy.pieceSize((1L shl 20) * 2049))     // 超过 2048 片 → 翻倍
        assertEquals(1L shl 20, PiecePolicy.pieceSize(0))
    }

    @Test fun coverageAccumulatesAcrossSegmentBoundaries() {
        val a = Segment(0, 0, 1500).apply { downloaded = 1200 }
        val b = Segment(1, 1500, 3000).apply { downloaded = 300 }
        val fills = PiecePolicy.coverage(listOf(a, b), 3000, 1000)
        assertEquals(listOf(255, 127, 0), fills.map { it.toInt() and 0xFF })  // 0-999 全满;1000-1499 半满;其余空
    }

    @Test fun coverageHandlesTinyFilesAndZeroProgress() {
        assertEquals(0, PiecePolicy.coverage(emptyList(), 0, 1024).size)
        val s = Segment(0, 0, 500).apply { downloaded = 500 }
        assertEquals(listOf(255), PiecePolicy.coverage(listOf(s), 500, 1024).map { it.toInt() and 0xFF })
    }
}
