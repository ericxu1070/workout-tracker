package dev.gzclp.tracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val OPTIONS = listOf("Start workout", "Open session", "Log top sets", "Quick log")
private val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, START_OR_LOG)

/** The Workout tab's one button: closed it is a single pill, and a tap opens it into the four ways to start or log. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class StartMenuTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val picked = mutableListOf<String>()

    private fun home() = rule.setContent {
        GzTheme(Palettes.tokyo) { HomeScreen(Demo.state, { picked += "start" }, {}, onStartTop = { picked += "top" }) }
    }

    private fun assertClosed() {
        rule.onNodeWithText(START_OR_LOG).assert(hasClickAction())
        for (o in OPTIONS) rule.onNodeWithText(o).assertDoesNotExist()
        rule.onNode(pane).assertDoesNotExist()
    }

    @Test fun closedItIsOneButtonThatSaysItOpens() {
        home()
        assertClosed()
        rule.onNodeWithText(START_OR_LOG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
    }

    @Test fun aTapOpensItIntoFourNamedButtons() {
        home()
        rule.onNodeWithText(START_OR_LOG).performClick()
        for (o in OPTIONS) rule.onNodeWithText(o).assert(hasClickAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        // The button has become the menu: TalkBack is told a pane opened, and can dismiss it.
        rule.onNodeWithText(START_OR_LOG).assertDoesNotExist()
        rule.onNode(pane).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
    }

    /** The menu grows out of the button: its sides and bottom edge stay the button's, and Start workout lands a pad's width inside them. */
    @Test fun itOpensOnTheButtonWithStartWorkoutUnderTheThumb() {
        home()
        val closed = rule.onNodeWithText(START_OR_LOG).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.waitForIdle()
        val open = rule.onNode(pane).fetchSemanticsNode().boundsInRoot
        assertEquals(closed.left, open.left, 1f)
        assertEquals(closed.right, open.right, 1f)
        assertEquals(closed.bottom, open.bottom, 1f)
        assertTrue(open.height > 3 * closed.height)
        val start = rule.onNodeWithText("Start workout").fetchSemanticsNode().boundsInRoot
        val pad = 8 * rule.density.density
        assertEquals(closed.left + pad, start.left, 1f)
        assertEquals(closed.right - pad, start.right, 1f)
        assertEquals(closed.bottom - pad, start.bottom, 1f)
        assertEquals(closed.height, start.height, 1f)
        assertTrue(closed.contains(start.center))
    }

    @Test fun eachOptionDoesWhatItsButtonDid() {
        home()
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Start workout").performClick()
        assertEquals(listOf("start"), picked)
        assertClosed()
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Log top sets").performClick()
        assertEquals(listOf("start", "top"), picked)
        assertClosed()
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Open session").performClick()
        // Its sheet carries its name, so the menu is known closed by the others.
        for (o in OPTIONS - "Open session") rule.onNodeWithText(o).assertDoesNotExist()
        rule.onNodeWithText("Start blank").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Start blank").assertDoesNotExist()
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Quick log").performClick()
        for (o in OPTIONS - "Quick log") rule.onNodeWithText(o).assertDoesNotExist()
        rule.onNodeWithText("Counts toward your stats, never your program.").assertExists()
        rule.onNodeWithText("Choose").assertExists()
        assertEquals(listOf("start", "top"), picked)
    }

    @Test fun backClosesItAndPicksNothing() {
        home()
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Start workout").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        assertClosed()
        assertEquals(emptyList<String>(), picked)
        // Closed, Back is no longer the menu's.
        rule.runOnIdle { assertTrue(!rule.activity.onBackPressedDispatcher.hasEnabledCallbacks()) }
    }

    @Test fun aTapOutsideClosesItAndPicksNothing() {
        home()
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Start workout").assertExists()
        // On the first exercise's row, which the tap must not reach.
        rule.onRoot().performTouchInput { click(Offset(width / 2f, height * 0.25f)) }
        assertClosed()
        assertEquals(emptyList<String>(), picked)
    }

    /** A tap in the pane's own padding, between its options, is not a tap outside. */
    @Test fun aTapBetweenItsOptionsLeavesItOpen() {
        home()
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.waitForIdle()
        val b = rule.onNode(pane).fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput { click(Offset(b.center.x, b.bottom - 4 * rule.density.density)) }
        rule.onNodeWithText("Start workout").assertExists()
        assertEquals(emptyList<String>(), picked)
    }

    /** A phone on its side has no room for the whole menu: it stops short of the screen's top, Start workout stays in place, and the rest scroll. */
    @Config(sdk = [35], qualifiers = "w891dp-h250dp-420dpi")
    @Test fun onAShortScreenItStaysOnScreenAndItsOptionsScroll() {
        home()
        val closed = rule.onNodeWithText(START_OR_LOG).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.waitForIdle()
        val open = rule.onNode(pane).fetchSemanticsNode().boundsInRoot
        assertEquals(16 * rule.density.density, open.top, 1f)
        assertEquals(closed.bottom, open.bottom, 1f)
        rule.onNodeWithText("Start workout").assertIsDisplayed()
        rule.onNodeWithText("Quick log").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("Choose").assertExists()
    }

    /** With a keyboard, focus goes to the option under the button, and back to the button when the menu closes. */
    @Test fun focusMovesIntoTheMenuAndBackToTheButton() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        home()
        rule.onNodeWithText(START_OR_LOG).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Start workout").assertIsFocused()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText(START_OR_LOG).assertIsFocused()
    }

    /** It takes frames to open and to close: the pane is part grown on the way, between the button's height and its own. */
    @Test fun itGrowsOutOfTheButtonAndBackIntoIt() {
        home()
        val closed = rule.onNodeWithText(START_OR_LOG).fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.mainClock.advanceTimeBy(64)
        val part = rule.onNode(pane).fetchSemanticsNode().boundsInRoot
        rule.mainClock.advanceTimeBy(1000)
        val open = rule.onNode(pane).fetchSemanticsNode().boundsInRoot
        assertTrue("${closed.height} < ${part.height} < ${open.height}", part.height > closed.height && part.height < open.height)
        // Its bottom edge is the button's, still easing back from the press the button was let go at.
        assertEquals(closed.bottom, part.bottom, 0.03f * closed.height)
        rule.onRoot().performTouchInput { click(Offset(width / 2f, height * 0.25f)) }
        rule.mainClock.advanceTimeBy(64)
        val closing = rule.onNode(pane).fetchSemanticsNode().boundsInRoot
        assertTrue(closing.height > closed.height && closing.height < open.height)
        rule.mainClock.advanceTimeBy(1000)
        assertClosed()
    }
}

/** With the system's Remove animations on, the menu is closed or open and never drawn in between, not even for a frame. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class StartMenuNoMotionTest {
    @get:Rule val rule = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    @Test fun itOpensAndClosesAtOnce() {
        rule.setContent { GzTheme(Palettes.tokyo) { HomeScreen(Demo.state, {}, {}) } }
        rule.waitForIdle()
        val closed = rule.onNodeWithText(START_OR_LOG).fetchSemanticsNode().boundsInRoot.height
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText(START_OR_LOG).performClick()
        val heights = (1..20).map {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodes(pane).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.height ?: closed
        }
        val open = heights.last()
        assertTrue(open > 3 * closed)
        assertEquals("never part grown: $heights", setOf(closed, open), heights.toSet())
        assertTrue("open within two frames: $heights", heights[1] == open)
        rule.onRoot().performTouchInput { click(Offset(width / 2f, height * 0.25f)) }
        val after = (1..20).map {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodes(pane).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.height ?: closed
        }
        assertEquals("never part shrunk: $after", setOf(closed), after.toSet() - open)
        assertTrue("closed within two frames: $after", after[1] == closed)
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Start workout").assertDoesNotExist()
        rule.onNodeWithText(START_OR_LOG).assertExists()
    }
}
