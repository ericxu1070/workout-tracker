package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The weigh-in "Saved · Undo" bar lands on the Log weight pill, flat and Liquid Glass, and keeps a tap on it from reaching the pill. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class UndoBarTest {
    @get:Rule(order = 0) val activity = ComposeActivity()
    @get:Rule(order = 1) val rule = createComposeRule()

    private fun check(p: Palette) {
        var logs = 0
        rule.setContent {
            GzTheme(p) {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Exercises, {}) { ExercisesScreen(Demo.state.copy(active = null), {}, {}, { logs++ }, today = Demo.today) }
                    UndoBar("Saved 165.4 lb", {}, Modifier.padding(bottom = tabBarHeight(true) + Space.lg))
                }
            }
        }
        val pill = rule.onNodeWithText("Log weight").fetchSemanticsNode().boundsInRoot
        val bar = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).fetchSemanticsNode().boundsInRoot
        assertEquals("${p.id} top", pill.top, bar.top, 1f)
        assertEquals("${p.id} bottom", pill.bottom, bar.bottom, 1f)
        rule.onNodeWithText("Saved 165.4 lb").performClick()
        rule.runOnIdle { assertEquals("${p.id}: a tap on the bar reached Log weight", 0, logs) }
    }

    @Test fun coversLogWeight() = check(Palettes.tokyo)

    @Test fun coversLogWeightInLiquidGlass() = check(Palettes.tokyo.glassed)
}
