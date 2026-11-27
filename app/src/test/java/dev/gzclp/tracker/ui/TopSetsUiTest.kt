package dev.gzclp.tracker.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.Event
import dev.gzclp.tracker.log.Exercise
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime

/** A top-sets session on screen: one row per exercise, prefilled from the plan, and what History shows of it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class TopSetsUiTest {
    @get:Rule(order = 0) val activity = ComposeActivity()
    @get:Rule(order = 1) val rule = createComposeRule()

    private val now = OffsetDateTime.parse("2026-11-27T18:48:00-08:00")

    private fun AppState.then(build: (AppState) -> List<Event>): AppState = build(this).fold(this) { s, e -> s.apply(Entry(s.lastSeq + 1, now, e)) }

    /** GZCLP with a top-sets session of its first day open. */
    private fun open(): AppState =
        AppState().then { Commands.setup(mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105), Programs.GZCLP) }
            .then { Commands.startTop(it, now) }

    @Test fun theCheckLogsTheRowAsPrefilledFromThePlan() {
        val state = open()
        val squat = state.sessionExercises().first()
        val logged = mutableListOf<List<Any>>()
        rule.setContent {
            GzTheme(Palettes.tokyo) { WorkoutScreen(state, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, onLogTop = { ex: Exercise, w, r, n -> logged += listOf(ex.id, w, r, n) }) }
        }
        rule.onNodeWithText("Top sets").assertExists()
        rule.onNodeWithText("Cancel").assertExists()
        rule.onNodeWithContentDescription("Log Squat top set").performClick()
        assertEquals(listOf(listOf(squat.id, squat.weight!!, squat.scheme.reps, squat.scheme.sets)), logged)
    }

    @Test fun historyShowsOneRowPerExerciseThatOpensIt() {
        var state = open()
        val squat = state.sessionExercises().first()
        state = state.then { Commands.logSet(it, squat, 1, 3, squat.weight!!, now, emptyMap(), sets = 5) }.then { Commands.endTop(it, now, 2400) }
        val opened = mutableListOf<String>()
        rule.setContent { GzTheme(Palettes.tokyo) { SessionScreen(state, state.sessions.single(), {}, {}, onExercise = { opened += it }) } }
        rule.onNodeWithText("40 min").assertExists()
        rule.onNodeWithText("Set 1").assertDoesNotExist()
        rule.onNodeWithText("Left running").assertDoesNotExist()
        rule.onNodeWithText("Squat").performClick()
        assertEquals(listOf(squat.id), opened)
    }

    @Test fun theFinishSheetStartsAtTheEstimate() {
        rule.setContent { GzTheme(Palettes.tokyo) { Sheet(true, {}) { SheetContent(SheetState.TopFinish(40 * 60L), AppState(), {}, {}, {}) } } }
        rule.onNodeWithText("How long?").assertExists()
        rule.onNodeWithText("40 min").assertExists()
        rule.onNodeWithText("1 hr 20 min").assertExists()
    }

    @Test fun theFinishSheetNamesTheRowsLeftUnchecked() {
        var state = open()
        val (squat, bench, row) = state.sessionExercises()
        state = state.then { Commands.logSet(it, squat, 1, 3, squat.weight!!, now, emptyMap(), sets = 5) }
            .then { Commands.logSet(it, bench, 1, 10, bench.weight!!, now, emptyMap(), sets = 3) }
        rule.setContent { GzTheme(Palettes.tokyo) { Sheet(true, {}) { SheetContent(SheetState.TopFinish(40 * 60L, unlogged = state.topUnlogged()), state, {}, {}, {}) } } }
        rule.onNodeWithText("${row.name} is not logged").assertExists()
    }
}
