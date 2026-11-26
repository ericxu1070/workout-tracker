package dev.gzclp.tracker.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test fun loadedTurnsEveryEffectOnAndPerformanceOrRemoveAnimationsNone() {
        assertEquals(LoadedEffects, effectsFor(loaded = true, animatorsOn = true))
        assertEquals(emptySet<Effect>(), effectsFor(loaded = false, animatorsOn = true))
        assertEquals(emptySet<Effect>(), effectsFor(loaded = true, animatorsOn = false))
    }

    @Test fun aRingSpreadsAndCalmsAndADropRightAfterAStrongerOneIsTheSameImpact() {
        val f = RippleField()
        f.drop(Offset(10f, 10f), 1f)
        f.drop(Offset(500f, 500f), 0.5f)
        assertEquals(1, f.waves.size)
        f.tick(1_000L)
        f.tick(1_000L + RippleField.DURATION_NS / 2)
        assertTrue(rippleRadius(0.5f, 100f) in 50f..100f)
        assertTrue(rippleAmp(0.5f, 1f) < 1f)
        f.tick(1_000L + RippleField.DURATION_NS + 1)
        assertEquals(0, f.waves.size)
    }
}
