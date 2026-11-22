package dev.gzclp.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class SessionTimeTest {
    private val start = OffsetDateTime.parse("2026-11-20T18:26:00-08:00")
    private val sheet = SheetState.SessionTime("s1", start, 22 * 3600L + 15)

    @Test fun opensWithTheFieldsAndKeepsTheSecondsUntilTyped() {
        assertEquals("22", sheet.hours)
        assertEquals("0", sheet.minutes)
        assertEquals(22 * 3600L + 15, sheet.durationSec)
    }

    @Test fun typingSetsTheWholeDuration() {
        assertEquals(3600L, sheet.typed("1", "0")!!.durationSec)
        assertEquals(40 * 60L, sheet.typed("", "40")!!.durationSec)
        assertEquals(65 * 60L, sheet.typed("1", "05")!!.durationSec)
        assertFalse("empty is not saveable", sheet.typed("", "")!!.ok)
        assertFalse("over a day is not saveable", sheet.typed("25", "0")!!.ok)
    }

    @Test fun rejectsKeysThatAreNotAnHourOrMinute() {
        assertNull(sheet.typed("1", "60"))
        assertNull(sheet.typed("100", "0"))
        assertNull(sheet.typed("1.", "0"))
    }

    @Test fun pickingAnEndRefillsTheFields() {
        val s = sheet.typed("", "")!!.withDuration(5400)
        assertEquals("1" to "30", s.hours to s.minutes)
    }
}
