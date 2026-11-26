package dev.gzclp.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.OffsetDateTime

/** The step pill, read off real pixels: a column just left of the set numbers is tinted only where the pill is. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class StepPillTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val t0 = OffsetDateTime.parse("2026-11-25T18:00:00-08:00")
    private val p = Palettes.tokyo

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
    private fun check(label: String) = rule.onNode(clickLabel(label))

    /** The middle of the tinted run in the column 12dp from the left edge (inside the pill's 12dp bleed, outside every row), or null with no pill. */
    private fun pillCenter(img: Bitmap = frame()): Float? {
        val x = (12 * rule.density.density).toInt()
        val bg = p.bg.toArgb()
        // Runs of tinted pixels; the pill is the one between 16 and 64dp tall (a row, its inflation and its stretch), not a bar at the screen's edge.
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (y in 0..img.height) {
            val tinted = y < img.height && img.getPixel(x, y) != bg
            if (tinted && start < 0) start = y
            if (!tinted && start >= 0) { runs += start until y; start = -1 }
        }
        val dp = rule.density.density
        val pill = runs.filter { it.count() in (16 * dp).toInt()..(64 * dp).toInt() }
        assertTrue("several pill-sized runs: $pill", pill.size <= 1)
        return pill.firstOrNull()?.let { (it.first + it.last) / 2f }
    }

    /** Drawn straight from the view, so a frame mid-animation can be read with the clock paused. */
    private fun frame(): Bitmap {
        val v = rule.activity.window.decorView
        return Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { rule.runOnUiThread { v.draw(Canvas(it)) } }
    }

    private fun rowCenter(label: String) = check(label).fetchSemanticsNode().boundsInRoot.center.y

    private fun assertPillOn(label: String) {
        val c = pillCenter()
        assertNotNull("no pill", c)
        assertEquals("pill not on $label", rowCenter(label), c!!, 2f)
    }

    @Test fun pillFollowsTheCurrentSetAndTravelsBetweenRows() {
        val store = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() })
        store.dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115), Programs.GZCLP) }
        while (true) {
            val missing = Commands.missingWeight(store.state.value) ?: break
            store.dispatch(t0) { Commands.startingWeight(it, missing, 50) }
        }
        store.dispatch(t0) { Commands.start(it, t0) }
        var state by mutableStateOf(store.state.value)
        val (first, second) = state.sessionExercises().let { it[0] to it[1] }
        rule.setContent {
            GzTheme(p) {
                WorkoutScreen(
                    state, t0.plusMinutes(5), null,
                    onLog = { e, i, w, r, rpe ->
                        store.dispatch(t0) { Commands.logSet(it, e, i, r, w, t0, Tier.entries.associateWith { t -> t.defaultRestSec }, rpe) }
                        state = store.state.value
                    },
                    onClear = { e: Exercise, i: Int -> store.dispatch(t0) { Commands.clearSet(it, e, i) }; state = store.state.value },
                    onEditWeight = {}, onSwap = {}, onSkipRest = {}, onSettings = {}, onEnd = {}, warmups = false,
                )
            }
        }
        rule.waitForIdle()
        assertPillOn("Log ${first.name} set 1")

        // Mid-flight, the pill is drawn between set 1 and set 2: it travels rather than fading out and back in.
        rule.mainClock.autoAdvance = false
        check("Log ${first.name} set 1").performClick()
        rule.mainClock.advanceTimeBy(64)
        val from = rowCenter("Clear ${first.name} set 1")
        val to = rowCenter("Log ${first.name} set 2")
        val mid = pillCenter()
        assertNotNull("pill vanished mid-flight", mid)
        assertTrue("pill at $mid, not between $from and $to", mid!! > from + 2 && mid < to - 2)
        // A springy move: it carries past set 2 and settles back onto it.
        val shots = System.getProperty("gzclp.shots")
        val path = (1..40).mapNotNull { n ->
            rule.mainClock.advanceTimeByFrame()
            val img = frame()
            if (shots != null && n % 3 == 0) File(shots, "pill-flight/%02d.png".format(n)).apply { parentFile!!.mkdirs() }.outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
            pillCenter(img)
        }
        assertTrue("no overshoot past $to: max ${path.max()}", path.max() > to + 2)
        assertEquals(to, path.last(), 2f)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertPillOn("Log ${first.name} set 2")

        // Clearing set 1 brings it back.
        check("Clear ${first.name} set 1").performClick()
        rule.waitForIdle()
        assertPillOn("Log ${first.name} set 1")

        // Across an exercise: once the first exercise is done, the pill lands on the second's set 1.
        for (i in 1..first.scheme.sets) { check("Log ${first.name} set $i").performScrollTo().performClick(); rule.waitForIdle() }
        check("Log ${second.name} set 1").performScrollTo()
        rule.waitForIdle()
        assertPillOn("Log ${second.name} set 1")
        System.getProperty("gzclp.shots")?.let { rule.onRoot().captureRoboImage(File(it, "${p.id}-pill-next-exercise.png").path) }
    }

    /** In an open session the pill sits in the card holding the session's first unlogged set, and only there. */
    @Test fun openSessionPillMarksTheFirstUnloggedSet() {
        val store = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() })
        store.dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115), Programs.GZCLP) }
        store.dispatch(t0) { Commands.startOpen(it, t0) }
        OpenSessionManager.reset()
        OpenSessionManager.start(fromToday = true, store.state.value) {}
        var state by mutableStateOf(store.state.value)
        val first = OpenSessionManager.exercises.first().exercise
        var palette by mutableStateOf(p)
        rule.setContent {
            GzTheme(palette) {
                OpenSessionScreen(
                    state, t0.plusMinutes(5),
                    onLog = { e, i, w, r, rpe ->
                        store.dispatch(t0) { Commands.logSet(it, e, i, r, w, t0, Tier.entries.associateWith { t -> t.defaultRestSec }, rpe) }
                        state = store.state.value
                    },
                    onClear = { _, _ -> }, onFinish = {}, onSettings = {},
                )
            }
        }
        check("Log ${first.name} set 1").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodes(androidx.compose.ui.test.isSelected()).assertCountEquals(1)
        rule.onNode(androidx.compose.ui.test.isSelected() and androidx.compose.ui.test.hasAnyDescendant(clickLabel("Log ${first.name} set 2"))).assertExists()
        System.getProperty("gzclp.shots")?.let { out ->
            for (t in listOf(Palettes.tokyo, Palettes.light, Palettes.glass)) {
                palette = t
                rule.waitForIdle()
                rule.onRoot().captureRoboImage(File(out, "${t.id}-02-open-session-pill.png").path)
            }
        }
        OpenSessionManager.reset()
    }
}
