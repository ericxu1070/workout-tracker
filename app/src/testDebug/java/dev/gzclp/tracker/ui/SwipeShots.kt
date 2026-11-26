package dev.gzclp.tracker.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.log.mondayOf
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders, from demo data, the Workout and History pages caught mid-swipe; runs only when -PgzclpShots is set. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SwipeShots {
    @get:Rule val rule = createComposeRule()

    private val now = Demo.now
    private val state = Demo.state

    /** Holds a finger 40% of the way across, so the thumb sits between two segments. */
    private fun midSwipe(p: Palette, name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val out = System.getProperty("gzclp.shots")
        assumeNotNull(out)
        rule.setContent { GzTheme(p) { content() } }
        rule.onRoot().performTouchInput {
            down(center)
            for (i in 1..10) moveBy(Offset(-width * 0.04f, 0f))
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(out, "${p.id}-$name.png").path)
        rule.onRoot().performTouchInput { up() }
    }

    @Test fun workoutMidSwipe() = midSwipe(Palettes.jade, "30-workout-mid-swipe") {
        TabFrame(Tab.Workout, {}) {
            WorkoutPages(0, {}) { page ->
                when (page) {
                    1 -> CardioScreen(state, now.toLocalDate(), 150, {}, {}, {}, {}, {}, {})
                    2 -> StretchScreen(state, "after_workout", {}, {}, {}, {}, {})
                    else -> HomeScreen(state, {}, {}, {}, onMode = {})
                }
            }
        }
    }

    @Test fun historyMidSwipe() = midSwipe(Palettes.light, "30-history-mid-swipe") {
        TabFrame(Tab.History, {}) {
            HistoryScreen(state, HistoryView.Week, {}, {}, {}, now.toLocalDate()) {
                WeekView(state, emptyList(), mondayOf(now.toLocalDate()).minusDays(7), now.toLocalDate(), {}, {})
            }
        }
    }
}
