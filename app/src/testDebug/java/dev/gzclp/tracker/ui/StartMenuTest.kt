package dev.gzclp.tracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
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

    /** The Workout screen as a finger finds it, or with a keyboard in use: Robolectric's own window is in neither mode for certain. */
    private fun home(keyboard: Boolean = false) = rule.setContent {
        val input = object : InputModeManager {
            override val inputMode = if (keyboard) InputMode.Keyboard else InputMode.Touch
            override fun requestInputMode(inputMode: InputMode) = false
        }
        CompositionLocalProvider(LocalInputModeManager provides input) {
            GzTheme(Palettes.tokyo) { HomeScreen(Demo.state, { picked += "start" }, {}, onStartTop = { picked += "top" }) }
        }
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

    /** The Workout tab's Lifting page under its fixed header (the gear and the Lifting, Cardio, Stretching switch), which dims with the page. */
    private fun underTheHeader(p: Palette, mode: () -> Int, onMode: (Int) -> Unit, onSettings: () -> Unit) = rule.setContent {
        GzTheme(p) { WorkoutPages(mode(), onMode, onSettings) { if (it == 0) HomeScreen(Demo.state, { picked += "start" }, {}) } }
    }

    /** The dimmed header is outside the menu as the dimmed page is: a tap on it closes the menu, and reaches nothing in the header. */
    private fun aTapOnTheDimmedHeaderClosesIt(p: Palette) {
        var mode by mutableStateOf(0)
        var settings = 0
        underTheHeader(p, { mode }, { mode = it }, { settings++ })
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Start workout").assertExists()
        rule.onNodeWithText("Cardio").performClick()
        assertClosed()
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        assertClosed()
        assertEquals(0, mode)
        assertEquals(0, settings)
        assertEquals(emptyList<String>(), picked)
        // Lit again, the header is live.
        rule.onNodeWithContentDescription("Settings").performClick()
        assertEquals(1, settings)
    }

    @Test fun aTapOnTheDimmedHeaderClosesIt() = aTapOnTheDimmedHeaderClosesIt(Palettes.tokyo)
    @Test fun aTapOnTheDimmedHeaderClosesItInLiquidGlass() = aTapOnTheDimmedHeaderClosesIt(Palettes.glass)

    /** Under a sheet the header still swallows taps: the menu's way of closing does not outlive the menu, even into the sheet it opened. */
    @Test fun theHeaderStillSwallowsTapsUnderASheet() {
        var mode by mutableStateOf(0)
        var settings = 0
        underTheHeader(Palettes.tokyo, { mode }, { mode = it }, { settings++ })
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Quick log").performClick()
        rule.onNodeWithText("Choose").assertExists()
        rule.onNodeWithText("Cardio").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Choose").assertExists()
        assertEquals(0, mode)
        assertEquals(0, settings)
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

    /**
     * Under a finger no focus is left on the button as the menu closes: handed back as to a keyboard, it left the button wearing a
     * focus ring. (Whether a touch may focus the open menu's first option is the system's call: a phone lets it, this test window does not.)
     */
    @Test fun aFingerLeavesNoFocusOnTheButton() {
        home()
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Start workout").assertExists()
        rule.onRoot().performTouchInput { click(Offset(width / 2f, height * 0.25f)) }
        rule.onNodeWithText(START_OR_LOG).assertIsNotFocused()
    }

    /** The tab bar stays live under the open menu: leaving the tab closes it, so it is not found open on the way back. */
    @Test fun leavingTheTabClosesIt() {
        var tab by mutableStateOf(Tab.Workout)
        rule.setContent { GzTheme(Palettes.tokyo) { TabHost({ tab }, { tab = it }) { if (it == Tab.Workout) HomeScreen(Demo.state, { picked += "start" }, {}) } } }
        rule.onNodeWithText(START_OR_LOG).performClick()
        rule.onNodeWithText("Start workout").assertExists()
        rule.onNodeWithText("History").performClick()
        rule.runOnIdle { assertEquals(Tab.History, tab) }
        // Back is the other tab's now, not a menu's left open out of sight.
        rule.runOnIdle { assertTrue(!rule.activity.onBackPressedDispatcher.hasEnabledCallbacks()) }
        rule.onNodeWithText("Workout").performClick()
        assertClosed()
        assertEquals(emptyList<String>(), picked)
    }

    /** With a keyboard, focus goes to the option under the button, and back to the button when the menu closes. */
    @Test fun focusMovesIntoTheMenuAndBackToTheButton() {
        home(keyboard = true)
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
        val opening = frames()
        rule.mainClock.advanceTimeBy(1000)
        val open = rule.onNode(pane).fetchSemanticsNode().boundsInRoot
        val part = opening.filter { it.height > closed.height && it.height < open.height }
        assertTrue("part grown on the way: ${opening.map { it.height }}", part.size >= 3)
        // Its bottom edge is the button's, still easing back from the press the button was let go at.
        for (b in part) assertEquals(closed.bottom, b.bottom, 0.03f * closed.height)
        rule.onRoot().performTouchInput { click(Offset(width / 2f, height * 0.25f)) }
        val closing = frames()
        assertTrue("part shrunk on the way: ${closing.map { it.height }}", closing.count { it.height > closed.height && it.height < open.height } >= 3)
        rule.mainClock.advanceTimeBy(1000)
        assertClosed()
    }

    /** The pane's bounds over the next ten frames, while there is one. */
    private fun frames() = (1..10).mapNotNull {
        rule.mainClock.advanceTimeByFrame()
        rule.onAllNodes(pane).fetchSemanticsNodes().singleOrNull()?.boundsInRoot
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
        assertTrue("closed within three frames: $after", after[2] == closed)
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Start workout").assertDoesNotExist()
        rule.onNodeWithText(START_OR_LOG).assertExists()
    }
}
