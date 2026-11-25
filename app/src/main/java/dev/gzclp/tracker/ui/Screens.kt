package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import dev.gzclp.tracker.log.shouldSuggest
import dev.gzclp.tracker.log.strengthSuggestion
import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Progression
import dev.gzclp.tracker.engine.Cycle
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.Warmup
import dev.gzclp.tracker.log.SetLogged
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.SetKey
import dev.gzclp.tracker.ui.Type.tabular
import java.time.Duration
import java.time.OffsetDateTime

/** A full-height screen: scrolling content below the status bar, optional bottom bar. */
@Composable
fun ScreenFrame(bottom: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val title = remember { LargeTitle() }
    var top by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val barPx = with(androidx.compose.ui.platform.LocalDensity.current) { 44.dp.toPx() }
    val inline by remember { androidx.compose.runtime.derivedStateOf { title.bottom < top } }
    // Status bar inset outside the scroll, so content clips below it instead of scrolling under the clock.
    Column(Modifier.fillMaxSize().background(p.bg).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { top = it.positionInRoot().y + barPx }) {
            androidx.compose.runtime.CompositionLocalProvider(LocalLargeTitle provides title) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.lg).padding(bottom = Space.x3),
                    content = content,
                )
            }
            // Once the large title scrolls under it, the title shows small in a bar, as iOS collapses it.
            androidx.compose.animation.AnimatedVisibility(inline, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                Column(Modifier.fillMaxWidth().background(p.bg)) {
                    Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Space.x4), contentAlignment = Alignment.Center) {
                        Txt(title.text, Type.headline, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.separator))
                }
            }
        }
        bottom?.invoke()
    }
}

@Composable
fun NavRow(onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(Icons.chevronLeft, "Back", onBack, Modifier.padding(start = 0.dp))
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SetupScreen(onStart: (Map<Lift, Int>, String) -> Unit, onRestore: () -> Unit, message: String?, initial: Map<Lift, String> = emptyMap(), initialProgram: String = Programs.GZCLP) {
    val p = LocalPalette.current
    val values = remember { mutableStateMapOf<Lift, String>().apply { putAll(initial) } }
    var program by rememberSaveable { mutableStateOf(initialProgram) }
    val parsed = Lift.entries.associateWith { values[it]?.toIntOrNull()?.takeIf { v -> v in 20..1500 } }
    val ready = parsed.values.all { it != null }
    val gzclp = Programs.of(program).gzclp
    ScreenFrame(bottom = {
        BottomBar {
            PillButton("Restore log", onRestore, Modifier.weight(1f))
            PillButton("Start program", { onStart(parsed.mapValues { it.value!! }, program) }, Modifier.weight(1f), primary = true, enabled = ready)
        }
    }) {
        Spacer(Modifier.height(Space.x4))
        Txt("Starting weights", Type.largeTitle)
        Spacer(Modifier.height(Space.sm))
        Txt(
            "Enter a recent 5-rep max for each lift.",
            Type.body, color = p.fg2,
        )
        Spacer(Modifier.height(Space.xxl))
        Group {
            Lift.entries.forEachIndexed { i, lift ->
                GroupRow(first = i == 0) {
                    Column(Modifier.weight(1f)) {
                        Txt(lift.label, Type.headline)
                        val rm = parsed[lift]
                        Txt(
                            when {
                                rm == null -> "5RM"
                                gzclp -> "T1 ${Fmt.lb(Progression.t1FromFiveRm(rm))} · T2 ${Fmt.lb(Progression.t2FromFiveRm(rm))}"
                                else -> "Week 1 at ${Fmt.lb(Cycle.baseFromFiveRm(rm))}"
                            },
                            Type.footnote, color = p.fg2,
                        )
                    }
                    NumberField(values[lift].orEmpty(), { v -> values[lift] = v.filter(Char::isDigit).take(4) }, last = i == Lift.entries.lastIndex)
                    Txt(" lb", Type.body, color = p.fg2)
                }
            }
        }
        if (message != null) GroupFooter(message, color = p.red)
        GroupHeader("Program")
        ProgramPicker(program) { program = it }
    }
}

@Composable
fun ProgramPicker(selected: String, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    Group {
        Programs.all.forEachIndexed { i, prog ->
            GroupRow(first = i == 0, onClick = { onSelect(prog.id) }) {
                Column(Modifier.weight(1f)) {
                    Txt(prog.name, Type.body)
                    Txt("${prog.days.size} days · " + if (prog.gzclp) "T1, T2, T3 progression" else "3-week cycle", Type.footnote, color = p.fg2)
                }
                if (prog.id == selected) Icon(Icons.check, p.accent, 20.dp)
            }
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    last: Boolean = true,
    modifier: Modifier = Modifier,
    placeholder: String = "0",
    width: Dp = 88.dp,
    color: Color = LocalPalette.current.fg,
    onCommit: () -> Unit = {},
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // Re-asks as the keyboard slides up, so the field ends in the shrunken viewport, not behind a bottom bar.
    val bring = remember { BringIntoViewRequester() }
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, ime) { if (focused) bring.bringIntoView() }
    Box(modifier.bringIntoViewRequester(bring).width(width).height(44.dp).clip(RoundedCornerShape(Radius.md)).background(p.fill).padding(horizontal = Space.md), contentAlignment = Alignment.CenterEnd) {
        if (value.isEmpty()) Txt(placeholder, Type.body.tabular(), color = p.fg3, align = TextAlign.End)
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth()
                .onFocusChanged { if (focused && !it.isFocused) onCommit(); focused = it.isFocused }
                .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
            textStyle = Type.body.tabular().copy(color = color, textAlign = TextAlign.End),
            singleLine = true, cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (last) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
    }
}

/** "T1 · 5×3+" or "Week 2 of 3 · 3×10". */
fun Exercise.detail(): String =
    if (tier.cyclic) "Week ${(week ?: 0) + 1} of ${weeks.size} · ${scheme.display}" else "${tier.label} · ${scheme.display}"

/** Warm-ups go before a GZCLP T1 lift and before a split's barbell lift on a week of 6 reps or fewer. */
fun Exercise.warmsUp(): Boolean = tier == Tier.T1 || (tier == Tier.CUSTOM && id in Catalog.barbell && scheme.reps <= 6)

@Composable
fun HomeScreen(
    state: AppState,
    onStart: () -> Unit,
    onSettings: () -> Unit,
    onEditWeight: (Exercise) -> Unit,
    onMode: (Int) -> Unit = {},
    onUseSuggestion: (Exercise, Int) -> Unit = { e, _ -> onEditWeight(e) },
    onStartOpen: () -> Unit = {},
) {
    val p = LocalPalette.current
    val day = state.nextDay
    val exercises = state.exercises(day).map(LocalPlates.current::prescribe)
    val missing = exercises.count { it.weight == null }
    var showOpenSessionSheet by remember { mutableStateOf(false) }
    ScreenFrame(bottom = {
        BottomBar {
            PillButton("Open session", { showOpenSessionSheet = true }, Modifier.weight(1f))
            Spacer(Modifier.width(Space.md))
            PillButton("Start workout", onStart, Modifier.weight(1f), primary = true)
        }
    }) {
        NavRow { IconButton(Icons.settings, "Settings", onSettings, tint = p.fg2) }
        WorkoutSwitch(0, onMode)
        Spacer(Modifier.height(Space.xxl))
        Txt(day.title, Type.largeTitle)
        Spacer(Modifier.height(Space.xs))
        val last = state.sessions.lastOrNull { it.sets.isNotEmpty() }
        Txt(
            state.program.name + " · " + if (last == null) "your first workout" else "last: ${last.day.title} on ${Fmt.date(last.startedAt)}",
            Type.subhead, color = p.fg2,
        )
        Spacer(Modifier.height(Space.xxl))
        Group {
            exercises.forEachIndexed { i, ex ->
                val plan = ex.weight
                val suggested = if (plan != null) state.strengthSuggestion(ex) else null
                GroupRow(first = i == 0, onClick = { onEditWeight(ex) }) {
                    Column(Modifier.weight(1f)) {
                        Txt(ex.name, Type.headline, maxLines = 1)
                        Txt(ex.detail(), Type.footnote, color = p.fg2)
                        if (plan != null && suggested != null && state.shouldSuggest(ex, suggested)) {
                            Spacer(Modifier.height(Space.xs))
                            StrengthSuggestionChip(plan, suggested, { onUseSuggestion(ex, suggested) })
                        }
                    }
                    Txt(ex.weight?.let(Fmt::lb) ?: "Set weight", Type.headline.tabular(), color = p.accent)
                }
            }
        }
        if (missing > 0) GroupFooter(if (missing == 1) "One exercise needs a starting weight." else "$missing exercises need a starting weight.")
    }
    Sheet(showOpenSessionSheet, { showOpenSessionSheet = false }) {
        OpenSessionStartSheet(
            state = state,
            onStartFromToday = {
                showOpenSessionSheet = false
                OpenSessionManager.start(fromToday = true, state, onStartOpen)
            },
            onStartBlank = {
                showOpenSessionSheet = false
                OpenSessionManager.start(fromToday = false, state, onStartOpen)
            },
            onDismiss = { showOpenSessionSheet = false },
        )
    }
}
/** What the rest card needs; null when not resting. */
data class RestUi(val remainingSec: Long, val totalSec: Int, val label: String)

@Composable
fun WorkoutScreen(
    state: AppState,
    now: OffsetDateTime,
    rest: RestUi?,
    onLog: (Exercise, Int, Int, Int, Double?) -> Unit,
    onClear: (Exercise, Int) -> Unit,
    onEditWeight: (Exercise) -> Unit,
    onSwap: (Exercise) -> Unit,
    onSkipRest: () -> Unit,
    onSettings: () -> Unit,
    onEnd: () -> Unit,
    warmups: Boolean = true,
    onUseSuggestion: (Exercise, Int) -> Unit = { e, _ -> onEditWeight(e) },
    onFinishOpen: (counts: Boolean) -> Unit = { onEnd() },
    onMode: ((Int) -> Unit)? = null,
) {
    val p = LocalPalette.current
    val a = state.active ?: return
    if (a.open) {
        LaunchedEffect(a.id, OpenSessionManager.isActive) { OpenSessionManager.attach(state) }
        OpenSessionScreen(
            state = state,
            now = now,
            onLog = onLog,
            onClear = onClear,
            onFinish = { counts ->
                OpenSessionManager.reset()
                onFinishOpen(counts)
            },
            onSettings = onSettings,
            onMode = onMode,
        )
        return
    }
    // The rest timer lives in the fixed bar, never in the list, so logging a set moves no row under the thumb.
    ScreenFrame(bottom = {
        BottomBar {
            if (rest != null && rest.remainingSec > 0) RestBar(rest, onSkipRest, Modifier.weight(1f))
            else PillButton("End workout", onEnd, Modifier.weight(1f))
        }
    }) {
        NavRow { IconButton(Icons.settings, "Settings", onSettings, tint = p.fg2) }
        // Lifting shows the session; Cardio and Stretching stay a tap away mid-lift.
        onMode?.let { WorkoutSwitch(0, it); Spacer(Modifier.height(Space.xxl)) }
        Txt(a.day.title, Type.largeTitle, maxLines = 1)
        Spacer(Modifier.height(Space.xs))
        Txt("${a.program.name} · ${Fmt.minutes(Duration.between(a.startedAt, now).seconds)}", Type.subhead.tabular(), color = p.fg2)
        val exercises = state.sessionExercises().map(LocalPlates.current::prescribe)
        val next = exercises.firstNotNullOfOrNull { ex -> (1..ex.scheme.sets).map { SetKey(ex.id, ex.tier, it) }.firstOrNull { it !in a.sets } }
        exercises.forEach { ex ->
            Spacer(Modifier.height(Space.x3))
            ExerciseBlock(state, ex, next, onLog, onClear, onEditWeight, onSwap, warmups, onUseSuggestion)
        }
    }
}

/** The running rest in the bottom bar: time left over a draining bar, and Skip rest. Same height as End workout. */
@Composable
fun RestBar(rest: RestUi, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(Fmt.clock(rest.remainingSec), Type.title2.tabular(), Modifier.alignByBaseline())
                Spacer(Modifier.width(Space.sm))
                Txt(rest.label, Type.footnote, Modifier.weight(1f).alignByBaseline(), p.fg2, maxLines = 1)
            }
            Spacer(Modifier.height(Space.sm))
            val frac = (rest.remainingSec.toFloat() / rest.totalSec.coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(p.fillPressed)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(p.accent))
            }
        }
        PillButton("Skip rest", onSkip)
    }
}

@Composable
private fun ExerciseBlock(
    state: AppState,
    ex: Exercise,
    next: SetKey?,
    onLog: (Exercise, Int, Int, Int, Double?) -> Unit,
    onClear: (Exercise, Int) -> Unit,
    onEditWeight: (Exercise) -> Unit,
    onSwap: (Exercise) -> Unit,
    warmups: Boolean,
    onUseSuggestion: (Exercise, Int) -> Unit = { e, _ -> onEditWeight(e) },
) {
    val p = LocalPalette.current
    val sets = state.active?.sets.orEmpty()
    val plates = LocalPlates.current.takeIf { ex.id in Catalog.barbell }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(ex.name, Type.title3, maxLines = 1)
            Txt(ex.detail(), Type.footnote, color = p.fg2)
            ex.swappedFrom?.let { Txt("In place of ${state.name(it)}", Type.footnote, color = p.fg2, maxLines = 1) }
            if (plates != null && ex.weight != null) PlateLine(plates, ex.weight)
        }
        if ((1..ex.scheme.sets).none { SetKey(ex.id, ex.tier, it) in sets }) SmallPill("Swap", { onSwap(ex) }, Modifier.padding(start = Space.sm))
        // A substitute has no prescription to edit: its weight is only a suggestion from last time.
        if (ex.swappedFrom != null) ex.weight?.let { Txt(Fmt.lb(it), Type.title3.tabular(), Modifier.padding(start = Space.lg), p.fg2) }
        else Pressable({ onEditWeight(ex) }, Modifier.height(44.dp), label = "Change ${ex.name} weight") {
            Txt(Fmt.lb(ex.weight ?: 0), Type.title3.tabular(), Modifier.padding(start = Space.lg), p.accent)
        }
    }
    FormToggle(ex.id)
    val plan = ex.weight
    val suggested = if (plan != null) state.strengthSuggestion(ex) else null
    if (plan != null && suggested != null && state.shouldSuggest(ex, suggested)) {
        Spacer(Modifier.height(Space.xs))
        StrengthSuggestionChip(plan, suggested, { onUseSuggestion(ex, suggested) })
    }
    Spacer(Modifier.height(Space.sm))
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Txt("Set", Type.caption, Modifier.width(SET_COL), p.fg2)
        // Header labels end where the field digits end, inside the fields' horizontal padding.
        Txt("lb", Type.caption, Modifier.width(WEIGHT_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
        Txt("Reps", Type.caption, Modifier.width(REPS_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
        Txt("RPE", Type.caption, Modifier.width(RPE_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        val allPlates = LocalPlates.current
        // A substitute never logged before has no weight to warm up to.
        if (warmups && ex.warmsUp() && ex.weight != null) Warmup.sets(ex.weight).map { (w, r) -> allPlates.round(w) to r }.distinctBy { it.first }.forEachIndexed { i, (w, r) ->
            WarmupRow(state.active?.id.orEmpty(), "${ex.name} warm-up ${i + 1}", w, r, allPlates)
        }
        for (i in 1..ex.scheme.sets) {
            val key = SetKey(ex.id, ex.tier, i)
            SetRow(
                index = i,
                amrap = ex.scheme.amrap && i == ex.scheme.sets,
                targetReps = ex.scheme.reps,
                defaultWeight = Commands.setWeight(state, ex, i),
                logged = sets[key]?.event,
                current = next == key,
                exName = ex.name,
                plates = plates,
                plateWeight = ex.weight,
                onLog = { w, r, rpe -> onLog(ex, i, w, r, rpe) },
                onClear = { onClear(ex, i) },
            )
        }
    }
}

@Composable
fun StrengthSuggestionChip(
    plan: Int,
    suggested: Int,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    Pressable(
        onClick = onUse,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(p.fill)
            .padding(horizontal = Space.md, vertical = Space.xs)
            .semantics(mergeDescendants = true) {},
        label = "Plan $plan · recent sets say $suggested · Use",
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Txt("Plan $plan · recent sets say $suggested ·", Type.footnote, color = p.fg2)
            Txt("Use", Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = p.accent)
        }
    }
}

private val SET_COL = 32.dp
// Sized so the row fits a 360dp-wide phone with the RPE column.
private val WEIGHT_COL = 76.dp
private val REPS_COL = 60.dp
private val RPE_COL = 56.dp

/** What the RPE field accepts while typing: empty, 1 to 9 with an optional .5, or 10. */
private val RPE_INPUT = Regex("(10|[1-9](\\.5?)?)?")

/** Plates per side for [weight], or that the plates can't make it. */
@Composable
fun PlateLine(plates: Plates, weight: Int, modifier: Modifier = Modifier) =
    Txt(plates.perSideLabel(weight) ?: "Not loadable with your plates", Type.footnote.tabular(), modifier, LocalPalette.current.fg2, maxLines = 1)

/**
 * One set: its own weight and reps, prefilled from the prescription, an optional RPE, and a check that logs or clears it.
 * Its plate line shows only when its weight differs from [plateWeight], the one the exercise header already loads.
 */
@Composable
fun SetRow(
    index: Int,
    amrap: Boolean,
    targetReps: Int,
    defaultWeight: Int?,
    logged: SetLogged?,
    current: Boolean,
    exName: String,
    plates: Plates?,
    plateWeight: Int?,
    onLog: (weight: Int, reps: Int, rpe: Double?) -> Unit,
    onClear: () -> Unit,
) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var w by remember(logged?.weight, defaultWeight) { mutableStateOf((logged?.weight ?: defaultWeight)?.toString() ?: "") }
    val weightFocus = remember { FocusRequester() }
    var r by remember(logged?.actualReps) { mutableStateOf(logged?.actualReps?.toString() ?: if (amrap) "" else targetReps.toString()) }
    fun weight() = w.toIntOrNull() ?: logged?.weight ?: defaultWeight
    var e by remember(logged?.rpe) { mutableStateOf(logged?.rpe?.let(Fmt::rpe) ?: "") }
    fun reps() = r.toIntOrNull() ?: logged?.actualReps ?: targetReps
    val commit = { if (logged != null) onLog(weight() ?: logged.weight, reps(), e.toDoubleOrNull()) }
    val short = logged != null && logged.actualReps < targetReps
    val name = "$exName set $index"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Txt(if (amrap) "$index+" else "$index", Type.headline.tabular(), Modifier.width(SET_COL), if (current) p.fg else p.fg2)
        // No weight known (a first-time substitute) leaves the field empty; the check then asks for one instead of logging 0.
        NumberField(
            w, { w = it.filter(Char::isDigit).take(4) }, last = false, Modifier.focusRequester(weightFocus), placeholder = "",
            width = WEIGHT_COL, onCommit = commit, label = "$name weight",
        )
        NumberField(
            r, { r = it.filter(Char::isDigit).take(3) }, width = REPS_COL, placeholder = if (amrap) "$targetReps+" else "$targetReps",
            color = if (short) p.red else p.fg, onCommit = commit, label = "$name reps",
        )
        NumberField(
            e, { if (RPE_INPUT.matches(it)) e = it }, width = RPE_COL, placeholder = "",
            onCommit = commit, label = "$name RPE", keyboardType = KeyboardType.Decimal,
        )
        Spacer(Modifier.weight(1f))
        val hit = logged != null && !short
        Pressable(
            {
                focus.clearFocus()
                val wt = weight()
                when {
                    logged != null -> {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        onClear()
                    }
                    wt == null -> weightFocus.requestFocus()
                    else -> {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onLog(wt, reps(), e.toDoubleOrNull())
                    }
                }
            },
            Modifier.size(44.dp), CircleShape,
            fill = when {
                hit -> p.accent
                short -> p.red.copy(alpha = 0.18f)
                else -> p.fill
            },
            pressedFill = if (hit) Color.Black.copy(alpha = 0.14f) else p.fillPressed,
            border = if (current) p.accent else null,
            label = if (logged != null) "Clear $name" else "Log $name",
        ) {
            Icon(Icons.check, if (hit) p.onAccent else if (short) p.red else p.fg3, 20.dp)
        }
    }
    val wt = weight()
    if (plates != null && wt != null && wt != plateWeight) PlateLine(plates, wt, Modifier.padding(start = SET_COL + Space.md))
}

/** A warm-up: fixed weight and reps, and a check that only lives on screen (never logged). */
@Composable
private fun WarmupRow(sessionId: String, name: String, weight: Int, reps: Int, plates: Plates) {
    val p = LocalPalette.current
    var done by rememberSaveable(sessionId) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Txt("W", Type.headline, Modifier.width(SET_COL), p.fg3)
        Txt("$weight", Type.body.tabular(), Modifier.width(WEIGHT_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
        Txt("$reps", Type.body.tabular(), Modifier.width(REPS_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
        Spacer(Modifier.weight(1f))
        Pressable(
            { done = !done }, Modifier.size(44.dp), CircleShape,
            fill = if (done) p.accent.copy(alpha = 0.18f) else p.fill,
            label = if (done) "Undo $name" else "Done $name",
        ) {
            Icon(Icons.check, if (done) p.accent else p.fg3, 20.dp)
        }
    }
    plates.perSideLabel(weight)?.let { Txt(it, Type.footnote.tabular(), Modifier.padding(start = SET_COL + Space.md), p.fg3, maxLines = 1) }
}

fun restLabel(ex: Exercise, setIndex: Int): String =
    "Rest after ${ex.name.lowercase()} " + if (setIndex == ex.scheme.sets) "last set" else "set $setIndex"

fun unloggedSets(state: AppState): Int {
    if (state.active == null) return 0
    return state.sessionExercises().sumOf { ex -> Commands.reps(state, ex).count { it == null } }
}
