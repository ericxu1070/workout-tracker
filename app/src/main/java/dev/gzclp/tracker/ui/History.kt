package dev.gzclp.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.key
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.DistanceUnit
import dev.gzclp.tracker.health.HealthRecords
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Cardio
import dev.gzclp.tracker.log.Deletion
import dev.gzclp.tracker.log.SessionRecord
import dev.gzclp.tracker.log.SetLogged
import dev.gzclp.tracker.ui.Type.tabular
import java.time.OffsetDateTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

enum class HistoryView { Week, Month, All }

@Composable
fun HistoryScreen(
    state: AppState, view: HistoryView, onView: (HistoryView) -> Unit, onOpen: (String) -> Unit, onCardio: (Cardio) -> Unit,
    today: java.time.LocalDate = java.time.LocalDate.now(),
    deleting: Deletion? = null, onDelete: (Deletion) -> Unit = {},
    week: @Composable ColumnScope.() -> Unit,
) {
    val pager = rememberPages(view.ordinal, HistoryView.entries.size) { onView(HistoryView.entries[it]) }
    // All three kept composed, as the Workout pages are, so a tapped view shows on the next frame instead of composing first.
    Pages(pager, beyond = 2, header = {
        Spacer(Modifier.height(52.dp))
        Txt("History", Type.largeTitle)
        Spacer(Modifier.height(Space.lg))
        Segmented(HistoryView.entries.map { it.name }, view.ordinal, { onView(HistoryView.entries[it]) }, pager = pager)
    }) { page ->
        ScreenFrame {
            when (HistoryView.entries[page]) {
                HistoryView.Week -> week()
                HistoryView.Month -> {
                    Spacer(Modifier.height(Space.lg))
                    MonthGrid(state, today, java.time.YearMonth.from(today), onOpen, onCardio)
                }
                HistoryView.All -> AllHistory(state, onOpen, onCardio, deleting, onDelete)
            }
        }
    }
}

/** Every finished lift and cardio session, newest first, under the average workout and the last 16 lifts' bars; a row swipes away. */
@Composable
private fun ColumnScope.AllHistory(state: AppState, onOpen: (String) -> Unit, onCardio: (Cardio) -> Unit, deleting: Deletion?, onDelete: (Deletion) -> Unit) {
    val p = LocalPalette.current
    val done = state.sessions.filter { it.sets.isNotEmpty() }
    val cardio = state.cardio.values
    if (done.isEmpty() && cardio.isEmpty()) {
        Spacer(Modifier.height(Space.xxl))
        Txt("No workouts yet", Type.headline)
        return
    }
    if (done.isNotEmpty()) {
        val recent = done.takeLast(16)
        val avg = done.sumOf { it.durationSec } / done.size
        Spacer(Modifier.height(Space.xxl))
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(Fmt.minutes(avg), Type.title1)
            Spacer(Modifier.width(Space.sm))
            Txt("average workout", Type.subhead, Modifier.padding(bottom = 4.dp), p.fg2)
        }
        Spacer(Modifier.height(Space.md))
        BarChart(recent)
    }
    GroupHeader(if (cardio.isEmpty()) "${done.size} ${if (done.size == 1) "workout" else "workouts"}" else "${done.size} lifting · ${cardio.size} cardio")
    Group {
        (done.map { it.startedAt to it } + cardio.map { it.startedAt to it }).sortedByDescending { it.first }.forEachIndexed { i, (_, s) ->
            val target = if (s is Cardio) Deletion.Cardio(s.id) else Deletion.Workout((s as SessionRecord).id)
            key(target) { SwipeToDelete("Delete", deleting == target, { onDelete(target) }) {
            if (s is Cardio) GroupRow(first = i == 0, onClick = { onCardio(s) }) {
                val unit = Activities.of(s.activity)?.unit ?: DistanceUnit.MI
                Column(Modifier.weight(1f)) {
                    Txt("${s.name} · ${Fmt.date(s.startedAt)}", Type.headline, maxLines = 1)
                    Txt(
                        listOfNotNull(Fmt.time(s.startedAt), s.distanceM?.let { distanceText(it, unit) }, s.distanceM?.let { HealthRecords.pace(s.activity, unit, it, s.durationSec) })
                            .joinToString(" · "),
                        Type.footnote, color = p.fg2, maxLines = 1,
                    )
                }
                Txt(Fmt.minutes(s.durationSec), Type.subhead.tabular(), Modifier.padding(start = Space.sm), p.fg2)
                Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
            } else if (s is SessionRecord) GroupRow(first = i == 0, onClick = { onOpen(s.id) }) {
                Column(Modifier.weight(1f)) {
                    Txt("${s.day.title} · ${Fmt.date(s.startedAt)}", Type.headline, maxLines = 1)
                    Txt(s.sets.map { it.exercise }.distinct().joinToString(", ") { state.name(it) }, Type.footnote, color = p.fg2, maxLines = 1)
                }
                Txt(Fmt.minutes(s.durationSec), Type.subhead.tabular(), Modifier.padding(start = Space.sm), p.fg2)
                Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
            }
            } }
        }
    }
}

/** One finished workout: its time, which [onEditTime] corrects, then every logged set with its own weight and reps; an exercise's card swipes away. */
@Composable
fun SessionScreen(
    state: AppState, s: SessionRecord, onBack: () -> Unit, onEditTime: () -> Unit, deleting: Deletion? = null, onDelete: (Deletion) -> Unit = {},
) {
    val p = LocalPalette.current
    ScreenFrame {
        NavRow(onBack)
        Txt(s.day.title, Type.largeTitle)
        Spacer(Modifier.height(Space.xs))
        Txt(Fmt.date(s.startedAt), Type.subhead, color = p.fg2)
        Spacer(Modifier.height(Space.lg))
        Group {
            GroupRow(first = true, onClick = onEditTime) {
                Column(Modifier.weight(1f)) {
                    Txt("Duration", Type.body)
                    Txt(Fmt.span(s.startedAt, s.startedAt.plusSeconds(s.durationSec)), Type.footnote.tabular(), color = p.fg2, maxLines = 1)
                }
                Txt(Fmt.minutes(s.durationSec), Type.body.tabular(), Modifier.padding(start = Space.sm), p.fg2)
                Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
            }
            s.afterLastSet?.let { after ->
                GroupRow(first = false) {
                    Txt("Left running", Type.body, Modifier.weight(1f))
                    Txt(Fmt.minutes(after), Type.body.tabular(), color = if (after >= LONG_AFTER_LAST_SEC) p.red else p.fg2)
                }
            }
        }
        val gaps = s.setGaps
        s.sets.groupBy { it.exercise to it.tier }.forEach { (key, sets) ->
            val first = sets.first()
            GroupHeader("${state.name(key.first)} · ${Tier.of(key.second)?.label ?: key.second} ${first.stage.replace("x", "×")}")
            val target = Deletion.Exercise(s.id, key.first, key.second)
            key(target) {
                SwipeToDelete("Delete ${state.name(key.first)}", deleting == target, { onDelete(target) }, shape = RoundedCornerShape(Radius.lg)) {
                    SetList(sets.sortedBy { it.setIndex }) { gaps[Triple(it.exercise, it.tier, it.setIndex)] }
                }
            }
        }
    }
}

/** A workout that ran on this long after its last set was probably left running. */
private const val LONG_AFTER_LAST_SEC = 15 * 60L

/**
 * Logged sets as rows: "Set 1", its rest before it ([gap]) under a Rest header when given, then weight × reps right-aligned, short
 * sets in red. Sets that all match (one weight, every rep made, no RPE) fold into one "4 × 3 at 270 lb" row that a tap opens.
 */
@Composable
fun SetList(sets: List<SetLogged>, gap: (SetLogged) -> Long? = { null }) {
    val p = LocalPalette.current
    val same = sets.size > 1 && sets.all { it.weight == sets[0].weight && it.actualReps == sets[0].actualReps && it.actualReps >= it.targetReps && it.rpe == null && it.tier == sets[0].tier }
    var open by rememberSaveable { mutableStateOf(false) }
    val gaps = sets.any { gap(it) != null } && (!same || open)
    if (gaps) Row(Modifier.padding(start = Space.lg, bottom = Space.xs)) {
        Spacer(Modifier.width(SET_LABEL))
        Txt("Rest", Type.footnote, color = p.fg2)
    }
    Group {
        if (same) {
            val s0 = sets[0]
            GroupRow(first = true, onClick = { open = !open }) {
                Txt("${sets.size} × ${s0.actualReps}" + (if (sets.any { it.amrap }) "+" else "") + " at ${Fmt.lb(s0.weight)}", Type.body.tabular(), Modifier.weight(1f), maxLines = 1)
                val turn by animateFloatAsState(if (open) 90f else 0f, tween(220, easing = EaseOutQuart), label = "sets")
                Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.rotate(turn))
            }
        }
        Disclosed(!same || open) {
            sets.forEachIndexed { i, set ->
                GroupRow(first = i == 0 && !same) {
                    Txt("Set ${set.setIndex}", Type.body, Modifier.width(SET_LABEL), maxLines = 1)
                    Txt(gap(set)?.let { Fmt.clock(it) }.orEmpty(), Type.subhead.tabular(), Modifier.weight(1f), p.fg2, maxLines = 1)
                    Txt(Fmt.lb(set.weight) + " × ", Type.body.tabular(), color = p.fg2)
                    Txt("${set.actualReps}" + if (set.amrap) "+" else "", Type.body.tabular(), color = if (set.actualReps < set.targetReps) p.red else p.fg)
                    set.rpe?.let { Txt("  RPE ${Fmt.rpe(it)}", Type.body.tabular(), color = p.fg2) }
                }
            }
        }
    }
}

/** The set label's column, so each rest time lines up under its header. */
private val SET_LABEL = 72.dp

/** About three gridlines over [range], on a 1, 2, 2.5 or 5 step. */
private fun niceStep(range: Float): Float {
    val raw = range / 3f
    val mag = 10f.pow(floor(log10(raw)))
    return listOf(1f, 2f, 2.5f, 5f, 10f).first { it * mag >= raw * 0.999f } * mag
}

/** 180, 1.6, 2.25: no trailing zeros. */
private fun tick(v: Float) = "%.2f".format(v).trimEnd('0').trimEnd('.')

data class Pt(val t: OffsetDateTime, val v: Float)

@Composable
fun LineChart(
    series: List<Pair<List<Pt>, Color>>,
    names: List<String>,
    unit: String = " lb",
    minSpan: Float = 15f,
    value: (Float) -> String = { it.roundToInt().toString() },
) {
    val p = LocalPalette.current
    val tm = rememberTextMeasurer()
    val all = series.flatMap { it.first }
    val times = all.map { it.t.toEpochSecond() }.distinct().sorted()
    val lo0 = all.minOf { it.v }
    val hi0 = all.maxOf { it.v }
    val step = niceStep((hi0 - lo0).coerceAtLeast(minSpan))
    val lo = floor(lo0 / step + 0.001f) * step
    val hi = (ceil(hi0 / step - 0.001f) * step).let { if (it <= lo) lo + step else it }
    val ticks = (0..((hi - lo) / step).roundToInt()).map { lo + it * step }
    fun Density.xAt(i: Int, w: Float): Float {
        val left = 48.dp.toPx()
        val right = w - 64.dp.toPx()
        return if (times.size < 2) (left + right) / 2 else left + (right - left) * i / (times.size - 1)
    }
    // Each series' value at time i, as "Top 185 lb", for the callout and screen readers.
    fun at(i: Int) = series.mapIndexedNotNull { si, (pts, color) ->
        pts.firstOrNull { it.t.toEpochSecond() == times[i] }?.let { pt -> listOf(names[si], value(pt.v) + unit).filter { it.isNotEmpty() }.joinToString(" ") to color }
    }
    fun read(i: Int) = Fmt.shortDate(all.first { it.t.toEpochSecond() == times[i] }.t) + ", " + at(i).joinToString(", ") { it.first }
    val pick = remember(times) { mutableStateOf<Int?>(null) }
    Canvas(Modifier.fillMaxWidth().height(200.dp).chartPick(pick, times.size, times.indices.joinToString("; ") { read(it) }, ::read) { i, w -> xAt(i, w) }) {
        val left = 48.dp.toPx()
        val right = size.width - 64.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        fun x(t: Long) = xAt(times.indexOf(t), size.width)
        fun y(v: Float) = bottom - (bottom - top) * (v - lo) / (hi - lo)
        ticks.forEachIndexed { i, g ->
            drawLine(p.separator, Offset(left, y(g)), Offset(size.width, y(g)), 1.dp.toPx())
            label(tm, if (i == ticks.lastIndex) tick(g) + unit else tick(g), Offset(0f, y(g)), p.fg2, alignY = 0.5f)
        }
        all.minByOrNull { it.t }?.let { label(tm, Fmt.shortDate(it.t), Offset(left, bottom + 6.dp.toPx()), p.fg2) }
        all.maxByOrNull { it.t }?.let { label(tm, Fmt.shortDate(it.t), Offset(right, bottom + 6.dp.toPx()), p.fg2, alignX = 1f) }
        val ends = mutableListOf<Triple<Offset, String, Color>>()
        series.forEachIndexed { si, (pts, color) ->
            if (pts.isEmpty()) return@forEachIndexed
            val path = Path()
            pts.forEachIndexed { i, pt ->
                val o = Offset(x(pt.t.toEpochSecond()), y(pt.v))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val last = pts.last()
            val end = Offset(x(last.t.toEpochSecond()), y(last.v))
            drawCircle(color, 4.dp.toPx(), end)
            ends += Triple(end, listOf(names[si], value(last.v)).filter { it.isNotEmpty() }.joinToString(" "), color)
        }
        // End labels sit beside their points, pushed apart vertically so two close series never overlap.
        val gap = 16.dp.toPx()
        var floorY = Float.NEGATIVE_INFINITY
        ends.sortedBy { it.first.y }.forEach { (end, text, color) ->
            val ly = maxOf(end.y, floorY + gap)
            floorY = ly
            label(tm, text, Offset(end.x + 8.dp.toPx(), ly), color, alignY = 0.5f)
        }
        pick.value?.takeIf { it < times.size }?.let { i ->
            val px = xAt(i, size.width)
            drawLine(p.fg3, Offset(px, top), Offset(px, bottom), 1.dp.toPx())
            series.forEach { (pts, color) ->
                pts.firstOrNull { it.t.toEpochSecond() == times[i] }?.let {
                    drawCircle(p.bg, 6.dp.toPx(), Offset(px, y(it.v)))
                    drawCircle(color, 4.dp.toPx(), Offset(px, y(it.v)))
                }
            }
            callout(tm, p, px, Fmt.shortDate(all.first { it.t.toEpochSecond() == times[i] }.t), at(i))
        }
    }
}

@Composable
fun BarChart(sessions: List<SessionRecord>) {
    val p = LocalPalette.current
    val tm = rememberTextMeasurer()
    val mins = sessions.map { it.durationSec / 60f }
    val step = niceStep(mins.max().coerceAtLeast(30f))
    val hi = ceil(mins.max() / step) * step
    fun read(i: Int) = Fmt.shortDate(sessions[i].startedAt) + ", " + Fmt.minutes(sessions[i].durationSec)
    val pick = remember(sessions) { mutableStateOf<Int?>(null) }
    Canvas(Modifier.fillMaxWidth().height(160.dp).chartPick(pick, sessions.size, sessions.indices.joinToString("; ") { read(it) }, ::read) { i, w ->
        val left = 48.dp.toPx()
        left + (w - left) / sessions.size * (i + 0.5f)
    }) {
        val left = 48.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        fun y(v: Float) = bottom - (bottom - top) * v / hi
        var g = 0f
        while (g <= hi + 0.1f) {
            drawLine(p.separator, Offset(left, y(g)), Offset(size.width, y(g)), 1.dp.toPx())
            label(tm, if (g + step > hi + 0.1f) "${g.toInt()} min" else "${g.toInt()}", Offset(0f, y(g)), p.fg2, alignY = 0.5f)
            g += step
        }
        // The bars share the plot's whole width, so the gridlines never run on over nothing.
        val slot = (size.width - left) / sessions.size
        val barW = minOf(slot * 0.62f, 24.dp.toPx())
        mins.forEachIndexed { i, m ->
            val x = left + slot * i + (slot - barW) / 2
            val h = (bottom - y(m)).coerceAtLeast(2.dp.toPx())
            val dim = pick.value.let { it != null && it != i }
            // The latest workout is the user's current one; earlier bars are context.
            val c = if (i == mins.lastIndex) p.accent else p.fg2
            drawRoundRect(if (dim) c.copy(alpha = 0.35f) else c, Offset(x, bottom - h), Size(barW, h), CornerRadius(3.dp.toPx()))
        }
        label(tm, Fmt.shortDate(sessions.first().startedAt), Offset(left, bottom + 6.dp.toPx()), p.fg2)
        if (sessions.size > 1) {
            label(tm, Fmt.shortDate(sessions.last().startedAt), Offset(size.width, bottom + 6.dp.toPx()), p.fg2, alignX = 1f)
        }
        pick.value?.takeIf { it < sessions.size }?.let { i ->
            callout(tm, p, left + slot * (i + 0.5f), Fmt.shortDate(sessions[i].startedAt), listOf(Fmt.minutes(sessions[i].durationSec) to p.fg), barW / 2)
        }
    }
}

fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, color: Color, alignX: Float = 0f, alignY: Float = 0f) {
    val r = tm.measure(text, Type.caption.tabular().copy(color = color))
    drawText(r, topLeft = Offset(at.x - r.size.width * alignX, at.y - r.size.height * alignY))
}
