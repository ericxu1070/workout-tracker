package dev.gzclp.tracker

import androidx.compose.animation.core.tween
import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import dev.gzclp.tracker.cardio.CardioNotice
import dev.gzclp.tracker.engine.Activity
import dev.gzclp.tracker.health.HealthRecords
import dev.gzclp.tracker.ui.ActivitiesScreen
import dev.gzclp.tracker.ui.CardioRunScreen
import dev.gzclp.tracker.ui.CardioScreen
import dev.gzclp.tracker.ui.cardioEdit
import dev.gzclp.tracker.ui.cardioPast
import dev.gzclp.tracker.ui.durationEndingAt
import dev.gzclp.tracker.ui.withActivity
import java.time.Duration
import dev.gzclp.tracker.log.BodyWeight
import dev.gzclp.tracker.log.BodyWeightLogged
import dev.gzclp.tracker.log.Deletion
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.adaptSessionStartEvents
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.ProgramDefine
import dev.gzclp.tracker.ui.SplitDayScreen
import dev.gzclp.tracker.ui.SplitScreen
import dev.gzclp.tracker.log.weighIns
import dev.gzclp.tracker.log.weighInScale
import dev.gzclp.tracker.remind.WeighIn
import dev.gzclp.tracker.ui.BodyWeightScreen
import dev.gzclp.tracker.ui.Fmt
import dev.gzclp.tracker.ui.Space
import dev.gzclp.tracker.ui.UndoBar
import java.time.LocalDate
import dev.gzclp.tracker.log.SetKey
import dev.gzclp.tracker.log.exerciseLogs
import dev.gzclp.tracker.log.mondayOf
import dev.gzclp.tracker.ui.DaysScreen
import dev.gzclp.tracker.ui.ExerciseScreen
import dev.gzclp.tracker.ui.ExercisesScreen
import dev.gzclp.tracker.ui.FoodScreen
import dev.gzclp.tracker.ui.SessionScreen
import dev.gzclp.tracker.ui.Tab
import dev.gzclp.tracker.ui.homeTab
import dev.gzclp.tracker.ui.TabFrame
import dev.gzclp.tracker.ui.TabHost
import dev.gzclp.tracker.ui.UpcomingScreen
import dev.gzclp.tracker.ui.BackStack
import dev.gzclp.tracker.ui.GzTheme
import dev.gzclp.tracker.ui.GlassTabBar
import dev.gzclp.tracker.ui.LocalPalette
import dev.gzclp.tracker.ui.backdropSource
import dev.gzclp.tracker.ui.rememberBackdrop
import dev.gzclp.tracker.ui.HistoryScreen
import dev.gzclp.tracker.ui.HistoryView
import dev.gzclp.tracker.ui.HomeScreen
import dev.gzclp.tracker.ui.GuidedStretchPlayerScreen
import dev.gzclp.tracker.ui.StretchScreen
import dev.gzclp.tracker.ui.StretchRoutineScreen
import dev.gzclp.tracker.log.StretchRoutineDefine
import dev.gzclp.tracker.stretch.StretchRoutines
import dev.gzclp.tracker.ui.LocalPlates
import dev.gzclp.tracker.ui.LocalShowPlates
import dev.gzclp.tracker.ui.SettingsPage
import dev.gzclp.tracker.ui.SettingsScreen
import dev.gzclp.tracker.ui.SetupScreen
import dev.gzclp.tracker.ui.Sheet
import dev.gzclp.tracker.ui.SheetContent
import dev.gzclp.tracker.ui.SheetState
import dev.gzclp.tracker.ui.WeekView
import dev.gzclp.tracker.ui.WorkoutPages
import dev.gzclp.tracker.ui.WorkoutScreen
import dev.gzclp.tracker.ui.photoTimeNote
import dev.gzclp.tracker.ui.unloggedSets
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val model by lazy { AppModel.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { it.leave(SplashExit.Fade) }
        setContent {
            val palette = model.palette(androidx.compose.foundation.isSystemInDarkTheme())
            LaunchedEffect(palette.dark) {
                val style = if (palette.dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            GzTheme(palette) { CompositionLocalProvider(LocalPlates provides model.plates, LocalShowPlates provides model.showPlates) { App(model) } }
        }
        if (savedInstanceState == null) onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == WeighIn.ACTION_LOG) model.logWeightRequested = true
        if (intent.action == CardioNotice.ACTION_STOP) model.stopCardioRequested = true
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}

/**
 * A screen pushed over the tabs; [arg] is a session or exercise id, for [Activities] "log" or "change" (blank starts a session),
 * a split id for [Split], "split/day" for [SplitDay], a routine's [StretchRoutine.key] for [Stretch] and [StretchRoutine],
 * and nothing for [SettingsPage], which shows the app's settingsPage. [depth] orders them, so going back slides the way it came.
 */
internal enum class Route(val depth: Int) {
    Settings(1), SettingsPage(2), Days(3), Session(1), Exercise(1), BodyWeight(1), Activities(1), Split(3), SplitDay(4), Stretch(2), StretchRoutine(1),
}

/** The weigh-in just saved at [lb] to entry [id]; [before] is that entry before an edit, null for a new one. */
private data class WeighInUndo(val id: String, val before: BodyWeight?, val lb: Double)

/** Where Back from [route] lands: the screen back() opens, and the one a Back swipe shows under the finger. */
internal fun backTo(route: Route?, playerFromPage: Boolean): Route? = when (route) {
    Route.Days, Route.Split -> Route.SettingsPage
    Route.SettingsPage -> Route.Settings
    Route.SplitDay -> Route.Split
    Route.Stretch -> if (playerFromPage) Route.StretchRoutine else null
    else -> null
}

/** A [BackHandler] whose [enabled] is read in this scope alone, so a change to it recomposes only this. */
@Composable
private fun LazyBackHandler(enabled: () -> Boolean, onBack: () -> Unit) {
    BackHandler(enabled(), onBack)
}

@Composable
private fun App(model: AppModel) {
    val state by model.store.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(homeTab(state)) }
    var route by rememberSaveable { mutableStateOf<Route?>(null) }
    var arg by rememberSaveable { mutableStateOf("") }
    // The player was started from its routine's page, so leaving it goes back there.
    var playerFromPage by rememberSaveable { mutableStateOf(false) }
    // Apart from [arg], so leaving a split for its settings page never changes what the split screen sliding out shows.
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Program) }
    var sheet by remember { mutableStateOf<SheetState?>(null) }
    var lastSheet by remember { mutableStateOf<SheetState?>(null) }
    // The row a Delete question is about stays swiped open until the sheet closes.
    val deleting = (sheet as? SheetState.Delete)?.target
    var weighUndo by remember { mutableStateOf<WeighInUndo?>(null) }
    var restoreAfterPick by remember { mutableStateOf(false) }
    var foodDay by rememberSaveable { mutableStateOf<LocalDate?>(null) } // null follows today across midnight
    var historyView by rememberSaveable { mutableStateOf(HistoryView.Week) }
    var historyWeek by rememberSaveable { mutableStateOf<LocalDate?>(null) } // null follows this week across Sunday midnight
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.active != null || state.cardioActive != null) {
        while (state.active != null || state.cardioActive != null) {
            nowMillis = System.currentTimeMillis()
            delay(250)
        }
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
    val healthPermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(HealthRecords.PERMISSIONS)) model.setHealth(true) else model.setHealth(false, "Health Connect access was not allowed, so nothing is written.")
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            model.setFolder(uri)
            if (restoreAfterPick) model.restore() else model.syncLater()
        }
        restoreAfterPick = false
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) model.addMeal() }
    fun snapMeal() {
        foodDay = null
        model.mealError = null
        model.capture.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", model.capture)
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            model.mealError = "No camera app found."
        }
    }
    fun restore() {
        model.notice = null
        if (model.hasFolder()) model.restore() else {
            restoreAfterPick = true
            folderPicker.launch(null)
        }
    }

    fun open(s: SheetState) {
        lastSheet = s
        sheet = s
    }
    fun close() {
        sheet = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) model.importMeal(uri) { taken ->
            val now = model.now()
            val note = photoTimeNote(taken, now)
            open(SheetState.AddMeal(model.upload, if (note == null && taken != null) taken else now, now, note))
        }
    }
    fun uploadMeal() {
        foodDay = null
        model.mealError = null
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    /** The system time picker for a picked photo's meal time. */
    fun pickMealTime(s: SheetState.AddMeal) {
        TimePickerDialog(context, { _, h, m ->
            val c = sheet as? SheetState.AddMeal ?: return@TimePickerDialog
            open(c.copy(at = c.at.withHour(h).withMinute(m).withSecond(0)))
        }, s.at.hour, s.at.minute, DateFormat.is24HourFormat(context)).show()
    }
    fun push(r: Route, a: String = "") {
        arg = a
        route = r
    }
    fun openSettings(page: SettingsPage) {
        settingsPage = page
        route = Route.SettingsPage
    }
    fun requestNotifications() {
        if (!model.notificationsAllowed && Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    /** Opens the weigh-in sheet: a new one on a slider around the latest weight, or [entry] to edit around its own. */
    fun logWeight(entry: BodyWeight? = null) {
        val today = LocalDate.now()
        val scale = if (entry != null) weighInScale(entry, entry.at.toLocalDate()) else weighInScale(model.store.state.value.weighIns().lastOrNull(), today)
        open(SheetState.BodyWeight(entry?.id, scale, entry?.at ?: model.now(), today))
    }
    LaunchedEffect(model.logWeightRequested, state.started) {
        if (model.logWeightRequested && state.started) {
            model.logWeightRequested = false
            route = null
            tab = Tab.Exercises
            logWeight()
        }
    }

    fun startCardio(a: Activity) {
        requestNotifications()
        model.dispatch { Commands.startCardio(it, a, model.now()) }
        route = null
        tab = Tab.Workout
    }
    /** Opens the stop sheet with the running session's time frozen at this tap. */
    fun stopCardio() {
        val run = model.store.state.value.cardioActive ?: return
        open(SheetState.CardioStop(run, Duration.between(run.startedAt, model.now()).seconds.coerceAtMost(Commands.CARDIO_DURATION_SEC.last)))
    }
    fun pickCardioEnd(s: SheetState.CardioStop) {
        val end = s.run.startedAt.plusSeconds(s.durationSec)
        TimePickerDialog(context, { _, h, m ->
            val c = sheet as? SheetState.CardioStop ?: return@TimePickerDialog
            open(c.copy(durationSec = durationEndingAt(c.run.startedAt, h, m, c.maxSec)))
        }, end.hour, end.minute, DateFormat.is24HourFormat(context)).show()
    }
    fun pickCardioStart(s: SheetState.CardioStop) {
        TimePickerDialog(context, { _, h, m ->
            val c = sheet as? SheetState.CardioStop ?: return@TimePickerDialog
            open(c.copy(run = c.run.copy(startedAt = c.run.startedAt.withHour(h).withMinute(m).withSecond(0).withNano(0))))
        }, s.run.startedAt.hour, s.run.startedAt.minute, DateFormat.is24HourFormat(context)).show()
    }
    /** The system time picker for a finished workout's end; an end at or before its start means the next day. */
    fun pickSessionEnd(s: SheetState.SessionTime) {
        val end = s.startedAt.plusSeconds(s.durationSec)
        TimePickerDialog(context, { _, h, m ->
            val c = sheet as? SheetState.SessionTime ?: return@TimePickerDialog
            open(c.withDuration(durationEndingAt(c.startedAt, h, m, Commands.SESSION_DURATION_SEC.last)))
        }, end.hour, end.minute, DateFormat.is24HourFormat(context)).show()
    }
    /** Leaves the activity picker; changing a sheet's activity goes back to that sheet, with [a] when one was picked. */
    fun closeActivities(a: Activity? = null) {
        val draft = lastSheet as? SheetState.CardioStop
        route = null
        when {
            arg == "log" && a != null -> open(cardioPast(a, model.now()))
            arg == "change" && draft != null -> open(if (a == null) draft else draft.withActivity(a))
            a != null -> startCardio(a)
        }
    }
    LaunchedEffect(model.stopCardioRequested, state.cardioActive) {
        if (!model.stopCardioRequested) return@LaunchedEffect
        model.stopCardioRequested = false
        if (state.cardioActive == null) return@LaunchedEffect
        route = null
        tab = Tab.Workout
        stopCardio()
    }

    /** The weight sheet for [ex]; a missing starting weight is prefilled with an estimate from its recent sets. */
    fun weightSheet(ex: Exercise) = SheetState.Weight(ex, ex.weight ?: Commands.estimate(model.store.state.value, ex, model.plates) ?: 45)

    /** Asks for each missing starting weight in turn, then starts the workout. */
    fun startFlow() {
        val missing = Commands.missingWeight(model.store.state.value)
        if (missing != null) return open(weightSheet(missing))
        close()
        requestNotifications()
        model.dispatch { s ->
            val adapt = if (model.prefs.autoAdaptWeights) s.adaptSessionStartEvents(s.nextDay) else emptyList()
            adapt + Commands.start(adapt.fold(s) { st, e -> st.apply(Entry(0, model.now(), e)) }, model.now())
        }
    }
    /** Open sessions pick their own weights, so nothing is missing. */
    fun startOpenFlow() {
        close()
        requestNotifications()
        model.dispatch { Commands.startOpen(it, model.now()) }
    }
    fun finishWorkout(fiveRm: Int?, counts: Boolean? = null) {
        model.dispatch { Commands.end(it, model.now(), fiveRm, counts) }
        model.cancelRest()
        close()
    }
    fun endFlow() {
        val pending = Commands.pendingFiveRm(state)
        if (pending != null) open(SheetState.FiveRm(pending, pending.weight ?: 0)) else finishWorkout(null)
    }

    fun back() {
        val r = route
        when {
            sheet != null -> close()
            r == null -> tab = Tab.Exercises
            r == Route.Activities -> closeActivities()
            else -> {
                if (r == Route.Days || r == Route.Split) settingsPage = SettingsPage.Program
                if (r == Route.SplitDay) arg = arg.substringBefore('/')
                route = backTo(r, playerFromPage)
            }
        }
    }
    // Read in its own scope: a tab switch would otherwise recompose all of this, and every kept tab's screen with it.
    // A pushed screen's pop is the [BackStack]'s, which shows it under the finger.
    LazyBackHandler({ sheet != null || route != null || tab != Tab.Exercises }, ::back)

    Box(Modifier.fillMaxSize()) {
        // In Liquid Glass the sheet and the undo bar blur the screen under them.
        val backdrop = rememberBackdrop()
        BackStack(
            route, { it?.depth ?: 0 }, { sheet == null && route != null }, { backTo(route, playerFromPage) }, ::back,
            Modifier.backdropSource(backdrop),
        ) { shown ->
            val session = state.sessions.firstOrNull { it.id == arg }
            val log = if (shown == Route.Exercise) state.exerciseLogs().firstOrNull { it.id == arg } else null
            val run = state.cardioActive
            val split = state.customPrograms[arg.substringBefore('/')]?.takeIf { shown == Route.Split || shown == Route.SplitDay }
            val splitDay = split?.day(arg.substringAfter('/', ""))
            // Held for the whole session, so a log change mid-routine cannot rebuild After workout under the player.
            val stretch = if (shown == Route.Stretch) remember(arg) { StretchRoutines.all(state).firstOrNull { it.key == arg } } else null
            val routine = if (shown == Route.StretchRoutine) remember(state, arg) { StretchRoutines.all(state).firstOrNull { it.key == arg } } else null
            when {
                !state.started && shown != Route.Settings && shown != Route.SettingsPage -> SetupScreen(
                    onStart = { rm, program -> model.dispatch { if (it.started) emptyList() else Commands.setup(rm, program) } },
                    onRestore = ::restore, message = model.notice,
                )
                shown == Route.Settings || shown == Route.SettingsPage -> SettingsScreen(
                    started = state.started, programId = state.programId, increments = state.increments,
                    themeId = model.theme, laptop = model.laptop, restTargets = model.restTargets, glass = model.glass, onGlass = model::chooseGlass,
                    sync = model.sync.copy(notice = model.notice), notificationsAllowed = model.notificationsAllowed,
                    weighInReminder = model.weighInReminder, weighInMinutes = model.weighInMinutes, health = model.health, plates = model.plates,
                    onBack = { route = if (shown == Route.SettingsPage) Route.Settings else null }, onTheme = model::chooseTheme, onRest = model::setRest,
                    onProgram = { id -> model.dispatch { Commands.chooseProgram(it, id) } },
                    onIncrements = { inc -> model.dispatch { Commands.setIncrements(it, inc) } },
                    onDays = { if (state.program.custom) push(Route.Split, state.programId) else route = Route.Days }, onChooseFolder = { folderPicker.launch(null) },
                    onRestore = ::restore, onAllowNotifications = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onWeighInReminder = {
                        model.chooseWeighInReminder(it)
                        if (it) requestNotifications()
                    },
                    onWeighInTime = model::chooseWeighInTime, onPlates = model::choosePlates,
                    showPlates = model.showPlates, onShowPlates = model::chooseShowPlates, bodyFemale = model.bodyFemale, onBodyFemale = model::chooseBodyFemale, bodyBuild = model.bodyBuild, onBodyBuild = model::chooseBodyBuild,
                    warmups = model.warmups, onWarmups = model::chooseWarmups,
                    cardioGoalMin = model.cardioGoalMin, onCardioGoal = model::chooseCardioGoal,
                    splits = state.customPrograms.values.toList(), onOpenSplit = { push(Route.Split, it) }, onNewSplit = { open(SheetState.NewSplit()) },
                    autoAdaptWeights = model.autoAdaptWeights, onAutoAdaptWeights = model::chooseAutoAdaptWeights,
                    mealReminder = model.mealReminder, onMealReminder = { model.chooseMealReminder(it); if (it) requestNotifications() },
                    stretchReminder = model.stretchReminder, stretchReminderMinutes = model.stretchReminderMinutes,
                    onStretchReminder = { model.chooseStretchReminder(it); if (it) requestNotifications() }, onStretchReminderTime = model::chooseStretchReminderTime,
                    stretchRoutine = model.stretchRoutine, onStretchRoutine = model::chooseStretchRoutine, customStretches = state.customStretches.values.toList(),
                    stretchSound = model.stretchSound, onStretchSound = model::chooseStretchSound,
                    stretchHaptics = model.stretchHaptics, onStretchHaptics = model::chooseStretchHaptics,
                    waterReminder = model.waterReminder, onWaterReminder = { model.chooseWaterReminder(it); if (it) requestNotifications() },
                    saveToGallery = model.saveToGallery, onSaveToGallery = model::chooseSaveToGallery,
                    waterGoalMl = model.waterGoalMl(state, LocalDate.now()), onWaterGoal = model::chooseWaterGoal,
                    page = settingsPage.takeIf { shown == Route.SettingsPage }, onPage = ::openSettings,
                    onHealthConnect = { on ->
                        val unavailable = if (on) model.healthUnavailable() else null
                        if (on && unavailable == null) healthPermissions.launch(HealthRecords.PERMISSIONS) else model.setHealth(false, unavailable)
                    },
                )
                shown == Route.Days -> DaysScreen(
                    state, { openSettings(SettingsPage.Program) },
                    onAdd = { open(SheetState.AddExercise(it, "")) },
                    onRemove = { day, id -> model.dispatch { s -> Commands.removeExercise(s, s.program, day, id) } },
                )
                shown == Route.Split && split != null -> SplitScreen(
                    state, split, { openSettings(SettingsPage.Program) }, { push(Route.SplitDay, "${split.id}/$it") }, ::open, model::dispatch,
                )
                shown == Route.SplitDay && split != null && splitDay != null -> SplitDayScreen(
                    state, split, splitDay, { push(Route.Split, split.id) }, ::open, model::dispatch,
                )
                shown == Route.Session && session != null -> SessionScreen(
                    state, session, { route = null }, { open(SheetState.SessionTime(session.id, session.startedAt, session.durationSec)) },
                    deleting, { open(SheetState.Delete(it)) },
                )
                shown == Route.Exercise && log != null -> ExerciseScreen(state, log, { route = null }, { logWeight() }, onSaveMeta = { meta -> model.dispatch { listOf(meta) } })
                shown == Route.BodyWeight -> BodyWeightScreen(state, LocalDate.now(), { route = null }, { logWeight() }, ::logWeight, deleting) { open(SheetState.Delete(it)) }
                shown == Route.Stretch && stretch != null -> GuidedStretchPlayerScreen(
                    stretch, onBack = { if (playerFromPage) push(Route.StretchRoutine, arg) else route = null },
                    onFinish = { logged -> model.dispatch { listOf(logged) }; if (playerFromPage) push(Route.StretchRoutine, arg) else route = null },
                    sound = model.stretchSound, haptics = model.stretchHaptics,
                )
                shown == Route.StretchRoutine && routine != null -> StretchRoutineScreen(
                    routine, onBack = { route = null }, onStart = { playerFromPage = true; push(Route.Stretch, routine.key) },
                    onDuplicate = {
                        var id: String? = null
                        val name = "My " + routine.name
                        model.dispatch { s -> Commands.newStretchRoutine(s, name, routine.moves).also { e -> id = e.filterIsInstance<StretchRoutineDefine>().firstOrNull()?.routine } }
                        id?.let { push(Route.StretchRoutine, it) }
                    },
                    open = ::open, edit = model::dispatch,
                )
                shown == Route.Activities -> ActivitiesScreen(
                    state, { closeActivities() }, ::closeActivities,
                    title = when (arg) { "log" -> "Log past session"; "change" -> "Activity"; else -> "Start cardio" },
                )
                else -> TabHost({ tab }, { tab = it }) { t ->
                        when (t) {
                            Tab.Workout -> if (run != null) CardioRunScreen(
                                run, java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(),
                                onSettings = { push(Route.Settings) }, onDiscard = { open(SheetState.CardioDiscard(run)) }, onStop = ::stopCardio,
                            ) else WorkoutPages(model.workoutMode, model::chooseWorkoutMode, onSettings = { push(Route.Settings) }) { page ->
                                when (page) {
                                    1 -> CardioScreen(
                                        state, LocalDate.now(), model.cardioGoalMin,
                                        onStart = { push(Route.Activities) }, onQuick = ::startCardio,
                                        onLogPast = { push(Route.Activities, "log") }, onEdit = { open(cardioEdit(it, model.now())) }, lifting = state.active != null,
                                    )
                                    2 -> StretchScreen(
                                        state, model.stretchRoutine,
                                        onStartRoutine = { playerFromPage = false; push(Route.Stretch, it.key) },
                                        onOpenRoutine = { push(Route.StretchRoutine, it.key) }, onNewRoutine = { open(SheetState.StretchName(null, "")) },
                                    )
                                    else -> if (state.active == null) HomeScreen(
                                        state, onStart = ::startFlow,
                                        onEditWeight = { open(weightSheet(it)) },
                                                                                onUseSuggestion = { ex, w -> model.dispatch { Commands.editWeight(it, ex, w) } },
                                        onStartOpen = ::startOpenFlow,
                                    ) else WorkoutScreen(
                                        state, java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(), model.rest(nowMillis),
                                        onLog = { ex, i, w, r, rpe ->
                                            val wasLogged = state.active?.sets?.containsKey(SetKey(ex.id, ex.tier, i)) == true
                                            model.dispatch { Commands.logSet(it, ex, i, r, w, model.now(), model.restTargets, rpe) }
                                            if (!wasLogged) model.startRest(ex, i)
                                        },
                                        onClear = { ex, i ->
                                            model.dispatch { Commands.clearSet(it, ex, i) }
                                            model.cancelRestFor(ex, i)
                                        },
                                        onEditWeight = { open(weightSheet(it)) },
                                        onSwap = { open(SheetState.Swap(it, "")) },
                                        onSkipRest = model::cancelRest,
                                        onEnd = {
                                            val n = unloggedSets(state)
                                            if (n > 0) open(SheetState.EndConfirm(n)) else endFlow()
                                        },
                                        warmups = model.warmups,
                                        onUseSuggestion = { ex, w -> model.dispatch { Commands.editWeight(it, ex, w) } },
                                        onFinishOpen = { counts -> if (counts) endFlow() else finishWorkout(null, counts = false) },
                                    )
                                }
                            }
                            Tab.Upcoming -> UpcomingScreen(
                                state, java.time.LocalDate.now(), onOpenSession = { push(Route.Session, it) },
                                onUseSuggestion = { ex, w -> model.dispatch { Commands.editWeight(it, ex, w) } },
                            )
                            Tab.Exercises -> ExercisesScreen(state, onOpen = { push(Route.Exercise, it) }, onBodyWeight = { push(Route.BodyWeight) }, onLogWeight = { logWeight() }, female = model.bodyFemale)
                            Tab.History -> HistoryScreen(
                                state, historyView, { historyView = it }, onOpen = { push(Route.Session, it) }, onCardio = { open(cardioEdit(it, model.now())) },
                                deleting = deleting, onDelete = { open(SheetState.Delete(it)) },
                            ) {
                                val today = LocalDate.now()
                                val thisWeek = mondayOf(today)
                                WeekView(
                                    state, model.meals, historyWeek ?: thisWeek, today,
                                    onWeek = { historyWeek = it.takeIf { m -> m < thisWeek } }, onExercise = { push(Route.Exercise, it) },
                                )
                            }
                            Tab.Food -> {
                                val today = LocalDate.now()
                                FoodScreen(
                                    model.meals, foodDay ?: today, today, model.mealError,
                                    onDay = { foodDay = it.takeIf { d -> d < today } }, onSnap = ::snapMeal, onUpload = ::uploadMeal,
                                    onOpen = { open(SheetState.Meal(it, today)) },
                                )
                            }
                        } }
            }
        }
        LaunchedEffect(weighUndo) {
            delay(4000)
            weighUndo = null
        }
        UndoBar(
            weighUndo?.let { "Saved ${Fmt.lb(it.lb)}" },
            onUndo = {
                weighUndo?.let { u -> model.dispatch { Commands.undoBodyWeight(it, u.id, u.before) } }
                weighUndo = null
            },
            // Clears the tab bar where there is one.
            Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = if (route != null) Space.lg else if (LocalPalette.current.glass) GlassTabBar + Space.xs + Space.sm else 64.dp),
            backdrop,
        )
        Sheet(sheet != null, ::close, backdrop) {
            val s = sheet ?: lastSheet ?: return@Sheet
            SheetContent(
                s, state, update = { sheet = it; lastSheet = it }, onDismiss = ::close, onPickTime = ::pickMealTime, onPickEnd = ::pickCardioEnd,
                onPickStart = ::pickCardioStart, onPickSessionEnd = ::pickSessionEnd, onActivity = { close(); push(Route.Activities, "change") },
                onConfirm = { c ->
                    when (c) {
                        is SheetState.Weight -> {
                            model.dispatch { Commands.editWeight(it, c.ex, c.weight) }
                            if (c.starting && state.active == null && tab == Tab.Workout && route == null) startFlow() else close()
                        }
                        is SheetState.FiveRm -> finishWorkout(c.fiveRm)
                        is SheetState.EndConfirm -> endFlow()
                        is SheetState.BodyWeight -> {
                            if (c.delete && c.id != null) model.dispatchWeighIn { Commands.clearBodyWeight(it, c.id) }
                            else model.dispatchWeighIn { s ->
                                Commands.logBodyWeight(s, c.id, c.at, c.lb).also { e ->
                                    e.filterIsInstance<BodyWeightLogged>().firstOrNull()?.let { weighUndo = WeighInUndo(it.entryId, s.bodyWeights[it.entryId], it.weight) }
                                }
                            }
                            close()
                            if (model.weighInReminder) requestNotifications()
                        }
                        is SheetState.Delete -> {
                            val t = c.target
                            // Its last exercise gone, the workout's page has nothing left to show.
                            if (t is Deletion.Exercise && state.sessions.firstOrNull { it.id == t.sessionId }?.sets?.all { it.exercise == t.exercise && it.tier == t.tier } == true) route = null
                            model.dispatch { Commands.delete(it, t) }
                            close()
                        }
                        is SheetState.Swap -> {
                            model.dispatch { Commands.swap(it, c.ex, c.name) }
                            close()
                        }
                        is SheetState.Meal -> {
                            model.deleteMeal(c.meal)
                            close()
                        }
                        is SheetState.AddMeal -> {
                            model.addMeal(c.photo, c.at)
                            foodDay = c.at.toLocalDate().takeIf { it < LocalDate.now() }
                            close()
                        }
                        is SheetState.AddExercise -> {
                            model.dispatch { if (c.split != null) Commands.addSplitExercise(it, c.split, c.day.id, c.name) else Commands.addExercise(it, it.program, c.day, c.name) }
                            close()
                        }
                        is SheetState.NewSplit -> {
                            val name = "My " + Programs.of(c.from).name.replaceFirstChar(Char::lowercase)
                            var id: String? = null
                            model.dispatch { s -> Commands.copySplit(s, c.from, name).also { e -> id = e.filterIsInstance<ProgramDefine>().firstOrNull()?.program } }
                            close()
                            id?.let { push(Route.Split, it) }
                        }
                        is SheetState.SplitName -> {
                            model.dispatch {
                                when {
                                    c.add -> Commands.addDay(it, c.split, c.name)
                                    c.day != null -> Commands.renameDay(it, c.split, c.day, c.name)
                                    else -> Commands.renameSplit(it, c.split, c.name)
                                }
                            }
                            close()
                        }
                        is SheetState.SplitWave -> {
                            model.dispatch {
                                if (c.ex == null || c.day == null) Commands.setAll(it, c.split, c.wave) else Commands.setWave(it, c.split, c.day, c.ex, c.wave)
                            }
                            close()
                        }
                        is SheetState.DeleteSplit -> {
                            if (c.day == null) {
                                model.dispatch { Commands.deleteSplit(it, c.split) }
                                openSettings(SettingsPage.Program)
                            } else {
                                model.dispatch { Commands.removeDay(it, c.split, c.day) }
                                push(Route.Split, c.split)
                            }
                            close()
                        }
                        is SheetState.StretchName -> {
                            if (c.routine == null) {
                                var id: String? = null
                                model.dispatch { s -> Commands.newStretchRoutine(s, c.name).also { e -> id = e.filterIsInstance<StretchRoutineDefine>().firstOrNull()?.routine } }
                                id?.let { push(Route.StretchRoutine, it) }
                            } else model.dispatch { Commands.renameStretchRoutine(it, c.routine, c.name) }
                            close()
                        }
                        is SheetState.StretchHold -> {
                            model.dispatch { Commands.setStretchHold(it, c.routine, c.index, c.sec) }
                            close()
                        }
                        is SheetState.AddStretchMove -> {
                            model.dispatch { Commands.addStretchMove(it, c.routine, c.move) }
                            close()
                        }
                        is SheetState.DeleteStretchRoutine -> {
                            model.dispatch { Commands.deleteStretchRoutine(it, c.routine) }
                            route = null
                            close()
                        }
                        is SheetState.CardioStop -> {
                            if (c.now == null) model.dispatch { Commands.stopCardio(it, model.now(), c.durationSec, c.distanceM, c.rpeValue, c.notes) }
                            else model.dispatch { Commands.logCardio(it, c.run.id.ifEmpty { null }, Activity(c.run.activity, c.run.name), c.run.startedAt, c.durationSec, c.distanceM, c.rpeValue, c.notes, model.now()) }
                            close()
                        }
                        is SheetState.SessionTime -> {
                            model.dispatch { Commands.editSession(it, c.id, c.durationSec) }
                            close()
                        }
                        is SheetState.CardioDiscard -> {
                            model.dispatch { Commands.clearCardio(it, c.run.id) }
                            close()
                        }
                    }
                },
            )
        }
    }
}
