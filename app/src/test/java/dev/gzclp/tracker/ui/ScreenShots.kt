package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.exerciseLogs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.OffsetDateTime

/** Renders every screen in every theme. Run with ./gradlew recordRoborazziDebug. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ScreenShots {
    private val out = File(System.getProperty("gzclp.shots") ?: "build/shots")
    private val rest = Tier.entries.associateWith { it.defaultRestSec }
    private val t0 = OffsetDateTime.parse("2026-09-28T18:00:00-07:00")
    private val startWeights = mapOf("lat_pulldown" to 100, "dumbbell_row" to 50, "incline_db_press" to 50, "lateral_raise" to 15, "tricep_pushdown" to 40,
        "seated_cable_row" to 100, "face_pull" to 35, "barbell_curl" to 50, "romanian_deadlift" to 135, "leg_curl" to 70, "calf_raise" to 90,
        "chest_fly" to 25, "overhead_tricep_extension" to 40, "barbell_row" to 115, "rear_delt_fly" to 15, "hammer_curl" to 25, "leg_press" to 230, "leg_extension" to 70)

    private fun Store.workout(start: OffsetDateTime, k: Int, end: Boolean = true) {
        while (true) {
            val ex = Commands.missingWeight(state.value) ?: break
            dispatch(start) { Commands.startingWeight(it, ex, startWeights[ex.id] ?: 45) }
        }
        dispatch(start) { Commands.start(it, start) }
        var t = start
        val a = state.value.active!!
        for (ex in state.value.exercises(a.program, a.day)) for (i in 1..ex.scheme.sets) {
            if (!end && t > start.plusMinutes(10)) return
            val amrap = ex.scheme.amrap && i == ex.scheme.sets
            val miss = (k == 4 && ex.tier == Tier.T1 && i == 4) || (k == 9 && i == 3 && ex.week == 2 && ex.id == "lat_pulldown")
            val reps = when {
                miss -> ex.scheme.reps - 1
                amrap && ex.tier == Tier.T3 -> 18 + k
                amrap -> ex.scheme.reps + 3 - k % 3
                else -> ex.scheme.reps
            }
            val w = (ex.weight ?: 0) + if (k == 7 && i == 3 && ex.tier == Tier.CYCLE) 5 else 0
            t = t.plusSeconds(rest.getValue(ex.tier) + 25L + (k * 13 + i * 7) % 40)
            dispatch(t) { Commands.logSet(it, ex, i, reps, w, t, rest) }
        }
        if (end) t.plusSeconds(90).let { e -> dispatch(e) { Commands.end(it, e, null) } }
    }

    /** Five GZCLP workouts, then push/pull/legs, then a push day in progress. */
    private fun history(): Pair<AppState, OffsetDateTime> {
        val store = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() })
        store.dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115), Programs.GZCLP) }
        var day = 0L
        for (k in 0 until 5) store.workout(t0.plusDays(day).also { day += 2 }.plusMinutes(k * 17L), k)
        store.dispatch(t0) { Commands.chooseProgram(it, "ppl") }
        for (k in 5 until 17) store.workout(t0.plusDays(day).also { day += if (k % 6 == 5) 2 else 1 }.plusMinutes(k * 11L % 50), k)
        val start = t0.plusDays(day).withHour(18).withMinute(42)
        store.workout(start, 17, end = false)
        return store.state.value to start.plusMinutes(14).plusSeconds(18)
    }

    /** [tall] renders the whole scrolling page on a very tall screen, to review everything below the fold. */
    private fun shot(p: Palette, name: String, tall: Boolean = false, content: @Composable () -> Unit) {
        out.mkdirs()
        if (tall) RuntimeEnvironment.setQualifiers("w411dp-h3000dp-420dpi")
        captureRoboImage(File(out, "${p.id}-$name.png").path) {
            GzTheme(p) { Box(Modifier.fillMaxSize()) { content() } }
        }
        if (tall) RuntimeEnvironment.setQualifiers("w411dp-h891dp-420dpi")
    }

    @Test fun allScreensAllThemes() {
        val (live, now) = history()
        val idle = live.copy(active = null)
        val workout = live.active!!
        val ex = live.exercises(workout.program, workout.day)
        val gz = idle.copy(programId = Programs.GZCLP)
        val today = now.toLocalDate()
        val bench = live.exerciseLogs().first { it.id == "bench" }
        for (p in Palettes.all) {
            shot(p, "01-setup") {
                SetupScreen({ _, _ -> }, {}, null, mapOf(Lift.SQUAT to "245", Lift.BENCH to "175", Lift.DEADLIFT to "295"), initialProgram = "ppl")
            }
            shot(p, "02-next-workout") { TabFrame(Tab.Workout, {}) { HomeScreen(idle, {}, {}, {}) } }
            shot(p, "03-workout-rest") {
                TabFrame(Tab.Workout, {}) { WorkoutScreen(live, now, RestUi(67, 90, restLabel(ex[1], 1)), { _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
            shot(p, "04-gzclp-next") { TabFrame(Tab.Workout, {}) { HomeScreen(gz, {}, {}, {}) } }
            shot(p, "05-starting-weight-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Workout, {}) { HomeScreen(idle, {}, {}, {}) }
                    val missing = idle.exercise(dev.gzclp.tracker.engine.Slot("face_pull_new", Tier.CYCLE)).copy(name = "Cable crunch")
                    Sheet(true, {}) { SheetContent(SheetState.Weight(missing, 45), idle, {}, {}, {}) }
                }
            }
            shot(p, "06-upcoming-full", tall = true) { TabFrame(Tab.Upcoming, {}) { UpcomingScreen(idle, today, {}) } }
            shot(p, "08-exercises") { TabFrame(Tab.Exercises, {}) { ExercisesScreen(idle, {}) } }
            shot(p, "09-exercise-detail") { ExerciseScreen(idle, bench, {}) }
            shot(p, "08-exercises-full", tall = true) { TabFrame(Tab.Exercises, {}) { ExercisesScreen(idle, {}) } }
            shot(p, "10-history") { TabFrame(Tab.History, {}) { HistoryScreen(idle, {}) } }
            shot(p, "11-session") { SessionScreen(idle, idle.sessions.last(), {}) }
            shot(p, "12-settings") {
                SettingsScreen(
                    true, idle.programId, idle.increments, p.id, rest, SyncUi("GZCLP", "Fri, Oct 30, 7:42 PM", null), false,
                    {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {},
                )
            }
            shot(p, "12-settings-full", tall = true) {
                SettingsScreen(
                    true, idle.programId, idle.increments, p.id, rest, SyncUi("GZCLP", "Fri, Oct 30, 7:42 PM", null), false,
                    {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {},
                )
            }
            shot(p, "13-days") { DaysScreen(idle, {}, {}, { _, _ -> }) }
            shot(p, "14-workout-full", tall = true) {
                TabFrame(Tab.Workout, {}) { WorkoutScreen(live, now, null, { _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
        }
    }
}

