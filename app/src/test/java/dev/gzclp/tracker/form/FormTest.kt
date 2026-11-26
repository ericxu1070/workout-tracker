package dev.gzclp.tracker.form

import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.ui.Orbit
import dev.gzclp.tracker.ui.Projector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
open class FormTest {
    @Before fun asPieces() { Figure.on = false }
    @After fun asMesh() { Figure.on = true }

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

    @Test fun cameraCirclesUntilAFingerHoldsIt() {
        val o = Orbit()
        o.spin(1f)
        assertEquals(47f, o.yaw, 0.001f)
        o.held = true
        o.spin(1f)
        assertEquals(47f, o.yaw, 0.001f)
        o.held = false
        o.spin(30f)
        assertEquals(47f, o.yaw, 0.001f) // a full turn
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

    @Test fun noPartOfTheBodyEverGoesBelowTheFloor() = everyPose { id, _, p, _ ->
        p.lowest().takeIf { it < -sink(id) }?.let { "y=$it" }
    }

    /** How far the body of [id] may sink into the floor. */
    protected open fun sink(id: String) = 0.005

    /** The viewer fits this sphere into the card at every angle, so a plate poking out of it is cut off somewhere. */
    @Test fun theBodyAndWhatItLiftsStayInsideTheCameraSphere() = everyPose { _, m, p, _ ->
        var far = 0.0
        p.framed { x, y, z -> far = maxOf(far, V(x - m.center[0], y - m.center[1], z - m.center[2]).len) }
        (far - m.radius).takeIf { it > 0 }?.let { "reaches ${"%.3f".format(it)} m outside the camera sphere" }
    }

    @Test fun handsAndFeetReachWhatTheyHoldOrStandOn() = everyPose { id, _, p, _ ->
        p.strain.takeIf { it > short(id) }?.let { "a limb falls ${"%.3f".format(it)} m short" }
    }

    /** How far a hand or foot of [id] may fall short of what it holds or stands on. */
    protected open fun short(id: String) = 0.01

    @Test fun standingMovesKeepTheirWeightOverTheFeet() = everyPose { _, m, p, _ ->
        if (!m.balance || p.feet == 0) return@everyPose null
        val over = p.stance * (1.0 / p.feet)
        val c = p.com()
        val off = V(c.x - over.x, 0.0, c.z - over.z).len
        off.takeIf { it > 0.03 }?.let { "centre of mass ${"%.3f".format(it)} m off the feet" }
    }

    /** On a foam roller or ball the whole body travels over the floor, so the legs drag or ride along with it. */
    private val ROLLING = setOf("shoulders_foam_roll_rear", "hips_foam_roll_it_band", "hips_foam_roll_adductors")

    @Test fun feetOnTheFloorNeverSlide() {
        val p = Poser()
        val bad = mutableListOf<String>()
        for (id in all - ROLLING) {
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

    @Test fun customIdsFindTheMotionTheySpellOrNone() {
        assertEquals("pull_up", Motions.resolve("pull_ups"))
        assertEquals("chin_up", Motions.resolve("Chin-Ups"))
        assertEquals("squat", Motions.resolve("squat"))
        assertEquals(null, Motions.resolve("incl"))
        assertEquals(null, Motions.resolve("custom_thing_nobody_knows"))
    }

    /** The two ends of the long thin rods of radius [r] in [p]'s gear, rejoined from their pieces. */
    private fun rods(p: Poser, r: Double) = p.gear.filter { it.mesh == Body.rod && it.sx == r }.map { it.frame.o to it.frame.at(0.0, it.sy, 0.0) }

    @Test fun theInclinePressStacksEachForearmUnderALevelDumbbell() = everyPose(listOf("incline_db_press")) { _, _, p, _ ->
        val tilt = SIDES.maxOf { Math.toDegrees(kotlin.math.acos(p[Bone.forearm(it)].y dot V.UP)) }
        val roll = rods(p, 0.016).maxOf { (a, b) -> Math.toDegrees(kotlin.math.asin(kotlin.math.abs((b - a).unit() dot V.UP))) }
        var inside = 0
        val heads = p.gear.filter { it.held && it.sx == 0.07 }
        p.skin(Bone.TRUNK) { x, y, z ->
            val q = V(x, y, z)
            if (heads.any { g -> val t = (q - g.frame.o) dot g.frame.y; t in 0.0..g.sy && ((q - g.frame.o) - g.frame.y * t).len < g.sx }) inside++
        }
        when {
            tilt > 3 -> "forearm ${"%.1f".format(tilt)}° off vertical"
            roll > 3 -> "dumbbell tilted ${"%.1f".format(roll)}°"
            inside > 0 -> "a dumbbell head cuts into the body at $inside points"
            else -> null
        }
    }

    @Test fun theSeatedRowCableRunsLevelFromAPulleyAtTheHands() = everyPose(listOf("seated_cable_row")) { _, _, p, _ ->
        val (a, b) = rods(p, 0.005).first()
        val slope = Math.toDegrees(kotlin.math.asin(kotlin.math.abs((b - a).unit() dot V.UP)))
        slope.takeIf { it > 2 }?.let { "cable ${"%.1f".format(it)}° off level" }
    }

    @Test fun theLatPulldownColumnStandsInFront() = everyPose(listOf("lat_pulldown")) { _, _, p, _ ->
        val post = rods(p, 0.04).first().first
        "column at z=${"%.2f".format(post.z)}, behind the hips".takeIf { post.z < p[Bone.PELVIS].o.z }
    }

    @Test fun aPullUpBarEndsAtItsPosts() = everyPose(BODYWEIGHT.keys.filter { "pull_up" in it || "chin_up" in it || "muscle_up" in it }) { _, _, p, _ ->
        val bar = rods(p, 0.016).flatMap { it.toList() }.filter { kotlin.math.abs(it.y - 2.25) < 1e-6 }
        val ends = listOf(bar.minBy { it.x }, bar.maxBy { it.x })
        val posts = rods(p, 0.03).map { it.first }
        ends.filter { e -> posts.none { V(e.x - it.x, 0.0, e.z - it.z).len < 0.03 } }.takeIf { it.isNotEmpty() }?.let { "bar end $it past the posts" }
    }

    @Test fun theBarNeverPassesThroughTheLegs() = everyPose(listOf("deadlift", "romanian_deadlift", "barbell_row")) { _, _, p, _ ->
        val bar = p.gear.firstOrNull { it.mesh == Body.rod && it.sx == 0.014 }?.frame?.o ?: return@everyPose "no barbell"
        var worst = 1.0
        p.skin(listOf(Bone.THIGH_L, Bone.THIGH_R, Bone.SHIN_L, Bone.SHIN_R)) { _, y, z -> worst = minOf(worst, kotlin.math.hypot(y - bar.y, z - bar.z)) }
        worst.takeIf { it < clear() }?.let { "bar ${"%.3f".format(it)} m from the leg's axis line" }
    }

    /** How near the leg's axis line the bar may pass. */
    protected open fun clear() = 0.009
}
