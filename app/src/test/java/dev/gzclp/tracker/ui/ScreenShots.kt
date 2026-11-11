package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.ExerciseDefine
import dev.gzclp.tracker.log.ExerciseSwap
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.exerciseLogs
import dev.gzclp.tracker.log.weighIns
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
            // RPE on last sets only, like a lifter who rates just the hard ones.
            val rpe = if (i == ex.scheme.sets) 8.0 + (k + i) % 4 * 0.5 else null
            dispatch(t) { Commands.logSet(it, ex, i, reps, w, t, rest, rpe) }
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
        // Morning weigh-ins from day 4: a slow drift down with day-to-day noise, plus one evening weigh-in.
        for (d in 4L..day) {
            val at = t0.plusDays(d).withHour(6).withMinute(40 + (d * 7 % 15).toInt())
            store.dispatch(at) { Commands.logBodyWeight(it, null, at, 184.6 - d * 0.12 + listOf(0.0, 0.8, -0.4, 0.5, -0.6, 0.2, 0.9)[(d % 7).toInt()]) }
        }
        t0.plusDays(day - 1).withHour(21).let { at -> store.dispatch(at) { Commands.logBodyWeight(it, null, at, 183.8) } }
        val start = t0.plusDays(day).withHour(18).withMinute(42)
        store.workout(start, 17, end = false)
        return store.state.value to start.plusMinutes(14).plusSeconds(18)
    }

    /** A GZCLP A1 ten minutes in, to show the T1 warm-ups. */
    private fun gzclpLive(): Pair<AppState, OffsetDateTime> {
        val store = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() })
        store.dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115), Programs.GZCLP) }
        store.workout(t0, 0, end = false)
        return store.state.value to t0.plusMinutes(11)
    }

    /** Four meals on [day], drawn as plates on a table, saved the way the camera flow saves them. */
    private fun meals(day: java.time.LocalDate): List<dev.gzclp.tracker.food.Meal> {
        val log = dev.gzclp.tracker.food.FoodLog(java.nio.file.Files.createTempDirectory("food").toFile().apply { deleteOnExit() })
        val foods = listOf(
            listOf(0xFFF2C14E, 0xFFE07A5F, 0xFF8D6E63), listOf(0xFF81B29A, 0xFFF4A261, 0xFFE9C46A),
            listOf(0xFFD4A373, 0xFF6A994E, 0xFFBC4749), listOf(0xFFE76F51, 0xFF2A9D8F, 0xFFF4F1DE),
        )
        return listOf(7 to 48, 12 to 31, 15 to 52, 19 to 14).mapIndexed { i, (h, m) ->
            val bmp = android.graphics.Bitmap.createBitmap(1280, 960, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            c.drawColor(0xFF6D4C41.toInt() + i * 0x060302)
            paint.color = 0xFFF5F5F0.toInt(); c.drawCircle(640f, 480f, 400f, paint)
            paint.color = 0xFFE0E0D8.toInt(); c.drawCircle(640f, 480f, 330f, paint)
            foods[i].forEachIndexed { k, col ->
                paint.color = col.toInt()
                c.drawCircle(520f + k * 120f, 420f + (k % 2) * 140f, 150f - k * 20f, paint)
            }
            val cam = File.createTempFile("cam", ".jpg").apply { outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) } }
            log.add(cam, day.atTime(h, m).atOffset(t0.offset))
        }
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

    /** The Laptop theme for every Omarchy theme on the laptop, as the laptop hook syncs it. */
    @Test fun laptopThemes() {
        val (live, now) = history()
        val idle = live.copy(active = null)
        val ex = live.exercises(live.active!!.program, live.active!!.day)
        val food = meals(now.toLocalDate())
        val themes = File(javaClass.getResource("/themes")!!.toURI()).list()!!.filter { it.endsWith(".raw.toml") }.map { it.removeSuffix(".raw.toml") }
        for (name in themes.sorted()) {
            val p = LaptopTheme.parse(javaClass.getResource("/themes/$name.toml")!!.readText())!!
            val file = p.copy(id = "laptop-$name")
            shot(file, "03-workout-rest") {
                TabFrame(Tab.Workout, {}) { WorkoutScreen(live, now, RestUi(67, 90, restLabel(ex[1], 1)), { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) }
            }
            shot(file, "18-food") { TabFrame(Tab.Food, {}) { FoodScreen(food, now.toLocalDate(), now.toLocalDate(), null, {}, {}, {}) } }
            shot(file, "14-workout-full", tall = true) {
                TabFrame(Tab.Workout, {}) { WorkoutScreen(live, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) }
            }
            for (tall in listOf(false, true)) shot(file, if (tall) "12-settings-full" else "12-settings", tall) {
                SettingsScreen(
                    true, idle.programId, idle.increments, LaptopTheme.ID, p, rest, SyncUi("GZCLP", "Fri, Oct 30, 7:42 PM", null), false, true, 7 * 60, HealthUi(true, null), Plates(),
                    {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
        }
    }

    @Test fun allScreensAllThemes() {
        val (live, now) = history()
        val idle = live.copy(active = null)
        val workout = live.active!!
        val ex = live.exercises(workout.program, workout.day)
        val gz = idle.copy(programId = Programs.GZCLP)
        val today = now.toLocalDate()
        val bench = live.exerciseLogs().first { it.id == "bench" }
        val swapped = live.apply(Entry(live.lastSeq + 1, now, ExerciseSwap(workout.id, "tricep_pushdown", Tier.CYCLE.id, "overhead_tricep_extension")))
        val newSub = live.apply(Entry(live.lastSeq + 1, now, ExerciseDefine("cable_kickback", "Cable kickback", false)))
            .let { it.apply(Entry(it.lastSeq + 1, now, ExerciseSwap(workout.id, "tricep_pushdown", Tier.CYCLE.id, "cable_kickback"))) }
        val (gzLive, gzNow) = gzclpLive()
        val food = meals(today)
        for (p in Palettes.all) {
            shot(p, "01-setup") {
                SetupScreen({ _, _ -> }, {}, null, mapOf(Lift.SQUAT to "245", Lift.BENCH to "175", Lift.DEADLIFT to "295"), initialProgram = "ppl")
            }
            shot(p, "02-next-workout") { TabFrame(Tab.Workout, {}) { HomeScreen(idle, {}, {}, {}) } }
            shot(p, "03-workout-rest") {
                TabFrame(Tab.Workout, {}) { WorkoutScreen(live, now, RestUi(67, 90, restLabel(ex[1], 1)), { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) }
            }
            shot(p, "04-gzclp-next") { TabFrame(Tab.Workout, {}) { HomeScreen(gz, {}, {}, {}) } }
            for (tall in listOf(false, true)) shot(p, if (tall) "04-gzclp-warmups-full" else "04-gzclp-warmups", tall) {
                TabFrame(Tab.Workout, {}) { WorkoutScreen(gzLive, gzNow, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) }
            }
            shot(p, "05-starting-weight-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Workout, {}) { HomeScreen(idle, {}, {}, {}) }
                    val missing = idle.exercise(dev.gzclp.tracker.engine.Slot("face_pull_new", Tier.CYCLE)).copy(name = "Cable crunch")
                    Sheet(true, {}) { SheetContent(SheetState.Weight(missing, 45), idle, {}, {}, {}) }
                }
            }
            shot(p, "06-upcoming-full", tall = true) { TabFrame(Tab.Upcoming, {}) { UpcomingScreen(idle, today, {}) } }
            shot(p, "08-exercises") { TabFrame(Tab.Exercises, {}) { ExercisesScreen(idle, {}, {}, {}) } }
            shot(p, "09-exercise-detail") { ExerciseScreen(idle, bench, {}, {}) }
            shot(p, "09-exercise-relative") { ExerciseScreen(idle, bench, {}, {}, initialRelative = true) }
            shot(p, "09-exercise-relative-empty") { ExerciseScreen(idle.copy(bodyWeights = emptyMap()), bench, {}, {}, initialRelative = true) }
            shot(p, "08-exercises-full", tall = true) { TabFrame(Tab.Exercises, {}) { ExercisesScreen(idle, {}, {}, {}) } }
            shot(p, "15-body-weight") { BodyWeightScreen(idle, today, {}, {}, {}) }
            shot(p, "15-body-weight-full", tall = true) { BodyWeightScreen(idle, today, {}, {}, {}) }
            shot(p, "15-body-weight-empty") { BodyWeightScreen(idle.copy(bodyWeights = emptyMap()), today, {}, {}, {}) }
            val lastWeight = idle.weighIns().last()
            shot(p, "16-log-weight-sheet") {
                Box(Modifier.fillMaxSize()) {
                    BodyWeightScreen(idle, today, {}, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.BodyWeight(null, TextFieldValue("182.4", TextRange(5)), now, today), idle, {}, {}, {}) }
                }
            }
            shot(p, "16-edit-weight-sheet") {
                Box(Modifier.fillMaxSize()) {
                    BodyWeightScreen(idle, today, {}, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.BodyWeight(lastWeight.id, TextFieldValue(Fmt.tenths(lastWeight.lb)), lastWeight.at, today), idle, {}, {}, {}) }
                }
            }
            shot(p, "10-history") { TabFrame(Tab.History, {}) { HistoryScreen(idle, {}) } }
            shot(p, "11-session") { SessionScreen(idle, idle.sessions.last(), {}) }
            shot(p, "12-settings") {
                SettingsScreen(
                    true, idle.programId, idle.increments, p.id, null, rest, SyncUi("GZCLP", "Fri, Oct 30, 7:42 PM", null), false, true, 7 * 60, HealthUi(true, null), Plates(),
                    {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
            shot(p, "12-settings-full", tall = true) {
                SettingsScreen(
                    true, idle.programId, idle.increments, p.id, null, rest, SyncUi("GZCLP", "Fri, Oct 30, 7:42 PM", null), false, true, 7 * 60, HealthUi(true, null), Plates(),
                    {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
            shot(p, "13-days") { DaysScreen(idle, {}, {}, { _, _ -> }) }
            shot(p, "14-workout-full", tall = true) {
                TabFrame(Tab.Workout, {}) { WorkoutScreen(live, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) }
            }
            shot(p, "17-workout-swapped", tall = true) {
                TabFrame(Tab.Workout, {}) { WorkoutScreen(swapped, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) }
            }
            shot(p, "17-swap-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Workout, {}) { WorkoutScreen(swapped, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.Swap(swapped.sessionExercises()[2], "fly"), swapped, {}, {}, {}) }
                }
            }
            shot(p, "17-workout-new-substitute", tall = true) {
                TabFrame(Tab.Workout, {}) { WorkoutScreen(newSub, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) }
            }
            shot(p, "17-swap-create-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Workout, {}) { WorkoutScreen(swapped, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.Swap(swapped.sessionExercises()[2], "Incl", create = true), swapped, {}, {}, {}) }
                }
            }
            shot(p, "18-food") { TabFrame(Tab.Food, {}) { FoodScreen(food, today, today, null, {}, {}, {}) } }
            shot(p, "18-food-empty") { TabFrame(Tab.Food, {}) { FoodScreen(food, today.minusDays(1), today, null, {}, {}, {}) } }
            shot(p, "18-meal-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Food, {}) { FoodScreen(food, today, today, null, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.Meal(food[1], today), idle, {}, {}, {}) }
                }
            }
            shot(p, "18-meal-delete-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Food, {}) { FoodScreen(food, today, today, null, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.Meal(food[1], today, delete = true), idle, {}, {}, {}) }
                }
            }
            shot(p, "17-swap-back-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Workout, {}) { WorkoutScreen(swapped, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.Swap(swapped.sessionExercises()[3], ""), swapped, {}, {}, {}) }
                }
            }
        }
    }
}

