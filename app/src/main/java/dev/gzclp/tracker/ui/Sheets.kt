package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchRoutines
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.WeighInScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.gzclp.tracker.ui.Type.tabular
import java.time.LocalDate
import java.time.OffsetDateTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.DistanceUnit
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Wave
import dev.gzclp.tracker.log.CardioRun
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.ExerciseDefine
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/** Which sheet is open; values being edited live here so the sheet is a pure view. */
sealed interface SheetState {
    /** The working weight; [starting] when the exercise has none yet and the workout waits on it. */
    data class Weight(val ex: Exercise, val weight: Int, val starting: Boolean = ex.weight == null) : SheetState
    data class FiveRm(val ex: Exercise, val fiveRm: Int) : SheetState
    data class EndConfirm(val unlogged: Int) : SheetState
    /** Adds an exercise to [day]: of the active built-in program, or of custom [split] when set. */
    data class AddExercise(val day: ProgramDay, val name: String, val split: String? = null) : SheetState
    /** "Start from": the preset a new split copies; confirmed with the picked [from]. */
    data class NewSplit(val from: String = "") : SheetState
    /** Names split [split]: a new day when [add], else day [day] when set, else the split itself. */
    data class SplitName(val split: String, val day: String?, val name: String, val add: Boolean = false) : SheetState
    /** Sets and week reps for exercise [ex] of [day], or for every exercise in [split] ("Set all") when [ex] is null. */
    data class SplitWave(val split: String, val day: String?, val ex: String?, val title: String, val wave: Wave) : SheetState
    /** Confirms deleting day [day] of [split], or the whole split when [day] is null. */
    data class DeleteSplit(val split: String, val day: String?) : SheetState
    /** Names custom stretch routine [routine], or a new one when null. */
    data class StretchName(val routine: String?, val name: String) : SheetState
    /** Hold time [sec] for move [index] of custom stretch routine [routine]. */
    data class StretchHold(val routine: String, val index: Int, val sec: Int) : SheetState
    /** Picks a move to add to custom stretch routine [routine]; confirmed with the picked [move]. */
    data class AddStretchMove(val routine: String, val move: String = "") : SheetState
    data class DeleteStretchRoutine(val routine: String) : SheetState
    /** Swaps [ex] for the exercise named [name], for the active workout only; [create] asks before adding [name] as a new exercise. */
    data class Swap(val ex: Exercise, val name: String, val create: Boolean = false) : SheetState
    /** A logged meal's photo, with Delete as the confirm action once [delete] has asked. */
    data class Meal(val meal: dev.gzclp.tracker.food.Meal, val today: LocalDate, val delete: Boolean = false) : SheetState
    /** A gallery [photo] about to be saved as a meal at [at]; [note] says why its own time was not usable. */
    data class AddMeal(val photo: java.io.File, val at: OffsetDateTime, val now: OffsetDateTime, val note: String?) : SheetState
    /**
     * A new weigh-in when [id] is null, else an edit of that one, at stop [stop] of [scale], or the [typed] weight when the
     * number was typed; [delete] marks the Delete button.
     */
    data class BodyWeight(
        val id: String?, val scale: WeighInScale, val at: OffsetDateTime, val today: LocalDate, val stop: Int = scale.start, val delete: Boolean = false,
        val typed: Double? = null,
    ) : SheetState {
        val lb: Double get() = typed ?: scale.lb(stop)
    }
    /**
     * Stopping [run]; [maxSec] is its elapsed time when Stop was tapped. [distance] is in the activity's unit and [rpe] is a
     * slider stop (1 is RPE 1, 19 is RPE 10), both 0 for none. [distanceText] holds the distance while it is being typed;
     * [reach] is the longest distance typed, which keeps the slider stretched to it. [now] is set when the sheet logs a past
     * session instead (a blank [CardioRun.id]) or edits the finished one with [run]'s id, and then the activity and start change too.
     */
    data class CardioStop(
        val run: CardioRun, val maxSec: Long, val durationSec: Long = maxSec, val distance: Double = 0.0,
        val distanceText: String? = null, val rpe: Int = 0, val notes: String = "", val reach: Double = 0.0,
        val now: OffsetDateTime? = null,
    ) : SheetState {
        val unit: DistanceUnit? get() = Activities.of(run.activity)?.unit
        val distanceM: Int? get() = unit?.let { Math.round(distance * it.meters).toInt() }?.takeIf { it > 0 }
        val rpeValue: Double? get() = if (rpe == 0) null else 0.5 + rpe * 0.5
    }
    data class CardioDiscard(val run: CardioRun) : SheetState
    /**
     * Correcting finished lifting session [id]'s duration, from its fixed start [startedAt]. [hours] and [minutes] hold the
     * typed fields; [durationSec] keeps its seconds until one is typed, so opening and saving changes nothing.
     */
    data class SessionTime(
        val id: String, val startedAt: OffsetDateTime, val durationSec: Long,
        val hours: String = (durationSec / 3600).toString(), val minutes: String = (durationSec % 3600 / 60).toString(),
    ) : SheetState {
        val ok: Boolean get() = durationSec in Commands.SESSION_DURATION_SEC

        fun typed(h: String, m: String): SessionTime? {
            if (h.length > 2 || m.length > 2 || !h.all(Char::isDigit) || !m.all(Char::isDigit) || (m.toIntOrNull() ?: 0) > 59) return null
            return copy(hours = h, minutes = m, durationSec = (h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60)
        }

        fun withDuration(sec: Long) = SessionTime(id, startedAt, sec)
    }
}

@Composable
private fun SheetTitle(title: String, body: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Txt(title, Type.title2)
        if (body != null) Txt(body, Type.subhead, color = LocalPalette.current.fg2)
    }
}

/** A one-line text field, an exercise name by default. */
@Composable
fun NameField(name: String, placeholder: String = "Name, e.g. Face pull", max: Int = 40, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(Radius.md)).background(p.fill).padding(horizontal = Space.lg),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (name.isEmpty()) Txt(placeholder, Type.body, color = p.fg3)
        BasicTextField(
            name, { onChange(it.take(max)) }, Modifier.fillMaxWidth(),
            textStyle = Type.body.copy(color = p.fg), singleLine = true, cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/** Tappable (label, exercise name) rows under a [NameField]. */
@Composable
private fun Suggestions(items: List<Pair<String, String>>, onPick: (String) -> Unit) {
    if (items.isNotEmpty()) Group {
        items.forEachIndexed { i, (label, name) ->
            GroupRow(first = i == 0, onClick = { onPick(name) }) { Txt(label, Type.body, color = LocalPalette.current.accent) }
        }
    }
}

/** Cancel beside a primary [action], for a sheet's confirm step. */
@Composable
private fun CancelOr(action: String, onCancel: () -> Unit, onAction: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        PillButton("Cancel", onCancel, Modifier.weight(1f), onSurface = true)
        PillButton(action, onAction, Modifier.weight(1f), primary = true)
    }
}

@Composable
fun ColumnScope.SheetContent(
    s: SheetState, state: AppState, update: (SheetState) -> Unit, onDismiss: () -> Unit, onConfirm: (SheetState) -> Unit,
    onPickTime: (SheetState.AddMeal) -> Unit = {}, onPickEnd: (SheetState.CardioStop) -> Unit = {},
    onPickStart: (SheetState.CardioStop) -> Unit = {}, onActivity: (SheetState.CardioStop) -> Unit = {},
    onPickSessionEnd: (SheetState.SessionTime) -> Unit = {},
) {
    when (s) {
        is SheetState.Weight -> {
            if (s.starting) SheetTitle("Starting weight for ${s.ex.name.lowercase()}", s.ex.detail())
            else SheetTitle("${s.ex.name} weight", s.ex.detail())
            Stepper(s.weight.toString(), { update(s.copy(weight = (s.weight - 5).coerceAtLeast(0))) }, { update(s.copy(weight = s.weight + 5)) }, unit = "lb")
            PillButton(
                if (s.starting) "Save and continue" else "Save ${Fmt.lb(s.weight)}", { onConfirm(s) }, Modifier.fillMaxWidth(),
                primary = true, enabled = s.starting || s.weight != s.ex.weight,
            )
        }
        is SheetState.FiveRm -> {
            SheetTitle("New 5RM for ${s.ex.name.lowercase()}", "${s.ex.name} missed 10×1+. It restarts at 5×3+ with 85% of this 5RM: ${Fmt.lb(dev.gzclp.tracker.engine.Progression.t1FromFiveRm(s.fiveRm))}.")
            Stepper(s.fiveRm.toString(), { update(s.copy(fiveRm = (s.fiveRm - 5).coerceAtLeast(5))) }, { update(s.copy(fiveRm = s.fiveRm + 5)) }, unit = "lb")
            PillButton("End workout", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true)
        }
        is SheetState.EndConfirm -> {
            SheetTitle(
                "End workout?",
                "${s.unlogged} ${if (s.unlogged == 1) "set" else "sets"} not logged",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PillButton("Keep going", onDismiss, Modifier.weight(1f), onSurface = true)
                PillButton("End workout", { onConfirm(s) }, Modifier.weight(1f), primary = true)
            }
        }
        is SheetState.BodyWeight -> {
            val p = LocalPalette.current
            val haptic = LocalHapticFeedback.current
            SheetTitle(if (s.id == null) "Log weight" else "Edit weigh-in", null)
            WeighInNumber(s.lb) { lb ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirm(s.copy(typed = lb))
            }
            StepSlider(
                s.stop, s.scale.count,
                onStop = {
                    if (it != s.stop) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    update(s.copy(stop = it, typed = null))
                },
                description = "Weight",
                from = s.scale.start.takeIf { s.scale.centered },
                releaseLabel = "Save ${Fmt.lb(s.lb)}",
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirm(s)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniStep(Icons.chevronLeft, "Day before") { update(s.copy(at = s.at.minusDays(1))) }
                Txt(Fmt.weighInTime(s.at, s.today), Type.body.tabular(), Modifier.weight(1f), align = TextAlign.Center)
                MiniStep(Icons.chevronRight, "Day after", enabled = s.at.toLocalDate() < s.today) { update(s.copy(at = s.at.plusDays(1))) }
            }
            if (s.id != null) PillButton("Delete", { onConfirm(s.copy(delete = true)) }, Modifier.fillMaxWidth(), onSurface = true)
        }
        is SheetState.Meal -> if (!s.delete) MealSheet(s.meal, s.today, { update(s.copy(delete = true)) }, onDismiss) else {
            SheetTitle("Delete this meal?", "Its photo is deleted from this phone and can't be recovered.")
            CancelOr("Delete", { update(s.copy(delete = false)) }) { onConfirm(s) }
        }
        is SheetState.AddMeal -> {
            SheetTitle("Add meal", s.note)
            AddMealSheet(s, { onPickTime(s) }, update) { onConfirm(s) }
        }
        is SheetState.CardioStop -> {
            val n = s.run.name
            // Lowercase reads naturally ("Save running session") but would mangle an acronym like HIIT.
            SheetTitle(
                when {
                    s.now == null -> "Save " + (if (n.drop(1).any(Char::isUpperCase)) n else n.lowercase()) + " session"
                    s.run.id.isEmpty() -> "Log past session"
                    else -> "Edit session"
                },
                null,
            )
            CardioStopSheet(s, state, update, { onPickEnd(s) }, { onPickStart(s) }, { onActivity(s) }) { onConfirm(s) }
        }
        is SheetState.SessionTime -> {
            SheetTitle("Edit duration", null)
            val p = LocalPalette.current
            val end = s.startedAt.plusSeconds(s.durationSec)
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Hours", Type.body, Modifier.weight(1f))
                    NumberField(s.hours, { h -> s.typed(h, s.minutes)?.let(update) }, last = false, label = "Hours")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Minutes", Type.body, Modifier.weight(1f))
                    NumberField(s.minutes, { m -> s.typed(s.hours, m)?.let(update) }, label = "Minutes")
                }
                Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("Ends", Type.body, Modifier.weight(1f))
                    Pressable({ onPickSessionEnd(s) }, Modifier.heightIn(min = 44.dp), label = "Change end time") {
                        Txt(Fmt.time(end), Type.headline.tabular(), color = p.accent)
                    }
                    Txt(Fmt.dayAfter(s.startedAt, end), Type.body, color = p.fg2)
                }
            }
            Txt("Started ${Fmt.time(s.startedAt)}. ${if (s.ok) "" else "Between 1 minute and 24 hours."}".trim(), Type.footnote, color = if (s.ok) p.fg2 else p.red)
            PillButton("Save", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true, enabled = s.ok)
        }
        is SheetState.CardioDiscard -> {
            val finished = s.run.id in state.cardio
            SheetTitle(if (finished) "Delete this session?" else "Discard this session?", null)
            CancelOr(if (finished) "Delete" else "Discard", onDismiss) { onConfirm(s) }
        }
        is SheetState.AddExercise -> {
            SheetTitle("Add to ${s.day.title}", null)
            NameField(s.name) { update(s.copy(name = it)) }
            val q = s.name.trim()
            val matches = state.moves.values.filter { q.isNotEmpty() && it.name.contains(q, ignoreCase = true) && !it.name.equals(q, ignoreCase = true) }
            Suggestions(matches.take(4).map { it.name to it.name }) { onConfirm(s.copy(name = it)) }
            PillButton(if (q.isEmpty()) "Add exercise" else "Add $q", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true, enabled = q.isNotEmpty())
        }
        is SheetState.NewSplit -> {
            val p = LocalPalette.current
            SheetTitle("Start from", null)
            Group {
                Programs.all.filter { !it.gzclp }.forEachIndexed { i, prog ->
                    GroupRow(first = i == 0, onClick = { onConfirm(s.copy(from = prog.id)) }) {
                        Txt(prog.name, Type.body, Modifier.weight(1f), maxLines = 1)
                        Txt("${prog.days.size} days", Type.body, color = p.fg2)
                    }
                }
            }
            PillButton("Cancel", onDismiss, Modifier.fillMaxWidth(), onSurface = true)
        }
        is SheetState.SplitName -> {
            SheetTitle(if (s.add) "Add day" else if (s.day != null) "Rename day" else "Rename split", null)
            NameField(s.name, if (s.day == null && !s.add) "Name, e.g. My PPL" else "Name, e.g. Push A", Commands.SPLIT_NAME_MAX) { update(s.copy(name = it)) }
            PillButton(if (s.add) "Add day" else "Save", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true, enabled = s.name.isNotBlank())
        }
        is SheetState.SplitWave -> {
            val p = LocalPalette.current
            val w = s.wave
            SheetTitle(s.title, null)
            Column {
                Txt("Sets", Type.footnote, color = p.fg2)
                Stepper(
                    w.sets.toString(),
                    { update(s.copy(wave = w.copy(sets = (w.sets - 1).coerceAtLeast(Wave.SETS.first)))) },
                    { update(s.copy(wave = w.copy(sets = (w.sets + 1).coerceAtMost(Wave.SETS.last)))) },
                )
            }
            Group {
                w.reps.forEachIndexed { i, r ->
                    fun set(v: Int) = update(s.copy(wave = w.copy(reps = w.reps.toMutableList().also { it[i] = v.coerceIn(Wave.REPS) })))
                    GroupRow(first = i == 0) {
                        Txt("Week ${i + 1}", Type.body, Modifier.weight(1f))
                        MiniStep(Icons.minus, "Fewer week ${i + 1} reps", enabled = r > Wave.REPS.first) { set(r - 1) }
                        Txt("$r", Type.body.tabular(), Modifier.width(56.dp), align = TextAlign.Center)
                        MiniStep(Icons.plus, "More week ${i + 1} reps", enabled = r < Wave.REPS.last) { set(r + 1) }
                    }
                }
            }
            PillButton(if (s.ex == null) "Set all" else "Save", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true)
        }
        is SheetState.DeleteSplit -> {
            val split = state.customPrograms[s.split]
            val day = s.day?.let { split?.day(it) }
            SheetTitle(
                "Delete ${day?.title ?: split?.name}?",
                // Deleting the split in use hands the workout to its preset; say which.
                if (s.day == null && state.programId == s.split) split?.from?.let { "${Programs.of(it).name} takes over." } else null,
            )
            CancelOr("Delete", onDismiss) { onConfirm(s) }
        }
        is SheetState.StretchName -> {
            SheetTitle(if (s.routine == null) "New routine" else "Rename routine", null)
            NameField(s.name, "Name, e.g. Morning", Commands.SPLIT_NAME_MAX) { update(s.copy(name = it)) }
            PillButton(if (s.routine == null) "Create routine" else "Save", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true, enabled = s.name.isNotBlank())
        }
        is SheetState.StretchHold -> {
            val move = state.customStretches[s.routine]?.moves?.getOrNull(s.index) ?: return
            val p = LocalPalette.current
            SheetTitle(move.name, null)
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(Radius.md)).background(p.bg)) {
                FormView(Motions.of(move), Modifier.fillMaxSize(), focus = move.focus, still = 1f)
            }
            val hold = StretchRoutines.HOLD_SEC
            Stepper(
                "${s.sec}",
                { update(s.copy(sec = (s.sec - StretchRoutines.HOLD_STEP).coerceAtLeast(hold.first))) },
                { update(s.copy(sec = (s.sec + StretchRoutines.HOLD_STEP).coerceAtMost(hold.last))) },
                unit = "sec", repeat = true,
            )
            PillButton("Save", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true)
        }
        is SheetState.AddStretchMove -> {
            val p = LocalPalette.current
            SheetTitle("Add move", null)
            Group(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                StretchMoves.ALL.forEachIndexed { i, move ->
                    GroupRow(first = i == 0, onClick = { onConfirm(s.copy(move = move.id)) }) {
                        MoveThumb(move)
                        Spacer(Modifier.width(Space.sm))
                        Column(Modifier.weight(1f)) {
                            Txt(move.name, Type.body, maxLines = 1)
                            Txt(move.targetArea.displayName, Type.footnote, color = p.fg2)
                        }
                        Txt("${move.holdSec}s", Type.body.tabular(), Modifier.padding(start = Space.sm), p.fg2)
                    }
                }
            }
            PillButton("Cancel", onDismiss, Modifier.fillMaxWidth(), onSurface = true)
        }
        is SheetState.DeleteStretchRoutine -> {
            SheetTitle("Delete ${state.customStretches[s.routine]?.name}?", null)
            CancelOr("Delete", onDismiss) { onConfirm(s) }
        }
        is SheetState.Swap -> if (s.create) {
            val q = s.name.trim()
            SheetTitle("Create \"$q\"?", "Adds it as a new exercise and swaps it in for this workout.")
            CancelOr("Create", { update(s.copy(create = false)) }) { onConfirm(s) }
        } else {
            val original = state.name(s.ex.swappedFrom ?: s.ex.id)
            SheetTitle("Swap ${s.ex.name.lowercase()}", "For this workout only")
            NameField(s.name) { update(s.copy(name = it)) }
            val q = s.name.trim()
            val back = if (s.ex.swappedFrom != null && q.isEmpty()) listOf("Back to $original" to original) else emptyList()
            val matches = state.moves.values.map { it.name }.filter {
                q.isNotEmpty() && it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) && Commands.swap(state, s.ex, it).isNotEmpty()
            }
            Suggestions(back + matches.take(4).map { it to it }) { onConfirm(s.copy(name = it)) }
            val events = Commands.swap(state, s.ex, q)
            PillButton(
                if (q.isEmpty()) "Swap exercise" else "Swap for $q", { if (events.any { it is ExerciseDefine }) update(s.copy(create = true)) else onConfirm(s) },
                Modifier.fillMaxWidth(), primary = true, enabled = events.isNotEmpty(),
            )
        }
    }
}

/**
 * The weigh-in readout. A tap selects it for typing and Done saves the typed weight, so the same weight as last time
 * saves without a drag; a slider move resets it to the slider's weight.
 */
@Composable
private fun WeighInNumber(lb: Double, onSave: (Double) -> Unit) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    val shown = Fmt.tenths(lb)
    var edit by remember(lb) { mutableStateOf<TextFieldValue?>(null) }
    var bad by remember(lb) { mutableStateOf(false) }
    // The tap that focuses the field also drops a caret; the first selection after focus is widened back to all.
    var selectAll by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        BasicTextField(
            edit ?: TextFieldValue(shown, TextRange(shown.length)),
            { v ->
                val text = v.text.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
                edit = if (selectAll && text == shown) v.copy(selection = TextRange(0, text.length)) else v.copy(text = text)
                selectAll = false
                bad = false
            },
            Modifier.width(IntrinsicSize.Min).widthIn(min = 40.dp)
                .onFocusChanged {
                    edit = if (it.isFocused) TextFieldValue(shown, TextRange(0, shown.length)) else null
                    selectAll = it.isFocused
                }
                .semantics { contentDescription = "Weight in pounds" },
            textStyle = Type.display.copy(color = if (bad) p.red else p.fg),
            singleLine = true, cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val typed = (edit?.text ?: shown).replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 10) / 10.0 }
                if (typed == null || typed !in Commands.BODY_WEIGHT_RANGE) bad = true else {
                    focus.clearFocus()
                    onSave(typed)
                }
            }),
        )
        Txt(" lb", Type.title3, Modifier.padding(bottom = 12.dp), p.fg2)
    }
}
