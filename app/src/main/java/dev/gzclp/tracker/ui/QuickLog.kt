package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.isCalisthenics
import dev.gzclp.tracker.log.snapshotBodyweight
import java.time.OffsetDateTime

/** What a quick log of [exercise] starts from: its last logged sets, reps and weight (added weight for a bodyweight move), or 3 × 10 and none. */
internal fun quickDefaults(state: AppState, exercise: String): Triple<Int, Int, Int?> {
    val last = state.sessions.asReversed().firstNotNullOfOrNull { s -> s.sets.filter { it.exercise == exercise && it.actualReps > 0 }.ifEmpty { null } }
        ?: return Triple(3, 10, null)
    val w = last[0].addedLb?.toInt() ?: last[0].weight
    return Triple(last.size.coerceIn(Commands.QUICK_SETS), last[0].actualReps.coerceIn(Commands.QUICK_REPS), w.takeIf { it != 0 })
}

/**
 * A quick log: an exercise from the catalog (or a new one), sets × reps and an optional weight, logged as done just now and
 * counted for stats only. Picking the exercise swaps the sheet to the exercise list and back.
 */
@Composable
fun ColumnScope.QuickLogSheet(state: AppState, now: () -> OffsetDateTime, onLog: (name: String, sets: Int, reps: Int, weight: Int?) -> Unit) {
    val p = LocalPalette.current
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var sets by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    if (picking) {
        AddExerciseSheet(
            state, title = "Quick log",
            onSelect = { id ->
                val (n, r, w) = quickDefaults(state, id)
                name = state.name(id); sets = "$n"; reps = "$r"; weight = w?.toString().orEmpty()
                picking = false
            },
            onDismiss = { picking = false },
            onCreate = { typed -> name = typed; sets = "3"; reps = "10"; weight = ""; picking = false },
        )
        return
    }
    val id = name?.let { n -> state.moves.values.firstOrNull { it.name.equals(n, ignoreCase = true) }?.id }
    val bodyweight = id != null && isCalisthenics(id)
    val nSets = sets.toIntOrNull()?.takeIf { it in Commands.QUICK_SETS }
    val nReps = reps.toIntOrNull()?.takeIf { it in Commands.QUICK_REPS }
    SheetTitle("Quick log", "Counts toward your stats, never your program.")
    Group {
        GroupRow(first = true, onClick = { picking = true }) {
            Txt("Exercise", Type.body, Modifier.weight(1f))
            Txt(name ?: "Choose", Type.body, Modifier.padding(start = Space.sm), if (name == null) p.accent else p.fg2, maxLines = 1)
            Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
        }
        QuickField("Sets", sets, "3", last = false) { sets = it.filter(Char::isDigit).take(2) }
        QuickField("Reps", reps, "10", last = false) { reps = it.filter(Char::isDigit).take(3) }
        QuickField(if (bodyweight) "Added weight, lb" else "Weight, lb", weight, if (bodyweight) "0" else "None") { weight = it.filter(Char::isDigit).take(4) }
    }
    if (bodyweight) {
        val bw = state.snapshotBodyweight(now())
        GroupFooter(if (bw != null) "Your body weight, ${Fmt.lb(bw)}, counts as the load, plus anything added." else "Log a weigh-in and your body weight counts as the load.")
    }
    Spacer(Modifier.height(Space.sm))
    val ready = name != null && nSets != null && nReps != null
    PillButton(
        if (ready) "Log $nSets × $nReps" else "Log", { onLog(name!!, nSets!!, nReps!!, weight.toIntOrNull()?.takeIf { it > 0 }) },
        Modifier.fillMaxWidth(), primary = true, enabled = ready,
    )
}

/** One labeled number row of the quick log. */
@Composable
private fun QuickField(label: String, value: String, placeholder: String, last: Boolean = true, onChange: (String) -> Unit) {
    GroupRow(first = false) {
        Column(Modifier.weight(1f)) { Txt(label, Type.body, maxLines = 1) }
        NumberField(value, onChange, last = last, placeholder = placeholder, label = label)
    }
}
