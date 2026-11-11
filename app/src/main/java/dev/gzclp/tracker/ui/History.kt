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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.DistanceUnit
import dev.gzclp.tracker.health.HealthRecords
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Cardio
import dev.gzclp.tracker.log.SessionRecord
import dev.gzclp.tracker.log.SetLogged
import dev.gzclp.tracker.ui.Type.tabular
import java.time.OffsetDateTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    state: AppState, all: Boolean, onAll: (Boolean) -> Unit, onOpen: (String) -> Unit, onCardio: (Cardio) -> Unit,
    week: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val done = state.sessions.filter { it.sets.isNotEmpty() }
    ScreenFrame {
        Spacer(Modifier.height(52.dp))
        Txt("History", Type.largeTitle)
        Spacer(Modifier.height(Space.lg))
        Segmented(listOf("Week", "All"), if (all) 1 else 0, { onAll(it == 1) })
        if (!all) {
            week()
            return@ScreenFrame
        }
        val cardio = state.cardio.values
        if (done.isEmpty() && cardio.isEmpty()) {
            Spacer(Modifier.height(Space.xxl))
            Txt("No workouts yet", Type.headline)
            return@ScreenFrame
        }
        if (done.isNotEmpty()) {
            val recent = done.takeLast(16)
            val avg = recent.sumOf { it.durationSec } / recent.size
            Spacer(Modifier.height(Space.xxl))
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(Fmt.minutes(avg), Type.title1)
                Spacer(Modifier.width(Space.sm))
                Txt("average workout, last ${recent.size}", Type.subhead, Modifier.padding(bottom = 4.dp), p.fg2)
            }
            Spacer(Modifier.height(Space.md))
            BarChart(recent)
        }
        GroupHeader(if (cardio.isEmpty()) "${done.size} ${if (done.size == 1) "workout" else "workouts"}" else "${done.size} lifting · ${cardio.size} cardio")
        Group {
            (done.map { it.startedAt to it } + cardio.map { it.startedAt to it }).sortedByDescending { it.first }.forEachIndexed { i, (_, s) ->
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
            }
        }
    }
}

/** One finished workout: every logged set with its own weight and reps. */
@Composable
fun SessionScreen(state: AppState, s: SessionRecord, onBack: () -> Unit) {
    val p = LocalPalette.current
    ScreenFrame {
        NavRow(onBack)
        Txt(s.day.title, Type.largeTitle)
        Spacer(Modifier.height(Space.xs))
        Txt("${Fmt.date(s.startedAt)}, ${Fmt.time(s.startedAt)} · ${Fmt.minutes(s.durationSec)} · ${s.program.name}", Type.subhead, color = p.fg2)
        s.sets.groupBy { it.exercise to it.tier }.forEach { (key, sets) ->
            val first = sets.first()
            GroupHeader("${state.name(key.first)} · ${Tier.of(key.second)?.label ?: key.second} ${first.stage.replace("x", "×")}")
            SetList(sets.sortedBy { it.setIndex })
        }
    }
}

/** Logged sets as rows: "Set 1", then weight × reps right-aligned, short sets in red. */
@Composable
fun SetList(sets: List<SetLogged>) {
    val p = LocalPalette.current
    Group {
        sets.forEachIndexed { i, set ->
            GroupRow(first = i == 0) {
                Txt("Set ${set.setIndex}" + if (set.amrap) " (AMRAP)" else "", Type.body, Modifier.weight(1f), maxLines = 1)
                Txt(Fmt.lb(set.weight) + " × ", Type.body.tabular(), color = p.fg2)
                Txt("${set.actualReps}", Type.body.tabular(), color = if (set.actualReps < set.targetReps) p.red else p.fg)
                set.rpe?.let { Txt(" @ ${Fmt.rpe(it)}", Type.body.tabular(), color = p.fg2) }
            }
        }
    }
}

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
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val left = 48.dp.toPx()
        val right = size.width - 64.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        fun x(t: Long) = if (times.size < 2) (left + right) / 2 else left + (right - left) * times.indexOf(t) / (times.size - 1)
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
    }
}

@Composable
fun BarChart(sessions: List<SessionRecord>) {
    val p = LocalPalette.current
    val tm = rememberTextMeasurer()
    val mins = sessions.map { it.durationSec / 60f }
    val step = niceStep(mins.max().coerceAtLeast(30f))
    val hi = ceil(mins.max() / step) * step
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
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
        val slot = (size.width - left) / 16
        val barW = slot * 0.62f
        mins.forEachIndexed { i, m ->
            val x = left + slot * i + (slot - barW) / 2
            val h = (bottom - y(m)).coerceAtLeast(2.dp.toPx())
            drawRoundRect(p.accent, Offset(x, bottom - h), Size(barW, h), CornerRadius(3.dp.toPx()))
        }
        label(tm, Fmt.shortDate(sessions.first().startedAt), Offset(left, bottom + 6.dp.toPx()), p.fg2)
        if (sessions.size > 1) {
            val lastX = left + slot * (sessions.size - 1) + slot / 2
            label(tm, Fmt.shortDate(sessions.last().startedAt), Offset(lastX, bottom + 6.dp.toPx()), p.fg2, alignX = if (sessions.size > 13) 1f else 0.5f)
        }
    }
}

fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, color: Color, alignX: Float = 0f, alignY: Float = 0f) {
    val r = tm.measure(text, Type.caption.tabular().copy(color = color))
    drawText(r, topLeft = Offset(at.x - r.size.width * alignX, at.y - r.size.height * alignY))
}
