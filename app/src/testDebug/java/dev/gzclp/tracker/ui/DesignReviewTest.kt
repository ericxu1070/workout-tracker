package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.weighInScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime

/** The design review's quick fixes that a tap or drag can prove. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class DesignReviewTest {
    @get:Rule val rule = createComposeRule()

    private val now = OffsetDateTime.parse("2026-11-25T08:00:00-08:00")

    @Test fun lettingGoOfTheWeighInSliderSavesNothingUntilSave() {
        val today = now.toLocalDate()
        val start = SheetState.BodyWeight(null, weighInScale(null, today), now, today)
        val saved = mutableListOf<SheetState>()
        rule.setContent {
            var s by remember { mutableStateOf<SheetState>(start) }
            Column { SheetContent(s, AppState(), { s = it }, {}, { saved += it }) }
        }
        rule.onNodeWithContentDescription("Weight").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(emptyList<SheetState>(), saved)
        rule.onNodeWithText("Save", substring = true).performClick()
        val weighIn = saved.single() as SheetState.BodyWeight
        assertNotEquals(start.lb, weighIn.lb, 0.0)
    }

    @Test fun aTapOnTheCupLogsTheWholeCup() {
        val logged = mutableListOf<Int>()
        rule.setContent { InteractiveCup(350, { logged += it }) }
        rule.onNodeWithContentDescription("Water cup").performClick()
        rule.waitForIdle()
        assertEquals(listOf(350), logged)
    }

    @Test fun anEmptyOpenSessionFinishesWithoutAskingAndHasOneAddExercise() {
        val setup = Commands.setup(Lift.entries.associateWith { 100 }, Programs.GZCLP).fold(AppState()) { s, e -> s.apply(Entry(0, now, e)) }
        val state = Commands.startOpen(setup, now).fold(setup) { s, e -> s.apply(Entry(1, now, e)) }
        val finished = mutableListOf<Boolean>()
        rule.setContent { OpenSessionScreen(state, now, { _, _, _, _, _ -> }, { _, _ -> }, { finished += it }) }
        rule.onAllNodesWithText("Add exercise").assertCountEquals(1)
        rule.onNodeWithText("Finish session").performClick()
        assertEquals(listOf(false), finished)
        rule.onAllNodesWithText("Count it").assertCountEquals(0)
    }

    /** Drawn at 44 dp, a small button still takes a touch 2 dp outside it: Compose widens touch bounds to 48 dp. */
    @Test fun aSmallButtonTakesTouchesOutToFortyEightDp() {
        var taps = 0
        rule.setContent { Column(Modifier.padding(24.dp)) { IconButton(Icons.plus, "Add", { taps++ }) } }
        rule.onNodeWithContentDescription("Add").performTouchInput { click(Offset(-1.5.dp.toPx(), centerY)) }
        assertEquals(1, taps)
    }
}
