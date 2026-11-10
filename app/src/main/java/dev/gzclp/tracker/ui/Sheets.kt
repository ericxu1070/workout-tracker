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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Day
import dev.gzclp.tracker.log.Exercise

/** Which sheet is open; values being edited live here so the sheet is a pure view. */
sealed interface SheetState {
    data class Reps(val ex: Exercise, val setIndex: Int, val reps: Int, val logged: Boolean) : SheetState
    data class Weight(val ex: Exercise, val weight: Int) : SheetState
    data class FiveRm(val ex: Exercise, val fiveRm: Int) : SheetState
    data class EndConfirm(val unlogged: Int) : SheetState
    data class AddT3(val day: Day, val name: String, val weight: Int) : SheetState
}

@Composable
private fun SheetTitle(title: String, body: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Txt(title, Type.title2)
        if (body != null) Txt(body, Type.subhead, color = LocalPalette.current.fg2)
    }
}

@Composable
fun ColumnScope.SheetContent(s: SheetState, update: (SheetState) -> Unit, onDismiss: () -> Unit, onConfirm: (SheetState) -> Unit, onClear: (SheetState.Reps) -> Unit) {
    when (s) {
        is SheetState.Reps -> {
            val amrap = s.ex.scheme.amrap && s.setIndex == s.ex.scheme.sets
            SheetTitle(
                "${s.ex.name} · " + if (amrap) "last set" else "set ${s.setIndex}",
                "Target ${s.ex.scheme.reps}${if (amrap) "+" else ""} reps at ${Fmt.lb(s.ex.state.weight)}",
            )
            Stepper(s.reps.toString(), { update(s.copy(reps = (s.reps - 1).coerceAtLeast(0))) }, { update(s.copy(reps = s.reps + 1)) }, unit = "reps")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                if (s.logged) PillButton("Clear set", { onClear(s) }, Modifier.weight(1f), onSurface = true)
                PillButton("Log ${s.reps} reps", { onConfirm(s) }, Modifier.weight(1f), primary = true)
            }
        }
        is SheetState.Weight -> {
            SheetTitle("${s.ex.name} ${s.ex.tier.label}", "${s.ex.scheme.display} · saved as a manual edit")
            Stepper(s.weight.toString(), { update(s.copy(weight = (s.weight - 5).coerceAtLeast(0))) }, { update(s.copy(weight = s.weight + 5)) }, unit = "lb")
            PillButton("Save ${Fmt.lb(s.weight)}", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true, enabled = s.weight != s.ex.state.weight)
        }
        is SheetState.FiveRm -> {
            SheetTitle("New 5RM for ${s.ex.name.lowercase()}", "${s.ex.name} missed 10×1+. It restarts at 5×3+ with 85% of this 5RM: ${Fmt.lb(dev.gzclp.tracker.engine.Progression.t1FromFiveRm(s.fiveRm))}.")
            Stepper(s.fiveRm.toString(), { update(s.copy(fiveRm = (s.fiveRm - 5).coerceAtLeast(5))) }, { update(s.copy(fiveRm = s.fiveRm + 5)) }, unit = "lb")
            PillButton("End workout", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true)
        }
        is SheetState.EndConfirm -> {
            SheetTitle(
                "End workout?",
                "${s.unlogged} ${if (s.unlogged == 1) "set is" else "sets are"} not logged. A lift with some sets logged counts the rest as missed; a lift with none keeps its weight.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PillButton("Keep going", onDismiss, Modifier.weight(1f), onSurface = true)
                PillButton("End workout", { onConfirm(s) }, Modifier.weight(1f), primary = true)
            }
        }
        is SheetState.AddT3 -> {
            val p = LocalPalette.current
            SheetTitle("Add accessory to ${s.day.name}", "3×15+, adds 5 lb when the last set reaches 25.")
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
            Stepper(s.weight.toString(), { update(s.copy(weight = (s.weight - 5).coerceAtLeast(0))) }, { update(s.copy(weight = s.weight + 5)) }, unit = "lb")
            PillButton(if (s.name.isBlank()) "Add accessory" else "Add ${s.name.trim()}", { onConfirm(s) }, Modifier.fillMaxWidth(), primary = true, enabled = s.name.isNotBlank())
        }
    }
}
