package dev.gzclp.tracker.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordTest {
    @Test fun aSetIsARecordOnlyWhenItsEstimatedMaxBeatsTheBestSoFar() {
        // 200 x 5 is an estimated 233.
        assertTrue(isRecord(232, 200, 5))
        assertFalse(isRecord(233, 200, 5))
        assertFalse(isRecord(null, 200, 5))
        assertFalse(isRecord(100, 200, 0))
    }

    @Test fun theBestSoFarTakesThisWorkoutsOtherSetsButNotTheOneBeingLogged() {
        val at = java.time.OffsetDateTime.parse("2026-11-26T10:00:00Z")
        val k1 = SetKey("squat", dev.gzclp.tracker.engine.Tier.T1, 1)
        val k2 = SetKey("squat", dev.gzclp.tracker.engine.Tier.T1, 2)
        fun set(k: SetKey, w: Int, r: Int) = LoggedSet(SetLogged("s", k.exercise, "T1", "5x3", k.setIndex, r, r, w, w, false), at)
        val sets = mapOf(k1 to set(k1, 300, 3), k2 to set(k2, 100, 3))
        // 300 x 3 is an estimated 330; 100 x 3, 110.
        assertEquals(330, bestSoFar(200, sets, "squat", k2))
        assertEquals(200, bestSoFar(200, sets, "squat", k1))
        assertEquals(null, bestSoFar(null, emptyMap(), "squat", k1))
    }
}
