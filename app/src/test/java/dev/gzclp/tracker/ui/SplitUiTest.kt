package dev.gzclp.tracker.ui

import dev.gzclp.tracker.engine.Scheme
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.Wave
import dev.gzclp.tracker.log.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitUiTest {
    private val wave = Wave(5, listOf(8, 6, 5))
    private fun ex(id: String, week: Int, tier: Tier = Tier.CUSTOM) =
        Exercise(id, id, tier, if (tier == Tier.CUSTOM) wave.weeks[week] else Scheme(5, 3, true), 100, week, wave = wave.takeIf { tier == Tier.CUSTOM })

    @Test fun aSplitExerciseReadsLikeTheCycle() {
        assertEquals("Week 2 of 3 · 5×6", ex("bench", 1).detail())
    }

    @Test fun splitWarmUpsAreForBarbellLiftsAtSixRepsOrFewer() {
        // Week 1 is 8 reps, weeks 2 and 3 are 6 and 5; a cable fly is never loaded on a bar.
        assertEquals(listOf(false, true, true), (0..2).map { ex("bench", it).warmsUp() })
        assertEquals(false, ex("chest_fly", 2).warmsUp())
        assertEquals(true, ex("squat", 0, Tier.T1).warmsUp())
    }
}
