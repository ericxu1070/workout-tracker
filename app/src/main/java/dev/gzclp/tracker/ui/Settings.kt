package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Increments
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Program
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Slot
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.stretch.StretchRoutines
import dev.gzclp.tracker.ui.Type.tabular

/** [notice] is a one-off result such as a restore count, shown apart from sync status. */
data class SyncUi(val folder: String?, val lastSync: String?, val error: String?, val notice: String? = null)

/** [message] explains a failed write or why writing turned off. */
data class HealthUi(val on: Boolean, val message: String?)

/** A page under Settings' short root list, as in iOS Settings. Program needs a started log. */
enum class SettingsPage(val title: String) {
    Program("Program"), Lifting("Lifting"), Goals("Goals"), Stretching("Stretching"), Reminders("Reminders"), Sync("Sync folder"), Appearance("Appearance"),
}

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
    showPlates: Boolean = true,
    onShowPlates: (Boolean) -> Unit = {},
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
    stretchRoutine: String = StretchRoutines.ID_AFTER_WORKOUT,
    onStretchRoutine: (String) -> Unit = {},
    customStretches: List<dev.gzclp.tracker.stretch.StretchRoutine> = emptyList(),
    page: SettingsPage? = null,
    onPage: (SettingsPage) -> Unit = {},
    initialShowMore: Boolean = Palettes.custom.any { it.id == themeId },
) {
    val p = LocalPalette.current
    var showMore by rememberSaveable { mutableStateOf(initialShowMore) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember(context) { dev.gzclp.tracker.Prefs(context) }
    val galleryState = androidx.compose.runtime.remember(prefs) { androidx.compose.runtime.mutableStateOf(prefs.saveToGallery) }
    val galleryEnabled = saveToGallery ?: galleryState.value
    // The Flexibility days share one id, which picks the day's routine, so they are one choice here.
    val moreRoutines = StretchRoutines.more.distinctBy { it.id }.map { it.id to if (it.id == StretchRoutines.ID_FLEXIBILITY) "Flexibility" else it.name }
    val topRoutines = listOf(StretchRoutines.ID_AFTER_WORKOUT to "After workout") + StretchRoutines.popular.map { it.id to it.name }
    val customRoutines = customStretches.map { it.id to it.name }
    val routines = topRoutines + moreRoutines + customRoutines
    var moreShown by rememberSaveable { mutableStateOf(moreRoutines.any { it.first == stretchRoutine }) }
    val anyReminder = weighInReminder || mealReminder || stretchReminder || waterReminder

    @Composable
    fun PageRow(to: SettingsPage, first: Boolean, value: String? = null, valueColor: Color = p.fg2) =
        GroupRow(first = first, onClick = { onPage(to) }) {
            Txt(to.title, Type.body, Modifier.padding(end = Space.lg), maxLines = 1)
            Txt(value.orEmpty(), Type.body, Modifier.weight(1f), valueColor, maxLines = 1, align = TextAlign.End)
            Spacer(Modifier.width(Space.xs))
            Icon(Icons.chevronRight, p.fg3, 20.dp)
        }

    @Composable
    fun ToggleRow(label: String, on: Boolean, first: Boolean, a11y: String = label, onChange: (Boolean) -> Unit) = GroupRow(first = first) {
        Txt(label, Type.body, Modifier.weight(1f))
        Toggle(on, a11y, onChange)
    }

    @Composable
    fun StepRow(label: String, value: String, first: Boolean, valueWidth: androidx.compose.ui.unit.Dp, less: String, more: String, onLess: () -> Unit, onMore: () -> Unit) =
        GroupRow(first = first) {
            Txt(label, Type.body, Modifier.weight(1f), maxLines = 1)
            MiniStep(Icons.minus, less, onClick = onLess)
            Txt(value, Type.body.tabular(), Modifier.widthIn(min = valueWidth).padding(horizontal = Space.sm), maxLines = 1, align = TextAlign.Center)
            MiniStep(Icons.plus, more, onClick = onMore)
        }

    ScreenFrame {
        NavRow(onBack)
        Txt(page?.title ?: "Settings", Type.largeTitle)

        when (page) {
            null -> {
                if (started) {
                    Spacer(Modifier.height(Space.xxl))
                    Group { PageRow(SettingsPage.Program, true, (splits.firstOrNull { it.id == programId } ?: Programs.of(programId)).name) }
                }
                Spacer(Modifier.height(Space.xxl))
                Group {
                    PageRow(SettingsPage.Lifting, true)
                    PageRow(SettingsPage.Goals, false)
                    PageRow(SettingsPage.Stretching, false, routines.firstOrNull { it.first == stretchRoutine }?.second ?: routines.first().second)
                    val on = listOf(weighInReminder, mealReminder, stretchReminder, waterReminder).count { it }
                    PageRow(SettingsPage.Reminders, false, if (on == 0) "Off" else "$on on")
                }
                Spacer(Modifier.height(Space.xxl))
                Group {
                    GroupRow(first = true) {
                        Column(Modifier.weight(1f)) {
                            Txt("Health Connect", Type.body)
                            health.message?.let { Txt(it, Type.footnote, color = p.fg2, maxLines = 2) }
                        }
                        Toggle(health.on, "Write workouts and weight to Health Connect", onHealthConnect)
                    }
                    GroupRow(first = false) {
                        Column(Modifier.weight(1f)) {
                            Txt("Save meal photos", Type.body)
                            Txt("Pictures/Food", Type.footnote, color = p.fg2)
                        }
                        Toggle(galleryEnabled, "Save meal photos to Pictures/Food") { enabled ->
                            galleryState.value = enabled
                            onSaveToGallery?.invoke(enabled) ?: run { prefs.saveToGallery = enabled }
                        }
                    }
                    PageRow(
                        SettingsPage.Sync, false,
                        when {
                            sync.folder == null -> "Off"
                            sync.error != null -> "Failed"
                            else -> sync.folder
                        },
                        if (sync.folder != null && sync.error != null) p.red else p.fg2,
                    )
                }
                Spacer(Modifier.height(Space.xxl))
                Group { PageRow(SettingsPage.Appearance, true, Palettes.label(themeId)) }
                if (!notificationsAllowed) {
                    Spacer(Modifier.height(Space.xxl))
                    Group {
                        GroupRow(first = true, onClick = onAllowNotifications) { Txt("Allow notifications", Type.body, color = p.accent) }
                    }
                }
            }

            SettingsPage.Program -> {
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
                GroupHeader("Cycle steps")
                Group {
                    val rows = listOf(
                        Triple("Upper body", increments.upperStep) { v: Int -> increments.copy(upperStep = v) },
                        Triple("Lower body", increments.lowerStep) { v: Int -> increments.copy(lowerStep = v) },
                        Triple("Next cycle", increments.cycleBump) { v: Int -> increments.copy(cycleBump = v) },
                    )
                    rows.forEachIndexed { i, (label, v, with) ->
                        StepRow(label, "+$v lb", i == 0, 72.dp, "Smaller step for $label", "Larger step for $label",
                            { onIncrements(with((v - 5).coerceAtLeast(0))) }, { onIncrements(with((v + 5).coerceAtMost(50))) })
                    }
                }
            }

            SettingsPage.Lifting -> {
                GroupHeader("Bar and plates")
                Group {
                    ToggleRow("Show plates", showPlates, true, onChange = onShowPlates)
                    StepRow("Bar", Fmt.lb(plates.bar), false, 96.dp, "Lighter bar", "Heavier bar",
                        { onPlates(plates.copy(bar = (plates.bar - 5).coerceAtLeast(5))) }, { onPlates(plates.copy(bar = (plates.bar + 5).coerceAtMost(100))) })
                    Plates.SIZES.forEach { size ->
                        val n = plates.pairs[size] ?: 0
                        val name = "${Plates.label(size)} lb"
                        StepRow("$name plates", if (n == 1) "1 pair" else "$n pairs", false, 96.dp, "One less pair of $name plates", "One more pair of $name plates",
                            { onPlates(plates.copy(pairs = plates.pairs + (size to (n - 1).coerceAtLeast(0)))) },
                            { onPlates(plates.copy(pairs = plates.pairs + (size to (n + 1).coerceAtMost(20)))) })
                    }
                }
                GroupHeader("Rest timer")
                Group {
                    // Custom splits rest by each week's reps, so they get no row.
                    Tier.entries.filter { it != Tier.CUSTOM }.forEachIndexed { i, tier ->
                        val v = restTargets.getValue(tier)
                        StepRow(tier.label, Fmt.clock(v.toLong()), i == 0, 96.dp, "Shorter ${tier.label} rest", "Longer ${tier.label} rest",
                            { onRest(tier, (v - 15).coerceAtLeast(15)) }, { onRest(tier, (v + 15).coerceAtMost(600)) })
                    }
                }
                Spacer(Modifier.height(Space.xxl))
                Group {
                    ToggleRow("Warm-up sets", warmups, true, onChange = onWarmups)
                    ToggleRow("Auto-adapt weights", autoAdaptWeights, false, onChange = onAutoAdaptWeights)
                }
            }

            SettingsPage.Goals -> {
                Spacer(Modifier.height(Space.xxl))
                Group {
                    StepRow("Cardio each week", Fmt.minutes(cardioGoalMin * 60L), true, 120.dp, "Smaller weekly cardio goal", "Larger weekly cardio goal",
                        { onCardioGoal((cardioGoalMin - 15).coerceAtLeast(15)) }, { onCardioGoal((cardioGoalMin + 15).coerceAtMost(20 * 60)) })
                    StepRow("Water each day", "$waterGoalMl ml", false, 120.dp, "Less water", "More water",
                        { onWaterGoal((waterGoalMl - 100).coerceAtLeast(500)) }, { onWaterGoal((waterGoalMl + 100).coerceAtMost(8000)) })
                }
            }

            SettingsPage.Stretching -> {
                GroupHeader("Default routine")
                Group {
                    val current = routines.firstOrNull { it.first == stretchRoutine }?.first ?: StretchRoutines.ID_AFTER_WORKOUT
                    @Composable
                    fun RoutineRow(id: String, name: String, first: Boolean) = GroupRow(first = first, onClick = { onStretchRoutine(id) }) {
                        Txt(name, Type.body, Modifier.weight(1f), maxLines = 1)
                        if (id == current) Icon(Icons.check, p.accent, 20.dp)
                    }
                    topRoutines.forEachIndexed { i, (id, name) -> RoutineRow(id, name, i == 0) }
                    ShowMoreRow(moreShown, { moreShown = !moreShown })
                    Disclosed(moreShown) { moreRoutines.forEach { (id, name) -> RoutineRow(id, name, false) } }
                    customRoutines.forEach { (id, name) -> RoutineRow(id, name, false) }
                }
            }

            SettingsPage.Reminders -> {
                Spacer(Modifier.height(Space.xxl))
                Group {
                    ToggleRow("Weigh-in", weighInReminder, true, "Weigh-in reminder", onWeighInReminder)
                    if (weighInReminder) StepRow("Weigh-in time", Fmt.clockTime(weighInMinutes), false, 96.dp, "Earlier reminder", "Later reminder",
                        { onWeighInTime((weighInMinutes - 15).mod(24 * 60)) }, { onWeighInTime((weighInMinutes + 15) % (24 * 60)) })
                    ToggleRow("Meals", mealReminder, false, "Meal reminder", onMealReminder)
                    ToggleRow("Stretching", stretchReminder, false, "Stretch reminder", onStretchReminder)
                    if (stretchReminder) StepRow("Stretch time", Fmt.clockTime(stretchReminderMinutes), false, 96.dp, "Earlier stretch reminder", "Later stretch reminder",
                        { onStretchReminderTime((stretchReminderMinutes - 15).mod(24 * 60)) }, { onStretchReminderTime((stretchReminderMinutes + 15) % (24 * 60)) })
                    ToggleRow("Water pace", waterReminder, false, "Water pace reminder", onWaterReminder)
                }
                if (anyReminder && !notificationsAllowed) {
                    Spacer(Modifier.height(Space.xxl))
                    Group {
                        GroupRow(first = true, onClick = onAllowNotifications) { Txt("Allow notifications", Type.body, color = p.accent) }
                    }
                }
            }

            SettingsPage.Sync -> {
                Spacer(Modifier.height(Space.xxl))
                Group {
                    GroupRow(first = true, onClick = onChooseFolder) {
                        Column(Modifier.weight(1f)) {
                            Txt("Folder", Type.body)
                            val status = when {
                                sync.folder == null -> null
                                sync.error != null -> "Last sync failed: ${sync.error}"
                                sync.lastSync != null -> "Last synced ${sync.lastSync}"
                                else -> "Not synced yet"
                            }
                            status?.let { Txt(it, Type.footnote, color = if (sync.error != null) p.red else p.fg2, maxLines = 2) }
                        }
                        Txt(sync.folder ?: "Choose", Type.body, Modifier.padding(start = Space.lg), if (sync.folder == null) p.accent else p.fg2, maxLines = 1)
                    }
                    if (!started) {
                        GroupRow(first = false, onClick = onRestore) {
                            Column(Modifier.weight(1f)) {
                                Txt("Restore from folder", Type.body, color = p.accent)
                                sync.notice?.let { Txt(it, Type.footnote, color = p.fg2) }
                            }
                        }
                    }
                }
            }

            SettingsPage.Appearance -> {
                Spacer(Modifier.height(Space.xxl))
                Group {
                    @Composable
                    fun ThemeRow(id: String, name: String, first: Boolean, detail: String? = null, swatch: @Composable () -> Unit) =
                        GroupRow(first = first, onClick = { onTheme(id) }, inset = Space.lg + 22.dp + Space.md) {
                            swatch()
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Txt(name, Type.body, maxLines = 1)
                                detail?.let { Txt(it, Type.footnote, color = p.fg2, maxLines = 1) }
                            }
                            if (id == themeId) Icon(Icons.check, p.accent, 20.dp)
                        }
                    ThemeRow(Palettes.light.id, "Light", true) { Swatch(Palettes.light) }
                    ThemeRow(Palettes.dark.id, "Dark", false) { Swatch(Palettes.dark) }
                    ThemeRow(Palettes.SYSTEM, "System", false) { Swatch(Palettes.light, Palettes.dark) }
                    ShowMoreRow(showMore, { showMore = !showMore }, lead = 22.dp + Space.md)
                    Disclosed(showMore) { Palettes.custom.forEach { t -> ThemeRow(t.id, t.name, false) { Swatch(t) } } }
                    ThemeRow(
                        LaptopTheme.ID, "Laptop", false,
                        laptop?.name?.split('-')?.joinToString(" ") { it.replaceFirstChar(Char::uppercase) } ?: if (sync.folder == null) "Choose a sync folder first" else "Not found in the sync folder",
                    ) { Swatch(laptop) }
                }
            }
        }
    }
}

/** A theme's page color ringed by a hairline, with its accent dotted in; [second] splits it down the middle, and null is an empty ring. */
@Composable
private fun Swatch(palette: Palette?, second: Palette? = null) {
    Box(Modifier.size(22.dp).clip(CircleShape).border(1.dp, LocalPalette.current.separator, CircleShape)) {
        if (palette != null) Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().background(palette.bg))
            Box(Modifier.weight(1f).fillMaxHeight().background((second ?: palette).bg))
        }
        if (palette != null && second == null) Box(Modifier.size(8.dp).align(Alignment.Center).clip(CircleShape).background(palette.accent))
    }
}

/** A preset's exercises per day. Edit shows the red remove and Delete, as in a split's edit mode; presets keep their order. */
@Composable
fun DaysScreen(state: AppState, onBack: () -> Unit, onAdd: (ProgramDay) -> Unit, onRemove: (ProgramDay, String) -> Unit, initialEditing: Boolean = false) {
    val p = LocalPalette.current
    val program = state.program
    var editing by rememberSaveable { mutableStateOf(initialEditing) }
    ScreenFrame {
        NavRow(onBack) { EditButton(editing) { editing = !editing } }
        Txt(program.name, Type.largeTitle)
        Spacer(Modifier.height(Space.xs))
        Txt(
            if (program.gzclp) "T3 lifts per workout" else "Exercises per workout",
            Type.subhead, color = p.fg2,
        )
        program.days.forEach { day ->
            val fixed = state.slots(program, day).filter { program.gzclp && it.tier != Tier.T3 }
            GroupHeader(day.title + fixed.joinToString("") { ", ${state.name(it.ex)} ${it.tier.label}" })
            val ids = Commands.dayList(state, program, day)
            val exercises = ids.map { state.exercise(Slot(it, if (program.gzclp) Tier.T3 else Tier.CYCLE)) }
            if (editing) {
                if (ids.isNotEmpty()) Group {
                    EditRows(
                        exercises.map { it.name }, ids, { exercises[it].weight?.let(Fmt::lb) }, removable = true,
                        onMove = null, onRemove = { i -> onRemove(day, ids[i]) },
                    )
                }
            } else Group {
                exercises.forEachIndexed { i, ex ->
                    GroupRow(first = i == 0) {
                        Txt(ex.name, Type.body, Modifier.weight(1f), maxLines = 1)
                        Txt(ex.weight?.let(Fmt::lb) ?: "No weight yet", Type.body.tabular(), Modifier.padding(start = Space.sm), p.fg2, maxLines = 1)
                    }
                }
                AddRow("Add exercise", first = ids.isEmpty()) { onAdd(day) }
            }
        }
    }
}
