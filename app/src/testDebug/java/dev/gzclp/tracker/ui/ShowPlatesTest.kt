package dev.gzclp.tracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.OffsetDateTime

/** Settings › Show plates off hides every "… a side" line on barbell sets and warm-ups; weights stay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ShowPlatesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun switchHidesPlateLines() {
        val t0 = OffsetDateTime.parse("2026-11-25T18:00:00-08:00")
        val store = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() })
        store.dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115), Programs.GZCLP) }
        while (true) {
            val missing = Commands.missingWeight(store.state.value) ?: break
            store.dispatch(t0) { Commands.startingWeight(it, missing, 50) }
        }
        store.dispatch(t0) { Commands.start(it, t0) }
        val state = store.state.value
        val ex = state.sessionExercises().first()
        var show by mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalShowPlates provides show) {
                WorkoutScreen(state, t0.plusMinutes(5), null, onLog = { _, _, _, _, _ -> }, onClear = { _, _ -> }, onEditWeight = {}, onSwap = {},
                    onSkipRest = {}, onSettings = {}, onEnd = {}, warmups = true)
            }
        }
        val weight = "${Plates().round(ex.weight!!)}"
        assertTrue(rule.onAllNodesWithText(Plates().perSideLabel(Plates().round(ex.weight!!))!!).fetchSemanticsNodes().isNotEmpty())

        show = false
        rule.waitForIdle()
        rule.onAllNodesWithText("a side", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Empty bar").assertCountEquals(0)
        rule.onAllNodesWithText("Not loadable", substring = true).assertCountEquals(0)
        assertTrue(rule.onAllNodesWithText(weight).fetchSemanticsNodes().isNotEmpty())
    }
}
