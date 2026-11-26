package dev.gzclp.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Debug-only (ui-test-manifest): the pill on the tab bar and on a segmented control moves only for a finger that lands on it.
 * A drag that starts elsewhere on the bar leaves the selection and the pill (the selected semantics follow the pill) where they were.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PillGrabTest {
    @get:Rule val rule = createComposeRule()

    private var tab by mutableStateOf(Tab.Workout)
    private var seg by mutableStateOf(0)
    private val modes = listOf("Lifting", "Cardio", "Stretching")

    private fun tabBar() = rule.setContent { GzTheme(Palettes.light) { TabBar(tab) { tab = it } } }
    private fun segmented() = rule.setContent { GzTheme(Palettes.light) { Segmented(modes, seg, { seg = it }) } }

    /** Presses the middle of [label]'s segment, and drags [segments] segment widths right, still holding. */
    private fun drag(label: String, segments: Float) {
        val b = rule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            down(b.center)
            for (i in 1..10) moveBy(Offset(b.width * segments / 10, 0f))
        }
        rule.waitForIdle()
    }

    private fun release() {
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
    }

    @Test fun tabBarDragOffPillMovesNothing() {
        tabBar()
        drag("Exercises", 1.6f)
        rule.onNodeWithText("Workout").assertIsSelected()
        release()
        assertEquals(Tab.Workout, tab)
        rule.onNodeWithText("Workout").assertIsSelected()
    }

    @Test fun tabBarDragOnPillSelectsOnRelease() {
        tabBar()
        drag("Workout", 1.6f)
        assertEquals(Tab.Workout, tab)
        release()
        assertEquals(Tab.Exercises, tab)
        rule.onNodeWithText("Exercises").assertIsSelected()
    }

    /** Quick taps across the bar: each selects exactly the tab tapped, and the pill lands on it. */
    @Test fun tabBarTapsOffPillSelectThatTab() {
        tabBar()
        for (t in listOf(Tab.History, Tab.Workout, Tab.Food, Tab.Upcoming, Tab.Exercises)) {
            rule.onNodeWithText(t.label).performClick()
            rule.waitForIdle()
            assertEquals(t, tab)
            rule.onNodeWithText(t.label).assertIsSelected()
        }
    }

    @Test fun segmentedDragOffPillMovesNothing() {
        segmented()
        drag("Cardio", 1.2f)
        rule.onNodeWithText("Lifting").assertIsSelected()
        release()
        assertEquals(0, seg)
        rule.onNodeWithText("Lifting").assertIsSelected()
    }

    @Test fun segmentedDragOnPillSelectsOnRelease() {
        segmented()
        drag("Lifting", 1.2f)
        assertEquals(0, seg)
        release()
        assertEquals(1, seg)
        rule.onNodeWithText("Cardio").assertIsSelected()
    }

    @Test fun segmentedTapOffPillSelects() {
        segmented()
        rule.onNodeWithText("Stretching").performClick()
        rule.waitForIdle()
        assertEquals(2, seg)
        rule.onNodeWithText("Stretching").assertIsSelected()
    }
}
