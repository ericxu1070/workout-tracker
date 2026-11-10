package dev.gzclp.tracker

import android.Manifest
import android.content.Intent
import android.graphics.Color
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
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.SetKey
import dev.gzclp.tracker.ui.AccessoriesScreen
import dev.gzclp.tracker.ui.GzTheme
import dev.gzclp.tracker.ui.HistoryScreen
import dev.gzclp.tracker.ui.HomeScreen
import dev.gzclp.tracker.ui.Palettes
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
            val palette = Palettes.of(model.theme)
            LaunchedEffect(palette.dark) {
                val style = if (palette.dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            GzTheme(palette) { App(model) }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}

private enum class Route { Main, History, Settings, Accessories }

@Composable
private fun App(model: AppModel) {
    val state by model.store.state.collectAsState()
    var route by rememberSaveable { mutableStateOf(Route.Main) }
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
    fun finishWorkout(fiveRm: Int?) {
        model.dispatch { Commands.end(it, model.now(), fiveRm) }
        model.cancelRest()
        close()
    }
    fun endFlow() {
        val pending = Commands.pendingFiveRm(state)
        if (pending != null) open(SheetState.FiveRm(pending, pending.state.weight)) else finishWorkout(null)
    }

    BackHandler(sheet != null || route != Route.Main) {
        when {
            sheet != null -> close()
            route == Route.Accessories -> route = Route.Settings
            else -> route = Route.Main
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (route) {
            Route.History -> HistoryScreen(state, { route = Route.Main })
            Route.Settings -> SettingsScreen(
                started = state.started, themeId = model.theme, restTargets = model.restTargets,
                sync = model.sync.copy(error = model.notice ?: model.sync.error), notificationsAllowed = model.notificationsAllowed,
                onBack = { route = Route.Main }, onTheme = model::chooseTheme, onRest = model::setRest,
                onAccessories = { route = Route.Accessories }, onChooseFolder = { folderPicker.launch(null) },
                onRestore = ::restore, onAllowNotifications = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
            Route.Accessories -> AccessoriesScreen(
                state, { route = Route.Settings },
                onAdd = { open(SheetState.AddT3(it, "", 50)) },
                onRemove = { day, id -> model.dispatch { s -> Commands.removeT3(s, day, id) } },
            )
            Route.Main -> when {
                !state.started -> SetupScreen(
                    onStart = { rm -> model.dispatch { if (it.started) emptyList() else Commands.setup(rm) } },
                    onRestore = ::restore, message = model.notice,
                )
                state.active == null -> HomeScreen(
                    state,
                    onStart = {
                        if (!model.notificationsAllowed) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        model.dispatch { Commands.start(it, model.now()) }
                    },
                    onHistory = { route = Route.History }, onSettings = { route = Route.Settings },
                    onEditWeight = { open(SheetState.Weight(it, it.state.weight)) },
                )
                else -> WorkoutScreen(
                    state, java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(), model.rest(nowMillis),
                    onSet = { ex, i ->
                        val logged = state.active?.sets?.get(SetKey(ex.liftId, ex.tier, i))?.event
                        val amrap = ex.scheme.amrap && i == ex.scheme.sets
                        when {
                            logged != null -> open(SheetState.Reps(ex, i, logged.actualReps, true))
                            amrap -> open(SheetState.Reps(ex, i, ex.scheme.reps, false))
                            else -> {
                                model.dispatch { Commands.logSet(it, ex, i, ex.scheme.reps, model.now(), model.restTargets) }
                                model.startRest(ex, i)
                            }
                        }
                    },
                    onLongSet = { ex, i ->
                        val logged = state.active?.sets?.get(SetKey(ex.liftId, ex.tier, i))?.event
                        open(SheetState.Reps(ex, i, logged?.actualReps ?: ex.scheme.reps, logged != null))
                    },
                    onEditWeight = { open(SheetState.Weight(it, it.state.weight)) },
                    onSkipRest = model::cancelRest,
                    onHistory = { route = Route.History },
                    onEnd = {
                        val n = unloggedSets(state)
                        if (n > 0) open(SheetState.EndConfirm(n)) else endFlow()
                    },
                )
            }
        }
        Sheet(sheet != null, ::close) {
            val s = sheet ?: lastSheet ?: return@Sheet
            SheetContent(
                s, update = { sheet = it; lastSheet = it }, onDismiss = ::close,
                onConfirm = { c ->
                    when (c) {
                        is SheetState.Reps -> {
                            model.dispatch { Commands.logSet(it, c.ex, c.setIndex, c.reps, model.now(), model.restTargets) }
                            if (!c.logged) model.startRest(c.ex, c.setIndex)
                            close()
                        }
                        is SheetState.Weight -> {
                            model.dispatch { Commands.editWeight(c.ex, c.weight) }
                            close()
                        }
                        is SheetState.FiveRm -> finishWorkout(c.fiveRm)
                        is SheetState.EndConfirm -> endFlow()
                        is SheetState.AddT3 -> {
                            model.dispatch { Commands.addT3(it, c.day, c.name, c.weight) }
                            close()
                        }
                    }
                },
                onClear = { c ->
                    model.dispatch { Commands.clearSet(it, c.ex, c.setIndex) }
                    close()
                },
            )
        }
    }
}
