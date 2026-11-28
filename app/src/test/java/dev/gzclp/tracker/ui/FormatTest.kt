package dev.gzclp.tracker.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A duration on screen is never negative: a clock set back mid-workout, or a log from a later date, reads as no time yet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FormatTest {
    @get:Rule(order = 0) val activity = ComposeActivity()
    @get:Rule(order = 1) val rule = createComposeRule()

    @Test fun minutesNeverGoNegative() {
        assertEquals("0 min", Fmt.minutes(-1))
        assertEquals("0 min", Fmt.minutes(-250_020))
    }

    /** The demo's last workout starts on Nov 30 at 6:42 PM. On a phone whose clock read Nov 27, its header showed "-4167 min". */
    @Test fun aWorkoutStartingLaterThanTheClockShowsNoNegativeTime() {
        val early = Demo.state.active!!.startedAt.minusMinutes(4167)
        rule.setContent { GzTheme(Palettes.tokyo) { WorkoutScreen(Demo.state, early, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) } }
        rule.onNodeWithText("0 min").assertExists()
        rule.onNodeWithText("-4167 min").assertDoesNotExist()
        System.getProperty("gzclp.shots")?.let { rule.onRoot().captureRoboImage(File(it, "tokyo-workout-before-its-start.png").path) }
    }
}
