package dev.gzclp.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Progression
import dev.gzclp.tracker.engine.Cycle
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.SetLogged
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.SetKey
import dev.gzclp.tracker.ui.Type.tabular
import java.time.Duration
import java.time.OffsetDateTime

/** A full-height screen: scrolling content under the status bar, optional bottom bar. */
@Composable
fun ScreenFrame(bottom: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().background(p.bg).imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Space.lg).padding(bottom = Space.x3),
            content = content,
        )
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
            "Enter a recent 5-rep max for each lift. GZCLP starts T1 at 85% of it and T2 at 65%. The cycle programs start week 1 at 65%. All rounded to 5 lb.",
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
        GroupFooter("You can switch programs any time in Settings. History and each exercise's progress carry over.")
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
) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Box(modifier.width(width).height(44.dp).clip(RoundedCornerShape(Radius.md)).background(p.fill).padding(horizontal = Space.md), contentAlignment = Alignment.CenterEnd) {
        if (value.isEmpty()) Txt(placeholder, Type.body.tabular(), color = p.fg3, align = TextAlign.End)
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth()
                .onFocusChanged { if (focused && !it.isFocused) onCommit(); focused = it.isFocused }
                .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
            textStyle = Type.body.tabular().copy(color = color, textAlign = TextAlign.End),
            singleLine = true, cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (last) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
    }
}

/** "T1 · 5×3+" or "Week 2 of 3 · 3×10". */
fun Exercise.detail(): String =
    if (tier == Tier.CYCLE) "Week ${(week ?: 0) + 1} of ${Cycle.weeks.size} · ${scheme.display}" else "${tier.label} · ${scheme.display}"

@Composable
fun HomeScreen(state: AppState, onStart: () -> Unit, onSettings: () -> Unit, onEditWeight: (Exercise) -> Unit) {
    val p = LocalPalette.current
    val day = state.nextDay
    val exercises = state.exercises(day)
    val missing = exercises.count { it.weight == null }
    ScreenFrame(bottom = {
        BottomBar { PillButton("Start workout", onStart, Modifier.weight(1f), primary = true) }
    }) {
        NavRow { IconButton(Icons.settings, "Settings", onSettings, tint = p.fg2) }
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
                GroupRow(first = i == 0, onClick = { onEditWeight(ex) }) {
                    Column(Modifier.weight(1f)) {
                        Txt(ex.name, Type.headline, maxLines = 1)
                        Txt(ex.detail(), Type.footnote, color = p.fg2)
                    }
                    Txt(ex.weight?.let(Fmt::lb) ?: "Set weight", Type.headline.tabular(), color = p.accent)
                }
            }
        }
        if (missing > 0) GroupFooter(if (missing == 1) "One exercise needs a starting weight." else "$missing exercises need a starting weight.")
    }
}
/** What the rest card needs; null when not resting. */
data class RestUi(val remainingSec: Long, val totalSec: Int, val label: String)

@Composable
fun WorkoutScreen(
    state: AppState,
    now: OffsetDateTime,
    rest: RestUi?,
    onLog: (Exercise, Int, Int, Int) -> Unit,
    onClear: (Exercise, Int) -> Unit,
    onEditWeight: (Exercise) -> Unit,
    onSkipRest: () -> Unit,
    onSettings: () -> Unit,
    onEnd: () -> Unit,
) {
    val p = LocalPalette.current
    val a = state.active ?: return
    ScreenFrame(bottom = {
        BottomBar { PillButton("End workout", onEnd, Modifier.weight(1f), primary = true) }
    }) {
        NavRow { IconButton(Icons.settings, "Settings", onSettings, tint = p.fg2) }
        Row(Modifier.fillMaxWidth()) {
            Txt(a.day.title, Type.largeTitle, Modifier.weight(1f).alignByBaseline(), maxLines = 1)
            Txt(Fmt.clock(Duration.between(a.startedAt, now).seconds), Type.title3.tabular(), Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(Space.xs))
        Txt("${a.program.name} · started ${Fmt.time(a.startedAt)}", Type.subhead, color = p.fg2)
        AnimatedVisibility(
            rest != null && rest.remainingSec > 0,
            enter = expandVertically(tween(240)) + fadeIn(tween(240)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            if (rest != null) RestCard(rest, onSkipRest, Modifier.padding(top = Space.xl))
        }
        val exercises = state.exercises(a.program, a.day)
        val next = exercises.firstNotNullOfOrNull { ex -> (1..ex.scheme.sets).map { SetKey(ex.id, ex.tier, it) }.firstOrNull { it !in a.sets } }
        exercises.forEach { ex ->
            Spacer(Modifier.height(Space.x3))
            ExerciseBlock(state, ex, next, onLog, onClear, onEditWeight)
        }
    }
}

@Composable
fun RestCard(rest: RestUi, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(p.elevated).padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val frac = (rest.remainingSec.toFloat() / rest.totalSec.coerceAtLeast(1)).coerceIn(0f, 1f)
        Canvas(Modifier.size(44.dp)) {
            val w = 4.dp.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(p.separator, 0f, 360f, false, tl, arcSize, style = Stroke(w))
            drawArc(p.accent, -90f, 360f * frac, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Txt(Fmt.clock(rest.remainingSec), Type.title1)
            Txt(rest.label, Type.footnote, color = p.fg2, maxLines = 1)
        }
        SmallPill("Skip rest", onSkip)
    }
}

@Composable
private fun ExerciseBlock(
    state: AppState,
    ex: Exercise,
    next: SetKey?,
    onLog: (Exercise, Int, Int, Int) -> Unit,
    onClear: (Exercise, Int) -> Unit,
    onEditWeight: (Exercise) -> Unit,
) {
    val p = LocalPalette.current
    val sets = state.active?.sets.orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(ex.name, Type.title3, maxLines = 1)
            Txt(ex.detail(), Type.footnote, color = p.fg2)
        }
        Pressable({ onEditWeight(ex) }, Modifier.height(44.dp), label = "Change ${ex.name} weight") {
            Txt(Fmt.lb(ex.weight ?: 0), Type.title3.tabular(), Modifier.padding(start = Space.lg), p.accent)
        }
    }
    Spacer(Modifier.height(Space.sm))
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Txt("Set", Type.caption, Modifier.width(SET_COL), p.fg2)
        // Header labels end where the field digits end, inside the fields' horizontal padding.
        Txt("lb", Type.caption, Modifier.width(WEIGHT_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
        Txt("Reps", Type.caption, Modifier.width(REPS_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
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
                onLog = { w, r -> onLog(ex, i, w, r) },
                onClear = { onClear(ex, i) },
            )
        }
    }
}

private val SET_COL = 32.dp
private val WEIGHT_COL = 88.dp
private val REPS_COL = 72.dp

/** One set: its own weight and reps, prefilled from the prescription, and a check that logs or clears it. */
@Composable
fun SetRow(
    index: Int,
    amrap: Boolean,
    targetReps: Int,
    defaultWeight: Int,
    logged: SetLogged?,
    current: Boolean,
    exName: String,
    onLog: (weight: Int, reps: Int) -> Unit,
    onClear: () -> Unit,
) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    var w by remember(logged?.weight, defaultWeight) { mutableStateOf((logged?.weight ?: defaultWeight).toString()) }
    var r by remember(logged?.actualReps) { mutableStateOf(logged?.actualReps?.toString() ?: if (amrap) "" else targetReps.toString()) }
    fun weight() = w.toIntOrNull() ?: logged?.weight ?: defaultWeight
    fun reps() = r.toIntOrNull() ?: logged?.actualReps ?: targetReps
    val commit = { if (logged != null) onLog(weight(), reps()) }
    val short = logged != null && logged.actualReps < targetReps
    val name = "$exName set $index"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Txt(if (amrap) "$index+" else "$index", Type.headline.tabular(), Modifier.width(SET_COL), if (current) p.fg else p.fg2)
        NumberField(w, { w = it.filter(Char::isDigit).take(4) }, last = false, width = WEIGHT_COL, onCommit = commit, label = "$name weight")
        NumberField(
            r, { r = it.filter(Char::isDigit).take(3) }, width = REPS_COL, placeholder = if (amrap) "$targetReps+" else "$targetReps",
            color = if (short) p.red else p.fg, onCommit = commit, label = "$name reps",
        )
        Spacer(Modifier.weight(1f))
        val hit = logged != null && !short
        Pressable(
            {
                focus.clearFocus()
                if (logged != null) onClear() else onLog(weight(), reps())
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
}

fun restLabel(ex: Exercise, setIndex: Int): String =
    "Rest after ${ex.name.lowercase()} " + if (setIndex == ex.scheme.sets) "last set" else "set $setIndex"

fun unloggedSets(state: AppState): Int {
    val a = state.active ?: return 0
    return state.exercises(a.program, a.day).sumOf { ex -> Commands.reps(state, ex).count { it == null } }
}
