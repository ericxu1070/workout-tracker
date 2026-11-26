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

/** Debug-only: the Compose test activity comes from ui-test-manifest, a debug dependency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class TabBarDragTest {
    @get:Rule val rule = createComposeRule()

    private var tab by mutableStateOf(Tab.Workout)

    private fun bar(p: Palette) = rule.setContent { GzTheme(p) { TabBar(tab) { tab = it } } }

    /** Drags the pill from Workout 1.6 tabs right and holds it there, then lets go: the tab changes only on release, to the nearest. */
    private fun dragSelectsNearestOnRelease(p: Palette) {
        bar(p)
        val w = rule.onNodeWithText("Workout").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            down(w.center)
            for (i in 1..16) moveBy(Offset(w.width * 0.1f, 0f))
        }
        rule.waitForIdle()
        assertEquals(Tab.Workout, tab)
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(Tab.Exercises, tab)
        rule.onNodeWithText("Exercises").assertIsSelected()
    }

    @Test fun dragSelectsNearestOnRelease() = dragSelectsNearestOnRelease(Palettes.light)
    @Test fun dragSelectsNearestOnReleaseInGlass() = dragSelectsNearestOnRelease(Palettes.glass)

    @Test fun tapSelects() {
        bar(Palettes.dark)
        rule.onNodeWithText("Food").performClick()
        rule.waitForIdle()
        assertEquals(Tab.Food, tab)
        rule.onNodeWithText("Food").assertIsSelected()
    }
}
