package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.form.Figure
import dev.gzclp.tracker.form.Focus
import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.stretch.StretchMoves
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Tiles of the form viewer, one figure each, to join into review sheets (ImageMagick's montage): each [Look] side by side,
 * and the one in use wherever it has to hold. Run with ./gradlew recordRoborazziDebug --tests '*FormLookShots'.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w300dp-h240dp-420dpi")
class FormLookShots {
    private val out = File(System.getProperty("gzclp.shots") ?: "build/shots", "look")

    private class Pose(val name: String, val id: String, val t: Float, val yaw: Float, val pitch: Float) {
        val focus: Focus? get() = StretchMoves.ALL.firstOrNull { it.id == id }?.focus
    }

    private val poses = listOf(
        Pose("squat", "squat", 1f, 35f, 12f),
        Pose("deadlift", "deadlift", 0f, 60f, 8f),
        Pose("bench", "bench", 0.5f, 55f, 20f),
        Pose("pigeon", "hips_pigeon", 1f, 35f, 18f),
        Pose("curl", "hammer_curl", 0.6f, 20f, 8f),
    )

    private fun tile(p: Palette, name: String, pose: Pose, size: String = "w300dp-h240dp-420dpi") {
        RuntimeEnvironment.setQualifiers(size)
        captureRoboImage(File(out, "$name.png").path) {
            GzTheme(p) {
                Box(Modifier.fillMaxSize().background(p.bg)) {
                    FormView(Motions.of(pose.id), Modifier.fillMaxSize().background(p.fill), focus = pose.focus, still = pose.t, orbit = Orbit().apply { yaw = pose.yaw; pitch = pose.pitch })
                }
            }
        }
    }

    private val looks = listOf("unlined" to Look.UNLINED, "thin-soft" to Look.THIN_SOFT, "medium-cel" to Look.MEDIUM_CEL, "bold-flat" to Look.BOLD_FLAT)

    private fun wearing(l: Look, shots: () -> Unit) {
        val worn = look
        look = l
        try { shots() } finally { look = worn; Figure.on = true; Figure.shape = "male" }
    }

    /** The same five figures in each look, in a light and a dark theme, full size and as the stretch list's thumbnail. */
    @Test fun variants() {
        for ((name, l) in looks) wearing(l) {
            for (p in listOf(Palettes.light, Palettes.jade)) for (pose in poses) {
                tile(p, "$name/${p.id}-${pose.name}", pose)
                tile(p, "$name/${p.id}-${pose.name}-thumb", pose, "w44dp-h44dp-420dpi")
            }
        }
    }

    /** The look in use: on every body, in every kind of theme, all the way round a lift at mid-rep, a degree at a time, and through the see-through pass. */
    @Test fun chosen() = wearing(look) {
        for (body in Figure.bodies) {
            Figure.shape = body
            tile(Palettes.light, "bodies/$body-curl", Pose("curl", "hammer_curl", 0.6f, 25f, 8f))
            tile(Palettes.light, "bodies/$body-squat", Pose("squat", "squat", 1f, 150f, 12f))
        }
        Figure.shape = "male"
        for (p in listOf(Palettes.light, Palettes.light.glassed, Palettes.jade, Palettes.tokyo, Palettes.dark, Palettes.glass)) {
            tile(p, "themes/${p.id}-bench", poses[2])
            tile(p, "themes/${p.id}-pigeon", poses[3])
        }
        for (yaw in 0 until 360 step 45) tile(Palettes.light, "turn/squat-${"%03d".format(yaw)}", Pose("squat", "squat", 0.5f, yaw.toFloat(), 12f))
        for (i in 0 until 12) {
            tile(Palettes.light, "steady/yaw-${"%02d".format(i)}", Pose("curl", "hammer_curl", 0.6f, 20f + i, 8f))
            tile(Palettes.light, "steady/rep-${"%02d".format(i)}", Pose("squat", "squat", 0.5f + i * 0.01f, 35f, 12f))
        }
        for (i in 0 until 48) tile(Palettes.light, "spin/${"%02d".format(i)}", Pose("squat", "squat", if (i < 24) i / 23f else (47 - i) / 23f, i * 7.5f, 12f), "w200dp-h160dp-420dpi")
        for ((id, yaw) in listOf("pull_up" to 90f, "pull_up" to 0f, "calf_raise" to 200f, "lat_pulldown" to 180f, "leg_press" to 270f, "bench" to 235f)) {
            for (p in listOf(Palettes.light, Palettes.jade)) tile(p, "through/${p.id}-$id-${yaw.toInt()}", Pose(id, id, 0.5f, yaw, 10f))
        }
    }
}
