package dev.gzclp.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbTest {
    @Test fun aRestingThumbIsThePlainPill() {
        assertEquals(0f, stretchFor(0f), 0f)
        assertEquals(1f to 1f, thumbScale(0f))
        val pill = thumbPill(50f, 18f, 100f, 36f, lift = 0f, stretch = 0f)
        assertEquals(100f, pill.width, 1e-4f)
        assertEquals(36f, pill.height, 1e-4f)
    }

    @Test fun fasterMeansShorterAndWiderEitherWay() {
        val speeds = listOf(0.5f, 2f, 5f, 12f, 40f)
        val stretches = speeds.map(::stretchFor)
        assertEquals(stretches, stretches.sorted())
        assertTrue(stretches.zipWithNext().all { (a, b) -> b > a })
        speeds.forEach { assertEquals(stretchFor(it), stretchFor(-it), 0f) }
        assertTrue(stretches.all { it < MAX_STRETCH })
        val (w, h) = thumbScale(stretchFor(6f))
        assertTrue("wide $w", w > 1.1f)
        assertTrue("short $h", h < 0.92f)
    }

    @Test fun theBounceAfterAStopIsTallAndNarrow() {
        val (w, h) = thumbScale(-0.12f)
        assertTrue(w < 1f && h > 1f)
    }

    /** Driven a frame at a time toward a target that moves every frame, as a pager's position does, the thumb keeps moving. */
    @Test fun aMovingTargetNeverFreezesTheThumb() {
        val m = ThumbMotion(0f)
        var last = 0f
        for (f in 1..20) {
            m.step(f / 20f, 1 / 60f)
            assertTrue("frame $f at ${m.x}", m.x > last)
            last = m.x
        }
    }

    /** A thumb flung at a segment lags, stretches wide while fast, overshoots into tall and narrow as it stops, then rests exactly on it. */
    @Test fun aStopBouncesThroughTallAndNarrowThenRests() {
        val m = ThumbMotion(0f)
        val xs = mutableListOf<Float>()
        val stretches = mutableListOf<Float>()
        var frames = 0
        while (!m.step(1f, 1 / 60f)) {
            xs += m.x; stretches += m.stretch; frames++
            assertTrue("never settles", frames < 300)
        }
        assertTrue("lags: ${xs[0]}", xs[0] < 0.5f)
        assertTrue("overshoots: ${xs.max()}", xs.max() > 1f)
        assertTrue("wide while fast: ${stretches.max()}", stretches.max() > 0.1f)
        assertTrue("tall as it stops: ${stretches.min()}", stretches.min() < -0.02f)
        assertEquals(1f, m.x, 0f)
        assertEquals(0f, m.stretch, 0f)
    }

    @Test fun squashKeepsTheArea() {
        for (s in listOf(-0.3f, -0.1f, 0.1f, 0.35f)) thumbScale(s).let { (w, h) -> assertEquals(1f, w * h, 1e-5f) }
    }

    @Test fun aLiftedThumbClearsTheBar() {
        // The thumb is 36 dp in a bar 4 dp taller each side.
        val pill = thumbPill(60f, 18f, 120f, 36f, lift = 1f, stretch = 0f)
        assertTrue("height ${pill.height}", pill.height > 44f + 8f)
        assertTrue("width ${pill.width}", pill.width < 120f * 1.1f)
        // Grown mostly taller: the selector contrasts with the bar instead of looking like a bigger copy of it.
        assertTrue("taller than wider", pill.height / 36f - 1f > 4 * (pill.width / 120f - 1f))
        assertEquals(18f, (pill.top + pill.bottom) / 2, 1e-4f)
    }

    /** Close behind a finger moving 4 segments a second: under 0.25 segment of lag once it has caught up. */
    @Test fun theThumbKeepsUpWithAFinger() {
        val m = ThumbMotion(0f)
        for (f in 1..30) m.step(f * 4f / 60f, 1 / 60f)
        assertTrue("lag ${2f - m.x}", 2f - m.x < 0.25f)
    }
}
