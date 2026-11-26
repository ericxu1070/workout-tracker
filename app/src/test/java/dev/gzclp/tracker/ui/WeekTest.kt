package dev.gzclp.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekTest {
    @Test
    fun weekChangeIsSignedTime() {
        assertEquals("−1 hr 6 min", signedMinutes(-66 * 60L))
        assertEquals("+26 min", signedMinutes(26 * 60L + 10))
        assertEquals("±0 min", signedMinutes(20L))
    }
}
