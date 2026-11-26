package dev.gzclp.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.gzclp.tracker.engine.Increments
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.form.Figure
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Entry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime

/** Debug-only: the Compose test activity comes from ui-test-manifest, a debug dependency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rootListsPagesAndOpensOne() {
        var opened: SettingsPage? = null
        rule.setContent {
            SettingsScreen(
                true, Programs.GZCLP, Increments(), "tokyo", null, Tier.entries.associateWith { it.defaultRestSec },
                SyncUi(null, null, null), true, true, 7 * 60, HealthUi(false, null), Plates(),
                {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onPage = { opened = it },
            )
        }
        // The root holds no setting of its own beyond two switches; rest timers live on Lifting.
        rule.onAllNodesWithText("Rest timer").assertCountEquals(0)
        rule.onNodeWithText("Tokyo Night").assertExists()
        rule.onNodeWithText("Lifting").performClick()
        assertEquals(SettingsPage.Lifting, opened)
    }

    private fun appearance(themeId: String, onBuild: (String) -> Unit = {}, onTheme: (String) -> Unit = {}) = rule.setContent {
        SettingsScreen(
            true, Programs.GZCLP, Increments(), themeId, null, Tier.entries.associateWith { it.defaultRestSec },
            SyncUi(null, null, null), true, true, 7 * 60, HealthUi(false, null), Plates(),
            {}, onTheme, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, page = SettingsPage.Appearance, onBodyBuild = onBuild,
        )
    }

    @Test fun showMoreRevealsEveryCustomTheme() {
        var chosen: String? = null
        appearance("light") { chosen = it }
        rule.onNodeWithText("System").assertExists()
        rule.onNodeWithText("Laptop").assertExists()
        rule.onAllNodesWithText("Gruvbox").assertCountEquals(0)
        rule.onNodeWithText("Show more").performClick()
        rule.onNodeWithText("Gruvbox").performScrollTo().performClick()
        assertEquals("gruvbox", chosen)
        rule.onNodeWithText("Show less").assertExists()
    }

    @Test fun appearanceAsksForGender() {
        appearance("light")
        rule.onNodeWithText("Gender").performScrollTo().assertExists()
        rule.onAllNodesWithText("Muscle map body").assertCountEquals(0)
        rule.onNodeWithText("Female").assertExists()
    }

    @Test fun theBuildIsAskedOnlyWhileTheMeshFigureIsOn() {
        var chosen: String? = null
        Figure.on = true
        try {
            appearance("light", onBuild = { chosen = it })
            rule.onNodeWithText("Bodybuilder").performScrollTo().performClick()
            assertEquals("bodybuilder", chosen)
        } finally {
            Figure.on = false
        }
    }

    @Test fun theBuildHidesWhileTheFigureIsPieces() {
        appearance("light")
        rule.onAllNodesWithText("Build").assertCountEquals(0)
    }

    @Test fun aCustomThemeOpensExpanded() {
        appearance("tokyo")
        rule.onNodeWithText("Show less").assertExists()
        rule.onNodeWithText("Jade").assertExists()
    }

    @Test fun presetExercisesRemoveOnlyInEditModeAfterDelete() {
        val now = OffsetDateTime.parse("2026-11-25T09:00:00-08:00")
        val state = Commands.setup(Lift.entries.associateWith { 100 }, Programs.GZCLP).fold(AppState()) { s, e -> s.apply(Entry(0, now, e)) }
        val removed = mutableListOf<String>()
        rule.setContent { DaysScreen(state, {}, {}, { _, id -> removed += id }) }
        rule.onAllNodesWithContentDescription("Remove", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Edit").performClick()
        val name = state.exercise(dev.gzclp.tracker.engine.Slot(Commands.dayList(state, state.program, state.program.days.first()).first(), Tier.T3)).name
        rule.onAllNodesWithContentDescription("Remove $name")[0].performClick()
        assertEquals(emptyList<String>(), removed)
        rule.onNodeWithText("Delete").performClick()
        assertEquals(1, removed.size)
        // Presets keep their order, so edit mode has no grip.
        rule.onAllNodesWithContentDescription("Reorder", substring = true).assertCountEquals(0)
    }
}
