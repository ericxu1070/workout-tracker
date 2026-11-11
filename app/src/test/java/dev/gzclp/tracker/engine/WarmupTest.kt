package dev.gzclp.tracker.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WarmupTest {
    @Test fun percentagesOfFirstWorkingSetRoundedToFive() {
        assertEquals(listOf(85 to 5, 125 to 5, 170 to 3), Warmup.sets(210)) // 84, 126, 168
    }

    @Test fun neverBelowTheBarNorRepeated() {
        assertEquals(listOf(45 to 5, 50 to 3), Warmup.sets(65)) // 26 and 39 both clamp to the bar
        assertEquals(listOf(45 to 5, 55 to 5, 75 to 3), Warmup.sets(95))
    }

    @Test fun noneWhenTheWorkingSetIsTheBar() {
        assertEquals(emptyList<Pair<Int, Int>>(), Warmup.sets(45))
        assertEquals(emptyList<Pair<Int, Int>>(), Warmup.sets(0))
    }
}
