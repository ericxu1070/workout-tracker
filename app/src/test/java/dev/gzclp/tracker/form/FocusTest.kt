package dev.gzclp.tracker.form

import dev.gzclp.tracker.stretch.StretchMoves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTest {
    private val legs = arrayOf(Bone.THIGH_L, Bone.SHIN_L)
    private fun leg(s: Int) = arrayOf(Bone.thigh(s), Bone.shin(s))

    @Test fun theHighlightFadesWithDistanceFromWhereTheStretchIsFelt() {
        val focus = StretchMoves.WALL_CALF_STRETCH.focus
        val w = focus.of(Body.leg, legs)
        val y = Body.leg.mesh.pos
        // Full on the calf, dimmer ring by ring up past the knee (the leg's rings sit at 0.38, 0.31 and 0.06), and dark at the hip.
        val calf = Build.THIGH + 0.14
        fun ring(at: Double) = (0 until Body.leg.mesh.verts).filter { kotlin.math.abs(y[it * 3 + 1] - at) < 0.015 }.maxOf { w[it] }
        val near = ring(calf); val mid = ring(0.38); val far = ring(0.31); val hip = ring(0.06)
        assertEquals(1f, near, 1e-6f)
        assertTrue("$near > $mid > $far", near > mid && mid > far)
        assertEquals(0f, hip, 0f)
        assertEquals(0.0, focus.at(V(0.0, 2.0, 0.0)), 0.0)
        assertTrue(w.all { it in 0f..1f })
    }

    @Test fun aOneSidedStretchLightsOneSideOnly() {
        for (move in StretchMoves.ALL) {
            val left = move.focus.of(Body.leg, leg(L)).max() + move.focus.of(Body.flat, arrayOf(Bone.ARM_L, Bone.FOREARM_L, Bone.HAND_L)).max()
            val right = move.focus.of(Body.leg, leg(R)).max() + move.focus.of(Body.flat, arrayOf(Bone.ARM_R, Bone.FOREARM_R, Bone.HAND_R)).max()
            if (move.focus.spots.size == 2) assertEquals(move.id, left, right, 1e-3f)
        }
        val calf = StretchMoves.WALL_CALF_STRETCH.focus
        assertTrue(calf.of(Body.leg, leg(L)).max() > 0.9f)
        assertEquals(0f, calf.of(Body.leg, leg(R)).max(), 0f)
        assertEquals(0f, calf.of(Body.of(Bone.FOOT_R)!!, Bone.FOOT_R).max(), 0f)
        // The arm reaching across the chest lights, the one drawing it in stays dark.
        val cross = StretchMoves.CROSS_BODY_SHOULDER.focus
        assertTrue(cross.of(Body.fist, arrayOf(Bone.ARM_L, Bone.FOREARM_L, Bone.HAND_L)).max() > 0.9f)
        assertEquals(0f, cross.of(Body.fist, arrayOf(Bone.ARM_R, Bone.FOREARM_R, Bone.HAND_R)).max(), 0f)
    }

    @Test fun everyStretchLightsTheBodyWhereItIsFelt() {
        val chains = listOf(Body.torso to arrayOf(Bone.PELVIS, Bone.WAIST, Bone.CHEST, Bone.NECK)) +
            SIDES.flatMap { s -> listOf(Body.leg to leg(s), Body.flat to arrayOf(Bone.arm(s), Bone.forearm(s), Bone.hand(s))) }
        val rigid = Bone.entries.mapNotNull { b -> Body.of(b)?.let { it to b } }
        for (move in StretchMoves.ALL) {
            val peak = maxOf(chains.maxOf { (s, b) -> move.focus.of(s, b).max() }, rigid.maxOf { (m, b) -> move.focus.of(m, b).max() })
            assertTrue("${move.id} peaks at $peak", peak > 0.6f)
        }
    }
}
