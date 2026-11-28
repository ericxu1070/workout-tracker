package dev.gzclp.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.Event
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime

/** An open session on screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class OpenSessionUiTest {
    @get:Rule(order = 0) val activity = ComposeActivity()
    @get:Rule(order = 1) val rule = createComposeRule()

    private val now = OffsetDateTime.parse("2026-11-27T18:48:00-08:00")

    private fun AppState.then(build: (AppState) -> List<Event>): AppState = build(this).fold(this) { s, e -> s.apply(Entry(s.lastSeq + 1, now, e)) }

    @After fun reset() {
        OpenSessionManager.exercises.clear()
        OpenSessionManager.isActive = false
    }

    @Test fun addExerciseLeavesOutTheExercisesAlreadyInTheSession() {
        val state = AppState().then { Commands.setup(mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105), Programs.GZCLP) }
            .then { Commands.startOpen(it, now) }
        OpenSessionManager.start(fromToday = true, state) {}
        rule.setContent { GzTheme(Palettes.tokyo) { OpenSessionScreen(state, now, { _, _, _, _, _ -> }, { _, _ -> }, {}) } }
        rule.onNodeWithText("Add exercise").performScrollTo().performClick()
        rule.onNodeWithText("All exercises").assertExists()
        // Squat and lat pulldown are already cards; deadlift is not, so the sheet lists it.
        rule.onAllNodesWithText("Squat").assertCountEquals(1)
        rule.onAllNodesWithText("Lat pulldown").assertCountEquals(1)
        rule.onAllNodesWithText("Deadlift").assertCountEquals(1)
    }
}
