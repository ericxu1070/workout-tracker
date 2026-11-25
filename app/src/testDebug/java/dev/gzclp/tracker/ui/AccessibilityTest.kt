package dev.gzclp.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.gzclp.tracker.engine.Increments
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Debug-only: the Compose test activity comes from ui-test-manifest, a debug dependency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun iconButtonExposesItsLabel() {
        rule.setContent { IconButton(Icons.settings, "Settings", {}) }
        rule.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test fun restoreNoticeIsNotASyncFailure() {
        rule.setContent {
            SettingsScreen(
                false, Programs.GZCLP, Increments(), "jade", null, Tier.entries.associateWith { it.defaultRestSec },
                SyncUi("GZCLP", "Fri, Oct 30, 7:42 PM", null, notice = "Restored 79 log entries"), true, false, 0, HealthUi(false, null), Plates(),
                {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, page = SettingsPage.Sync,
            )
        }
        rule.onAllNodesWithText("Restored 79 log entries").assertCountEquals(1)
        rule.onAllNodesWithText("Last sync failed", substring = true).assertCountEquals(0)
    }

    private fun role(r: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, r)

    @Test fun toggleIsANamedSwitchWithState() {
        var on by mutableStateOf(false)
        rule.setContent { Toggle(on, "Pace reminder") { on = it } }
        rule.onNodeWithContentDescription("Pace reminder").assert(role(Role.Switch)).assertIsOff().performClick()
        rule.onNodeWithContentDescription("Pace reminder").assertIsOn()
    }

    @Test fun segmentsAndTabsAnnounceSelection() {
        rule.setContent {
            Segmented(listOf("Lifting", "Cardio"), 1, {})
            TabBar(Tab.History) {}
        }
        rule.onNodeWithText("Cardio").assert(role(Role.Tab)).assertIsSelected()
        rule.onNodeWithText("Lifting").assert(role(Role.Tab)).assertIsNotSelected()
        rule.onNodeWithText("History").assert(role(Role.Tab)).assertIsSelected()
        rule.onNodeWithText("Workout").assertIsNotSelected()
    }

    @Test fun cupLogsAFullCupFromTalkBack() {
        var logged = 0
        rule.setContent { InteractiveCup(350, { logged = it }) }
        val action = rule.onNodeWithContentDescription("Water cup").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("Log 350 ml", action.label)
        rule.runOnIdle { action.action() }
        assertEquals(350, logged)
    }
}
