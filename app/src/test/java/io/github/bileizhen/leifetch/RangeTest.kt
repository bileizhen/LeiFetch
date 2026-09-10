package io.github.bileizhen.leifetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import io.github.bileizhen.leifetch.nsfx.*

class RangeTest {
    @Test fun acceptsLargeOffsets() {
        assertEquals(9_000_000_000L, NsfxDownloadEngine.parseRange("bytes 5000000000-5999999999/9000000000", 5_000_000_000, 5_999_999_999))
    }
    @Test fun rejectsWrongOffset() {
        assertThrows(RangeFailure::class.java) { NsfxDownloadEngine.parseRange("bytes 0-99/1000", 100, 199) }
    }
    @Test fun rejectsWildcardTotal() {
        assertThrows(RangeFailure::class.java) { NsfxDownloadEngine.parseRange("bytes 0-99/*", 0, 99) }
    }
}
