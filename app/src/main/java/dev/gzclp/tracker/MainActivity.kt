package dev.gzclp.tracker

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.health.connect.client.PermissionController
import dev.gzclp.tracker.health.HealthRecords
import dev.gzclp.tracker.log.BodyWeight
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.weighIns
import dev.gzclp.tracker.remind.WeighIn
import dev.gzclp.tracker.ui.BodyWeightScreen
import dev.gzclp.tracker.ui.Fmt
import java.time.LocalDate
import dev.gzclp.tracker.log.SetKey
import dev.gzclp.tracker.log.exerciseLogs
import dev.gzclp.tracker.ui.DaysScreen
import dev.gzclp.tracker.ui.ExerciseScreen
import dev.gzclp.tracker.ui.ExercisesScreen
import dev.gzclp.tracker.ui.SessionScreen
import dev.gzclp.tracker.ui.Tab
import dev.gzclp.tracker.ui.TabFrame
import dev.gzclp.tracker.ui.UpcomingScreen
import dev.gzclp.tracker.ui.GzTheme
import dev.gzclp.tracker.ui.HistoryScreen
import dev.gzclp.tracker.ui.HomeScreen
import dev.gzclp.tracker.ui.SettingsScreen
import dev.gzclp.tracker.ui.SetupScreen
import dev.gzclp.tracker.ui.Sheet
import dev.gzclp.tracker.ui.SheetContent
import dev.gzclp.tracker.ui.SheetState
import dev.gzclp.tracker.ui.WorkoutScreen
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
            GzTheme(palette) { App(model) }
        }
        if (savedInstanceState == null) onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == WeighIn.ACTION_LOG) model.logWeightRequested = true
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}

/** A screen pushed over the tabs; [arg] is a session or exercise id. */
private enum class Route { Settings, Days, Session, Exercise, BodyWeight }

@Composable
private fun App(model: AppModel) {
    val state by model.store.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Workout) }
    var route by rememberSaveable { mutableStateOf<Route?>(null) }
    var arg by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf<SheetState?>(null) }
    var lastSheet by remember { mutableStateOf<SheetState?>(null) }
    var restoreAfterPick by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.active != null) {
        while (state.active != null) {
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
        when {
            !state.started && route != Route.Settings -> SetupScreen(
                onStart = { rm, program -> model.dispatch { if (it.started) emptyList() else Commands.setup(rm, program) } },
                onRestore = ::restore, message = model.notice,
            )
            route == Route.Settings -> SettingsScreen(
                started = state.started, programId = state.programId, increments = state.increments,
                themeId = model.theme, laptop = model.laptop, restTargets = model.restTargets,
                sync = model.sync.copy(error = model.notice ?: model.sync.error), notificationsAllowed = model.notificationsAllowed,
                weighInReminder = model.weighInReminder, weighInMinutes = model.weighInMinutes, health = model.health,
                onBack = { route = null }, onTheme = model::chooseTheme, onRest = model::setRest,
                onProgram = { id -> model.dispatch { Commands.chooseProgram(it, id) } },
                onIncrements = { inc -> model.dispatch { Commands.setIncrements(it, inc) } },
                onDays = { push(Route.Days) }, onChooseFolder = { folderPicker.launch(null) },
                onRestore = ::restore, onAllowNotifications = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onWeighInReminder = {
                    model.chooseWeighInReminder(it)
                    if (it) requestNotifications()
                },
                onWeighInTime = model::chooseWeighInTime,
                warmups = model.warmups, onWarmups = model::chooseWarmups,
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
            else -> TabFrame(tab, { tab = it }) {
                    when (tab) {
                        Tab.Workout -> if (state.active == null) HomeScreen(
                            state, onStart = ::startFlow, onSettings = { push(Route.Settings) },
                            onEditWeight = { open(SheetState.Weight(it, it.weight ?: 45)) },
                        ) else WorkoutScreen(
                            state, java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(), model.rest(nowMillis),
                            onLog = { ex, i, w, r ->
                                val wasLogged = state.active?.sets?.containsKey(SetKey(ex.id, ex.tier, i)) == true
                                model.dispatch { Commands.logSet(it, ex, i, r, w, model.now(), model.restTargets) }
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
                        Tab.History -> HistoryScreen(state, onOpen = { push(Route.Session, it) })
                    }
            }
        }
        Sheet(sheet != null, ::close) {
            val s = sheet ?: lastSheet ?: return@Sheet
            SheetContent(
                s, state, update = { sheet = it; lastSheet = it }, onDismiss = ::close,
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
                        is SheetState.AddExercise -> {
                            model.dispatch { Commands.addExercise(it, it.program, c.day, c.name) }
                            close()
                        }
                    }
                },
            )
        }
    }
}
