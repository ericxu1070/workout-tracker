package dev.gzclp.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.NormLift
import dev.gzclp.tracker.engine.Norms
import dev.gzclp.tracker.engine.Peer
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.STANDING_DAYS
import dev.gzclp.tracker.log.Standing
import dev.gzclp.tracker.log.currentLifts
import dev.gzclp.tracker.log.overall
import dev.gzclp.tracker.log.peer
import dev.gzclp.tracker.log.standing
import dev.gzclp.tracker.ui.Type.tabular
import java.time.LocalDate
import kotlin.math.roundToInt

/** 1st, 2nd, 3rd, 11th, 62nd. */
fun ordinal(n: Int): String = "$n" + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

/** "men at 181 lb, age 34", or "ages 24 to 34" with no birth year. */
fun Peer.describe(): String =
    "${if (female) "women" else "men"} at ${(bodyKg / Norms.KG_PER_LB).roundToInt()} lb, " + (age?.let { "age ${it.roundToInt()}" } ?: "ages 24 to 34")

/** Whose lifts the curve is drawn from, and what the lifter's own number is. */
fun normsSource(): String =
    "Compared with the first raw, drug-tested meet of ${"%,d".format(Norms.lifters)} lifters, ${Norms.fromYear} to ${Norms.toYear}, from OpenPowerlifting. " +
        "Yours is your best estimated 1RM of the last $STANDING_DAYS days; theirs are single lifts judged in competition."

/**
 * How [standing]'s peers spread over the lift, with the share under the lifter filled in. Hold and drag, or tap, to read any
 * whole percentile: its weight and that as a multiple of body weight.
 */
@Composable
fun StandingCurve(standing: Standing) {
    val p = LocalPalette.current
    val tm = rememberTextMeasurer()
    val peer = standing.peer
    val norm = remember(peer.female, standing.lift) { Norms.of(peer.female, standing.lift) }
    val bodyLb = peer.bodyKg / Norms.KG_PER_LB
    // Percentiles 1 to 99, in lb: the stops a finger reads.
    val stops = remember(norm, peer) { FloatArray(99) { (norm.liftAt((it + 1) / 100.0, peer) / Norms.KG_PER_LB).toFloat() } }
    val you = standing.lb.toFloat()
    // The plot runs from the 0.5th to the 99.5th percentile, and up to a quarter further for a lifter outside that; one
    // further out still is drawn at the edge, so the curve never shrinks to a sliver.
    val p0 = (norm.liftAt(0.005, peer) / Norms.KG_PER_LB).toFloat()
    val p100 = (norm.liftAt(0.995, peer) / Norms.KG_PER_LB).toFloat()
    val lo = minOf(p0, maxOf(you * 0.96f, p0 - (p100 - p0) / 4))
    val hi = maxOf(p100, minOf(you * 1.04f, p100 + (p100 - p0) / 4))
    val samples = 96
    val curve = remember(norm, peer, lo, hi) {
        var d = FloatArray(samples + 1) { i -> norm.density((lo + (hi - lo) * i / samples) * Norms.KG_PER_LB, peer).toFloat() }
        // Two 1-2-1 passes take out the small kinks a table of quantiles leaves, without moving the hump.
        repeat(2) { d = FloatArray(d.size) { i -> (d[maxOf(i - 1, 0)] + 2 * d[i] + d[minOf(i + 1, samples)]) / 4 } }
        d
    }
    val peak = curve.max()
    fun read(i: Int) = "${ordinal(i + 1)} percentile, ${stops[i].roundToInt()} lb, ${Fmt.ratio(stops[i] / bodyLb)} body weight"
    val describe = "${standing.lift.label} among ${peer.describe()}: median ${stops[49].roundToInt()} lb, you ${standing.lb} lb, ${ordinal(standing.rank)} percentile"
    val pick = remember(norm, peer) { mutableStateOf<Int?>(null) }
    Canvas(Modifier.fillMaxWidth().height(200.dp).chartPick(pick, stops.size, describe, ::read) { i, w -> w * (stops[i] - lo) / (hi - lo) }) {
        val top = 28.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        fun x(lb: Float) = size.width * (lb - lo) / (hi - lo)
        fun y(d: Float) = bottom - (bottom - top) * d / peak
        fun density(lb: Float): Float {
            val f = ((lb - lo) / (hi - lo) * samples).coerceIn(0f, samples.toFloat())
            val i = f.toInt().coerceAtMost(samples - 1)
            return curve[i] + (curve[i + 1] - curve[i]) * (f - i)
        }
        val line = Path()
        val area = Path().apply { moveTo(0f, bottom) }
        curve.forEachIndexed { i, d ->
            val o = Offset(size.width * i / samples, y(d))
            if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y)
            area.lineTo(o.x, o.y)
        }
        area.lineTo(size.width, bottom)
        area.close()
        // Everyone the lifter is ahead of.
        clipRect(right = x(you).coerceIn(0f, size.width)) { drawPath(area, p.accent.copy(alpha = 0.28f)) }
        drawLine(p.separator, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        drawPath(line, p.fg2, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // The median, the 10th and the 90th, ticked under the curve and named on the axis.
        // The median is always named; the 10th and 90th only where their names clear it.
        val names = listOf(49 to "Median ${stops[49].roundToInt()} lb", 9 to "10th ${stops[9].roundToInt()}", 89 to "90th ${stops[89].roundToInt()}")
        val medianHalf = tm.measure(names[0].second, Type.caption.tabular()).size.width / 2f
        names.forEach { (i, name) ->
            val sx = x(stops[i])
            drawLine(p.fg3, Offset(sx, bottom), Offset(sx, y(density(stops[i]))), 1.dp.toPx())
            val half = tm.measure(name, Type.caption.tabular()).size.width / 2f
            if (i == 49 || kotlin.math.abs(sx - x(stops[49])) >= half + medianHalf + 8.dp.toPx()) label(tm, name, Offset(sx, bottom + 6.dp.toPx()), p.fg2, alignX = 0.5f)
        }
        val yx = x(you).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
        drawLine(p.accent, Offset(yx, bottom), Offset(yx, top - 6.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(p.bg, 6.dp.toPx(), Offset(yx, y(density(you))))
        drawCircle(p.accent, 4.dp.toPx(), Offset(yx, y(density(you))))
        // "You" sits beside its line, on the side with room.
        val right = yx < size.width * 0.7f
        label(tm, "You ${standing.lb} lb", Offset(yx + if (right) 8.dp.toPx() else -8.dp.toPx(), 0f), p.accent, alignX = if (right) 0f else 1f)
        pick.value?.takeIf { it < stops.size }?.let { i ->
            val px = x(stops[i])
            drawLine(p.fg3, Offset(px, top), Offset(px, bottom), 1.dp.toPx())
            drawCircle(p.bg, 6.dp.toPx(), Offset(px, y(density(stops[i]))))
            drawCircle(p.fg, 4.dp.toPx(), Offset(px, y(density(stops[i]))))
            callout(tm, p, px, "${ordinal(i + 1)} percentile", listOf("${stops[i].roundToInt()} lb" to p.fg, "${Fmt.ratio(stops[i] / bodyLb)} body weight" to p.fg2))
        }
    }
}

/** A standing's headline, its sentence, and its curve. [what] names the number, as in "estimated 1RM 315 lb". */
@Composable
fun StandingBlock(standing: Standing, what: String) {
    val p = LocalPalette.current
    Column {
        Txt("${ordinal(standing.rank)} percentile", Type.title1)
        Txt("$what, more than ${standing.rank}% of ${standing.peer.describe()}", Type.subhead, color = p.fg2)
    }
    Spacer(Modifier.height(Space.md))
    StandingCurve(standing)
}

/** The Percentile view of a competition lift's page: where its current estimated 1RM falls among the lifter's peers. */
@Composable
fun LiftStanding(lift: NormLift, lb: Int, peer: Peer?, onLogWeight: () -> Unit, onStrength: () -> Unit) {
    val p = LocalPalette.current
    Spacer(Modifier.height(Space.lg))
    if (peer == null) {
        Txt("No body weight yet", Type.headline)
        Txt("A percentile compares you with lifters of your body weight", Type.subhead, Modifier.padding(top = Space.xs), p.fg2)
        Spacer(Modifier.height(Space.lg))
        PillButton("Log weight", onLogWeight, Modifier.fillMaxWidth(), primary = true, icon = Icons.plus)
        return
    }
    StandingBlock(standing(lift, lb, peer), "Estimated 1RM $lb lb")
    Spacer(Modifier.height(Space.lg))
    Group {
        GroupRow(first = true, onClick = onStrength) {
            Column(Modifier.weight(1f)) {
                Txt("Strength", Type.headline, maxLines = 1)
                Txt(if (peer.age == null) "Set your birth year, and see all three lifts" else "All three lifts and your total", Type.footnote, color = p.fg2, maxLines = 1)
            }
            Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
        }
    }
    GroupFooter(normsSource())
}

/** The total's standing, each lift's, and who the lifter is compared as. */
@Composable
fun StrengthScreen(
    state: AppState, female: Boolean, birthYear: Int?, today: LocalDate,
    onBack: () -> Unit, onBirthYear: (Int) -> Unit, onOpen: (String) -> Unit, onLogWeight: () -> Unit,
) {
    val p = LocalPalette.current
    val peer = state.peer(female, birthYear, today)
    val lifts = remember(state.sessions) { state.currentLifts() }
    ScreenFrame {
        NavRow(onBack)
        Txt("Strength", Type.largeTitle)
        Spacer(Modifier.height(Space.lg))
        if (peer == null) {
            Txt("No body weight yet", Type.headline)
            Txt("A percentile compares you with lifters of your body weight", Type.subhead, Modifier.padding(top = Space.xs), p.fg2)
            Spacer(Modifier.height(Space.lg))
            PillButton("Log weight", onLogWeight, Modifier.fillMaxWidth(), primary = true, icon = Icons.plus)
        } else {
            val total = overall(lifts, peer)
            if (total != null) StandingBlock(total, "Total ${total.lb} lb")
            else {
                Txt("No total yet", Type.headline)
                Txt("Log ${NormLift.lifts.filter { it !in lifts }.joinToString(" and ") { it.label.lowercase() }} to rank your total", Type.subhead, Modifier.padding(top = Space.xs), p.fg2)
            }
            GroupHeader("Lifts")
            Group {
                NormLift.lifts.forEachIndexed { i, lift ->
                    val lb = lifts[lift]
                    GroupRow(first = i == 0, onClick = lb?.let { { onOpen(lift.id) } }) {
                        Column(Modifier.weight(1f)) {
                            Txt(lift.label, Type.headline, maxLines = 1)
                            Txt(lb?.let { "Estimated 1RM $it lb" } ?: "Not logged yet", Type.footnote, color = p.fg2, maxLines = 1)
                        }
                        if (lb != null) {
                            Txt(ordinal(standing(lift, lb, peer).rank), Type.subhead.tabular(), Modifier.padding(start = Space.sm))
                            Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
                        }
                    }
                }
            }
        }
        GroupHeader("Compared as")
        Group {
            GroupRow(first = true) {
                Txt("Gender", Type.body, Modifier.weight(1f), maxLines = 1)
                Txt(if (female) "Female" else "Male", Type.body, color = p.fg2)
            }
            GroupRow(first = false) {
                Txt("Body weight", Type.body, Modifier.weight(1f), maxLines = 1)
                Txt(peer?.let { "${Fmt.tenths(it.bodyKg / Norms.KG_PER_LB)} lb" } ?: "None", Type.body.tabular(), color = p.fg2)
            }
            // With no year yet, the first step lands on 30 years old.
            val year = birthYear ?: (today.year - 30)
            GroupRow(first = false) {
                Txt("Birth year", Type.body, Modifier.weight(1f), maxLines = 2)
                MiniStep(Icons.minus, "Earlier birth year", enabled = year > today.year - 90) { onBirthYear(if (birthYear == null) year else year - 1) }
                // Wide enough for "Not set", so the steppers stay put when the first tap sets a year.
                Txt(birthYear?.toString() ?: "Not set", Type.body.tabular(), Modifier.widthIn(min = 80.dp).padding(horizontal = Space.sm), maxLines = 1, align = TextAlign.Center)
                MiniStep(Icons.plus, "Later birth year", enabled = year < today.year - 13) { onBirthYear(if (birthYear == null) year else year + 1) }
            }
        }
        GroupFooter("Gender is under Settings, Appearance. Body weight is your latest 7-day average. Height is not used: no meet records it.")
        GroupFooter(normsSource())
    }
}
