package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.gzclp.tracker.log.bouts
import dev.gzclp.tracker.log.dayMix
import dev.gzclp.tracker.log.mondayOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** History's calendars: one week start, and today marked whatever it held. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class CalendarTest {
    @get:Rule val rule = createComposeRule()

    private fun mondayComesFirst() {
        val mon = rule.onNodeWithText("Mon").getBoundsInRoot().left
        val sun = rule.onNodeWithText("Sun").getBoundsInRoot().left
        assertTrue("Mon at $mon, Sun at $sun", mon < sun)
    }

    @Test fun monthStartsOnMonday() {
        rule.setContent { Column { MonthGrid(Demo.state, Demo.today, YearMonth.from(Demo.today), {}) } }
        mondayComesFirst()
    }

    @Test fun weekStartsOnMonday() {
        rule.setContent { Column { WeekView(Demo.state, emptyList(), mondayOf(Demo.today), Demo.today, {}, {}) } }
        mondayComesFirst()
    }

    @Test fun todayIsMarkedEvenAfterAWorkout() {
        val day = dayMix(Demo.state.bouts()).keys.max()
        rule.setContent { Column { MonthGrid(Demo.state, day, YearMonth.from(day), {}) } }
        rule.onNodeWithContentDescription("Today, ", substring = true).assertExists()
    }
}
