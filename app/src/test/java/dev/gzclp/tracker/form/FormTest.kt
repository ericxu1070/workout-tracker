package dev.gzclp.tracker.form

import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.ui.Orbit
import dev.gzclp.tracker.ui.Projector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormTest {
    @Test fun pitchNeverGoesBelowTheFloorOrPastStraightDown() {
        val o = Orbit()
        for (dy in listOf(-5000f, -90f, 0f, 90f, 5000f)) {
            o.drag(13f, dy)
            assertTrue("pitch ${o.pitch}", o.pitch in 0f..89f)
        }
        assertEquals(0f, Orbit.clampPitch(-30f), 0f)
        assertEquals(89f, Orbit.clampPitch(120f), 0f)
    }

    @Test fun yawTurnsFreely() {
        val o = Orbit()
        repeat(40) { o.drag(100f, 0f) }
        assertTrue(o.yaw > -360f && o.yaw < 360f)
        o.reset()
        assertEquals(35f, o.yaw, 0f)
    }

    @Test fun lookAtTargetProjectsToTheCanvasCenter() {
        val pr = Projector()
        val target = floatArrayOf(0.3f, 0.9f, -0.2f)
        for (yaw in -360..360 step 45) for (pitch in listOf(-20f, 0f, 15f, 45f, 89f, 120f)) {
            pr.set(yaw.toFloat(), pitch, target, 3.5f, 800f, 200f, 120f)
            pr.project(target[0], target[1], target[2])
            assertEquals("yaw $yaw pitch $pitch", 200f, pr.sx, 0.01f)
            assertEquals("yaw $yaw pitch $pitch", 120f, pr.sy, 0.01f)
            assertEquals(3.5f, pr.depth, 0.001f)
        }
    }

    @Test fun everyExerciseAndStretchHasItsOwnMotion() {
        val missing = all.filterNot { Motions.known(it) }
        assertTrue("no motion for $missing", missing.isEmpty())
        assertTrue(Motions.of("custom_thing_nobody_knows").radius > 0f)
    }

    private val all = Catalog.all.map { it.id } + StretchMoves.ALL.map { it.id }

    /** Runs [check] on every motion at 21 points through its range and fails listing what it returned. */
    private fun everyPose(ids: List<String> = all, check: (id: String, m: Motion, p: Poser, d: Double) -> String?) {
        val p = Poser()
        val bad = mutableListOf<String>()
        for (id in ids) {
            val m = Motions.of(id)
            for (k in 0..20) {
                m.pose(p, k / 20.0)
                check(id, m, p, k / 20.0)?.let { bad += "$id d=${k / 20.0}: $it" }
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun noPartOfTheBodyEverGoesBelowTheFloor() = everyPose { _, _, p, _ ->
        p.lowest().takeIf { it < -0.005 }?.let { "y=$it" }
    }

    /** The viewer fits this sphere into the card at every angle, so a plate poking out of it is cut off somewhere. */
    @Test fun theBodyAndWhatItLiftsStayInsideTheCameraSphere() = everyPose { _, m, p, _ ->
        var far = 0.0
        p.framed { x, y, z -> far = maxOf(far, V(x - m.center[0], y - m.center[1], z - m.center[2]).len) }
        (far - m.radius).takeIf { it > 0 }?.let { "reaches ${"%.3f".format(it)} m outside the camera sphere" }
    }

    @Test fun handsAndFeetReachWhatTheyHoldOrStandOn() = everyPose { _, _, p, _ ->
        p.strain.takeIf { it > 0.01 }?.let { "a limb falls ${"%.3f".format(it)} m short" }
    }

    @Test fun standingMovesKeepTheirWeightOverTheFeet() = everyPose { _, m, p, _ ->
        if (!m.balance || p.feet == 0) return@everyPose null
        val over = p.stance * (1.0 / p.feet)
        val c = p.com()
        val off = V(c.x - over.x, 0.0, c.z - over.z).len
        off.takeIf { it > 0.03 }?.let { "centre of mass ${"%.3f".format(it)} m off the feet" }
    }

    @Test fun feetOnTheFloorNeverSlide() {
        val p = Poser()
        val bad = mutableListOf<String>()
        for (id in all) {
            val m = Motions.of(id)
            for (b in listOf(Bone.TOES_L, Bone.TOES_R)) {
                val at = (0..20).map { m.pose(p, it / 20.0); p[b].o }
                val still = at.all { kotlin.math.abs(it.y - at[0].y) < 0.005 }
                val slide = at.maxOf { V(it.x - at[0].x, 0.0, it.z - at[0].z).len }
                if (still && slide > 0.01) bad += "$id $b slides ${"%.3f".format(slide)} m"
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun theBarNeverPassesThroughTheLegs() = everyPose(listOf("deadlift", "romanian_deadlift", "barbell_row")) { _, _, p, _ ->
        val bar = p.gear.firstOrNull { it.mesh == Body.rod && it.sx == 0.014 }?.frame?.o ?: return@everyPose "no barbell"
        var worst = 1.0
        p.skin(listOf(Bone.THIGH_L, Bone.THIGH_R, Bone.SHIN_L, Bone.SHIN_R)) { _, y, z -> worst = minOf(worst, kotlin.math.hypot(y - bar.y, z - bar.z)) }
        worst.takeIf { it < 0.009 }?.let { "bar ${"%.3f".format(it)} m from the leg's axis line" }
    }
}
