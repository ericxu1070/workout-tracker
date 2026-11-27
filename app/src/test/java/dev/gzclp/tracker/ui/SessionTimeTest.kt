package dev.gzclp.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class SessionTimeTest {
    private val start = OffsetDateTime.parse("2026-11-20T18:10:00-08:00")
    private val sheet = SheetState.SessionTime("s1", start, 40 * 60L)

    @Test fun pickingAnEndKeepsTheStart() {
        val s = sheet.endingAt(19, 5)
        assertEquals(start to 55 * 60L, s.startedAt to s.durationSec)
        assertEquals("an end at or before the start is the next day", 23 * 3600L + 50 * 60, sheet.endingAt(18, 0).durationSec)
    }

    @Test fun pickingAStartKeepsTheEnd() {
        val s = sheet.startingAt(17, 50)
        assertEquals(start.minusMinutes(20) to 60 * 60L, s.startedAt to s.durationSec)
        assertEquals(sheet.end, s.end)
        val before = sheet.startingAt(19, 0)
        assertEquals("a start at or after the end is the day before", start.minusDays(1).plusMinutes(50), before.startedAt)
        assertEquals(sheet.end, before.end)
        assertTrue(before.ok)
        // A top-sets workout ends when Finish was tapped, seconds and all: a picked start keeps whole minutes to that end.
        val odd = SheetState.SessionTime("s1", start.plusSeconds(50), 50 * 60L).startingAt(17, 30)
        assertEquals(90 * 60L, odd.durationSec)
    }

    @Test fun aTopSetsFinishSlidesAroundItsEstimate() {
        val s = SheetState.TopFinish(40 * 60L)
        assertEquals(40 * 60L, s.durationSec)
        assertEquals(80 * 60L, s.maxSec)
        assertEquals("a short session still slides up to an hour", 3600L, SheetState.TopFinish(10 * 60L).maxSec)
    }
}
