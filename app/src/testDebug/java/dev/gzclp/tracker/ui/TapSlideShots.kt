package dev.gzclp.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Frames 32 ms apart after a tap on each pill selector, from demo data: the tab bar, the Workout switch and History's segmented control,
 * in Liquid Glass (Loaded) and dark (Performance), for the PR's GIFs (`./gradlew recordRoborazziDebug --tests '*TapSlideShots' -PgzclpShots=dir`). The pill glides over, the bar and pill grow while it travels, and both settle after.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class TapSlideShots {
    @get:Rule val rule = createComposeRule()

    private fun frames(p: Palette, name: String, label: String, content: @Composable () -> Unit) {
        val out = System.getProperty("gzclp.shots")
        assumeNotNull(out)
        // 180 full-screen frames: rendered only by recordRoborazziDebug, not on every test run.
        assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        rule.setContent { GzTheme(p) { content() } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)
        val b = rule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(b.center); up() }
        repeat(30) { k ->
            rule.onRoot().captureRoboImage(File(out, "${p.id}-tap-$name-%02d.png".format(k)).path)
            rule.mainClock.advanceTimeBy(32)
        }
    }

    private fun tabs(p: Palette) = frames(p, "tabs", "History") {
        var tab by remember { mutableStateOf(Tab.Workout) }
        TabFrame(tab, { tab = it }) { HomeScreen(Demo.state, {}, {}) }
    }

    private fun switch(p: Palette) = frames(p, "switch", "Stretching") {
        var mode by remember { mutableStateOf(0) }
        TabFrame(Tab.Workout, {}) { WorkoutPages(mode, { mode = it }, {}) { if (it == 0) HomeScreen(Demo.state, {}, {}) } }
    }

    private fun segmented(p: Palette) = frames(p, "segmented", "All") {
        var view by remember { mutableStateOf(HistoryView.Week) }
        TabFrame(Tab.History, {}) { HistoryScreen(Demo.state, view, { view = it }, {}, {}, Demo.today) {} }
    }

    @Test fun tabsGlass() = tabs(Palettes.glass)
    @Test fun tabsDark() = tabs(Palettes.dark)
    @Test fun switchGlass() = switch(Palettes.glass)
    @Test fun switchDark() = switch(Palettes.dark)
    @Test fun segmentedGlass() = segmented(Palettes.glass)
    @Test fun segmentedDark() = segmented(Palettes.dark)
}
