package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.WaterLogged
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Debug-only: the Compose test activity comes from ui-test-manifest, a debug dependency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SheetAndWaterUiTest {
    @get:Rule val rule = createComposeRule()

    private fun sheet(onDismiss: () -> Unit) = rule.setContent {
        Box(Modifier.fillMaxSize()) { Sheet(true, onDismiss) { Txt("Log weight", Type.title3); Txt("Body", Type.body) } }
    }

    @Test fun swipingASheetDownClosesIt() {
        var closed = 0
        sheet { closed++ }
        rule.onNodeWithText("Log weight").performTouchInput { swipeDown(startY = top, endY = top + 600f) }
        assertEquals(1, closed)
    }

    @Test fun aShortSlowDragLeavesTheSheetOpen() {
        var closed = 0
        sheet { closed++ }
        rule.onNodeWithText("Log weight").performTouchInput { swipeDown(startY = top, endY = top + 20f, durationMillis = 800) }
        assertEquals(0, closed)
    }

    @Test fun theNewestDrinkHasAVisibleUndo() {
        val state = AppState(
            water = mapOf(
                "wt1" to WaterLogged("wt1", "2026-11-23T08:30:00-08:00", 250),
                "wt2" to WaterLogged("wt2", "2026-11-23T11:15:00-08:00", 500),
            ),
        )
        val cleared = mutableListOf<String>()
        rule.setContent { WaterScreen(state, LocalDate.parse("2026-11-23"), onClearWater = { cleared += it }) }
        rule.onAllNodesWithText("Undo", substring = true).assertCountEquals(1)
        rule.onNodeWithText("Undo 500 ml").performClick()
        assertEquals(listOf("wt2"), cleared)
    }

    @Test fun paceShowsOnlyWhenThereIsOne() {
        rule.setContent { WaterDailyRing(500, 2500, deficitMl = null) }
        rule.onAllNodesWithText("On pace").assertCountEquals(0)
        rule.onAllNodesWithText("behind pace", substring = true).assertCountEquals(0)
    }
}
