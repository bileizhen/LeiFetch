package io.github.bileizhen.leifetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    @Test fun strongEtagRejectsWeakValidator() {
        assertEquals("\"v1\"", NsfxDownloadEngine.strongEtag("  \"v1\" "))
        assertEquals("", NsfxDownloadEngine.strongEtag("W/\"v1\""))
    }
    @Test fun acceptsOnlyValidLastModifiedDate() {
        assertEquals("Mon, 23 Apr 2018 08:32:25 GMT",
            NsfxDownloadEngine.validLastModified("Mon, 23 Apr 2018 08:32:25 GMT"))
        assertEquals("", NsfxDownloadEngine.validLastModified("not-a-date"))
    }
    @Test fun prefersStrongEtagAndFallsBackToLastModified() {
        val date = "Mon, 23 Apr 2018 08:32:25 GMT"
        assertTrue(NsfxDownloadEngine.validatorMatches(
            FileInfo("https://example.invalid/a", 100, "\"v1\"", date, true), "\"v1\"", "changed"))
        assertTrue(NsfxDownloadEngine.validatorMatches(
            FileInfo("https://example.invalid/a", 100, "", date, true), null, date))
        assertFalse(NsfxDownloadEngine.validatorMatches(
            FileInfo("https://example.invalid/a", 100, "", date, true), null, "Tue, 24 Apr 2018 08:32:25 GMT"))
    }
}
