package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Exercises tab's muscle map, male and female, in Tokyo Night and Liquid Glass, over the demo's last full week, with a muscle tapped. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MuscleMapShots {
    @get:Rule(order = 0) val activity = ComposeActivity()
    @get:Rule(order = 1) val rule = createComposeRule()
    private val out = File(System.getProperty("gzclp.shots") ?: "build/shots")

    @Test fun styles() {
        out.mkdirs()
        val sunday = Demo.today.minusDays(1)
        val idle = Demo.state.copy(active = null)
        val look = mutableStateOf(Palettes.tokyo to false)
        rule.setContent {
            val (p, female) = look.value
            GzTheme(p) { Box(Modifier.fillMaxSize()) { TabFrame(Tab.Exercises, {}) { ExercisesScreen(idle, {}, {}, {}, today = sunday, female = female) } } }
        }
        for (p in listOf(Palettes.tokyo, Palettes.glass)) for (female in listOf(false, true)) {
            look.value = p to female
            rule.waitForIdle()
            rule.onRoot().captureRoboImage(File(out, "${p.id}-muscles-${if (female) "female" else "male"}.png").path)
        }
        look.value = Palettes.tokyo to false
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Muscles worked: ", substring = true).performTouchInput { click(percentOffset(0.244f, 0.255f)) }
        rule.onRoot().captureRoboImage(File(out, "tokyo-muscles-tapped.png").path)
    }
}
