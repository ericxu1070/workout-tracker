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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
fun SetupScreen(onStart: (Map<Lift, Int>) -> Unit, onRestore: () -> Unit, message: String?, initial: Map<Lift, String> = emptyMap()) {
    val p = LocalPalette.current
    val values = remember { mutableStateMapOf<Lift, String>().apply { putAll(initial) } }
    val parsed = Lift.entries.associateWith { values[it]?.toIntOrNull()?.takeIf { v -> v in 20..1500 } }
    val ready = parsed.values.all { it != null }
    ScreenFrame(bottom = {
        BottomBar {
            PillButton("Restore log", onRestore, Modifier.weight(1f))
            PillButton("Start program", { onStart(parsed.mapValues { it.value!! }) }, Modifier.weight(1f), primary = true, enabled = ready)
        }
    }) {
        Spacer(Modifier.height(Space.x4))
        Txt("Starting weights", Type.largeTitle)
        Spacer(Modifier.height(Space.sm))
        Txt("Enter a recent 5-rep max for each lift. T1 starts at 85% of it and T2 at 65%, rounded to 5 lb.", Type.body, color = p.fg2)
        Spacer(Modifier.height(Space.xxl))
        Group {
            Lift.entries.forEachIndexed { i, lift ->
                GroupRow(first = i == 0) {
                    Column(Modifier.weight(1f)) {
                        Txt(lift.label, Type.headline)
                        val rm = parsed[lift]
                        Txt(
                            if (rm == null) "5RM" else "T1 ${Fmt.lb(Progression.t1FromFiveRm(rm))} · T2 ${Fmt.lb(Progression.t2FromFiveRm(rm))}",
                            Type.footnote, color = p.fg2,
                        )
                    }
                    NumberField(values[lift].orEmpty(), { v -> values[lift] = v.filter(Char::isDigit).take(4) }, last = i == Lift.entries.lastIndex)
                    Txt(" lb", Type.body, color = p.fg2)
                }
            }
        }
        if (message != null) GroupFooter(message, color = p.red)
    }
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, last: Boolean = true, modifier: Modifier = Modifier, placeholder: String = "0") {
    val p = LocalPalette.current
    Box(modifier.width(88.dp).height(44.dp).clip(RoundedCornerShape(Radius.md)).background(p.fill).padding(horizontal = Space.md), contentAlignment = Alignment.CenterEnd) {
        if (value.isEmpty()) Txt(placeholder, Type.body.tabular(), color = p.fg3, align = TextAlign.End)
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(),
            textStyle = Type.body.tabular().copy(color = p.fg, textAlign = TextAlign.End),
            singleLine = true, cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        )
    }
}

@Composable
fun HomeScreen(state: AppState, onStart: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, onEditWeight: (Exercise) -> Unit) {
    val p = LocalPalette.current
    val day = state.nextDay
    ScreenFrame(bottom = {
        BottomBar {
            PillButton("History", onHistory, Modifier.weight(1f))
            PillButton("Start workout", onStart, Modifier.weight(1f), primary = true)
        }
    }) {
        NavRow { IconButton(Icons.settings, "Settings", onSettings, tint = p.fg2) }
        Txt("Workout ${day.name}", Type.largeTitle)
        Spacer(Modifier.height(Space.xs))
        val last = state.sessions.lastOrNull { it.sets.isNotEmpty() }
        Txt(
            if (last == null) "Your first workout" else "Last: ${last.day.name} on ${Fmt.date(last.startedAt)}, ${Fmt.minutes(last.durationSec)}",
            Type.subhead, color = p.fg2,
        )
        Spacer(Modifier.height(Space.xxl))
        Group {
            state.exercises(day).forEachIndexed { i, ex ->
                GroupRow(first = i == 0, onClick = { onEditWeight(ex) }) {
                    Column(Modifier.weight(1f)) {
                        Txt(ex.name, Type.headline, maxLines = 1)
                        Txt("${ex.tier.label} · ${ex.scheme.display}", Type.footnote, color = p.fg2)
                    }
                    Txt(Fmt.lb(ex.state.weight), Type.headline.tabular(), color = p.accent)
                }
            }
        }
        Spacer(Modifier.height(Space.xxl))
        Txt("After this", Type.footnote, Modifier.padding(start = Space.lg, bottom = Space.sm), p.fg2)
        Txt(
            "Workout ${day.next.name}: " + state.exercises(day.next).joinToString(", ") { it.name },
            Type.subhead, Modifier.padding(horizontal = Space.lg), p.fg2,
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
    onSet: (Exercise, Int) -> Unit,
    onLongSet: (Exercise, Int) -> Unit,
    onEditWeight: (Exercise) -> Unit,
    onSkipRest: () -> Unit,
    onHistory: () -> Unit,
    onEnd: () -> Unit,
) {
    val p = LocalPalette.current
    val a = state.active ?: return
    ScreenFrame(bottom = {
        BottomBar {
            PillButton("History", onHistory, Modifier.weight(1f))
            PillButton("End workout", onEnd, Modifier.weight(1f), primary = true)
        }
    }) {
        Spacer(Modifier.height(52.dp))
        Row(Modifier.fillMaxWidth()) {
            Txt("Workout ${a.day.name}", Type.largeTitle, Modifier.weight(1f).alignByBaseline())
            Txt(Fmt.clock(Duration.between(a.startedAt, now).seconds), Type.title3.tabular(), Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(Space.xs))
        Txt("Started ${Fmt.time(a.startedAt)}", Type.subhead, color = p.fg2)
        AnimatedVisibility(
            rest != null && rest.remainingSec > 0,
            enter = expandVertically(tween(240)) + fadeIn(tween(240)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            if (rest != null) RestCard(rest, onSkipRest, Modifier.padding(top = Space.xl))
        }
        val exercises = state.exercises(a.day)
        val next = exercises.firstNotNullOfOrNull { ex -> (1..ex.scheme.sets).firstOrNull { SetKey(ex.liftId, ex.tier, it) !in a.sets }?.let { SetKey(ex.liftId, ex.tier, it) } }
        exercises.forEach { ex ->
            Spacer(Modifier.height(Space.x3))
            ExerciseBlock(state, ex, next, onSet, onLongSet, onEditWeight)
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
private fun ExerciseBlock(state: AppState, ex: Exercise, next: SetKey?, onSet: (Exercise, Int) -> Unit, onLongSet: (Exercise, Int) -> Unit, onEditWeight: (Exercise) -> Unit) {
    val p = LocalPalette.current
    val sets = state.active?.sets.orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(ex.name, Type.title3, maxLines = 1)
            Txt("${ex.tier.label} · ${ex.scheme.display}", Type.footnote, color = p.fg2)
        }
        Pressable({ onEditWeight(ex) }, Modifier.height(44.dp), label = "Change weight") {
            Txt(Fmt.lb(ex.state.weight), Type.title3.tabular(), Modifier.padding(start = Space.lg), p.accent)
        }
    }
    Spacer(Modifier.height(Space.md))
    val n = ex.scheme.sets
    val perRow = if (n <= 5) n else (n + 1) / 2
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chipW = (maxWidth - Space.sm * 4) / 5
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            (1..n).chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    row.forEach { i ->
                        val logged = sets[SetKey(ex.liftId, ex.tier, i)]?.event
                        val amrap = ex.scheme.amrap && i == n
                        SetChip(
                            reps = logged?.actualReps?.toString() ?: (ex.scheme.reps.toString() + if (amrap) "+" else ""),
                            label = if (amrap) "last set" else "set $i",
                            done = logged != null,
                            short = logged != null && logged.actualReps < ex.scheme.reps,
                            current = next == SetKey(ex.liftId, ex.tier, i),
                            modifier = Modifier.width(chipW),
                            onClick = { onSet(ex, i) },
                            onLongClick = { onLongSet(ex, i) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetChip(reps: String, label: String, done: Boolean, short: Boolean, current: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val p = LocalPalette.current
    val hit = done && !short
    Pressable(
        onClick, modifier.height(64.dp), RoundedCornerShape(Radius.md),
        fill = if (hit) p.accent else p.elevated,
        pressedFill = if (hit) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.14f) else p.fillPressed,
        border = if (current) p.accent else null,
        onLongClick = onLongClick, label = "$label, $reps reps",
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(reps, Type.title3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold).tabular(), color = when {
                hit -> p.onAccent
                short -> p.red
                else -> p.fg
            })
            Txt(label, Type.caption, color = if (hit) p.onAccent.copy(alpha = 0.72f) else p.fg2, maxLines = 1)
        }
    }
}

fun restLabel(ex: Exercise, setIndex: Int): String =
    "Rest after ${ex.name.lowercase()} " + if (setIndex == ex.scheme.sets) "last set" else "set $setIndex"

fun unloggedSets(state: AppState): Int {
    val a = state.active ?: return 0
    return state.exercises(a.day).sumOf { ex -> Commands.reps(state, ex).count { it == null } }
}
