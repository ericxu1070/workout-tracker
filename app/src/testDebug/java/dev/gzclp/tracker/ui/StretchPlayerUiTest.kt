package dev.gzclp.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchPlayerState
import dev.gzclp.tracker.stretch.StretchRoutine
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

    private val first = StretchMoves.PIGEON_POSE.copy(holdSec = 2)
    private val second = StretchMoves.STANDING_QUAD_STRETCH.copy(holdSec = 3)
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
}
