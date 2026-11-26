package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchRoutines
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
import dev.gzclp.tracker.log.weighInScale
import dev.gzclp.tracker.log.WaterLog
import dev.gzclp.tracker.log.mondayOf
import dev.gzclp.tracker.engine.Activities
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
    /** The demo half-year, ten minutes into Monday's workout. */
    private fun history(): Pair<AppState, OffsetDateTime> = Demo.state to Demo.now

    private fun AppState.then(at: OffsetDateTime, build: (AppState) -> List<dev.gzclp.tracker.log.Event>): AppState =
        build(this).fold(this) { s, e -> s.apply(Entry(s.lastSeq + 1, at, e)) }

    /**
     * [base] with "My PPL" copied from push, pull, legs and in use: 5 × 8·6·5 presses and 3 × 15·12·10 lateral raises on Push A,
     * starting weights from the estimates, and one full rotation done, so Push A is next at week 2.
     */
    private fun split(base: AppState, now: OffsetDateTime): AppState {
        var s = base.then(now) { Commands.copySplit(it, "ppl", "My PPL") }
        val id = s.customPrograms.keys.last()
        val press = dev.gzclp.tracker.engine.Wave(5, listOf(8, 6, 5))
        s = s.then(now) { Commands.setWave(it, id, "push_a", "bench", press) }.then(now) { Commands.setWave(it, id, "push_a", "ohp", press) }
            .then(now) { Commands.setWave(it, id, "push_a", "lateral_raise", dev.gzclp.tracker.engine.Wave(3, listOf(15, 12, 10))) }
            .then(now) { Commands.chooseProgram(it, id) }
        for (d in s.program.days) for (ex in s.exercises(d)) if (ex.weight == null) s = s.then(now) { Commands.startingWeight(it, ex, Commands.estimate(it, ex, Plates()) ?: 45) }
        for (k in 0L until 6L) {
            val at = now.plusDays(k)
            s = s.then(at) { Commands.start(it, at) }
            for (ex in s.sessionExercises()) for (i in 1..ex.scheme.sets) s = s.then(at) { Commands.logSet(it, ex, i, ex.scheme.reps, ex.weight!!, at, rest) }
            s = s.then(at) { Commands.end(it, at.plusMinutes(50), null) }
        }
        return s
    }

    /** A GZCLP A1 ten minutes in, to show the T1 warm-ups. */
    private fun gzclpLive(): Pair<AppState, OffsetDateTime> {
        val store = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() })
        store.dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115), Programs.GZCLP) }
        with(Demo) { store.workout(t0, 0, end = false) }
        return store.state.value to t0.plusMinutes(11)
    }

    /** Four meals on each of [days], drawn as plates on a table, saved the way the camera flow saves them. */
    private fun meals(days: List<java.time.LocalDate>): List<dev.gzclp.tracker.food.Meal> {
        val log = dev.gzclp.tracker.food.FoodLog(java.nio.file.Files.createTempDirectory("food").toFile().apply { deleteOnExit() })
        val foods = listOf(
            listOf(0xFFF2C14E, 0xFFE07A5F, 0xFF8D6E63), listOf(0xFF81B29A, 0xFFF4A261, 0xFFE9C46A),
            listOf(0xFFD4A373, 0xFF6A994E, 0xFFBC4749), listOf(0xFFE76F51, 0xFF2A9D8F, 0xFFF4F1DE),
        )
        return days.flatMap { day -> Demo.meals(day).mapIndexed { i, at ->
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
            log.add(cam, at)
        } }
    }

    /** [tall] renders the whole scrolling page on a very tall screen, to review everything below the fold. */
    /** [screen] as the Workout tab's [mode] page, under the tab's fixed gear and switch. */
    @Composable
    private fun workoutTab(mode: Int, screen: @Composable () -> Unit) = TabFrame(Tab.Workout, {}) { WorkoutPages(mode, {}, {}) { if (it == mode) screen() } }

    /** Liquid Glass over a dark and a light theme, rendered beside the flat themes. */
    private val glassThemes = listOf(Palettes.glass, Palettes.light.glassed)

    private fun shot(p: Palette, name: String, tall: Boolean = false, content: @Composable () -> Unit) {
        out.mkdirs()
        if (tall) RuntimeEnvironment.setQualifiers("w411dp-h3000dp-420dpi")
        System.getProperty("gzclp.fontScale")?.let { RuntimeEnvironment.setFontScale(it.toFloat()) }
        captureRoboImage(File(out, "${p.id}-$name.png").path) {
            GzTheme(p) { Box(Modifier.fillMaxSize()) { content() } }
        }
        if (tall) RuntimeEnvironment.setQualifiers("w411dp-h891dp-420dpi")
    }

    /** The 3D form viewer open, from the side and from high above, in a dark and a light theme. */
    @Test fun formViewer() {
        val views = listOf("squat" to 1f, "bench" to 0f, "deadlift" to 0f, "pull_up" to 1f)
            .map { (id, t) -> Triple(id, Motions.of(id), t) } + Triple("stretch", Motions.of(StretchMoves.PIGEON_POSE), 1f)
        for (p in listOf(Palettes.jade, Palettes.light)) for ((name, motion, t) in views) for ((view, yaw, pitch) in listOf(Triple("side", 90f, 3f), Triple("top", 35f, 70f))) {
            val orbit = Orbit().apply { this.yaw = yaw; this.pitch = pitch }
            shot(p, "form-$name-$view") {
                Column(Modifier.fillMaxSize().background(p.bg).padding(horizontal = Space.lg).padding(top = 64.dp)) {
                    Txt(name.replace('_', '-'), Type.largeTitle)
                    Spacer(Modifier.height(Space.lg))
                    FormView(motion, Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(Radius.lg)).background(p.fill), still = t, orbit = orbit)
                }
            }
        }
    }

    /** Each stretch held, lit where it is felt: from its opening view, from behind, and mirrored for its second side. */
    @Test fun stretchFocus() {
        val p = Palettes.jade
        RuntimeEnvironment.setQualifiers("w420dp-h200dp-420dpi")
        for (move in StretchMoves.ALL) captureRoboImage(File(out, "focus/${move.id}.png").path) {
            GzTheme(p) {
                Row(Modifier.fillMaxSize().background(p.bg)) {
                    val home = Motions.of(move).yaw ?: DEFAULT_YAW
                    for ((yaw, mirror) in listOf(home to false, home + 180f to false, home to true)) {
                        FormView(Motions.of(move), Modifier.weight(1f).fillMaxHeight(), focus = move.focus, mirror = mirror, still = 1f, orbit = Orbit().apply { this.yaw = yaw; this.pitch = 15f })
                    }
                }
            }
        }
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-420dpi")
    }

    /** Every exercise and stretch at its start and far end, from the side and three-quarters, one row each, to review them all at once. */
    @Test fun formGallery() {
        val ids = Catalog.all.map { it.id } + StretchMoves.ALL.map { it.id }
        val p = Palettes.jade
        RuntimeEnvironment.setQualifiers("w420dp-h120dp-420dpi")
        for (id in ids) captureRoboImage(File(out, "gallery/$id.png").path) {
            GzTheme(p) {
                Row(Modifier.fillMaxSize().background(p.bg)) {
                    for ((yaw, pitch) in listOf(90f to 4f, 35f to 15f)) for (t in listOf(0f, 1f)) {
                        FormView(Motions.of(id), Modifier.weight(1f).fillMaxHeight(), still = t, orbit = Orbit().apply { this.yaw = yaw; this.pitch = pitch })
                    }
                }
            }
        }
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-420dpi")
    }

    /** Each body of the full-body mesh standing from the front, side and back, and from behind at the bottom of a squat, one sheet per body and theme. */
    @Test fun formBodies() {
        val views = listOf(Triple("stand", 0f, 0f), Triple("stand", 90f, 0f), Triple("stand", 180f, 0f), Triple("squat", 180f, 12f))
        RuntimeEnvironment.setQualifiers("w1440dp-h400dp-420dpi")
        try {
            dev.gzclp.tracker.form.Figure.on = true
            for (p in listOf(Palettes.jade, Palettes.light)) for (body in dev.gzclp.tracker.form.Figure.bodies) {
                dev.gzclp.tracker.form.Figure.shape = body
                captureRoboImage(File(out, "bodies/${p.id}-$body.png").path) {
                    GzTheme(p) {
                        Row(Modifier.fillMaxSize().background(p.bg)) {
                            for ((id, yaw, pitch) in views) {
                                FormView(Motions.of(id), Modifier.weight(1f).fillMaxHeight(), still = if (id == "squat") 1f else 0f, orbit = Orbit().apply { this.yaw = yaw; this.pitch = pitch })
                            }
                        }
                    }
                }
            }
        } finally {
            dev.gzclp.tracker.form.Figure.on = true
            dev.gzclp.tracker.form.Figure.shape = "male"
            RuntimeEnvironment.setQualifiers("w411dp-h891dp-420dpi")
        }
    }

    /**
     * Each body through the lifts and some stretches (three of them lying on the back, and the two seated ones it sinks into
     * the floor furthest, at their deepest), from the side and three-quarters.
     */
    @Test fun formBodyPoses() {
        val poses = listOf(
            "squat" to 1f, "deadlift" to 0f, "bench" to 0f, "ohp" to 1f, "pull_up" to 0f, "push_up" to 1f, "hips_pigeon" to 1f, "upper_back_foam_roll" to 0.5f,
            "spine_supine_twist" to 0.5f, "spine_iron_cross" to 0.5f, "hamstrings_legs_up_wall" to 0.5f,
            "spine_seated_twist" to 0f, "hamstrings_pancake" to 0f,
        )
        RuntimeEnvironment.setQualifiers("w450dp-h2190dp-420dpi")
        try {
            dev.gzclp.tracker.form.Figure.on = true
            for (body in dev.gzclp.tracker.form.Figure.bodies) {
                dev.gzclp.tracker.form.Figure.shape = body
                captureRoboImage(File(out, "bodies/poses-$body.png").path) {
                    GzTheme(Palettes.light) {
                        Column(Modifier.fillMaxSize().background(Palettes.light.bg)) {
                            for ((id, t) in poses) Row(Modifier.weight(1f)) {
                                val focus = StretchMoves.ALL.firstOrNull { it.id == id }?.focus
                                for ((yaw, pitch) in listOf(90f to 4f, 35f to 15f)) {
                                    FormView(Motions.of(id), Modifier.weight(1f).fillMaxHeight(), focus = focus, still = t, orbit = Orbit().apply { this.yaw = yaw; this.pitch = pitch })
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            dev.gzclp.tracker.form.Figure.on = true
            dev.gzclp.tracker.form.Figure.shape = "male"
            RuntimeEnvironment.setQualifiers("w411dp-h891dp-420dpi")
        }
    }

    /** The pieces and the full-body mesh trial in the same poses, one sheet per figure and theme, from the side and three-quarters. */
    @Test fun formFigure() {
        val poses = listOf("squat" to 1f, "squat" to 0.6f, "deadlift" to 0f, "ohp" to 1f, "pull_up" to 0f, "push_up" to 1f, "bench" to 0f, "hips_pigeon" to 1f, "upper_back_foam_roll" to 0.5f)
        RuntimeEnvironment.setQualifiers("w450dp-h1350dp-420dpi")
        try {
            for (p in listOf(Palettes.jade, Palettes.light)) for (mesh in listOf(false, true)) {
                dev.gzclp.tracker.form.Figure.on = mesh
                captureRoboImage(File(out, "figure/${p.id}-${if (mesh) "mesh" else "pieces"}.png").path) {
                    GzTheme(p) {
                        Column(Modifier.fillMaxSize().background(p.bg)) {
                            for ((id, t) in poses) Row(Modifier.weight(1f)) {
                                val focus = StretchMoves.ALL.firstOrNull { it.id == id }?.focus
                                for ((yaw, pitch) in listOf(90f to 4f, 35f to 15f, 200f to 15f)) {
                                    FormView(Motions.of(id), Modifier.weight(1f).fillMaxHeight(), focus = focus, still = t, orbit = Orbit().apply { this.yaw = yaw; this.pitch = pitch })
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            dev.gzclp.tracker.form.Figure.on = true
            RuntimeEnvironment.setQualifiers("w411dp-h891dp-420dpi")
        }
    }

    /** One sheet per theme of every color pairing PaletteContrastTest checks: text levels, controls on and off, chart series. */
    @Test fun paletteSheets() {
        val pts = (0..5).map { t0.plusWeeks(it.toLong()) }
        for (p in Palettes.all) shot(p, "00-palette") {
            Column(Modifier.fillMaxSize().background(p.bg).padding(Space.lg)) {
                Txt(p.name, Type.largeTitle)
                Spacer(Modifier.height(Space.md))
                Segmented(listOf("Lifting", "Cardio", "Stretching"), 0, {})
                Spacer(Modifier.height(Space.md))
                Group {
                    GroupRow(true) { Txt("Primary", Type.body, Modifier.weight(1f)); Toggle(true, "On") {} }
                    GroupRow(false) { Txt("Secondary", Type.body, Modifier.weight(1f), p.fg2); Toggle(false, "Off") {} }
                    GroupRow(false) { Txt("Tertiary", Type.body, Modifier.weight(1f), p.fg3); Icon(Icons.chevronRight, p.fg3, 20.dp) }
                    GroupRow(false) { Txt("Accent", Type.body, Modifier.weight(1f), p.accent); Txt("Delete", Type.body, color = p.red) }
                    GroupRow(false) { StepSlider(60, 100, {}, "Slider") }
                }
                Spacer(Modifier.height(Space.md))
                LineChart(listOf(pts.mapIndexed { i, t -> Pt(t, 150f + i * 4) } to p.fg2, pts.mapIndexed { i, t -> Pt(t, 120f + i * 5) } to p.accent), listOf("1RM", "Top"))
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    PillButton("Start", {}, Modifier.weight(1f), primary = true)
                    PillButton("Later", {}, Modifier.weight(1f))
                    Pressable({}, Modifier.heightIn(min = 52.dp), CircleShape, p.red) {
                        Txt("Delete", Type.headline, Modifier.padding(horizontal = Space.xl), p.onRed, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(Space.md))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.xl)).background(p.elevated).padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    Segmented(listOf("Week", "All"), 1, {})
                    StepSlider(25, 100, {}, "Sheet slider")
                }
            }
        }
    }

    /** The Laptop theme for every Omarchy theme on the laptop, as the laptop hook syncs it. */
    @Test fun laptopThemes() {
        val (live, now) = history()
        val idle = live.copy(active = null)
        val ex = live.exercises(live.active!!.program, live.active!!.day)
        val food = meals(listOf(now.toLocalDate()))
        val themes = File(javaClass.getResource("/themes")!!.toURI()).list()!!.filter { it.endsWith(".raw.toml") }.map { it.removeSuffix(".raw.toml") }
        for (name in themes.sorted()) {
            val p = LaptopTheme.parse(javaClass.getResource("/themes/$name.toml")!!.readText())!!
            val file = p.copy(id = "laptop-$name")
            shot(file, "03-workout-rest") {
                workoutTab(0) { WorkoutScreen(live, now, RestUi(67, 90, restLabel(ex[1], 1)), { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
            shot(file, "18-food") { TabFrame(Tab.Food, {}) { FoodScreen(food, now.toLocalDate(), now.toLocalDate(), null, {}, {}, {}, {}) } }
            shot(file, "14-workout-full", tall = true) {
                workoutTab(0) { WorkoutScreen(live, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
            for (tall in listOf(false, true)) shot(file, if (tall) "12-settings-full" else "12-settings", tall) {
                SettingsScreen(
                    true, idle.programId, idle.increments, LaptopTheme.ID, p, rest, SyncUi("GZCLP", "Mon, Nov 30, 7:42 AM", null), false, true, 7 * 60, HealthUi(true, null), Plates(),
                    {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
        }
    }

    @Test fun cardioScreens() {
        val (live, now) = history()
        val week = live.copy(active = null)
        val start = now.minusSeconds(41 * 60 + 30)
        val running = Commands.startCardio(week, Activities.of("running")!!, start).fold(week) { s, e -> s.apply(Entry(s.lastSeq + 1, start, e)) }
        val run = running.cardioActive!!
        val today = now.toLocalDate()
        for (p in Palettes.all + glassThemes + Palettes.of("gruvbox").glassed) {
            shot(p, "19-cardio") { workoutTab(1) { CardioScreen(week, today, 180, {}, {}, {}, {}) } }
            shot(p, "19-cardio-full", tall = true) { workoutTab(1) { CardioScreen(week, today, 180, {}, {}, {}, {}) } }
            shot(p, "19-cardio-mid-lift") { workoutTab(1) { CardioScreen(week, today, 180, {}, {}, {}, {}, lifting = true) } }
            shot(p, "03-workout-switch") {
                workoutTab(0) { WorkoutScreen(live, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
            shot(p, "19-cardio-empty") { workoutTab(1) { CardioScreen(week.copy(cardio = emptyMap()), today, 150, {}, {}, {}, {}) } }
            shot(p, "20-cardio-running") { TabFrame(Tab.Workout, {}) { CardioRunScreen(run, now, {}, {}, {}) } }
            shot(p, "20-cardio-stop-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Workout, {}) { CardioRunScreen(run, now, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.CardioStop(run, 41 * 60 + 30, distance = 5.0, rpe = 13), running, {}, {}, {}) }
                }
            }
            shot(p, "20-cardio-discard-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Workout, {}) { CardioRunScreen(run, now, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.CardioDiscard(run), running, {}, {}, {}) }
                }
            }
            val past = cardioPast(Activities.of("running")!!, now).let { it.copy(run = it.run.copy(startedAt = it.run.startedAt.minusDays(1))) }
            shot(p, "22-cardio-log-sheet") {
                Box(Modifier.fillMaxSize()) {
                    workoutTab(1) { CardioScreen(week, today, 180, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(past, week, {}, {}, {}) }
                }
            }
            val last = week.cardioWeek(mondayOf(today)).first()
            shot(p, "22-cardio-edit-sheet") {
                Box(Modifier.fillMaxSize()) {
                    workoutTab(1) { CardioScreen(week, today, 180, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(cardioEdit(last, now), week, {}, {}, {}) }
                }
            }
            shot(p, "22-cardio-delete-sheet") {
                Box(Modifier.fillMaxSize()) {
                    workoutTab(1) { CardioScreen(week, today, 180, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.CardioDiscard(cardioEdit(last, now).run), week, {}, {}, {}) }
                }
            }
            shot(p, "21-activities") { ActivitiesScreen(week, {}, {}) }
            shot(p, "21-activities-search") { ActivitiesScreen(week, {}, {}, initial = "Spike") }
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
        val swapped = live.apply(Entry(live.lastSeq + 1, now, ExerciseSwap(workout.id, "face_pull", Tier.T3.id, "rear_delt_fly")))
        val newSub = live.apply(Entry(live.lastSeq + 1, now, ExerciseDefine("band_pull_apart", "Band pull-apart", false)))
            .let { it.apply(Entry(it.lastSeq + 1, now, ExerciseSwap(workout.id, "face_pull", Tier.T3.id, "band_pull_apart"))) }
        val (gzLive, gzNow) = gzclpLive()
        // Today's meals, and every day of last week, the week the Week shots show.
        val food = meals(listOf(today) + (7 downTo 1).map { today.minusDays(it.toLong()) })
        val lastWeek = mondayOf(today).minusDays(7)
        val mine = split(idle, now.plusDays(1))
        val splitNow = now.plusDays(7)
        val mineLive = mine.then(splitNow) { Commands.start(it, splitNow) }
        val ppl = mine.program
        val pushA = ppl.days.first()
        // An evening routine of the user's own: a one-sided move held longer, and a move added twice.
        val stretchy = idle.then(now) { Commands.newStretchRoutine(it, "Evening wind-down", listOf(StretchMoves.CHILDS_POSE_LAT, StretchMoves.PIGEON_POSE, StretchMoves.SUPINE_SPINAL_TWIST, StretchMoves.BUTTERFLY_STRETCH, StretchMoves.PIGEON_POSE)) }
            .let { s -> s.then(now) { Commands.setStretchHold(it, s.customStretches.keys.last(), 1, 60) } }
        val evening = stretchy.customStretches.values.last()
        fun settings(p: Palette) = @Composable {
            SettingsScreen(
                true, mine.programId, mine.increments, p.id, null, rest, SyncUi("GZCLP", "Mon, Nov 30, 7:42 AM", null), false, true, 7 * 60, HealthUi(true, null), Plates(),
                {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, splits = mine.customPrograms.values.toList(), page = SettingsPage.Program,
            )
        }
        for (p in listOf(Palettes.light, Palettes.tokyo, Palettes.glass, Palettes.light.glassed)) for (more in listOf(false, true)) shot(p, "27-appearance${if (more) "-more" else ""}", tall = more) {
            SettingsScreen(
                true, mine.programId, mine.increments, p.id.removeSuffix("-glass"), Palettes.of("gruvbox").copy(id = LaptopTheme.ID, name = "gruvbox"), rest, SyncUi("GZCLP", "Mon, Nov 30, 7:42 AM", null), false, true, 7 * 60, HealthUi(true, null), Plates(),
                {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, loaded = p.glass, page = SettingsPage.Appearance, initialShowMore = more,
            )
        }
        for (p in Palettes.all + glassThemes) {
            shot(p, "23-settings-splits") { settings(p)() }
            shot(p, "23-start-from-sheet") {
                Box(Modifier.fillMaxSize()) {
                    settings(p)()
                    Sheet(true, {}) { SheetContent(SheetState.NewSplit(), mine, {}, {}, {}) }
                }
            }
            shot(p, "24-split") { SplitScreen(mine, ppl, {}, {}, {}, {}) }
            shot(p, "24-split-edit") { SplitScreen(mine, ppl, {}, {}, {}, {}, initialEditing = true) }
            shot(p, "24-split-delete-sheet") {
                Box(Modifier.fillMaxSize()) {
                    SplitScreen(mine, ppl, {}, {}, {}, {}, initialEditing = true)
                    Sheet(true, {}) { SheetContent(SheetState.DeleteSplit(ppl.id, null), mine, {}, {}, {}) }
                }
            }
            shot(p, "25-split-day") { SplitDayScreen(mine, ppl, pushA, {}, {}, {}) }
            shot(p, "25-split-day-edit") { SplitDayScreen(mine, ppl, pushA, {}, {}, {}, initialEditing = true) }
            shot(p, "26-split-wave-sheet") {
                Box(Modifier.fillMaxSize()) {
                    SplitDayScreen(mine, ppl, pushA, {}, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.SplitWave(ppl.id, pushA.id, "bench", "Bench press", pushA.slots.first().wave!!), mine, {}, {}, {}) }
                }
            }
            shot(p, "26-split-name-sheet") {
                Box(Modifier.fillMaxSize()) {
                    SplitScreen(mine, ppl, {}, {}, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.SplitName(ppl.id, null, "", add = true), mine, {}, {}, {}) }
                }
            }
            shot(p, "27-split-next-workout") { workoutTab(0) { HomeScreen(mine, {}, {}) } }
            shot(p, "27-split-workout-full", tall = true) {
                workoutTab(0) { WorkoutScreen(mineLive, splitNow.plusMinutes(3), null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
            shot(p, "27-split-exercise-detail") { ExerciseScreen(mine, mine.exerciseLogs().first { it.id == "bench" }, {}, {}) }
            shot(p, "01-setup") {
                SetupScreen({ _, _ -> }, {}, null, mapOf(Lift.SQUAT to "245", Lift.BENCH to "175", Lift.DEADLIFT to "295"), initialProgram = "ppl")
            }
            shot(p, "02-next-workout") { workoutTab(0) { HomeScreen(idle, {}, {}) } }
            shot(p, "02-open-session-sheet") {
                Box(Modifier.fillMaxSize()) {
                    workoutTab(0) { HomeScreen(idle, {}, {}) }
                    Sheet(true, {}) { OpenSessionStartSheet(idle, {}, {}, {}) }
                }
            }
            OpenSessionManager.reset()
            shot(p, "02-open-session-empty") { workoutTab(0) { OpenSessionScreen(idle, now, { _, _, _, _, _ -> }, { _, _ -> }, {}) } }
            shot(p, "02-open-session-count-sheet") {
                Box(Modifier.fillMaxSize()) {
                    workoutTab(0) { OpenSessionScreen(idle, now, { _, _, _, _, _ -> }, { _, _ -> }, {}) }
                    Sheet(true, {}) { OpenSessionCountSheet({}, {}) }
                }
            }
            shot(p, "02-stretching") { workoutTab(2) { StretchScreen(idle, "after_workout", {}, {}, {}) } }
            shot(p, "02-stretching-full", tall = true) { workoutTab(2) { StretchScreen(stretchy, evening.id, {}, {}, {}) } }
            shot(p, "02-stretching-library", tall = true) { workoutTab(2) { StretchScreen(stretchy, "throwers-shoulder", {}, {}, {}) } }
            shot(p, "28-stretch-routine") { StretchRoutineScreen(evening, {}, {}, {}, {}, {}) }
            shot(p, "28-stretch-routine-edit") { StretchRoutineScreen(evening, {}, {}, {}, {}, {}, initialEditing = true) }
            shot(p, "28-stretch-routine-builtin") { StretchRoutineScreen(StretchRoutines.flexibilityRest(), {}, {}, {}, {}, {}) }
            shot(p, "28-stretch-hold-sheet") {
                Box(Modifier.fillMaxSize()) {
                    StretchRoutineScreen(evening, {}, {}, {}, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.StretchHold(evening.id, 1, 45), stretchy, {}, {}, {}) }
                }
            }
            shot(p, "28-stretch-add-move-sheet") {
                Box(Modifier.fillMaxSize()) {
                    StretchRoutineScreen(evening, {}, {}, {}, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.AddStretchMove(evening.id), stretchy, {}, {}, {}) }
                }
            }
            shot(p, "28-stretch-new-sheet") {
                Box(Modifier.fillMaxSize()) {
                    workoutTab(2) { StretchScreen(stretchy, "after_workout", {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.StretchName(null, "Morning"), stretchy, {}, {}, {}) }
                }
            }
            shot(p, "03-workout-rest") {
                workoutTab(0) { WorkoutScreen(live, now, RestUi(67, 90, restLabel(ex[1], 1)), { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
            shot(p, "04-gzclp-next") { workoutTab(0) { HomeScreen(gz, {}, {}) } }
            for (tall in listOf(false, true)) shot(p, if (tall) "04-gzclp-warmups-full" else "04-gzclp-warmups", tall) {
                workoutTab(0) { WorkoutScreen(gzLive, gzNow, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
            shot(p, "05-starting-weight-sheet") {
                Box(Modifier.fillMaxSize()) {
                    workoutTab(0) { HomeScreen(idle, {}, {}) }
                    val missing = idle.exercise(dev.gzclp.tracker.engine.Slot("face_pull_new", Tier.CYCLE)).copy(name = "Cable crunch")
                    Sheet(true, {}) { SheetContent(SheetState.Weight(missing, 45), idle, {}, {}, {}) }
                }
            }
            shot(p, "06-upcoming-full", tall = true) { TabFrame(Tab.Upcoming, {}) { UpcomingScreen(idle, today, {}) } }
            shot(p, "08-exercises") { TabFrame(Tab.Exercises, {}) { ExercisesScreen(idle, {}, {}, {}, today = Demo.today.minusDays(1)) } }
            shot(p, "09-exercise-detail") { ExerciseScreen(idle, bench, {}, {}) }
            shot(p, "09-exercise-relative") { ExerciseScreen(idle, bench, {}, {}, initialRelative = true) }
            shot(p, "09-exercise-relative-empty") { ExerciseScreen(idle.copy(bodyWeights = emptyMap()), bench, {}, {}, initialRelative = true) }
            shot(p, "08-exercises-full", tall = true) { TabFrame(Tab.Exercises, {}) { ExercisesScreen(idle, {}, {}, {}, today = Demo.today.minusDays(1)) } }
            shot(p, "15-body-weight") { BodyWeightScreen(idle, today, {}, {}, {}) }
            shot(p, "15-body-weight-full", tall = true) { BodyWeightScreen(idle, today, {}, {}, {}) }
            shot(p, "15-body-weight-empty") { BodyWeightScreen(idle.copy(bodyWeights = emptyMap()), today, {}, {}, {}) }
            val lastWeight = idle.weighIns().last()
            shot(p, "16-log-weight-sheet") {
                Box(Modifier.fillMaxSize()) {
                    BodyWeightScreen(idle, today, {}, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.BodyWeight(null, weighInScale(lastWeight, today), now, today).let { it.copy(stop = it.stop - 6) }, idle, {}, {}, {}) }
                }
            }
            shot(p, "16-log-weight-sheet-first") {
                Box(Modifier.fillMaxSize()) {
                    BodyWeightScreen(idle.copy(bodyWeights = emptyMap()), today, {}, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.BodyWeight(null, weighInScale(null, today), now, today), idle, {}, {}, {}) }
                }
            }
            shot(p, "16-edit-weight-sheet") {
                Box(Modifier.fillMaxSize()) {
                    BodyWeightScreen(idle, today, {}, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.BodyWeight(lastWeight.id, weighInScale(lastWeight, lastWeight.at.toLocalDate()), lastWeight.at, today), idle, {}, {}, {}) }
                }
            }
            shot(p, "16-weigh-in-undo") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Exercises, {}) { ExercisesScreen(idle, {}, {}, {}, today = Demo.today.minusDays(1)) }
                    UndoBar("Saved ${Fmt.lb(lastWeight.lb)}", {}, Modifier.padding(bottom = tabBarHeight(true) + Space.lg))
                }
            }
            shot(p, "10-history") { TabFrame(Tab.History, {}) { HistoryScreen(idle, HistoryView.All, {}, {}, {}, today) {} } }
            for (tall in listOf(false, true)) shot(p, if (tall) "10-history-month-full" else "10-history-month", tall) { TabFrame(Tab.History, {}) { HistoryScreen(idle, HistoryView.Month, {}, {}, {}, today) {} } }
            for (tall in listOf(false, true)) shot(p, if (tall) "10-history-week-full" else "10-history-week", tall) {
                TabFrame(Tab.History, {}) { HistoryScreen(idle, HistoryView.Week, {}, {}, {}, today) { WeekView(idle, food, lastWeek, today, {}, {}) } }
            }
            shot(p, "10-history-week-empty") {
                TabFrame(Tab.History, {}) { HistoryScreen(idle, HistoryView.Week, {}, {}, {}, today) { WeekView(idle, food, mondayOf(today).minusDays(70), today, {}, {}) } }
            }
            for (tall in listOf(false, true)) shot(p, if (tall) "10-history-cardio-full" else "10-history-cardio", tall) {
                TabFrame(Tab.History, {}) { HistoryScreen(idle, HistoryView.All, {}, {}, {}, today) {} }
            }
            shot(p, "11-session") { SessionScreen(idle, idle.sessions.last(), {}, {}) }
            shot(p, "11-session-time-sheet") {
                val s = idle.sessions.last()
                Box(Modifier.fillMaxSize()) {
                    SessionScreen(idle, s, {}, {})
                    Sheet(true, {}) { SheetContent(SheetState.SessionTime(s.id, s.startedAt, 22 * 3600L), idle, {}, {}, {}) }
                }
            }
            shot(p, "11-session-left-running") { SessionScreen(idle, idle.sessions.last().let { it.copy(durationSec = 22 * 3600L) }, {}, {}) }
            shot(p, "12-settings") {
                SettingsScreen(
                    true, idle.programId, idle.increments, p.id, null, rest, SyncUi("GZCLP", "Mon, Nov 30, 7:42 AM", null), false, true, 7 * 60, HealthUi(true, null), Plates(),
                    {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
            for (page in SettingsPage.entries) shot(p, "12-settings-${page.name.lowercase()}", tall = page == SettingsPage.Lifting) {
                SettingsScreen(
                    true, idle.programId, idle.increments, p.id, null, rest, SyncUi("GZCLP", "Mon, Nov 30, 7:42 AM", null), false, true, 7 * 60, HealthUi(true, null), Plates(),
                    {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, stretchReminder = true, page = page,
                )
            }
            shot(p, "13-days") { DaysScreen(idle, {}, {}, { _, _ -> }) }
            shot(p, "13-days-edit") { DaysScreen(idle, {}, {}, { _, _ -> }, initialEditing = true) }
            shot(p, "14-workout-full", tall = true) {
                workoutTab(0) { WorkoutScreen(live, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
            shot(p, "17-workout-swapped", tall = true) {
                workoutTab(0) { WorkoutScreen(swapped, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
            shot(p, "17-swap-sheet") {
                Box(Modifier.fillMaxSize()) {
                    workoutTab(0) { WorkoutScreen(swapped, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.Swap(swapped.sessionExercises()[2], "fly"), swapped, {}, {}, {}) }
                }
            }
            shot(p, "17-workout-new-substitute", tall = true) {
                workoutTab(0) { WorkoutScreen(newSub, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
            }
            shot(p, "17-swap-create-sheet") {
                Box(Modifier.fillMaxSize()) {
                    workoutTab(0) { WorkoutScreen(swapped, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.Swap(swapped.sessionExercises()[2], "Incl", create = true), swapped, {}, {}, {}) }
                }
            }
            shot(p, "18-food") { TabFrame(Tab.Food, {}) { FoodScreen(food, today, today, null, {}, {}, {}, {}) } }
            shot(p, "29-water") { WaterScreen(idle, today, WaterLog.goalMl(idle, today, null), pace = false) }
            shot(p, "29-water-rest-day") { today.minusDays(8).let { d -> WaterScreen(idle, d, WaterLog.goalMl(idle, d, null), pace = false) } }
            shot(p, "18-food-empty") { TabFrame(Tab.Food, {}) { FoodScreen(food, today.minusDays(1), today, null, {}, {}, {}, {}) } }
            shot(p, "18-meal-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Food, {}) { FoodScreen(food, today, today, null, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.Meal(food[1], today), idle, {}, {}, {}) }
                }
            }
            shot(p, "18-meal-delete-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Food, {}) { FoodScreen(food, today, today, null, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.Meal(food[1], today, delete = true), idle, {}, {}, {}) }
                }
            }
            shot(p, "18-add-meal-sheet") {
                Box(Modifier.fillMaxSize()) {
                    TabFrame(Tab.Food, {}) { FoodScreen(food, today, today, null, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.AddMeal(food[2].file, now, now, photoTimeNote(null, now)), idle, {}, {}, {}) }
                }
            }
            shot(p, "17-swap-back-sheet") {
                Box(Modifier.fillMaxSize()) {
                    workoutTab(0) { WorkoutScreen(swapped, now, null, { _, _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
                    Sheet(true, {}) { SheetContent(SheetState.Swap(swapped.sessionExercises()[3], ""), swapped, {}, {}, {}) }
                }
            }
        }
        // The sheets above render solid, as the glass does where nothing records a backdrop; the app's own sheets blur the screen under them.
        shot(Palettes.glass, "05-starting-weight-sheet-blur") {
            val b = rememberBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().backdropSource(b)) { workoutTab(0) { HomeScreen(idle, {}, {}) } }
                val missing = idle.exercise(dev.gzclp.tracker.engine.Slot("face_pull_new", Tier.CYCLE)).copy(name = "Cable crunch")
                Sheet(true, {}, b) { SheetContent(SheetState.Weight(missing, 45), idle, {}, {}, {}) }
            }
        }
        shot(Palettes.glass, "16-weigh-in-undo-blur") {
            val b = rememberBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().backdropSource(b)) { TabFrame(Tab.Exercises, {}) { ExercisesScreen(idle, {}, {}, {}, today = Demo.today.minusDays(1)) } }
                UndoBar("Saved ${Fmt.lb(idle.weighIns().last().lb)}", {}, Modifier.padding(bottom = GlassTabBar + Space.xs + Space.sm), b)
            }
        }
    }
}

