package dev.gzclp.tracker.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.mondayOf
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.OffsetDateTime

/** Renders, from demo data, the Workout and History pages caught mid-swipe; runs only when -PgzclpShots is set. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SwipeShots {
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
                WeekView(state, emptyList(), mondayOf(now.toLocalDate()), now.toLocalDate(), {}, {})
            }
        }
    }
}
