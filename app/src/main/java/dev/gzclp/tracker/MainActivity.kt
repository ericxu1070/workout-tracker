package dev.gzclp.tracker

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
import androidx.compose.ui.text.TextRange
import androidx.core.content.FileProvider
import androidx.compose.ui.text.input.TextFieldValue
import androidx.health.connect.client.PermissionController
import dev.gzclp.tracker.cardio.CardioNotice
import dev.gzclp.tracker.engine.Activity
import dev.gzclp.tracker.health.HealthRecords
import dev.gzclp.tracker.ui.ActivitiesScreen
import dev.gzclp.tracker.ui.CardioRunScreen
import dev.gzclp.tracker.ui.CardioScreen
import dev.gzclp.tracker.ui.durationEndingAt
import java.time.Duration
import dev.gzclp.tracker.log.BodyWeight
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.weighIns
import dev.gzclp.tracker.remind.WeighIn
import dev.gzclp.tracker.ui.BodyWeightScreen
import dev.gzclp.tracker.ui.Fmt
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
import dev.gzclp.tracker.ui.TabFrame
import dev.gzclp.tracker.ui.UpcomingScreen
import dev.gzclp.tracker.ui.GzTheme
import dev.gzclp.tracker.ui.HistoryScreen
import dev.gzclp.tracker.ui.HomeScreen
import dev.gzclp.tracker.ui.LocalPlates
import dev.gzclp.tracker.ui.SettingsScreen
import dev.gzclp.tracker.ui.SetupScreen
import dev.gzclp.tracker.ui.Sheet
import dev.gzclp.tracker.ui.SheetContent
import dev.gzclp.tracker.ui.SheetState
import dev.gzclp.tracker.ui.WeekView
import dev.gzclp.tracker.ui.WorkoutScreen
import dev.gzclp.tracker.ui.photoTimeNote
import dev.gzclp.tracker.ui.unloggedSets
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val model by lazy { AppModel.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val palette = model.palette
            LaunchedEffect(palette.dark) {
                val style = if (palette.dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            GzTheme(palette) { CompositionLocalProvider(LocalPlates provides model.plates) { App(model) } }
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

/** A screen pushed over the tabs; [arg] is a session or exercise id. */
private enum class Route { Settings, Days, Session, Exercise, BodyWeight, Activities }

@Composable
private fun App(model: AppModel) {
    val state by model.store.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Workout) }
    var route by rememberSaveable { mutableStateOf<Route?>(null) }
    var arg by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf<SheetState?>(null) }
    var lastSheet by remember { mutableStateOf<SheetState?>(null) }
    var restoreAfterPick by remember { mutableStateOf(false) }
    var foodDay by rememberSaveable { mutableStateOf<LocalDate?>(null) } // null follows today across midnight
    var historyAll by rememberSaveable { mutableStateOf(false) }
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
    fun requestNotifications() {
        if (!model.notificationsAllowed && Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    /** Opens the weigh-in sheet: a new one prefilled with the latest weight, or [entry] to edit. */
    fun logWeight(entry: BodyWeight? = null) {
        val text = (entry ?: model.store.state.value.weighIns().lastOrNull())?.lb?.let(Fmt::tenths) ?: ""
        open(SheetState.BodyWeight(entry?.id, TextFieldValue(text, TextRange(0, text.length)), entry?.at ?: model.now(), LocalDate.now()))
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
    LaunchedEffect(model.stopCardioRequested, state.cardioActive) {
        if (!model.stopCardioRequested) return@LaunchedEffect
        model.stopCardioRequested = false
        if (state.cardioActive == null) return@LaunchedEffect
        route = null
        tab = Tab.Workout
        stopCardio()
    }

    /** Asks for each missing starting weight in turn, then starts the workout. */
    fun startFlow() {
        val missing = Commands.missingWeight(model.store.state.value)
        if (missing != null) return open(SheetState.Weight(missing, 45))
        close()
        requestNotifications()
        model.dispatch { Commands.start(it, model.now()) }
    }
    fun finishWorkout(fiveRm: Int?) {
        model.dispatch { Commands.end(it, model.now(), fiveRm) }
        model.cancelRest()
        close()
    }
    fun endFlow() {
        val pending = Commands.pendingFiveRm(state)
        if (pending != null) open(SheetState.FiveRm(pending, pending.weight ?: 0)) else finishWorkout(null)
    }

    BackHandler(sheet != null || route != null || tab != Tab.Workout) {
        when {
            sheet != null -> close()
            route == Route.Days -> route = Route.Settings
            route != null -> route = null
            else -> tab = Tab.Workout
        }
    }

    Box(Modifier.fillMaxSize()) {
        val session = state.sessions.firstOrNull { it.id == arg }
        val log = state.exerciseLogs().firstOrNull { it.id == arg }
        val run = state.cardioActive
        when {
            !state.started && route != Route.Settings -> SetupScreen(
                onStart = { rm, program -> model.dispatch { if (it.started) emptyList() else Commands.setup(rm, program) } },
                onRestore = ::restore, message = model.notice,
            )
            route == Route.Settings -> SettingsScreen(
                started = state.started, programId = state.programId, increments = state.increments,
                themeId = model.theme, laptop = model.laptop, restTargets = model.restTargets,
                sync = model.sync.copy(error = model.notice ?: model.sync.error), notificationsAllowed = model.notificationsAllowed,
                weighInReminder = model.weighInReminder, weighInMinutes = model.weighInMinutes, health = model.health, plates = model.plates,
                onBack = { route = null }, onTheme = model::chooseTheme, onRest = model::setRest,
                onProgram = { id -> model.dispatch { Commands.chooseProgram(it, id) } },
                onIncrements = { inc -> model.dispatch { Commands.setIncrements(it, inc) } },
                onDays = { push(Route.Days) }, onChooseFolder = { folderPicker.launch(null) },
                onRestore = ::restore, onAllowNotifications = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onWeighInReminder = {
                    model.chooseWeighInReminder(it)
                    if (it) requestNotifications()
                },
                onWeighInTime = model::chooseWeighInTime, onPlates = model::choosePlates,
                warmups = model.warmups, onWarmups = model::chooseWarmups,
                cardioGoalMin = model.cardioGoalMin, onCardioGoal = model::chooseCardioGoal,
                onHealthConnect = { on ->
                    val unavailable = if (on) model.healthUnavailable() else null
                    if (on && unavailable == null) healthPermissions.launch(HealthRecords.PERMISSIONS) else model.setHealth(false, unavailable)
                },
            )
            route == Route.Days -> DaysScreen(
                state, { route = Route.Settings },
                onAdd = { open(SheetState.AddExercise(it, "")) },
                onRemove = { day, id -> model.dispatch { s -> Commands.removeExercise(s, s.program, day, id) } },
            )
            route == Route.Session && session != null -> SessionScreen(state, session) { route = null }
            route == Route.Exercise && log != null -> ExerciseScreen(state, log, { route = null }, { logWeight() })
            route == Route.BodyWeight -> BodyWeightScreen(state, LocalDate.now(), { route = null }, { logWeight() }, ::logWeight)
            route == Route.Activities -> ActivitiesScreen(state, { route = null }, ::startCardio)
            else -> TabFrame(tab, { tab = it }) {
                    when (tab) {
                        Tab.Workout -> if (run != null) CardioRunScreen(
                            run, java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(),
                            onSettings = { push(Route.Settings) }, onDiscard = { open(SheetState.CardioDiscard(run)) }, onStop = ::stopCardio,
                        ) else if (state.active == null && model.cardioMode) CardioScreen(
                            state, LocalDate.now(), model.cardioGoalMin, onLifting = { model.chooseCardioMode(false) },
                            onSettings = { push(Route.Settings) }, onStart = { push(Route.Activities) }, onQuick = ::startCardio,
                        ) else if (state.active == null) HomeScreen(
                            state, onStart = ::startFlow, onSettings = { push(Route.Settings) },
                            onEditWeight = { open(SheetState.Weight(it, it.weight ?: 45)) },
                            onCardio = { model.chooseCardioMode(true) },
                        ) else WorkoutScreen(
                            state, java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(), model.rest(nowMillis),
                            onLog = { ex, i, w, r, rpe ->
                                val wasLogged = state.active?.sets?.containsKey(SetKey(ex.id, ex.tier, i)) == true
                                model.dispatch { Commands.logSet(it, ex, i, r, w, model.now(), model.restTargets, rpe) }
                                if (!wasLogged) model.startRest(ex, i)
                            },
                            onClear = { ex, i -> model.dispatch { Commands.clearSet(it, ex, i) } },
                            onEditWeight = { open(SheetState.Weight(it, it.weight ?: 45)) },
                            onSwap = { open(SheetState.Swap(it, "")) },
                            onSkipRest = model::cancelRest,
                            onSettings = { push(Route.Settings) },
                            onEnd = {
                                val n = unloggedSets(state)
                                if (n > 0) open(SheetState.EndConfirm(n)) else endFlow()
                            },
                            warmups = model.warmups,
                        )
                        Tab.Upcoming -> UpcomingScreen(state, java.time.LocalDate.now(), onOpenSession = { push(Route.Session, it) })
                        Tab.Exercises -> ExercisesScreen(state, onOpen = { push(Route.Exercise, it) }, onBodyWeight = { push(Route.BodyWeight) }, onLogWeight = { logWeight() })
                        Tab.History -> HistoryScreen(state, historyAll, { historyAll = it }, onOpen = { push(Route.Session, it) }) {
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
                    }
            }
        }
        Sheet(sheet != null, ::close) {
            val s = sheet ?: lastSheet ?: return@Sheet
            SheetContent(
                s, state, update = { sheet = it; lastSheet = it }, onDismiss = ::close, onPickTime = ::pickMealTime, onPickEnd = ::pickCardioEnd,
                onConfirm = { c ->
                    when (c) {
                        is SheetState.Weight -> {
                            model.dispatch { Commands.editWeight(it, c.ex, c.weight) }
                            if (c.starting && state.active == null && tab == Tab.Workout && route == null) startFlow() else close()
                        }
                        is SheetState.FiveRm -> finishWorkout(c.fiveRm)
                        is SheetState.EndConfirm -> endFlow()
                        is SheetState.BodyWeight -> {
                            val lb = c.lb
                            when {
                                c.delete && c.id != null -> model.dispatchWeighIn { Commands.clearBodyWeight(it, c.id) }
                                lb != null -> model.dispatchWeighIn { Commands.logBodyWeight(it, c.id, c.at, lb) }
                            }
                            close()
                            if (model.weighInReminder) requestNotifications()
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
                            model.dispatch { Commands.addExercise(it, it.program, c.day, c.name) }
                            close()
                        }
                        is SheetState.CardioStop -> {
                            model.dispatch { Commands.stopCardio(it, model.now(), c.durationSec, c.distanceM, c.rpeValue, c.notes) }
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
