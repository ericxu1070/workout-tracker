package dev.gzclp.tracker.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.Event
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime

/** The Workout tab's Quick log: pick an exercise (or name a new one), sets × reps, and what History shows of it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class QuickLogUiTest {
    @get:Rule(order = 0) val activity = ComposeActivity()
    @get:Rule(order = 1) val rule = createComposeRule()

    private val now = OffsetDateTime.parse("2026-11-27T18:48:00-08:00")

    private fun AppState.then(build: (AppState) -> List<Event>): AppState = build(this).fold(this) { s, e -> s.apply(Entry(s.lastSeq + 1, now, e)) }

    private fun setUp(): AppState =
        AppState().then { Commands.setup(mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105), Programs.GZCLP) }
            .then { Commands.logBodyWeight(it, null, now, 180.0) }

    private fun home(state: AppState, logged: MutableList<List<Any?>>) = rule.setContent {
        GzTheme(Palettes.tokyo) { HomeScreen(state, {}, {}, onQuickLog = { n, s, r, w -> logged += listOf(n, s, r, w) }, now = { now }) }
    }

    @Test fun aCatalogMoveLogsAsSetsTimesRepsWithBodyWeightAsItsLoad() {
        val logged = mutableListOf<List<Any?>>()
        home(setUp(), logged)
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Quick log").performClick()
        rule.onNodeWithText("Choose").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("handstand")
        rule.onNodeWithText("Handstand press").performClick()
        rule.onNodeWithText("Added weight, lb").assertExists()
        rule.onNodeWithText("Your body weight, 180.0 lb, counts as the load, plus anything added.").assertExists()
        rule.onNodeWithText("Log 3 × 10").performClick()
        assertEquals(listOf(listOf<Any?>("Handstand press", 3, 10, null)), logged)
    }

    @Test fun aNameNoExerciseHasIsAddedAsANewOne() {
        val logged = mutableListOf<List<Any?>>()
        home(setUp(), logged)
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Quick log").performClick()
        rule.onNodeWithText("Choose").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Wrist roller")
        rule.onNodeWithText("Add “Wrist roller”").performClick()
        rule.onNodeWithText("Weight, lb").assertExists()
        rule.onNodeWithText("Log 3 × 10").performClick()
        assertEquals(listOf(listOf<Any?>("Wrist roller", 3, 10, null)), logged)
    }

    @Test fun historyShowsItAsAQuickLogWithNoClock() {
        val state = setUp().then { Commands.quickLog(it, "Push-up", 3, 15, null, now) }
        val s = state.sessions.single()
        rule.setContent { GzTheme(Palettes.tokyo) { SessionScreen(state, s, {}, {}) } }
        rule.onNodeWithText("Quick log").assertExists()
        rule.onNodeWithText("Push-up").assertExists()
        rule.onNodeWithText("3 × 15 at 0 lb").assertExists()
        rule.onNodeWithText("Left running").assertDoesNotExist()
    }
}
