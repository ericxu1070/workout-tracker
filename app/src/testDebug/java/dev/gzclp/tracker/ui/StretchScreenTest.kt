package dev.gzclp.tracker.ui

import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import dev.gzclp.tracker.stretch.StretchMoves
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Debug-only: the Compose test activity comes from ui-test-manifest, a debug dependency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class StretchScreenTest {
    @get:Rule val rule = createComposeRule()

    /** Switching to Stretching built a 3D figure for every move in the library below the fold, which made the switch lag. */
    @Test fun theMoveLibraryBuildsOnlyTheFiguresOnScreen() {
        rule.setContent { GzTheme(Palettes.tokyo) { TabFrame(Tab.Workout, {}) { StretchScreen(null, "after_workout", {}, {}, {}, {}, {}) } } }
        val figures = rule.onAllNodesWithContentDescription("3D exercise form", substring = true).fetchSemanticsNodes().size
        assertTrue("$figures of ${StretchMoves.ALL.size} figures built", figures <= 3)
        val last = StretchMoves.ALL.last()
        rule.onNode(hasScrollToKeyAction()).performScrollToKey(last.id)
        rule.onNodeWithText(last.name).assertExists()
    }
}
