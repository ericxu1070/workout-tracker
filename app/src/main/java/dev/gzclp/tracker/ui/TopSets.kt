package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Scheme
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.SetKey
import dev.gzclp.tracker.log.SetLogged
import dev.gzclp.tracker.ui.Type.tabular

/** The one line a top-sets session logs for [ex]. */
fun topKey(ex: Exercise) = SetKey(ex.id, ex.tier, 1)

/** Names the open top-sets day's exercises still unchecked, which Finish leaves out; null when every one is logged. */
fun AppState.topUnlogged(): String? {
    val missed = active?.let { a -> exercises(a.program, a.day).filter { topKey(it) !in a.sets } }.orEmpty()
    return missed.singleOrNull()?.let { "${it.name} is not logged" } ?: missed.size.takeIf { it > 1 }?.let { "$it exercises are not logged" }
}

/**
 * A top-sets session's lines: its day's exercises, then the ones added to it, both the logged and those in [added] still waiting.
 * An added exercise has no prescription: it starts from its last top-sets line, or a weight its history suggests.
 */
fun topLines(state: AppState, added: List<String>): List<Exercise> {
    val a = state.active ?: return emptyList()
    val day = state.exercises(a.program, a.day)
    // In the order added, so logging one never moves a row; a logged one is also read back from the log.
    val extra = (added + a.sets.keys.filter { k -> day.none { it.key == k.exercise to k.tier } }.map { it.exercise }).distinct().filter { id -> day.none { it.id == id } }
    return day + extra.map { id ->
        val last = Commands.lastTop(state, id)
        Exercise(id, state.name(id), Tier.CUSTOM, Scheme(last?.sets ?: 3, last?.actualReps ?: 10, false), null)
    }
}

/** A session logged as one line per exercise: its best set and how many sets it got. No clock runs; Finish asks how long it took. */
@Composable
fun TopSetsScreen(
    state: AppState,
    onLog: (Exercise, weight: Int, reps: Int, sets: Int) -> Unit,
    onClear: (Exercise) -> Unit,
    onFinish: () -> Unit,
) {
    val p = LocalPalette.current
    val a = state.active ?: return
    var added by rememberSaveable(a.id) { mutableStateOf(listOf<String>()) }
    var adding by remember { mutableStateOf(false) }
    val lines = topLines(state, added)
    val next = lines.firstOrNull { topKey(it) !in a.sets }
    // Finish stays quiet until a line is logged, as an open session's does.
    val any = a.sets.isNotEmpty()
    ScreenFrame(bottom = {
        BottomBar { PillButton(if (any) "Finish" else "Cancel", onFinish, Modifier.weight(1f), primary = any) }
    }) {
        Spacer(Modifier.height(Space.xxl))
        Txt(a.day.title, Type.largeTitle, maxLines = 1)
        Spacer(Modifier.height(Space.xs))
        Txt("Top sets", Type.subhead, color = p.fg2)
        Spacer(Modifier.height(Space.x3))
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Spacer(Modifier.weight(1f))
            // Header labels end where the field digits end, inside the fields' horizontal padding.
            Txt("lb", Type.caption, Modifier.width(TOP_WEIGHT_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
            Txt("Reps", Type.caption, Modifier.width(TOP_REPS_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
            Txt("Sets", Type.caption, Modifier.width(TOP_SETS_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
            Spacer(Modifier.width(44.dp))
        }
        StepPillHost(p.bg, Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                lines.forEach { ex ->
                    val logged = a.sets[topKey(ex)]?.event
                    TopRow(
                        ex, logged, ex.weight ?: Commands.lastTop(state, ex.id)?.weight ?: Commands.suggestWeight(state, ex.id, ex.scheme.reps), ex == next,
                        // A line with no prescription aims at what it logs, so its reps never read as short.
                        onLog = { w, r, n -> onLog(if (ex.tier == Tier.CUSTOM && ex.wave == null) ex.copy(scheme = Scheme(n, r, false)) else ex, w, r, n) },
                        onClear = { onClear(ex) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.x3))
        PillButton("Add exercise", { adding = true }, Modifier.fillMaxWidth(), icon = Icons.plus)
    }
    Sheet(adding, { adding = false }) {
        AddExerciseSheet(
            state = state,
            onSelect = { id ->
                added = added + id
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

// Sized so the row leaves the name room on a 360dp-wide phone.
private val TOP_WEIGHT_COL = 64.dp
private val TOP_REPS_COL = 52.dp
private val TOP_SETS_COL = 48.dp

/**
 * One exercise of a top-sets session: its best set's weight and reps, how many sets it got in all, and a check that logs or clears
 * the line. The fields start from the prescription, or [defaultWeight] where there is none.
 */
@Composable
private fun TopRow(
    ex: Exercise,
    logged: SetLogged?,
    defaultWeight: Int,
    current: Boolean,
    onLog: (weight: Int, reps: Int, sets: Int) -> Unit,
    onClear: () -> Unit,
) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val feel = LocalMotion.current.feel
    var w by remember(logged?.weight, defaultWeight) { mutableStateOf((logged?.weight ?: defaultWeight).toString()) }
    var r by remember(logged?.actualReps, ex.scheme.reps) { mutableStateOf((logged?.actualReps ?: ex.scheme.reps).toString()) }
    var n by remember(logged?.sets, ex.scheme.sets) { mutableStateOf((logged?.sets ?: ex.scheme.sets).toString()) }
    val weightFocus = remember { FocusRequester() }
    fun weight() = w.toIntOrNull() ?: logged?.weight
    fun reps() = r.toIntOrNull() ?: logged?.actualReps ?: ex.scheme.reps
    fun sets() = (n.toIntOrNull() ?: logged?.sets ?: ex.scheme.sets).coerceIn(Commands.TOP_SETS)
    val commit = { if (logged != null) onLog(weight() ?: logged.weight, reps(), sets()) }
    val short = logged != null && logged.actualReps < logged.targetReps
    val name = "${ex.name} top set"
    val fieldFill = pillFill(current)
    Row(Modifier.fillMaxWidth().stepPill(current), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Txt(ex.name, Type.subhead.copy(fontWeight = Type.headline.fontWeight), Modifier.weight(1f), if (current) p.fg else p.fg2, maxLines = 2)
        NumberField(
            w, { w = it.filter(Char::isDigit).take(4) }, last = false, Modifier.focusRequester(weightFocus), placeholder = "",
            width = TOP_WEIGHT_COL, onCommit = commit, label = "$name weight", fill = fieldFill,
        )
        NumberField(
            r, { r = it.filter(Char::isDigit).take(3) }, last = false, width = TOP_REPS_COL, placeholder = "${ex.scheme.reps}",
            color = if (short) p.red else p.fg, onCommit = commit, label = "$name reps", fill = fieldFill,
        )
        NumberField(
            n, { n = it.filter(Char::isDigit).take(2) }, width = TOP_SETS_COL, placeholder = "${ex.scheme.sets}",
            onCommit = commit, label = "${ex.name} sets", fill = fieldFill,
        )
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
                    // An emptied weight field asks for one instead of logging 0.
                    wt == null -> weightFocus.requestFocus()
                    else -> {
                        if (feel == null) haptic.performHapticFeedback(HapticFeedbackType.Confirm) else feel.land(0.3f)
                        onLog(wt, reps(), sets())
                    }
                }
            },
            Modifier.size(44.dp).semantics { contentDescription = if (logged != null) "Clear $name" else "Log $name" }, CircleShape,
            fill = when {
                hit -> p.accent
                short -> p.red.copy(alpha = 0.18f)
                else -> fieldFill
            },
            pressedFill = if (hit) Color.Black.copy(alpha = 0.14f) else p.fillPressed,
            border = if (current) p.accent else null,
            label = if (logged != null) "Clear $name" else "Log $name",
            scale = SMALL_PRESS,
        ) {
            Icon(Icons.check, if (hit) p.onAccent else if (short) p.red else p.fg3, 20.dp)
        }
    }
}
