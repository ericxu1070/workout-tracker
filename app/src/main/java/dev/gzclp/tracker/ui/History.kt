package dev.gzclp.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.SessionRecord
import dev.gzclp.tracker.ui.Type.tabular
import java.time.OffsetDateTime
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun HistoryScreen(state: AppState, onBack: () -> Unit, initialTab: Int = 0, initialLift: Int = 0) {
    var tab by remember { mutableIntStateOf(initialTab) }
    ScreenFrame {
        NavRow(onBack)
        Txt("History", Type.largeTitle)
        Spacer(Modifier.height(Space.lg))
        Segmented(listOf("Workouts", "Charts"), tab, { tab = it })
        if (tab == 0) Sessions(state) else Charts(state, initialLift)
    }
}

private fun liftName(state: AppState, id: String) = Lift.of(id)?.label ?: state.t3[id]?.name ?: id

@Composable
private fun Sessions(state: AppState) {
    val p = LocalPalette.current
    val done = state.sessions.filter { it.sets.isNotEmpty() }.asReversed()
    Spacer(Modifier.height(Space.xxl))
    if (done.isEmpty()) {
        Txt("No workouts yet", Type.headline, Modifier.padding(horizontal = Space.lg))
        Txt("Finished workouts show up here with every set you logged.", Type.subhead, Modifier.padding(horizontal = Space.lg, vertical = Space.xs), p.fg2)
        return
    }
    var open by remember { mutableStateOf(done.first().id) }
    Group {
        done.forEachIndexed { i, s ->
            GroupRow(first = i == 0, onClick = { open = if (open == s.id) "" else s.id }) {
                Column(Modifier.weight(1f)) {
                    Txt("${s.day.name} · ${Fmt.date(s.startedAt)}", Type.headline)
                    if (open == s.id) SessionDetail(state, s) else Txt(
                        s.sets.map { it.lift }.distinct().joinToString(", ") { liftName(state, it) },
                        Type.footnote, color = p.fg2, maxLines = 1,
                    )
                }
                Txt(Fmt.minutes(s.durationSec), Type.subhead.tabular(), Modifier.align(Alignment.Top).padding(top = 1.dp), p.fg2)
            }
        }
    }
}

@Composable
private fun SessionDetail(state: AppState, s: SessionRecord) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = Space.sm, bottom = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        s.sets.groupBy { it.lift to it.tier }.forEach { (key, sets) ->
            val sorted = sets.sortedBy { it.setIndex }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(liftName(state, key.first), Type.subhead, maxLines = 1)
                    Txt("${key.second.uppercase()} · ${Fmt.lb(sorted.first().weight)}", Type.footnote, color = p.fg2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    sorted.forEach { Txt(it.actualReps.toString(), Type.subhead.tabular(), color = if (it.actualReps < it.targetReps) p.red else p.fg) }
                }
            }
        }
    }
}

private data class Pt(val t: OffsetDateTime, val v: Float)

private fun weightSeries(state: AppState, lift: Lift, tier: Tier): List<Pt> = state.sessions.mapNotNull { s ->
    s.sets.filter { it.lift == lift.id && it.tier == tier.id }.maxOfOrNull { it.weight }?.let { Pt(s.startedAt, it.toFloat()) }
}

@Composable
private fun Charts(state: AppState, initialLift: Int) {
    val p = LocalPalette.current
    var liftIx by remember { mutableIntStateOf(initialLift) }
    val lift = Lift.entries[liftIx]
    Spacer(Modifier.height(Space.xxl))
    Segmented(listOf("Squat", "Bench", "Deadlift", "OHP"), liftIx, { liftIx = it })
    val t1 = weightSeries(state, lift, Tier.T1)
    val t2 = weightSeries(state, lift, Tier.T2)
    Spacer(Modifier.height(Space.xxl))
    Txt("${lift.label} weight", Type.headline)
    if (t1.isEmpty() && t2.isEmpty()) {
        Txt("Shows up after your first ${lift.label.lowercase()} workout.", Type.subhead, Modifier.padding(top = Space.xs), p.fg2)
    } else {
        val lead = t1.lastOrNull() ?: t2.last()
        val first = (t1.ifEmpty { t2 }).first()
        val delta = (lead.v - first.v).toInt()
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Space.xs)) {
            Txt(Fmt.lb(lead.v.toInt()), Type.title1)
            Spacer(Modifier.padding(start = Space.sm))
            Txt(
                (if (t1.isEmpty()) "T2" else "T1") + when {
                    delta > 0 -> ", up $delta lb since ${Fmt.shortDate(first.t)}"
                    delta < 0 -> ", down ${-delta} lb since ${Fmt.shortDate(first.t)}"
                    else -> " working weight"
                },
                Type.subhead, Modifier.padding(bottom = 4.dp), p.fg2,
            )
        }
        Spacer(Modifier.height(Space.md))
        LineChart(listOf(t1 to p.accent, t2 to p.fg2), listOf("T1", "T2"))
    }

    val sessions = state.sessions.filter { it.sets.isNotEmpty() }.takeLast(16)
    Spacer(Modifier.height(Space.x3))
    Txt("Workout length", Type.headline)
    if (sessions.isEmpty()) {
        Txt("Shows up after your first workout.", Type.subhead, Modifier.padding(top = Space.xs), p.fg2)
    } else {
        val avg = sessions.sumOf { it.durationSec } / sessions.size
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Space.xs)) {
            Txt(Fmt.minutes(avg), Type.title1)
            Spacer(Modifier.padding(start = Space.sm))
            Txt("average over ${sessions.size} workouts", Type.subhead, Modifier.padding(bottom = 4.dp), p.fg2)
        }
        Spacer(Modifier.height(Space.md))
        BarChart(sessions)
    }
}

private fun niceStep(range: Float): Float {
    val raw = range / 3f
    return listOf(5f, 10f, 20f, 25f, 50f, 100f).firstOrNull { it >= raw } ?: (ceil(raw / 100f) * 100f)
}

@Composable
private fun LineChart(series: List<Pair<List<Pt>, Color>>, names: List<String>) {
    val p = LocalPalette.current
    val tm = rememberTextMeasurer()
    val all = series.flatMap { it.first }
    val times = all.map { it.t.toEpochSecond() }.distinct().sorted()
    val lo0 = all.minOf { it.v }
    val hi0 = all.maxOf { it.v }
    val step = niceStep((hi0 - lo0).coerceAtLeast(15f))
    val lo = floor(lo0 / step) * step
    val hi = (ceil(hi0 / step) * step).let { if (it <= lo) lo + step else it }
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val left = 48.dp.toPx()
        val right = size.width - 44.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        fun x(t: Long) = if (times.size < 2) (left + right) / 2 else left + (right - left) * times.indexOf(t) / (times.size - 1)
        fun y(v: Float) = bottom - (bottom - top) * (v - lo) / (hi - lo)
        var g = lo
        while (g <= hi + 0.1f) {
            drawLine(p.separator, Offset(left, y(g)), Offset(size.width, y(g)), 1.dp.toPx())
            label(tm, if (g + step > hi + 0.1f) "${g.toInt()} lb" else "${g.toInt()}", Offset(0f, y(g)), p.fg2, alignY = 0.5f)
            g += step
        }
        all.minByOrNull { it.t }?.let { label(tm, Fmt.shortDate(it.t), Offset(left, bottom + 6.dp.toPx()), p.fg2) }
        all.maxByOrNull { it.t }?.let { label(tm, Fmt.shortDate(it.t), Offset(right, bottom + 6.dp.toPx()), p.fg2, alignX = 1f) }
        series.forEachIndexed { si, (pts, color) ->
            if (pts.isEmpty()) return@forEachIndexed
            val path = Path()
            pts.forEachIndexed { i, pt ->
                val o = Offset(x(pt.t.toEpochSecond()), y(pt.v))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val last = pts.last()
            val lo2 = Offset(x(last.t.toEpochSecond()), y(last.v))
            drawCircle(color, 4.dp.toPx(), lo2)
            label(tm, "${names[si]} ${last.v.toInt()}", Offset(lo2.x + 8.dp.toPx(), lo2.y), color, alignY = 0.5f)
        }
    }
}

@Composable
private fun BarChart(sessions: List<SessionRecord>) {
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

private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, color: Color, alignX: Float = 0f, alignY: Float = 0f) {
    val r = tm.measure(text, Type.caption.tabular().copy(color = color))
    drawText(r, topLeft = Offset(at.x - r.size.width * alignX, at.y - r.size.height * alignY))
}
