package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Increments
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Program
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.engine.Slot
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.ui.Type.tabular

/** [notice] is a one-off result such as a restore count, shown apart from sync status. */
data class SyncUi(val folder: String?, val lastSync: String?, val error: String?, val notice: String? = null)

/** [message] explains a failed write or why writing turned off. */
data class HealthUi(val on: Boolean, val message: String?)

@Composable
fun SettingsScreen(
    started: Boolean,
    programId: String,
    increments: Increments,
    themeId: String,
    laptop: Palette?,
    restTargets: Map<Tier, Int>,
    sync: SyncUi,
    notificationsAllowed: Boolean,
    weighInReminder: Boolean,
    weighInMinutes: Int,
    health: HealthUi,
    plates: Plates,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
    onRest: (Tier, Int) -> Unit,
    onProgram: (String) -> Unit,
    onIncrements: (Increments) -> Unit,
    onDays: () -> Unit,
    onChooseFolder: () -> Unit,
    onRestore: () -> Unit,
    onAllowNotifications: () -> Unit,
    onWeighInReminder: (Boolean) -> Unit,
    onWeighInTime: (Int) -> Unit,
    onHealthConnect: (Boolean) -> Unit,
    onPlates: (Plates) -> Unit,
    warmups: Boolean = true,
    onWarmups: (Boolean) -> Unit = {},
    cardioGoalMin: Int = 150,
    onCardioGoal: (Int) -> Unit = {},
    splits: List<Program> = emptyList(),
    onOpenSplit: (String) -> Unit = {},
    onNewSplit: () -> Unit = {},
    autoAdaptWeights: Boolean = false,
    onAutoAdaptWeights: (Boolean) -> Unit = {},
    mealReminder: Boolean = true,
    onMealReminder: (Boolean) -> Unit = {},
    stretchReminder: Boolean = false,
    stretchReminderMinutes: Int = 20 * 60,
    onStretchReminder: (Boolean) -> Unit = {},
    onStretchReminderTime: (Int) -> Unit = {},
    waterReminder: Boolean = false,
    onWaterReminder: (Boolean) -> Unit = {},
    saveToGallery: Boolean? = null,
    onSaveToGallery: ((Boolean) -> Unit)? = null,
    waterGoalMl: Int = 2500,
    onWaterGoal: (Int) -> Unit = {},
    defaultStretchRoutine: String? = null,
    onStretchRoutine: ((String) -> Unit)? = null,
    onOpenStretchPlayer: ((String) -> Unit)? = null,
    onOpenStretchLibrary: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember(context) { dev.gzclp.tracker.Prefs(context) }
    val galleryState = androidx.compose.runtime.remember(prefs) { androidx.compose.runtime.mutableStateOf(prefs.saveToGallery) }
    val galleryEnabled = saveToGallery ?: galleryState.value
    var currentStretchRoutineId by androidx.compose.runtime.remember(defaultStretchRoutine) {
        androidx.compose.runtime.mutableStateOf(defaultStretchRoutine ?: prefs.stretchRoutine)
    }
    var activeStretchRoutine by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<dev.gzclp.tracker.stretch.StretchRoutine?>(null)
    }
    var showStretchLibrary by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    // System back leaves the player or library for Settings, as their own back buttons do, instead of closing Settings under them.
    androidx.activity.compose.BackHandler(activeStretchRoutine != null || showStretchLibrary) {
        if (activeStretchRoutine != null) activeStretchRoutine = null else showStretchLibrary = false
    }
    if (activeStretchRoutine != null) {
        GuidedStretchPlayerScreen(
            routine = activeStretchRoutine!!,
            onBack = { activeStretchRoutine = null },
            onFinish = { logged ->
                activeStretchRoutine = null
                runCatching { dev.gzclp.tracker.AppModel.get(context).dispatch { listOf(logged) } }
            },
        )
        return
    }
    if (showStretchLibrary) {
        StretchScreen(
            onBack = { showStretchLibrary = false },
            onStartRoutine = { routine ->
                showStretchLibrary = false
                activeStretchRoutine = routine
            },
        )
        return
    }
    ScreenFrame {
        NavRow(onBack)
        Txt("Settings", Type.largeTitle)

        if (started) {
            GroupHeader("Your splits")
            Group {
                // Tapping a split chooses it, like a preset; its chevron opens the editor.
                splits.forEachIndexed { i, s ->
                    GroupRow(first = i == 0, onClick = { onProgram(s.id) }) {
                        Column(Modifier.weight(1f)) {
                            Txt(s.name, Type.body, maxLines = 1)
                            Txt("${s.days.size} ${if (s.days.size == 1) "day" else "days"} · 3-week cycle", Type.footnote, color = p.fg2)
                        }
                        if (s.id == programId) Icon(Icons.check, p.accent, 20.dp)
                        Spacer(Modifier.width(Space.x3 - 20.dp))
                        Snug(20.dp) { m -> IconButton(Icons.chevronRight, "Edit ${s.name}", { onOpenSplit(s.id) }, m, tint = p.fg3) }
                    }
                }
                AddRow("New split", first = splits.isEmpty(), onClick = onNewSplit)
            }
            GroupHeader("Presets")
            ProgramPicker(programId, onProgram)
            Spacer(Modifier.height(Space.lg))
            Group {
                GroupRow(first = true, onClick = onDays) {
                    Txt("Exercises per workout", Type.body, Modifier.weight(1f))
                    Icon(Icons.chevronRight, p.fg3, 20.dp)
                }
            }
            GroupFooter("Switching keeps your history.")

            GroupHeader("Cycle steps")
            Group {
                val rows = listOf(
                    Triple("Upper body", increments.upperStep) { v: Int -> increments.copy(upperStep = v) },
                    Triple("Lower body", increments.lowerStep) { v: Int -> increments.copy(lowerStep = v) },
                    Triple("Next cycle", increments.cycleBump) { v: Int -> increments.copy(cycleBump = v) },
                )
                rows.forEachIndexed { i, (label, v, with) ->
                    GroupRow(first = i == 0) {
                        Txt(label, Type.body, Modifier.weight(1f), maxLines = 1)
                        MiniStep(Icons.minus, "Smaller step for $label") { onIncrements(with((v - 5).coerceAtLeast(0))) }
                        Txt("+$v lb", Type.body.tabular(), Modifier.width(72.dp).padding(horizontal = Space.sm), align = androidx.compose.ui.text.style.TextAlign.Center)
                        MiniStep(Icons.plus, "Larger step for $label") { onIncrements(with((v + 5).coerceAtMost(50))) }
                    }
                }
            }
            GroupFooter("Lower body is squat, deadlift, Romanian deadlift, and leg press. Next cycle applies after a heavy week with every set at its target reps.")
        }

        GroupHeader("Bar and plates")
        Group {
            GroupRow(first = true) {
                Txt("Bar", Type.body, Modifier.weight(1f))
                MiniStep(Icons.minus, "Lighter bar") { onPlates(plates.copy(bar = (plates.bar - 5).coerceAtLeast(5))) }
                Txt(Fmt.lb(plates.bar), Type.body.tabular(), Modifier.widthIn(min = 96.dp).padding(horizontal = Space.sm), maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center)
                MiniStep(Icons.plus, "Heavier bar") { onPlates(plates.copy(bar = (plates.bar + 5).coerceAtMost(100))) }
            }
            Plates.SIZES.forEach { size ->
                val n = plates.pairs[size] ?: 0
                val name = "${Plates.label(size)} lb"
                GroupRow(first = false) {
                    Txt("$name plates", Type.body, Modifier.weight(1f), maxLines = 1)
                    MiniStep(Icons.minus, "One less pair of $name plates") { onPlates(plates.copy(pairs = plates.pairs + (size to (n - 1).coerceAtLeast(0)))) }
                    Txt(if (n == 1) "1 pair" else "$n pairs", Type.body.tabular(), Modifier.widthIn(min = 96.dp).padding(horizontal = Space.sm), maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center)
                    MiniStep(Icons.plus, "One more pair of $name plates") { onPlates(plates.copy(pairs = plates.pairs + (size to (n + 1).coerceAtMost(20)))) }
                }
            }
        }

        GroupHeader("Theme")
        Group {
            Palettes.all.forEachIndexed { i, t ->
                GroupRow(first = i == 0, onClick = { onTheme(t.id) }) {
                    Txt(t.name, Type.body, Modifier.weight(1f))
                    if (t.id == themeId) Icon(Icons.check, p.accent, 20.dp)
                }
            }
            GroupRow(first = false, onClick = { onTheme(LaptopTheme.ID) }) {
                Column(Modifier.weight(1f)) {
                    Txt("Laptop", Type.body)
                    Txt(laptop?.name?.split('-')?.joinToString(" ") { it.replaceFirstChar(Char::uppercase) } ?: if (sync.folder == null) "Choose a sync folder first" else "Not found in the sync folder", Type.footnote, color = p.fg2, maxLines = 1)
                }
                if (themeId == LaptopTheme.ID) Icon(Icons.check, p.accent, 20.dp)
            }
        }
        GroupFooter("Laptop uses the Omarchy theme your laptop syncs to the sync folder.")

        GroupHeader("Rest timer")
        Group {
            // Custom splits rest by each week's reps, so they get no row.
            Tier.entries.filter { it != Tier.CUSTOM }.forEachIndexed { i, tier ->
                GroupRow(first = i == 0) {
                    Txt(tier.label, Type.body, Modifier.weight(1f))
                    val v = restTargets.getValue(tier)
                    MiniStep(Icons.minus, "Shorter ${tier.label} rest") { onRest(tier, (v - 15).coerceAtLeast(15)) }
                    Txt(Fmt.clock(v.toLong()), Type.body.tabular(), Modifier.width(56.dp).padding(horizontal = Space.sm), align = androidx.compose.ui.text.style.TextAlign.Center)
                    MiniStep(Icons.plus, "Longer ${tier.label} rest") { onRest(tier, (v + 15).coerceAtMost(600)) }
                }
            }
        }

        GroupHeader("Warm-ups")
        Group {
            GroupRow(first = true) {
                Txt("Warm-up sets", Type.body, Modifier.weight(1f))
                Toggle(warmups, "Warm-up sets", onWarmups)
            }
        }
        GroupFooter("40%×5, 60%×5, and 80%×3 before each GZCLP T1 lift, and before each split barbell lift at 6 reps or fewer. Not logged.")

        GroupHeader("Strength")
        Group {
            GroupRow(first = true) {
                Txt("Auto-adapt weights", Type.body, Modifier.weight(1f))
                Toggle(autoAdaptWeights, "Auto-adapt weights", onAutoAdaptWeights)
            }
        }
        GroupFooter("When recent sets show you are stronger, automatically adopt the suggested weight at session start.")

        GroupHeader("Cardio")
        Group {
            GroupRow(first = true) {
                Txt("Weekly goal", Type.body, Modifier.weight(1f), maxLines = 1)
                MiniStep(Icons.minus, "Smaller weekly cardio goal") { onCardioGoal((cardioGoalMin - 15).coerceAtLeast(15)) }
                Txt(Fmt.minutes(cardioGoalMin * 60L), Type.body.tabular(), Modifier.widthIn(min = 120.dp).padding(horizontal = Space.sm), maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center)
                MiniStep(Icons.plus, "Larger weekly cardio goal") { onCardioGoal((cardioGoalMin + 15).coerceAtMost(20 * 60)) }
            }
        }

        GroupHeader("Reminders")
        Group {
            GroupRow(first = true) {
                Txt("Morning reminder", Type.body, Modifier.weight(1f))
                Toggle(weighInReminder, "Morning reminder", onWeighInReminder)
            }
            if (weighInReminder) GroupRow(first = false) {
                Txt("Time", Type.body, Modifier.weight(1f))
                MiniStep(Icons.minus, "Earlier reminder") { onWeighInTime((weighInMinutes - 15).mod(24 * 60)) }
                Txt(Fmt.clockTime(weighInMinutes), Type.body.tabular(), Modifier.width(96.dp).padding(horizontal = Space.sm), align = androidx.compose.ui.text.style.TextAlign.Center)
                MiniStep(Icons.plus, "Later reminder") { onWeighInTime((weighInMinutes + 15) % (24 * 60)) }
            }
            GroupRow(first = false) {
                Txt("Meals", Type.body, Modifier.weight(1f))
                Toggle(mealReminder, "Meal reminder", onMealReminder)
            }
            GroupRow(first = false) {
                Txt("Stretching", Type.body, Modifier.weight(1f))
                Toggle(stretchReminder, "Stretch reminder", onStretchReminder)
            }
            if (stretchReminder) GroupRow(first = false) {
                Txt("Stretch time", Type.body, Modifier.weight(1f))
                MiniStep(Icons.minus, "Earlier stretch reminder") { onStretchReminderTime((stretchReminderMinutes - 15).mod(24 * 60)) }
                Txt(Fmt.clockTime(stretchReminderMinutes), Type.body.tabular(), Modifier.width(96.dp).padding(horizontal = Space.sm), align = androidx.compose.ui.text.style.TextAlign.Center)
                MiniStep(Icons.plus, "Later stretch reminder") { onStretchReminderTime((stretchReminderMinutes + 15) % (24 * 60)) }
            }
            GroupRow(first = false) {
                Txt("Water", Type.body, Modifier.weight(1f))
                Toggle(waterReminder, "Water reminder", onWaterReminder)
            }
        }
        if ((weighInReminder || mealReminder || stretchReminder || waterReminder) && !notificationsAllowed) {
            GroupFooter("Allow notifications below to get the reminder.")
        }

        GroupHeader("Food")
        Group {
            GroupRow(first = true) {
                Txt("Also save meal photos to the gallery", Type.body, Modifier.weight(1f))
                Toggle(galleryEnabled, "Also save meal photos to the gallery") { enabled ->
                    galleryState.value = enabled
                    onSaveToGallery?.invoke(enabled) ?: run { prefs.saveToGallery = enabled }
                }
            }
        }
        GroupFooter("Saved to Pictures/Food.")

        GroupHeader("Water")
        Group {
            GroupRow(first = true) {
                Txt("Daily goal", Type.body, Modifier.weight(1f))
                MiniStep(Icons.minus, "Less water") { onWaterGoal((waterGoalMl - 100).coerceAtLeast(500)) }
                Txt("$waterGoalMl ml", Type.body.tabular(), Modifier.widthIn(min = 96.dp).padding(horizontal = Space.sm), align = androidx.compose.ui.text.style.TextAlign.Center)
                MiniStep(Icons.plus, "More water") { onWaterGoal((waterGoalMl + 100).coerceAtMost(8000)) }
            }
            GroupRow(first = false) {
                Txt("Pace reminder", Type.body, Modifier.weight(1f))
                Toggle(waterReminder, "Pace reminder", onWaterReminder)
            }
        }
        GroupFooter("Reminds you if water intake falls 500 ml behind pace from wake time to 14 hours later (every 90 min max).")
        if (waterReminder && !notificationsAllowed) GroupFooter("Allow notifications below to get the reminder.")

        GroupHeader("Stretching")
        val routineOptions = listOf(
            dev.gzclp.tracker.stretch.StretchRoutines.ID_AFTER_WORKOUT to "After workout",
            dev.gzclp.tracker.stretch.StretchRoutines.ID_FLEXIBILITY to "Flexibility",
            dev.gzclp.tracker.stretch.StretchRoutines.ID_DANCE to "Dance",
            dev.gzclp.tracker.stretch.StretchRoutines.ID_ATHLETICS to "Athletics",
        )
        val selectedRoutineName = routineOptions.firstOrNull { it.first == currentStretchRoutineId }?.second ?: "After workout"
        Group {
            GroupRow(first = true, onClick = {
                val currentIndex = routineOptions.indexOfFirst { it.first == currentStretchRoutineId }
                val nextIndex = (currentIndex + 1) % routineOptions.size
                val nextId = routineOptions[nextIndex].first
                currentStretchRoutineId = nextId
                prefs.stretchRoutine = nextId
                onStretchRoutine?.invoke(nextId)
            }) {
                Txt("Default routine", Type.body, Modifier.weight(1f))
                Txt(selectedRoutineName, Type.body, color = p.accent)
            }
            GroupRow(first = false, onClick = {
                if (onOpenStretchPlayer != null) {
                    onOpenStretchPlayer(currentStretchRoutineId)
                } else {
                    // After workout picks its moves from today's lifts, so it needs the log.
                    val state = runCatching { dev.gzclp.tracker.AppModel.get(context).store.state.value }.getOrNull()
                    activeStretchRoutine = dev.gzclp.tracker.stretch.StretchRoutines.of(currentStretchRoutineId, state)
                }
            }) {
                Txt("Start routine", Type.body, Modifier.weight(1f), color = p.accent)
                Icon(Icons.play, p.accent, size = 18.dp)
            }
            GroupRow(first = false, onClick = {
                if (onOpenStretchLibrary != null) {
                    onOpenStretchLibrary()
                } else {
                    showStretchLibrary = true
                }
            }) {
                Txt("Move library & routines", Type.body, Modifier.weight(1f))
                Icon(Icons.chevronRight, p.fg2, size = 18.dp)
            }
        }
        GroupFooter(dev.gzclp.tracker.stretch.StretchRoutines.COACHING_DISCLAIMER)

        GroupHeader("Health Connect")
        Group {
            GroupRow(first = true) {
                Txt("Write workouts and weight", Type.body, Modifier.weight(1f))
                Toggle(health.on, "Write workouts and weight to Health Connect", onHealthConnect)
            }
        }
        health.message?.let { GroupFooter(it) }

        GroupHeader("Sync folder")
        Group {
            GroupRow(first = true, onClick = onChooseFolder) {
                Txt("Folder", Type.body, Modifier.weight(1f))
                Txt(sync.folder ?: "Choose", Type.body, color = if (sync.folder == null) p.accent else p.fg2, maxLines = 1)
            }
            if (!started) {
                GroupRow(first = false, onClick = onRestore) {
                    Txt("Restore from folder", Type.body, color = p.accent)
                }
            }
        }
        GroupFooter(
            when {
                sync.folder == null -> "Not syncing. The log stays on this phone."
                sync.error != null -> "Last sync failed: ${sync.error}. Logging continues on this phone."
                sync.lastSync != null -> "Last synced ${sync.lastSync}"
                else -> "Not synced yet"
            },
        )
        sync.notice?.let { GroupFooter(it) }

        if (!notificationsAllowed) {
            GroupHeader("Notifications")
            Group {
                GroupRow(first = true, onClick = onAllowNotifications) {
                    Txt("Allow notifications", Type.body, color = p.accent)
                }
            }
            GroupFooter("Needed for rest alerts and the weigh-in reminder.")
        }
    }
}

@Composable
fun DaysScreen(state: AppState, onBack: () -> Unit, onAdd: (ProgramDay) -> Unit, onRemove: (ProgramDay, String) -> Unit) {
    val p = LocalPalette.current
    val program = state.program
    ScreenFrame {
        NavRow(onBack)
        Txt(program.name, Type.largeTitle)
        Spacer(Modifier.height(Space.xs))
        Txt(
            if (program.gzclp) "T3 lifts per workout" else "Exercises per workout",
            Type.subhead, color = p.fg2,
        )
        program.days.forEach { day ->
            val fixed = state.slots(program, day).filter { program.gzclp && it.tier != Tier.T3 }
            GroupHeader(day.title + fixed.joinToString("") { ", ${state.name(it.ex)} ${it.tier.label}" })
            Group {
                val ids = Commands.dayList(state, program, day)
                ids.forEachIndexed { i, id ->
                    val ex = state.exercise(Slot(id, if (program.gzclp) Tier.T3 else Tier.CYCLE))
                    GroupRow(first = i == 0) {
                        Column(Modifier.weight(1f)) {
                            Txt(ex.name, Type.body, maxLines = 1)
                            Txt(ex.weight?.let(Fmt::lb) ?: "No weight yet", Type.footnote.tabular(), color = p.fg2)
                        }
                        IconButton(Icons.x, "Remove ${ex.name}", { onRemove(day, id) }, tint = p.fg2)
                    }
                }
                AddRow("Add exercise", first = ids.isEmpty()) { onAdd(day) }
            }
        }
    }
}
