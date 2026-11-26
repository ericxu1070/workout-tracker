package dev.gzclp.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
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
class WorkoutLayoutTest {
    @get:Rule val rule = createComposeRule()

    private val t0 = OffsetDateTime.parse("2026-11-25T18:00:00-08:00")

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
    private fun check(label: String) = rule.onNode(clickLabel(label))

    /** Logging a set starts the rest timer; the set rows must stay where the thumb is. */
    @Test fun loggingASetMovesNoSetRow() {
        val store = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() })
        store.dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115), Programs.GZCLP) }
        while (true) {
            val missing = Commands.missingWeight(store.state.value) ?: break
            store.dispatch(t0) { Commands.startingWeight(it, missing, 50) }
        }
        store.dispatch(t0) { Commands.start(it, t0) }
        var state by mutableStateOf(store.state.value)
        var rest by mutableStateOf<RestUi?>(null)
        val ex = state.sessionExercises().first()
        rule.setContent {
            WorkoutScreen(
                state, t0.plusMinutes(5), rest,
                onLog = { e, i, w, r, rpe ->
                    store.dispatch(t0) { Commands.logSet(it, e, i, r, w, t0, Tier.entries.associateWith { t -> t.defaultRestSec }, rpe) }
                    state = store.state.value
                    rest = RestUi(90, 90, restLabel(e, i))
                },
                onClear = { _, _ -> }, onEditWeight = {}, onSwap = {}, onSkipRest = { rest = null }, onEnd = {}, warmups = false,
            )
        }
        rule.onNodeWithText("End workout").assertExists()
        // The step pill sits on set 1 and, once it is logged, on set 2: one current row at a time.
        val current = rule.onAllNodes(isSelected())
        current.assertCountEquals(1)
        rule.onNode(isSelected() and hasAnyDescendant(clickLabel("Log ${ex.name} set 1"))).assertExists()
        val before = check("Log ${ex.name} set 2").fetchSemanticsNode().boundsInRoot
        check("Log ${ex.name} set 1").performClick()
        rule.waitForIdle()
        assertEquals(before, check("Log ${ex.name} set 2").fetchSemanticsNode().boundsInRoot)
        rule.onAllNodes(isSelected()).assertCountEquals(1)
        rule.onNode(isSelected() and hasAnyDescendant(clickLabel("Log ${ex.name} set 2"))).assertExists()
        rule.onNodeWithText(restLabel(ex, 1)).assertExists()
        rule.onAllNodesWithText("End workout").assertCountEquals(0)
        check("Clear ${ex.name} set 1").assertExists()

        rule.onNodeWithText("Skip rest").performClick()
        rule.waitForIdle()
        assertEquals(before, check("Log ${ex.name} set 2").fetchSemanticsNode().boundsInRoot)
    }
}
