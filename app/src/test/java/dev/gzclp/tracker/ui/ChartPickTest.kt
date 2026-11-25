package dev.gzclp.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartPickTest {
    private val xs = listOf(10f, 50f, 90f)

    @Test fun scrubSnapsToTheNearestPoint() {
        assertEquals(0, nearestPoint(-40f, 3) { xs[it] })
        assertEquals(1, nearestPoint(69f, 3) { xs[it] })
        assertEquals(2, nearestPoint(71f, 3) { xs[it] })
        assertEquals(2, nearestPoint(400f, 3) { xs[it] })
    }

    @Test fun tapOutsideReachPicksNothing() {
        assertEquals(1, nearestPoint(60f, 3, reach = 10f) { xs[it] })
        assertNull(nearestPoint(70f, 3, reach = 10f) { xs[it] })
        assertNull(nearestPoint(5f, 0) { 0f })
    }
}
