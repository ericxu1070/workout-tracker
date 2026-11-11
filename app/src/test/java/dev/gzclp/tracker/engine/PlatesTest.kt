package dev.gzclp.tracker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatesTest {
    private val gym = Plates()

    @Test fun perSideUsesHeaviestPlatesFirst() {
        assertEquals(emptyList<Double>(), gym.perSide(45))
        assertEquals(listOf(45.0), gym.perSide(135))
        assertEquals(listOf(45.0, 45.0, 10.0, 2.5), gym.perSide(250))
        assertEquals(listOf(45.0, 45.0, 45.0, 45.0, 35.0, 25.0, 10.0, 10.0, 5.0, 2.5), gym.perSide(580))
    }

    @Test fun everyFiveLbStepIsLoadableWithTheDefaults() {
        for (w in 45..580 step 5) assertEquals("$w lb", w, gym.round(w))
    }

    @Test fun targetsRoundToTheNearestLoadableWeight() {
        assertEquals(45, gym.round(35)) // below the bar
        assertEquals(580, gym.round(700)) // past the plates on hand
        assertNull(gym.perSide(137))
        assertEquals(135, gym.round(137))
        val noSmalls = Plates(pairs = mapOf(45.0 to 2, 10.0 to 1))
        assertEquals(135, noSmalls.round(140)) // tie between 135 and 145 goes lighter
        assertEquals(155, noSmalls.round(150 + 3))
    }

    @Test fun findsALoadGreedyWouldMiss() {
        // 50 a side: greedy takes a 45 and is stuck; two 25s work.
        val p = Plates(pairs = mapOf(45.0 to 1, 25.0 to 2))
        assertEquals(listOf(25.0, 25.0), p.perSide(145))
        // With a 5 on hand too, the conventional 45 + 5 wins the tie on plate count.
        assertEquals(listOf(45.0, 5.0), Plates(pairs = mapOf(45.0 to 1, 25.0 to 2, 5.0 to 1)).perSide(145))
    }

    @Test fun barWeightShiftsEveryLoad() {
        val p = Plates(bar = 35)
        assertEquals(listOf(45.0), p.perSide(125))
        assertEquals(35, p.round(20))
    }
}
