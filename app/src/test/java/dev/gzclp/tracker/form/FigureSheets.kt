package dev.gzclp.tracker.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.ui.FormView
import dev.gzclp.tracker.ui.GzTheme
import dev.gzclp.tracker.ui.Orbit
import dev.gzclp.tracker.ui.Palettes
import dev.gzclp.tracker.ui.Projector
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Review tiles of every body, drawn by the app's own [FormView]: standing from all round, the poses that stress a mesh
 * most, and close-ups of the joints, hands and head (the view drawn larger than the tile and moved, so a close-up is the
 * same drawing, only nearer). One PNG per body and view under the folder named by SHEETS, to lay out side by side:
 * SHEETS=\$PWD/build/review/after ./gradlew recordRoborazziDebug --tests 'dev.gzclp.tracker.form.FigureSheets'
 * SHEETS_BODIES and SHEETS_ONLY (comma-separated) keep to some bodies and to the views whose names hold one of the words.
 * SHEETS_MOVES renders moves in place of the views, each at a point through it, whole and close on the left elbow:
 * SHEETS_MOVES=muscle_up@0.43,muscle_up@0.44
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h640dp-mdpi")
class FigureSheets {
    /** One view: [motion] at [d], seen from [yaw] and [pitch], with [span] metres of it up the tile around the point [at]. */
    private class Tile(val name: String, val motion: () -> Motion, val d: Double, val yaw: Float, val pitch: Float, val span: Double, val at: Poser.() -> V)

    private val hold = Tempo(1.0, 0.0, 1.0, 0.0)
    private val stand = Motion(hold) { pelvis(V(0.0, STAND, 0.0)); feet() }
    private val overhead = Motion(hold) { pelvis(V(0.0, STAND, 0.0)); feet(); for (s in SIDES) arm(s, raise = 172.0, plane = 60.0, elbow = 6.0) }
    private fun curl(pronate: Double) = Motion(hold) {
        pelvis(V(0.0, STAND, 0.0)); feet()
        for (s in SIDES) arm(s, raise = 8.0, plane = 90.0, elbow = 135.0, pronate = pronate)
    }
    private val fists = Motion(hold) { pelvis(V(0.0, STAND, 0.0)); feet(); for (s in SIDES) arm(s, raise = 14.0, plane = 20.0, elbow = 10.0, fist = true) }

    private val middle: Poser.() -> V = { V(0.0, this[Bone.HEAD].at(0.0, 0.1, 0.0).y / 2, 0.0) }
    private val face: Poser.() -> V = { this[Bone.HEAD].at(0.0, 0.07, 0.0) }
    private val elbow: Poser.() -> V = { this[Bone.FOREARM_L].o }
    private val chest: Poser.() -> V = { this[Bone.CHEST].at(0.0, 0.15, 0.0) }

    private val tiles = listOf(
        Tile("01-stand-front", { stand }, 0.0, 0f, 0f, 1.95, middle),
        Tile("02-stand-side", { stand }, 0.0, 90f, 0f, 1.95, middle),
        Tile("03-stand-back", { stand }, 0.0, 180f, 0f, 1.95, middle),
        Tile("04-stand-quarter", { stand }, 0.0, 35f, 15f, 1.95, middle),
        Tile("05-stand-quarter-back", { stand }, 0.0, 145f, 15f, 1.95, middle),
        Tile("06-torso-front", { stand }, 0.0, 0f, 0f, 0.85, { this[Bone.CHEST].at(0.0, 0.02, 0.0) }),
        Tile("07-torso-quarter", { stand }, 0.0, 35f, 10f, 0.85, { this[Bone.CHEST].at(0.0, 0.02, 0.0) }),
        Tile("08-torso-back", { stand }, 0.0, 180f, 0f, 0.85, { this[Bone.CHEST].at(0.0, 0.02, 0.0) }),
        Tile("10-head-front", { stand }, 0.0, 0f, 0f, 0.42, face),
        Tile("11-head-quarter", { stand }, 0.0, 35f, 8f, 0.42, face),
        Tile("12-head-side", { stand }, 0.0, 90f, 0f, 0.42, face),
        Tile("13-head-back", { stand }, 0.0, 180f, 0f, 0.42, face),
        Tile("20-squat-quarter", { Motions.of("squat") }, 1.0, 35f, 10f, 1.5, { this[Bone.WAIST].o }),
        Tile("21-squat-side", { Motions.of("squat") }, 1.0, 90f, 0f, 1.5, { this[Bone.WAIST].o }),
        Tile("22-squat-back", { Motions.of("squat") }, 1.0, 180f, 12f, 1.5, { this[Bone.WAIST].o }),
        Tile("23-squat-hips-back", { Motions.of("squat") }, 1.0, 160f, 12f, 0.7, { this[Bone.PELVIS].o }),
        Tile("24-knee-side", { Motions.of("squat") }, 1.0, 90f, 0f, 0.6, { this[Bone.SHIN_L].o }),
        Tile("25-knee-quarter", { Motions.of("squat") }, 1.0, 35f, 10f, 0.6, { this[Bone.SHIN_L].o }),
        Tile("30-overhead-front", { overhead }, 0.0, 0f, 0f, 2.3, { V(0.0, 1.12, 0.0) }),
        Tile("31-overhead-back", { overhead }, 0.0, 180f, 0f, 2.3, { V(0.0, 1.12, 0.0) }),
        Tile("32-shoulder-front", { overhead }, 0.0, 20f, 0f, 0.6, { shoulder(L) }),
        Tile("33-shoulder-back", { overhead }, 0.0, 160f, 0f, 0.6, { shoulder(L) }),
        Tile("40-fold-side", { Motions.of("hamstrings_seated_single") }, 1.0, 90f, 4f, 1.3, { this[Bone.WAIST].o }),
        Tile("41-fold-quarter", { Motions.of("hamstrings_seated_single") }, 1.0, 35f, 15f, 1.3, { this[Bone.WAIST].o }),
        Tile("50-elbow-palm-up-side", { curl(0.0) }, 0.0, 90f, 0f, 0.5, elbow),
        Tile("51-elbow-palm-up-quarter", { curl(0.0) }, 0.0, 35f, 10f, 0.5, elbow),
        Tile("52-elbow-palm-in-side", { curl(90.0) }, 0.0, 90f, 0f, 0.5, elbow),
        Tile("53-elbow-palm-down-side", { curl(180.0) }, 0.0, 90f, 0f, 0.5, elbow),
        Tile("60-fist-side", { fists }, 0.0, 90f, 0f, 0.26, { hand(L) }),
        Tile("61-fist-front", { fists }, 0.0, 0f, 0f, 0.26, { hand(L) }),
        Tile("62-hand-side", { stand }, 0.0, 90f, 0f, 0.3, { hand(L) }),
        Tile("63-hand-front", { stand }, 0.0, 0f, 0f, 0.3, { hand(L) }),
        Tile("70-feet-quarter", { stand }, 0.0, 35f, 20f, 0.4, { V(0.0, 0.08, 0.05) }),
        // The shoulders turned furthest and across the chest, and the legs spread widest.
        Tile("80-eagle-quarter", { Motions.of("upper_back_eagle") }, 1.0, 35f, 10f, 0.8, chest),
        Tile("81-eagle-back", { Motions.of("upper_back_eagle") }, 1.0, 160f, 10f, 0.8, chest),
        Tile("82-backbend-quarter", { Motions.of("shoulders_backbend") }, 0.85, 35f, 10f, 1.1, chest),
        Tile("83-backbend-back", { Motions.of("shoulders_backbend") }, 0.85, 160f, 10f, 1.1, chest),
        Tile("84-bench-quarter", { Motions.of("bench") }, 1.0, 35f, 25f, 0.9, chest),
        Tile("85-push-up-quarter", { Motions.of("push_up") }, 1.0, 35f, 15f, 0.9, chest),
        Tile("86-row-quarter", { Motions.of("barbell_row") }, 1.0, 35f, 10f, 1.0, chest),
        Tile("87-row-back", { Motions.of("barbell_row") }, 1.0, 150f, 15f, 1.0, chest),
        Tile("88-straddle-front", { Motions.of("hips_foam_roll_adductors") }, 0.0, 0f, 25f, 1.0, { this[Bone.PELVIS].o }),
        Tile("89-straddle-back", { Motions.of("hips_foam_roll_adductors") }, 0.0, 180f, 35f, 0.7, { this[Bone.PELVIS].o }),
    )

    @Test fun everyBodyFromAllRound() {
        val out = System.getenv("SHEETS")
        assumeTrue("set SHEETS to a folder to render the review tiles", out != null)
        val p = Palettes.light
        val only = System.getenv("SHEETS_ONLY")?.ifEmpty { null }?.split(",")
        val bodies = System.getenv("SHEETS_BODIES")?.ifEmpty { null }?.split(",") ?: Figure.bodies
        val moves = System.getenv("SHEETS_MOVES")?.ifEmpty { null }?.split(",")?.map { it.substringBefore('@') to it.substringAfter('@').toDouble() }
        val views = moves?.flatMap { (id, d) ->
            listOf(
                Tile("$id-$d-whole", { Motions.of(id) }, d, 35f, 15f, 2.0, { this[Bone.CHEST].o }),
                Tile("$id-$d-elbow-front", { Motions.of(id) }, d, 35f, 15f, 0.55, elbow),
                Tile("$id-$d-elbow-back", { Motions.of(id) }, d, 145f, 15f, 0.55, elbow),
            )
        } ?: tiles
        try {
            for (body in bodies) {
                Figure.shape = body
                for (t in views) if (only == null || only.any { it in t.name }) shot(File(out, "$body/${t.name}.png"), t, p)
            }
        } finally {
            Figure.shape = "male"
        }
    }

    private fun shot(file: File, t: Tile, palette: dev.gzclp.tracker.ui.Palette) {
        val m = t.motion()
        val at = Poser().also { m.pose(it, t.d) }.let(t.at)
        // The camera as FormView sets it for a canvas w by h, looking at the tile's point.
        val pr = Projector()
        fun focal(w: Float, h: Float) = (m.radius * 3.5f).let { dist -> min(w, h) * 0.46f * sqrt(dist * dist - m.radius * m.radius) / m.radius }
        fun look(w: Float, h: Float) {
            pr.set(t.yaw, t.pitch, m.center, m.radius * 3.5f, focal(w, h), w / 2, h / 2)
            pr.project(at.x.toFloat(), at.y.toFloat(), at.z.toFloat())
        }
        look(W.toFloat(), H.toFloat())
        val zoom = H / (t.span * focal(W.toFloat(), H.toFloat()) / pr.depth)
        val w = (W * zoom).roundToInt(); val h = (H * zoom).roundToInt()
        look(w.toFloat(), h.toFloat())
        val dx = (W / 2 - pr.sx).roundToInt(); val dy = (H / 2 - pr.sy).roundToInt()
        file.parentFile.mkdirs()
        captureRoboImage(file.path) {
            GzTheme(palette) {
                Box(Modifier.fillMaxSize().background(palette.bg).clipToBounds()) {
                    FormView(
                        m,
                        Modifier.layout { measurable, _ ->
                            val placed = measurable.measure(Constraints.fixed(w, h))
                            layout(W, H) { placed.place(dx, dy) }
                        },
                        still = t.d.toFloat(), orbit = Orbit().apply { yaw = t.yaw; pitch = t.pitch },
                    )
                }
            }
        }
    }

    private companion object {
        /** A tile in pixels, as the qualifiers above make the screen. */
        const val W = 480
        const val H = 640
    }
}
