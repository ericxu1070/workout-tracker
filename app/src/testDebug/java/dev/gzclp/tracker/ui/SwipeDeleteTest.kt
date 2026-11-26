package dev.gzclp.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import dev.gzclp.tracker.log.Deletion
import dev.gzclp.tracker.log.weighIns
import androidx.compose.ui.test.click
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Swipe to delete on the demo log's weigh-ins and History rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SwipeDeleteTest {
    @get:Rule val rule = createComposeRule()

    private val state = Demo.state
    private val today = Demo.now.toLocalDate()
    private var deleting by mutableStateOf<Deletion?>(null)
    private val asked = mutableListOf<Deletion>()

    private fun bodyWeight() = rule.setContent {
        GzTheme(Palettes.light) {
            BodyWeightScreen(state, today, {}, {}, {}, deleting) { asked += it; deleting = it }
        }
    }

    private val newest get() = state.weighIns().last()
    private val row get() = rule.onNodeWithText(Fmt.weighInTime(newest.at, today), useUnmergedTree = true)
    private fun left() = row.fetchSemanticsNode().positionInRoot.x

    private val rowY get() = row.fetchSemanticsNode().boundsInRoot.center.y

    private fun drag(fraction: Float) {
        val y = rowY
        // From the row's right side, so the whole drag stays on it.
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.9f, y), Offset(width * (0.9f - fraction), y), 300) }
    }

    @Test fun aShortSwipeRestsOpenOnTheTrashAndATapAsks() {
        bodyWeight()
        val x0 = left()
        // Measured closed: once the row slides out of its clip its text has no bounds.
        val y = rowY
        drag(0.15f)
        rule.waitForIdle()
        assertTrue("rests open by the action's width", x0 - left() > 100f)
        assertTrue(asked.isEmpty())
        rule.onNodeWithText("Delete this weigh-in?").assertDoesNotExist()
        // The red action sits where the row was.
        rule.onRoot().performTouchInput { click(Offset(width - 80f, y)) }
        rule.waitForIdle()
        assertEquals(listOf<Deletion>(Deletion.WeighIn(newest.id)), asked)
    }

    @Test fun aLongSwipeAsksOnReleaseAndCancelSpringsBack() {
        bodyWeight()
        val x0 = left()
        drag(0.7f)
        rule.waitForIdle()
        assertEquals(listOf<Deletion>(Deletion.WeighIn(newest.id)), asked)
        assertTrue("held off screen while asking", x0 - left() > 500f)
        deleting = null
        rule.waitForIdle()
        assertEquals(x0, left(), 1f)
    }

    @Test fun aTapOnAnOpenRowClosesItInsteadOfOpeningIt() {
        var edited = 0
        rule.setContent { GzTheme(Palettes.light) { BodyWeightScreen(state, today, {}, {}, { edited++ }, null) { asked += it } } }
        val x0 = left()
        val y = rowY
        drag(0.15f)
        rule.waitForIdle()
        rule.onRoot().performTouchInput { click(Offset(width * 0.5f, y)) }
        rule.waitForIdle()
        assertEquals(0, edited)
        assertEquals(x0, left(), 1f)
        assertTrue(asked.isEmpty())
    }

    @Test fun talkBackDeletesThroughACustomAction() {
        bodyWeight()
        val node = rule.onNodeWithText(Fmt.weighInTime(newest.at, today), useUnmergedTree = true)
        val holder = generateSequence(node.fetchSemanticsNode()) { it.parent }.first { SemanticsActions.CustomActions in it.config }
        rule.runOnIdle { holder.config[SemanticsActions.CustomActions].single().action() }
        assertEquals(listOf<Deletion>(Deletion.WeighIn(newest.id)), asked)
    }

    @Test fun aRightSwipeOnAClosedHistoryRowStillTurnsThePage() {
        var view by mutableStateOf(HistoryView.All)
        rule.setContent {
            GzTheme(Palettes.light) { TabFrame(Tab.History, {}) { HistoryScreen(state, view, { view = it }, {}, {}, today, deleting, { asked += it; deleting = it }) {} } }
        }
        val workout = state.sessions.last { it.sets.isNotEmpty() }
        val y = rule.onNodeWithText("${workout.day.title} · ${Fmt.date(workout.startedAt)}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.y
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.2f, y), Offset(width * 0.9f, y), 300) }
        rule.waitForIdle()
        assertEquals(HistoryView.Month, view)
        assertTrue(asked.isEmpty())
        assertNull(deleting)
    }
}
