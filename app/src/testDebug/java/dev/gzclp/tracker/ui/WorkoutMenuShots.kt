package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders, from demo data, the Workout tab's one button closed, opening frame by frame and open: in the Performance look (dark, light)
 * and in Liquid Glass with Loaded motion (`./gradlew recordRoborazziDebug --tests '*WorkoutMenuShots' -PgzclpShots=dir`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class WorkoutMenuShots {
    @get:Rule val rule = createComposeRule()

    private fun shots(p: Palette, name: String) {
        val out = System.getProperty("gzclp.shots")
        assumeNotNull(out)
        // 84 full-screen frames: rendered only by recordRoborazziDebug, not on every test run.
        assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        rule.setContent {
            // Liquid Glass is shown as it ships by default, with Loaded motion; the solid looks with Performance.
            val motion = remember { Motion(if (p.glass) LoadedEffects else emptySet()) }
            CompositionLocalProvider(LocalMotion provides motion) {
                GzTheme(p) {
                    Box(Modifier.fillMaxSize().watchTaps(motion)) {
                        TabFrame(Tab.Workout, {}) { WorkoutPages(0, {}, {}) { if (it == 0) HomeScreen(Demo.state, {}, {}) } }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(out, "$name-0-closed.png").path)
        // Frames 32 ms apart from the tap, then the menu at rest.
        val b = rule.onNodeWithText(START_OR_LOG).fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(b.center); up() }
        repeat(12) { k ->
            rule.mainClock.advanceTimeBy(32)
            rule.onRoot().captureRoboImage(File(out, "$name-1-opening-%02d.png".format(k)).path)
        }
        rule.mainClock.advanceTimeBy(2000)
        rule.onRoot().captureRoboImage(File(out, "$name-2-open.png").path)
        // And closing, from a tap on the page above it.
        rule.onRoot().performTouchInput { down(topCenter + androidx.compose.ui.geometry.Offset(0f, height * 0.4f)); up() }
        repeat(8) { k ->
            rule.mainClock.advanceTimeBy(32)
            rule.onRoot().captureRoboImage(File(out, "$name-3-closing-%02d.png".format(k)).path)
        }
    }

    // One render per test: a compose rule takes one setContent.
    @Test fun dark() = shots(Palettes.dark, "dark")
    @Test fun light() = shots(Palettes.light, "light")
    @Test fun glassDark() = shots(Palettes.glass, "glass-dark")
    @Test fun glassLight() = shots(Palettes.light.glassed, "glass-light")
}
