package dev.gzclp.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Debug-only (ui-test-manifest): a tap on a pill selector moves the pill as a drag does. It glides there over about 400 ms, the bar
 * swells and the pill lifts while it travels, in Liquid Glass (Loaded) the pill clears to glass, and all of it settles once it lands.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ThumbTapTest {
    @get:Rule val rule = createComposeRule()

    private fun look() = rule.onNode(SemanticsMatcher.keyIsDefined(ThumbLookKey)).fetchSemanticsNode().config[ThumbLookKey]()

    /** Taps [label] (down and up on its center) with the clock held, and returns how the thumb looks at 150 ms and 300 ms in. */
    private fun tapAndWatch(label: String): Pair<ThumbLook, ThumbLook> {
        rule.waitForIdle()
        val b = rule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(b.center); up() }
        rule.mainClock.advanceTimeBy(150)
        val early = look()
        rule.mainClock.advanceTimeBy(150)
        val mid = look()
        rule.mainClock.autoAdvance = true
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
        return early to mid
    }

    /** Checks the slide from segment 0 to [to]: still on its way at 150 ms, grown mid-slide (clear glass only in [glass]), and settled after. */
    private fun assertSlides(p: Palette, label: String, to: Int, content: @Composable () -> Unit) {
        rule.setContent { GzTheme(p) { content() } }
        val (early, mid) = tapAndWatch(label)
        assertTrue("still gliding at 150 ms: ${early.x}", early.x > 0.2f && early.x < to * 0.9f)
        assertTrue("bar swells: ${mid.swell}", mid.swell > 1.02f)
        assertTrue("pill lifts: ${mid.lift}", mid.lift > 0.5f)
        if (p.glass) assertTrue("pill clears to glass: ${mid.glass}", mid.glass > 0.5f)
        else assertEquals("no glass in Performance", 0f, mid.glass, 0f)
        val rest = look()
        assertEquals(to.toFloat(), rest.x, 0f)
        assertEquals(1f, rest.swell, 1e-3f)
        assertEquals(0f, rest.lift, 1e-3f)
        assertEquals(0f, rest.glass, 1e-3f)
        rule.onNodeWithText(label).assertIsSelected()
    }

    private var seg by mutableStateOf(0)
    private var tab by mutableStateOf(Tab.Workout)
    private var mode by mutableStateOf(0)
    private val modes = listOf("Lifting", "Cardio", "Stretching")

    @Composable private fun segmented() = Segmented(listOf("Day", "Week", "Month"), seg, { seg = it })
    @Composable private fun tabBar() = TabBar(tab) { tab = it }
    @Composable private fun workoutSwitch() = WorkoutPages(mode, { mode = it }, {}) { Txt("${modes[it]} page", Type.body) }

    @Test fun segmentedTapInLoaded() { assertSlides(Palettes.glass, "Month", 2) { segmented() }; assertEquals(2, seg) }
    @Test fun segmentedTapInPerformance() { assertSlides(Palettes.light, "Month", 2) { segmented() }; assertEquals(2, seg) }
    @Test fun tabBarTapInLoaded() { assertSlides(Palettes.glass, Tab.entries[3].label, 3) { tabBar() }; assertEquals(Tab.entries[3], tab) }
    @Test fun tabBarTapInPerformance() { assertSlides(Palettes.dark, Tab.entries[3].label, 3) { tabBar() }; assertEquals(Tab.entries[3], tab) }
    @Test fun workoutSwitchTapInLoaded() { assertSlides(Palettes.glass, "Stretching", 2) { workoutSwitch() }; assertEquals(2, mode) }
    @Test fun workoutSwitchTapInPerformance() { assertSlides(Palettes.light, "Stretching", 2) { workoutSwitch() }; assertEquals(2, mode) }

    /** A tap on the segment already chosen moves nothing and grows nothing. */
    @Test fun tapOnTheChosenSegmentStaysPut() {
        rule.setContent { GzTheme(Palettes.glass) { segmented() } }
        val (early, mid) = tapAndWatch("Day")
        for (l in listOf(early, mid)) assertEquals(0f, l.x, 0f)
        assertEquals(0, seg)
    }
}
