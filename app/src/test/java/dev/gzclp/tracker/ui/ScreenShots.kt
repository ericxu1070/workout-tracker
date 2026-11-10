package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.OffsetDateTime

/** Renders every main screen in every theme. Run with ./gradlew recordRoborazziDebug. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ScreenShots {
    private val out = File(System.getProperty("gzclp.shots") ?: "build/shots")
    private val rest = Tier.entries.associateWith { it.defaultRestSec }
    private val t0 = OffsetDateTime.parse("2026-09-28T18:00:00-07:00")

    private fun history(): Pair<AppState, OffsetDateTime> {
        val store = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() })
        store.dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115)) }
        for (k in 0 until 14) {
            val start = t0.plusHours(k * 56L + (k % 3) * 3)
            store.dispatch(start) { Commands.start(it, start) }
            var t = start
            val day = store.state.value.active!!.day
            for (ex in store.state.value.exercises(day)) for (i in 1..ex.scheme.sets) {
                val amrap = ex.scheme.amrap && i == ex.scheme.sets
                val miss = (k == 6 && ex.tier == Tier.T1 && i == 4) || (k == 9 && ex.tier == Tier.T2 && i == 3)
                val reps = when {
                    miss -> ex.scheme.reps - 1
                    amrap && ex.tier == Tier.T3 -> 18 + k
                    amrap -> ex.scheme.reps + 3 - k % 3
                    else -> ex.scheme.reps
                }
                t = t.plusSeconds(rest.getValue(ex.tier) + 25L + (k * 13 + i * 7) % 40)
                store.dispatch(t) { Commands.logSet(it, ex, i, reps, t, rest) }
            }
            val end = t.plusSeconds(90)
            store.dispatch(end) { Commands.end(it, end, null) }
        }
        val start = t0.plusDays(33).withHour(18).withMinute(42)
        store.dispatch(start) { Commands.start(it, start) }
        val ex = store.state.value.exercises(store.state.value.active!!.day)
        listOf(0 to 1, 0 to 2, 0 to 3).forEachIndexed { n, (e, i) ->
            val t = start.plusMinutes(4L + n * 4)
            store.dispatch(t) { Commands.logSet(it, ex[e], i, ex[e].scheme.reps, t, rest) }
        }
        return store.state.value to start.plusMinutes(24).plusSeconds(18)
    }

    private fun shot(p: Palette, name: String, content: @Composable () -> Unit) {
        out.mkdirs()
        captureRoboImage(File(out, "${p.id}-$name.png").path) {
            GzTheme(p) { Box(Modifier.fillMaxSize()) { content() } }
        }
    }

    @Test fun allScreensAllThemes() {
        val (live, now) = history()
        val idle = live.copy(active = null)
        val workout = live.active!!
        val ex = live.exercises(workout.day)
        for (p in Palettes.all) {
            shot(p, "1-setup") {
                SetupScreen({}, {}, null, mapOf(Lift.SQUAT to "245", Lift.BENCH to "175", Lift.DEADLIFT to "295"))
            }
            shot(p, "2-next-workout") { HomeScreen(idle, {}, {}, {}, {}) }
            shot(p, "3-workout-rest") {
                WorkoutScreen(live, now, RestUi(107, 180, restLabel(ex[0], 3)), { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
            }
            shot(p, "4-amrap-sheet") {
                Box(Modifier.fillMaxSize()) {
                    WorkoutScreen(live, now, null, { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.Reps(ex[0], ex[0].scheme.sets, 6, false), {}, {}, {}, {}) }
                }
            }
            shot(p, "5-history") { HistoryScreen(idle, {}) }
            shot(p, "6-charts") { HistoryScreen(idle, {}, initialTab = 1) }
            shot(p, "7-settings") {
                SettingsScreen(
                    true, p.id, rest, SyncUi("GZCLP", "Fri, Oct 30, 7:42 PM", null), false,
                    {}, {}, { _, _ -> }, {}, {}, {}, {},
                )
            }
            shot(p, "8-accessories") { AccessoriesScreen(idle, {}, {}, { _, _ -> }) }
        }
    }
}
