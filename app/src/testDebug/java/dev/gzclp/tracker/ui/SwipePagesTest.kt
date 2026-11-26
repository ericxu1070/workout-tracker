package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

/** Debug-only: the Compose test activity comes from ui-test-manifest, a debug dependency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SwipePagesTest {
    @get:Rule val rule = createComposeRule()

    private val modes = listOf("Lifting", "Cardio", "Stretching")
    private var mode by mutableIntStateOf(0)

    private fun workout(sheet: Boolean = false) = rule.setContent {
        WorkoutPages(mode, { mode = it }) { page ->
            ScreenFrame {
                WorkoutSwitch(page) { mode = it }
                Txt("${modes[page]} page", Type.body)
                Spacer(Modifier.height(400.dp))
            }
            if (page == 0) Sheet(sheet, {}) { Txt("Sheet body", Type.body) }
        }
    }

    private fun selected(label: String) = rule.onAllNodes(hasText(label) and isSelected()).onFirst().assertExists()

    @Test fun swipingTheWorkoutPagesMovesTheSwitch() {
        workout()
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(1, mode)
        rule.onNodeWithText("Cardio page").assertIsDisplayed()
        selected("Cardio")
    }

    @Test fun tappingAndDraggingTheSwitchTurnThePages() {
        workout()
        rule.onNodeWithText("Stretching").performClick()
        rule.waitForIdle()
        assertEquals(2, mode)
        rule.onNodeWithText("Stretching page").assertIsDisplayed()
        // Dragging the thumb one segment left, as a UISegmentedControl allows.
        rule.onNodeWithText("Stretching").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(1, mode)
        rule.onNodeWithText("Cardio page").assertIsDisplayed()
        selected("Cardio")
    }

    @Test fun anOpenSheetHoldsThePage() {
        workout(sheet = true)
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(0, mode)
    }

    @Test fun swipingHistoryChangesTheView() {
        val state = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() }).run {
            dispatch(OffsetDateTime.parse("2026-11-25T18:00:00-08:00")) { Commands.setup(Lift.entries.associateWith { 100 }, Programs.GZCLP) }
            state.value
        }
        var view by mutableStateOf(HistoryView.Week)
        rule.setContent { HistoryScreen(state, view, { view = it }, {}, {}, LocalDate.of(2026, 11, 25)) { Txt("Week page", Type.body); Spacer(Modifier.height(600.dp)) } }
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(HistoryView.Month, view)
        selected("Month")
    }
}
