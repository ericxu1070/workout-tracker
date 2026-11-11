package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.gzclp.tracker.engine.Program
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.engine.Wave
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Event
import dev.gzclp.tracker.ui.Type.tabular
import kotlin.math.roundToInt

/** A 44dp touch target laid out at [width] x 36dp, so a small icon lines up with row text and keeps the row at its standard height. */
@Composable
fun Snug(width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) =
    Box(modifier.size(width, 36.dp), contentAlignment = Alignment.Center) { content(Modifier.requiredSize(44.dp)) }

/** The navigation bar's Edit, which reads Done while editing. */
@Composable
private fun EditButton(editing: Boolean, onClick: () -> Unit) {
    Pressable(onClick, Modifier.heightIn(min = 44.dp)) {
        Txt(if (editing) "Done" else "Edit", if (editing) Type.headline else Type.body, Modifier.padding(horizontal = Space.sm), LocalPalette.current.accent)
    }
}

/**
 * A list in edit mode: each row has a red remove button, its [names] entry and [trailing] text, and a drag grip.
 * Remove asks once more with a red Delete in place of the grip. TalkBack moves a row with the grip's actions.
 * [keys] are stable ids in the same order; [onMove] and [onRemove] take indices.
 */
@Composable
private fun EditRows(
    names: List<String>, keys: List<String>, trailing: (Int) -> String?, removable: Boolean,
    onMove: (Int, Int) -> Unit, onRemove: (Int) -> Unit,
) {
    val p = LocalPalette.current
    var from by remember { mutableIntStateOf(-1) }
    var dy by remember { mutableFloatStateOf(0f) }
    var rowPx by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableStateOf<String?>(null) }
    val count by rememberUpdatedState(keys.size)
    val move by rememberUpdatedState(onMove)
    fun target() = if (from < 0) -1 else (from + (dy / rowPx).roundToInt()).coerceIn(0, count - 1)
    keys.forEachIndexed { i, k ->
        key(k) {
            val index by rememberUpdatedState(i)
            val to = target()
            val shift = when {
                from < 0 -> 0f
                i == from -> dy
                i in from + 1..to -> -rowPx
                i in to until from -> rowPx
                else -> 0f
            }
            val lifted = i == from
            Box(
                Modifier.zIndex(if (lifted) 1f else 0f).graphicsLayer { translationY = shift }
                    .onSizeChanged { rowPx = it.height.toFloat() }
                    .then(if (lifted) Modifier.shadow(8.dp).background(p.elevated) else Modifier),
            ) {
                GroupRow(first = i == 0) {
                    val name = names[i]
                    Snug(22.dp) { m ->
                        Pressable(
                            { armed = if (armed == k) null else k }, m.semantics { contentDescription = "Remove $name" }, CircleShape,
                            enabled = removable, label = "Remove $name",
                        ) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(p.red), contentAlignment = Alignment.Center) { Icon(Icons.minus, Color.White, 16.dp) }
                        }
                    }
                    Spacer(Modifier.width(Space.md))
                    Txt(name, Type.body, Modifier.weight(1f), maxLines = 1)
                    trailing(i)?.let { Txt(it, Type.body.tabular(), Modifier.padding(start = Space.sm), p.fg2, maxLines = 1) }
                    if (armed == k) Pressable({ armed = null; onRemove(i) }, Modifier.padding(start = Space.sm).heightIn(min = 44.dp), CircleShape, p.red, label = "Delete $name") {
                        Txt("Delete", Type.subhead.copy(fontWeight = Type.headline.fontWeight), Modifier.padding(horizontal = Space.lg), Color.White, maxLines = 1)
                    } else Snug(22.dp, Modifier.padding(start = Space.lg)) { m ->
                        Box(
                            m.semantics {
                                contentDescription = "Reorder $name"
                                customActions = listOfNotNull(
                                    CustomAccessibilityAction("Move up") { move(index, index - 1); true }.takeIf { index > 0 },
                                    CustomAccessibilityAction("Move down") { move(index, index + 1); true }.takeIf { index < count - 1 },
                                )
                            }.pointerInput(k) {
                                detectDragGestures(
                                    onDragStart = { from = index; dy = 0f; armed = null },
                                    onDrag = { c, d -> c.consume(); dy += d.y },
                                    onDragEnd = {
                                        val f = from
                                        val t = target()
                                        from = -1
                                        dy = 0f
                                        if (t != f) move(f, t)
                                    },
                                    onDragCancel = { from = -1; dy = 0f },
                                )
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.grip, p.fg3, 22.dp) }
                    }
                }
            }
        }
    }
}

/** A split's page: its name, Set all, and its days, each opening its own page. [edit] dispatches; [open] shows a sheet. */
@Composable
fun SplitScreen(
    state: AppState, split: Program, onBack: () -> Unit, onOpenDay: (String) -> Unit,
    open: (SheetState) -> Unit, edit: ((AppState) -> List<Event>) -> Unit, initialEditing: Boolean = false,
) {
    val p = LocalPalette.current
    var editing by rememberSaveable { mutableStateOf(initialEditing) }
    ScreenFrame {
        NavRow(onBack) { EditButton(editing) { editing = !editing } }
        Txt(split.name, Type.largeTitle, maxLines = 2)
        Spacer(Modifier.height(Space.xxl))
        Group {
            GroupRow(first = true, onClick = { open(SheetState.SplitName(split.id, null, split.name)) }) {
                Txt("Name", Type.body)
                Txt(split.name, Type.body, Modifier.weight(1f).padding(start = Space.lg), p.fg2, maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.End)
            }
            GroupRow(first = false, onClick = {
                val wave = split.days.flatMap { it.slots }.firstOrNull()?.wave ?: Wave.DEFAULT
                open(SheetState.SplitWave(split.id, null, null, "Every exercise", wave))
            }) {
                Txt("Every exercise", Type.body, Modifier.weight(1f))
                Txt("Set all", Type.body, color = p.accent)
            }
        }
        GroupHeader("Days")
        Group {
            if (editing) EditRows(
                split.days.map { it.title }, split.days.map { it.id }, { null }, removable = split.days.size > 1,
                onMove = { f, t -> edit { Commands.moveDay(it, split.id, f, t) } },
                onRemove = { i -> edit { Commands.removeDay(it, split.id, split.days[i].id) } },
            ) else {
                split.days.forEachIndexed { i, d ->
                    GroupRow(first = i == 0, onClick = { onOpenDay(d.id) }) {
                        Column(Modifier.weight(1f)) {
                            Txt(d.title, Type.body, maxLines = 1)
                            Txt(d.slots.joinToString { state.name(it.ex) }.ifEmpty { "No exercises" }, Type.footnote, color = p.fg2, maxLines = 1)
                        }
                        Icon(Icons.chevronRight, p.fg3, 20.dp)
                    }
                }
                AddRow("Add day", first = false) { open(SheetState.SplitName(split.id, null, "", add = true)) }
            }
        }
        if (editing) {
            Spacer(Modifier.height(Space.xxl))
            Group {
                GroupRow(first = true, onClick = { open(SheetState.DeleteSplit(split.id, null)) }) { Txt("Delete split", Type.body, color = p.red) }
            }
        }
    }
}

/** One day of a split: its exercises with their sets × week reps, each opening the sets and reps sheet. */
@Composable
fun SplitDayScreen(
    state: AppState, split: Program, day: ProgramDay, onBack: () -> Unit,
    open: (SheetState) -> Unit, edit: ((AppState) -> List<Event>) -> Unit, initialEditing: Boolean = false,
) {
    val p = LocalPalette.current
    val plates = LocalPlates.current
    var editing by rememberSaveable { mutableStateOf(initialEditing) }
    ScreenFrame {
        NavRow(onBack) { EditButton(editing) { editing = !editing } }
        Txt(day.title, Type.largeTitle, maxLines = 2)
        Spacer(Modifier.height(Space.xs))
        Txt(split.name, Type.subhead, color = p.fg2, maxLines = 1)
        Spacer(Modifier.height(Space.xxl))
        if (editing) {
            if (day.slots.isNotEmpty()) Group {
                EditRows(
                    day.slots.map { state.name(it.ex) }, day.slots.map { it.ex }, { day.slots[it].wave?.display }, removable = true,
                    onMove = { f, t -> edit { Commands.moveSplitExercise(it, split.id, day.id, f, t) } },
                    onRemove = { i -> edit { Commands.removeSplitExercise(it, split.id, day.id, day.slots[i].ex) } },
                )
            }
            Spacer(Modifier.height(Space.xxl))
            Group {
                GroupRow(first = true, onClick = { open(SheetState.SplitName(split.id, day.id, day.title)) }) { Txt("Rename day", Type.body, color = p.accent) }
                if (split.days.size > 1) GroupRow(first = false, onClick = { open(SheetState.DeleteSplit(split.id, day.id)) }) { Txt("Delete day", Type.body, color = p.red) }
            }
        } else Group {
            day.slots.forEachIndexed { i, slot ->
                val ex = state.exercise(slot)
                val wave = slot.wave ?: Wave.DEFAULT
                GroupRow(first = i == 0, onClick = { open(SheetState.SplitWave(split.id, day.id, slot.ex, ex.name, wave)) }) {
                    Column(Modifier.weight(1f)) {
                        Txt(ex.name, Type.body, maxLines = 1)
                        Txt(plates.prescribe(ex).weight?.let(Fmt::lb) ?: "No weight yet", Type.footnote.tabular(), color = p.fg2)
                    }
                    Txt(wave.display, Type.body.tabular(), Modifier.padding(start = Space.sm), p.accent, maxLines = 1)
                    Spacer(Modifier.width(Space.xs))
                    Icon(Icons.chevronRight, p.fg3, 20.dp)
                }
            }
            AddRow("Add exercise", first = day.slots.isEmpty()) { open(SheetState.AddExercise(day, "", split.id)) }
        }
    }
}
