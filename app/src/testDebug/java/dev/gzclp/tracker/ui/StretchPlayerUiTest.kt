package dev.gzclp.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.gzclp.tracker.log.StretchLogged
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchPlayerState
import dev.gzclp.tracker.stretch.StretchRoutine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Debug-only: the Compose test activity comes from ui-test-manifest, a debug dependency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StretchPlayerUiTest {
    @get:Rule val rule = createComposeRule()

    private val first = StretchMoves.BUTTERFLY_STRETCH.copy(holdSec = 2)
    private val second = StretchMoves.STANDING_FORWARD_FOLD.copy(holdSec = 3)
    private val routine = StretchRoutine("t", "Test", "", "", listOf(first, second))

    private fun play() {
        // The figure draws every frame, so the clock only moves when the test moves it.
        rule.mainClock.autoAdvance = false
        rule.setContent { GuidedStretchPlayerScreen(routine, {}, {}) }
        rule.onNodeWithText(first.name).assertExists()
        rule.onNodeWithText("Get ready").assertExists()
    }

    @Test fun theTimerAdvancesToTheNextMoveTitleAtOnce() {
        play()
        rule.mainClock.advanceTimeBy((StretchPlayerState.READY_SEC + first.holdSec) * 1000L + 500)
        rule.onNodeWithText(second.name).assertExists()
        rule.onAllNodesWithText(first.name).assertCountEquals(0)
        rule.onNodeWithText("Get ready").assertExists()
    }

    @Test fun leavingMidRoutineLogsTheMovesHeld() {
        var shown by mutableStateOf(true)
        val left = mutableListOf<StretchLogged>()
        rule.mainClock.autoAdvance = false
        rule.setContent { if (shown) GuidedStretchPlayerScreen(routine, {}, {}, onLeave = { left += it }) }
        // Into the second move's hold: the first is done and the second has begun.
        rule.mainClock.advanceTimeBy((2 * StretchPlayerState.READY_SEC + first.holdSec + 1) * 1000L + 500)
        shown = false
        rule.mainClock.advanceTimeByFrame()
        assertEquals(listOf(first.id, second.id), left.single().moves)
    }

    @Test fun leavingBeforeAnyHoldLogsNothing() {
        var shown by mutableStateOf(true)
        val left = mutableListOf<StretchLogged>()
        rule.mainClock.autoAdvance = false
        rule.setContent { if (shown) GuidedStretchPlayerScreen(routine, {}, {}, onLeave = { left += it }) }
        rule.mainClock.advanceTimeBy(1000L)
        shown = false
        rule.mainClock.advanceTimeByFrame()
        assertEquals(emptyList<StretchLogged>(), left)
    }

    @Test fun pauseStopsTheTimer() {
        play()
        rule.onNodeWithContentDescription("Pause").performClick()
        rule.mainClock.advanceTimeBy(30_000L)
        rule.onNodeWithText(first.name).assertExists()
        rule.onNodeWithText("${StretchPlayerState.READY_SEC}").assertExists()
        rule.onNodeWithContentDescription("Resume").assertExists()
    }

    @Test fun skippingShowsTheNextMoveTitleAtOnce() {
        play()
        rule.onNodeWithContentDescription("Next move").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText(second.name).assertExists()
        rule.onAllNodesWithText(first.name).assertCountEquals(0)
    }

    @Test fun aOneSidedMoveSwitchesSidesHalfwayThroughItsHold() {
        val calf = StretchMoves.WALL_CALF_STRETCH.copy(holdSec = 4)
        rule.mainClock.autoAdvance = false
        rule.setContent { GuidedStretchPlayerScreen(StretchRoutine("t", "Test", "", "", listOf(calf, first)), {}, {}) }
        rule.onNodeWithText("Side 1").assertExists()
        rule.mainClock.advanceTimeBy((StretchPlayerState.READY_SEC + calf.firstSideSec) * 1000L + 500)
        rule.onNodeWithText("Switch").assertExists()
        rule.onNodeWithText("Side 2").assertExists()
        rule.onNodeWithText(calf.name).assertExists()
        rule.mainClock.advanceTimeBy((StretchPlayerState.SWITCH_SEC + calf.secondSideSec) * 1000L)
        rule.onNodeWithText(first.name).assertExists()
        rule.onNodeWithText("Get ready").assertExists()
    }
}
