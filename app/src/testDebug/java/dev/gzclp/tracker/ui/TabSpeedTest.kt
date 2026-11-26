package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Debug-only (ui-test-manifest): a tab or a page shows on the frame after the finger lands, with nothing left to compose. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class TabSpeedTest {
    @get:Rule val rule = createComposeRule()

    /** Each tab's screen is composed once, whichever way the tabs are switched after. */
    @Test fun tabsStayComposed() {
        var tab by mutableStateOf(Tab.Workout)
        val composed = mutableMapOf<Tab, Int>()
        rule.setContent {
            GzTheme(Palettes.light) {
                TabHost({ tab }, { tab = it }) { t ->
                    remember { composed.merge(t, 1, Int::plus) }
                    Txt("${t.label} screen", Type.body)
                }
            }
        }
        for (t in listOf(Tab.History, Tab.Workout, Tab.Food, Tab.History, Tab.Upcoming, Tab.Exercises, Tab.Workout)) {
            tab = t
            rule.waitForIdle()
            rule.onNodeWithText("${t.label} screen").assertExists()
        }
        assertEquals(Tab.entries.associateWith { 1 }, composed)
    }

    /** Switching tabs recomposes none of the kept screens, the one left and the one shown included. */
    @Test fun switchRecomposesNoScreen() {
        var tab by mutableStateOf(Tab.Workout)
        val runs = mutableMapOf<Tab, Int>()
        rule.setContent {
            GzTheme(Palettes.light) {
                TabHost({ tab }, { tab = it }) { t ->
                    SideEffect { runs.merge(t, 1, Int::plus) }
                    Txt("${t.label} screen", Type.body)
                }
            }
        }
        // Past the other tabs' composing in the background.
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        val before = runs.toMap()
        assertEquals(Tab.entries.associateWith { 1 }, before)
        for (t in listOf(Tab.History, Tab.Food, Tab.Workout, Tab.Upcoming)) {
            tab = t
            rule.waitForIdle()
        }
        assertEquals(before, runs.toMap())
    }

    /** A tap on a segment turns the pages there by the next frame: the page sits where the first one sat. */
    @Test fun tappedPageShowsOnTheNextFrame() {
        var mode by mutableStateOf(0)
        val modes = listOf("Lifting", "Cardio", "Stretching")
        rule.setContent {
            GzTheme(Palettes.light) {
                WorkoutPages(mode, { mode = it }, {}) { page ->
                    ScreenFrame {
                        Txt("${modes[page]} page", Type.body)
                        Spacer(Modifier.height(400.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
        val home = rule.onNodeWithText("Lifting page").fetchSemanticsNode().boundsInRoot.left
        val b = rule.onNodeWithText("Stretching").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(b.center) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(home, rule.onNodeWithText("Stretching page").fetchSemanticsNode().boundsInRoot.left, 0.5f)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(2, mode)
    }
}
