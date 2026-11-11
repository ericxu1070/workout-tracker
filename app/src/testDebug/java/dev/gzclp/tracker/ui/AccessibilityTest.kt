package dev.gzclp.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.gzclp.tracker.engine.Increments
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
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
                {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        rule.onAllNodesWithText("Restored 79 log entries").assertCountEquals(1)
        rule.onAllNodesWithText("Last sync failed", substring = true).assertCountEquals(0)
    }
}
