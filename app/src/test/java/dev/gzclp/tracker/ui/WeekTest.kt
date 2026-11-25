package dev.gzclp.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekTest {
    @Test
    fun tileChangeNamesItsReference() {
        assertEquals("1 hr 6 min less\nthan last week", minutesVs(-66 * 60L, "last week"))
        assertEquals("26 min more\nthan the week before", minutesVs(26 * 60L + 10, "the week before"))
        assertEquals("Same as last week", minutesVs(20L, "last week"))
    }
}
