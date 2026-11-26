package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders, from demo data, the bottom tab bar at rest and with its bubble held and dragged from Workout toward Upcoming. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class TabBarShots {
    @get:Rule val rule = createComposeRule()

    private fun shots(p: Palette) {
        val out = System.getProperty("gzclp.shots")
        assumeNotNull(out)
        rule.setContent { GzTheme(p) { TabFrame(Tab.Workout, {}) { HomeScreen(Demo.state, {}, {}) } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(out, "${p.id}-40-tabs-rest.png").path)
        val bar = rule.onNodeWithText("Workout").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            down(bar.center)
            for (i in 1..10) moveBy(Offset(bar.width * 0.06f, 0f))
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onRoot().captureRoboImage(File(out, "${p.id}-40-tabs-held.png").path)
        rule.onRoot().performTouchInput { up() }
    }

    /** Frames 32 ms apart after a tap from Workout to History: the pill springs across, bouncing a little as it lands. */
    @Test fun tapFrames() {
        val out = System.getProperty("gzclp.shots")
        assumeNotNull(out)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            var tab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Tab.Workout) }
            GzTheme(Palettes.dark) { TabBar(tab) { tab = it } }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("History").performClick()
        repeat(10) { k ->
            rule.mainClock.advanceTimeBy(32)
            rule.onRoot().captureRoboImage(File(out, "dark-41-tab-tap-%02d.png".format(k)).path)
        }
    }

    /** The bar alone with each tab selected, in light, dark and Liquid Glass. */
    @Test fun eachTab() {
        val out = System.getProperty("gzclp.shots")
        assumeNotNull(out)
        var p by androidx.compose.runtime.mutableStateOf(Palettes.light)
        var tab by androidx.compose.runtime.mutableStateOf(Tab.Workout)
        rule.setContent { GzTheme(p) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(p.bg)) { TabBar(tab) {} } } }
        for (pal in listOf(Palettes.light, Palettes.dark, Palettes.glass)) for (t in Tab.entries) {
            p = pal
            tab = t
            rule.waitForIdle()
            rule.onRoot().captureRoboImage(File(out, "${pal.id}-42-tab-${t.name.lowercase()}.png").path)
        }
    }

    // One render per test: a compose rule takes one setContent.
    @Test fun dark() = shots(Palettes.dark)
    @Test fun light() = shots(Palettes.light)
    @Test fun glass() = shots(Palettes.glass)
}
