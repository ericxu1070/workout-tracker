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

    @Test fun ringsDroppedAsFastAsTheyCanAllRunToTheirEnd() {
        val f = RippleField()
        var t = 1L
        // One a third of a second, the fastest a ring is taken as a new impact, for three seconds.
        repeat(9) {
            f.drop(Offset(10f, 10f), 0.5f)
            f.tick(t)
            // No ring is cut off: every one still spreading was dropped within the last 1.8 seconds.
            assertEquals(minOf(it + 1, 6), f.waves.size)
            t += 310_000_000L
            f.tick(t)
        }
    }

    @Test fun aChangeOfModeKeepsTheWaterAndItsRings() {
        val loaded = Motion(LoadedEffects)
        loaded.ripples!!.drop(Offset(10f, 10f))
        val performance = Motion(emptySet(), loaded.water)
        assertEquals(null, performance.ripples)
        assertEquals(1, performance.water.waves.size)
    }

    @Test fun aPageFadesOnlyOnceItIsWellOnItsWay() {
        assertEquals(1f, pageAlpha(0f))
        assertTrue(pageAlpha(0.5f) >= 0.75f)
        assertEquals(0f, pageAlpha(1f))
        assertEquals(0f, pageAlpha(2f))
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
