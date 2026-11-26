package dev.gzclp.tracker.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack names every control: a control with no visible text carries its label as its description. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NamedControlsTest {
    @get:Rule(order = 0) val activity = ComposeActivity()
    @get:Rule(order = 1) val rule = createComposeRule()

    @Test fun everyControlOnTheWorkoutHasAName() {
        rule.setContent { GzTheme(Palettes.tokyo) { WorkoutScreen(Demo.state, Demo.now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) } }
        val controls = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
        assertTrue(controls.size > 10)
        val unnamed = controls.filter { n ->
            n.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() && n.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
        }
        assertEquals("unnamed: ${unnamed.map { it.config.getOrNull(SemanticsActions.OnClick)?.label }}", 0, unnamed.size)
        rule.onNodeWithContentDescription("Log Squat set 4").assertExists()
    }

    @Test fun stepperButtonsAreNamed() {
        rule.setContent { GzTheme(Palettes.tokyo) { Stepper("30:00", {}, {}) } }
        rule.onNodeWithContentDescription("Decrease").assertExists()
        rule.onNodeWithContentDescription("Increase").assertExists()
    }
}
