package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.swipe
import dev.gzclp.tracker.log.Deletion
import dev.gzclp.tracker.log.weighIns
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

    @Test fun workoutMidSwipe() = midSwipe(Palettes.jade, "30-workout-mid-swipe") { workout() }

    /** Liquid Glass's thumb, lifted by the swipe: clearer glass than at rest, the labels under it bent at its rim. */
    @Test fun glassMidSwipe() = midSwipe(Palettes.glass, "30-workout-mid-swipe") { workout() }

    @androidx.compose.runtime.Composable private fun workout() {
        TabFrame(Tab.Workout, {}) {
            WorkoutPages(0, {}, {}) { page ->
                when (page) {
                    1 -> CardioScreen(state, now.toLocalDate(), 150, {}, {}, {}, {})
                    2 -> StretchScreen(state, "after_workout", {}, {}, {})
                    else -> HomeScreen(state, {}, {})
                }
            }
        }
    }

    /** Frames 32 ms apart of a fast swipe from Lifting and its release: the thumb lifts, trails, squashes, and settles. */
    @Test fun thumbFrames() {
        val out = System.getProperty("gzclp.shots")
        assumeNotNull(out)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            var mode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
            GzTheme(Palettes.dark) {
                TabFrame(Tab.Workout, {}) { WorkoutPages(mode, { mode = it }, {}) { page -> if (page == 1) CardioScreen(state, now.toLocalDate(), 150, {}, {}, {}, {}) else HomeScreen(state, {}, {}) } }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        var k = 0
        fun snap() = rule.onRoot().captureRoboImage(File(out, "dark-31-thumb-%02d.png".format(k++)).path)
        rule.onRoot().performTouchInput { down(Offset(width * 0.85f, height * 0.5f)) }
        repeat(6) {
            rule.onRoot().performTouchInput { moveBy(Offset(-width * 0.1f, 0f)) }
            rule.mainClock.advanceTimeBy(32)
            snap()
        }
        rule.onRoot().performTouchInput { up() }
        repeat(10) { rule.mainClock.advanceTimeBy(32); snap() }
    }

    @Test fun historyMidSwipe() = midSwipe(Palettes.light, "30-history-mid-swipe") { history() }

    @androidx.compose.runtime.Composable private fun history() {
        TabFrame(Tab.History, {}) {
            HistoryScreen(state, HistoryView.Week, {}, {}, {}, now.toLocalDate()) {
                WeekView(state, emptyList(), mondayOf(now.toLocalDate()).minusDays(7), now.toLocalDate(), {}, {})
            }
        }
    }

    /** Liquid Glass with the week held part way up: its rows pass under the floating header's switch and the tab bar, bent at their rims. */
    private fun scrolled(p: Palette) {
        val out = System.getProperty("gzclp.shots")
        assumeNotNull(out)
        rule.setContent { GzTheme(p) { history() } }
        rule.onRoot().performTouchInput {
            down(center)
            for (i in 1..10) moveBy(Offset(0f, -height * 0.025f))
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(out, "${p.id}-32-history-scrolled.png").path)
        rule.onRoot().performTouchInput { up() }
    }

    @Test fun glassScrolled() = scrolled(Palettes.glass)

    @Test fun lightGlassScrolled() = scrolled(Palettes.light.glassed)

    /** A theme with a colored page: its bars are glass tinted that color. */
    @Test fun tokyoGlassScrolled() = scrolled(Palettes.tokyo.glassed)

    /**
     * Swipes the row showing [rowText] open and shoots it, then taps its trash and shoots the question over it, as the
     * app does: the row held open under a sheet that blurs the screen.
     */
    private fun swipeDelete(p: Palette, name: String, rowText: String, screen: @androidx.compose.runtime.Composable (Deletion?, (Deletion) -> Unit) -> Unit) {
        val out = System.getProperty("gzclp.shots")
        assumeNotNull(out)
        var deleting by mutableStateOf<Deletion?>(null)
        rule.setContent {
            GzTheme(p) {
                val b = rememberBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().backdropSource(b)) { screen(deleting) { deleting = it } }
                    Sheet(deleting != null, { deleting = null }, b) { deleting?.let { SheetContent(SheetState.Delete(it), state, { deleting = null }, {}, {}) } }
                }
            }
        }
        val y = rule.onAllNodesWithText(rowText, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot.center.y
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.9f, y), Offset(width * 0.75f, y), 300) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(out, "${p.id}-$name-open.png").path)
        rule.onRoot().performTouchInput { click(Offset(width - 80f, y)) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(out, "${p.id}-$name-confirm.png").path)
    }

    private val today = now.toLocalDate()

    private fun weighIn(p: Palette) = swipeDelete(p, "31-weigh-in-delete", Fmt.weighInTime(state.weighIns().last().at, today)) { d, on ->
        BodyWeightScreen(state, today, {}, {}, {}, d, on)
    }

    private val workout get() = state.sessions.last { it.sets.isNotEmpty() }

    private fun history(p: Palette) = swipeDelete(p, "31-history-delete", "${workout.day.title} · ${Fmt.date(workout.startedAt)}") { d, on ->
        TabFrame(Tab.History, {}) { HistoryScreen(state, HistoryView.All, {}, {}, {}, today, d, on) {} }
    }

    private fun exercise(p: Palette) = swipeDelete(p, "31-exercise-delete", "Set 2") { d, on -> SessionScreen(state, workout, {}, {}, d, on) }

    // One render per test: a compose rule takes one setContent.
    @Test fun weighInDeleteLight() = weighIn(Palettes.light)
    @Test fun weighInDeleteGlass() = weighIn(Palettes.glass)
    @Test fun historyDeleteJade() = history(Palettes.jade)
    @Test fun historyDeleteGlass() = history(Palettes.glass)
    @Test fun exerciseDeleteLight() = exercise(Palettes.light)
    @Test fun exerciseDeleteGlass() = exercise(Palettes.glass)
}
