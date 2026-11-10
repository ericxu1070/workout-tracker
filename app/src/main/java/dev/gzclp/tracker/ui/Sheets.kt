package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.ui.Type.tabular
import java.time.LocalDate
import java.time.OffsetDateTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Exercise

/** Which sheet is open; values being edited live here so the sheet is a pure view. */
sealed interface SheetState {
    /** The working weight; [starting] when the exercise has none yet and the workout waits on it. */
    data class Weight(val ex: Exercise, val weight: Int, val starting: Boolean = ex.weight == null) : SheetState
    data class FiveRm(val ex: Exercise, val fiveRm: Int) : SheetState
    data class EndConfirm(val unlogged: Int) : SheetState
    data class AddExercise(val day: ProgramDay, val name: String) : SheetState
    /** A new weigh-in when [id] is null, else an edit of that one; [delete] marks the Delete button. */
    data class BodyWeight(val id: String?, val text: TextFieldValue, val at: OffsetDateTime, val today: LocalDate, val delete: Boolean = false) : SheetState {
        val lb: Double? get() = text.text.toDoubleOrNull()?.takeIf { it in Commands.BODY_WEIGHT_RANGE && text.text.substringAfter('.', "").length <= 1 }
    }
}

@Composable
private fun SheetTitle(title: String, body: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Txt(title, Type.title2)
        if (body != null) Txt(body, Type.subhead, color = LocalPalette.current.fg2)
    }
}

@Composable
fun ColumnScope.SheetContent(s: SheetState, state: AppState, update: (SheetState) -> Unit, onDismiss: () -> Unit, onConfirm: (SheetState) -> Unit) {
    when (s) {
        is SheetState.Weight -> {
            if (s.starting) SheetTitle("Starting weight for ${s.ex.name.lowercase()}", "${s.ex.detail()}. Pick a weight you can lift for every rep.")
            else SheetTitle("${s.ex.name} weight", "${s.ex.detail()} · saved as a manual edit")
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
                "${s.unlogged} ${if (s.unlogged == 1) "set is" else "sets are"} not logged. An exercise with some sets logged counts the rest as missed; one with none stays where it is.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PillButton("Keep going", onDismiss, Modifier.weight(1f), onSurface = true)
                PillButton("End workout", { onConfirm(s) }, Modifier.weight(1f), primary = true)
            }
        }
        is SheetState.BodyWeight -> {
            val p = LocalPalette.current
            val lb = s.lb
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            SheetTitle(if (s.id == null) "Log weight" else "Edit weigh-in", null)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    s.text, { v -> update(s.copy(text = v.copy(text = v.text.filter { it.isDigit() || it == '.' }.take(5)))) },
                    Modifier.width(IntrinsicSize.Min).focusRequester(focus),
                    textStyle = Type.display.copy(color = p.fg), singleLine = true, cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (lb != null) onConfirm(s) }),
                    decorationBox = { inner ->
                        Box {
                            if (s.text.text.isEmpty()) Txt("0.0", Type.display, color = p.fg3)
                            inner()
                        }
                    },
                )
                Txt(" lb", Type.title3, Modifier.padding(bottom = 12.dp), p.fg2)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniStep(Icons.chevronLeft, "Day before") { update(s.copy(at = s.at.minusDays(1))) }
                Txt(Fmt.weighInTime(s.at, s.today), Type.body.tabular(), Modifier.weight(1f), align = TextAlign.Center)
                MiniStep(Icons.chevronRight, "Day after", enabled = s.at.toLocalDate() < s.today) { update(s.copy(at = s.at.plusDays(1))) }
            }
            val save = if (lb == null) "Save weight" else "Save ${Fmt.lb(lb)}"
            if (s.id == null) PillButton(save, { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true, enabled = lb != null)
            else Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PillButton("Delete", { onConfirm(s.copy(delete = true)) }, Modifier.weight(1f), onSurface = true)
                PillButton(save, { onConfirm(s) }, Modifier.weight(1f), primary = true, enabled = lb != null)
            }
        }
        is SheetState.AddExercise -> {
            val p = LocalPalette.current
            SheetTitle("Add to ${s.day.title}", null)
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(Radius.md)).background(p.fill).padding(horizontal = Space.lg),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (s.name.isEmpty()) Txt("Name, e.g. Face pull", Type.body, color = p.fg3)
                BasicTextField(
                    s.name, { update(s.copy(name = it.take(40))) }, Modifier.fillMaxWidth(),
                    textStyle = Type.body.copy(color = p.fg), singleLine = true, cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            val q = s.name.trim()
            val matches = state.moves.values.filter { q.isNotEmpty() && it.name.contains(q, ignoreCase = true) && !it.name.equals(q, ignoreCase = true) }.take(4)
            if (matches.isNotEmpty()) Group {
                matches.forEachIndexed { i, m ->
                    GroupRow(first = i == 0, onClick = { onConfirm(s.copy(name = m.name)) }) { Txt(m.name, Type.body, color = p.accent) }
                }
            }
            PillButton(if (q.isEmpty()) "Add exercise" else "Add $q", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true, enabled = q.isNotEmpty())
        }
    }
}
