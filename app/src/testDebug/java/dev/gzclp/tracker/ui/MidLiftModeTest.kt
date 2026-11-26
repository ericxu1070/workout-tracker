package dev.gzclp.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.OffsetDateTime

/** Debug-only: the Compose test activity comes from ui-test-manifest, a debug dependency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MidLiftModeTest {
    @get:Rule val rule = createComposeRule()

    private val t0 = OffsetDateTime.parse("2026-11-25T18:00:00-08:00")

    private fun lifting() = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() }).run {
        dispatch(t0) { Commands.setup(Lift.entries.associateWith { 100 }, Programs.GZCLP) }
        while (true) {
            val missing = Commands.missingWeight(state.value) ?: break
            dispatch(t0) { Commands.startingWeight(it, missing, 50) }
        }
        dispatch(t0) { Commands.start(it, t0) }
        dispatch(t0) { Commands.logCardio(it, null, Activities.all.first(), t0.minusDays(1), 1800, null, null, null, t0) }
        state.value
    }

    @Test fun workoutShowsTheModeSwitchDuringALift() {
        var mode = 0
        val state = lifting()
        rule.setContent { WorkoutPages(0, { mode = it }, {}) { if (it == 0) WorkoutScreen(state, t0, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) } }
        rule.onNodeWithText("Cardio").performClick()
        rule.waitForIdle()
        assertEquals(1, mode)
    }

    @Test fun cardioDuringALiftOnlyLogsPastSessions() {
        val state = lifting()
        rule.setContent { CardioScreen(state, t0.toLocalDate(), 150, {}, {}, {}, {}, lifting = true) }
        rule.onNodeWithText("Lift in progress").assertIsNotEnabled()
        rule.onAllNodesWithText("Quick start").assertCountEquals(0)
    }
}
