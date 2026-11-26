package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Exercises tab's muscle map in both styles, male and female, in Tokyo Night and Liquid Glass, over the demo's last full week. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MuscleMapShots {
    private val out = File(System.getProperty("gzclp.shots") ?: "build/shots")

    @Test fun styles() {
        out.mkdirs()
        val sunday = Demo.today.minusDays(1)
        val idle = Demo.state.copy(active = null)
        for (p in listOf(Palettes.tokyo, Palettes.glass)) for (flat in listOf(true, false)) for (female in listOf(false, true)) {
            val name = "${p.id}-muscles-${if (flat) "flat" else "3d"}-${if (female) "female" else "male"}.png"
            captureRoboImage(File(out, name).path) {
                GzTheme(p) { Box(Modifier.fillMaxSize()) { TabFrame(Tab.Exercises, {}) { ExercisesScreen(idle, {}, {}, {}, today = sunday, female = female, flat = flat) } } }
            }
        }
    }
}
