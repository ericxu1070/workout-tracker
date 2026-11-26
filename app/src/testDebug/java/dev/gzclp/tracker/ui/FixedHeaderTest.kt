package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * The switch (and the Workout gear) crept up as you swiped Lifting, Cardio, Stretching: each page carried its own copy inside its
 * scroll, so a swipe that drifted upward scrolled it away. Now one header sits above the pages and never moves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FixedHeaderTest {
    @get:Rule val rule = createComposeRule()

    private val now = OffsetDateTime.parse("2026-11-25T18:00:00-08:00")
    private val state = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() }).run {
        dispatch(now) { Commands.setup(Lift.entries.associateWith { 135 }, Programs.GZCLP) }
        while (true) {
            val missing = Commands.missingWeight(state.value) ?: break
            dispatch(now) { Commands.startingWeight(it, missing, 50) }
        }
        for (d in 1..3L) dispatch(now) { Commands.logCardio(it, null, Activities.all[d.toInt() % 2], now.minusDays(d), 1800 + d * 300, null, null, null, now) }
        state.value
    }

    private fun bounds(text: String): Rect = rule.onNodeWithText(text).getBoundsInRoot().let { Rect(it.left.value, it.top.value, it.right.value, it.bottom.value) }
    private fun DpRect.rect() = Rect(left.value, top.value, right.value, bottom.value)

    /** Scrolls the page shown, then checks [header] stayed exactly where it was. */
    private fun scrollAndCheck(header: () -> List<Rect>, start: List<Rect>, title: String) {
        val before = bounds(title)
        rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.3f) }
        rule.waitForIdle()
        assertNotEquals("the page under the header scrolled", before, bounds(title))
        assertEquals(start, header())
    }

    @Test fun theWorkoutHeaderStaysPutAcrossSwipesAndScrolls() {
        var mode by mutableIntStateOf(0)
        val titles = listOf("Lifting page", "Cardio page", "Stretching page")
        rule.setContent {
            GzTheme(Palettes.dark) {
                TabFrame(Tab.Workout, {}) {
                    WorkoutPages(mode, { mode = it }, {}) { page -> ScreenFrame { Txt(titles[page], Type.body); Spacer(Modifier.height(3000.dp)) } }
                }
            }
        }
        val header = { listOf(rule.onNodeWithContentDescription("Settings").getBoundsInRoot().rect(), bounds("Lifting"), bounds("Stretching")) }
        val start = header()
        for (page in 0..2) {
            if (page > 0) {
                rule.onRoot().performTouchInput { swipeLeft() }
                rule.waitForIdle()
                assertEquals(page, mode)
            }
            scrollAndCheck(header, start, titles[page])
        }
    }

    @Test fun theHistorySwitchStaysPutAcrossSwipesAndScrolls() {
        var view by mutableStateOf(HistoryView.Week)
        rule.setContent {
            GzTheme(Palettes.light) {
                TabFrame(Tab.History, {}) {
                    HistoryScreen(state, view, { view = it }, {}, {}, LocalDate.of(2026, 11, 25)) { Txt("Week page", Type.body); Spacer(Modifier.height(3000.dp)) }
                }
            }
        }
        // The title, not the tab bar's History button.
        val title = { rule.onNode(hasText("History") and !hasClickAction()).getBoundsInRoot().rect() }
        val header = { listOf(title(), bounds("Week"), bounds("All")) }
        val start = header()
        scrollAndCheck(header, start, "Week page")
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(HistoryView.Month, view)
        assertEquals(start, header())
    }
}
