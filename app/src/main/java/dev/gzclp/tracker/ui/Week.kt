package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.DistanceUnit
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.food.Meal
import dev.gzclp.tracker.log.ActivityTotal
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.SessionBest
import dev.gzclp.tracker.log.firstDay
import dev.gzclp.tracker.log.mondayOf
import dev.gzclp.tracker.log.week
import dev.gzclp.tracker.ui.Type.tabular
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** History's Week view: the Monday to Sunday week from [monday], each total against the week before. */
@Composable
fun ColumnScope.WeekView(state: AppState, meals: List<Meal>, monday: LocalDate, today: LocalDate, onWeek: (LocalDate) -> Unit, onExercise: (String) -> Unit) {
    val p = LocalPalette.current
    val w = state.week(monday)
    val thisWeek = mondayOf(today)
    val first = listOfNotNull(state.firstDay(), meals.minOfOrNull { it.at.toLocalDate() }).minOrNull()
    fun mealsIn(m: LocalDate) = meals.count { mondayOf(it.at.toLocalDate()) == m }
    val mealCount = mealsIn(monday)
    Spacer(Modifier.height(Space.lg))
    Row(verticalAlignment = Alignment.CenterVertically) {
        MiniStep(Icons.chevronLeft, "Week before", enabled = first != null && monday > mondayOf(first)) { onWeek(monday.minusDays(7)) }
        Txt(weekLabel(monday, thisWeek), Type.headline, Modifier.weight(1f), align = TextAlign.Center)
        MiniStep(Icons.chevronRight, "Week after", enabled = monday < thisWeek) { onWeek(monday.plusDays(7)) }
    }
    if (w.workouts == 0 && w.cardioSessions == 0 && w.bodyWeight == null && mealCount == 0) {
        Spacer(Modifier.height(Space.xxl))
        Txt("Nothing logged", Type.headline)
        return
    }
    Spacer(Modifier.height(Space.lg))
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Tile("Gym", w.gymSec, count(w.workouts, "workout"), w.gymSec - w.prevGymSec, Modifier.weight(1f))
        Tile("Cardio", w.cardioSec, count(w.cardioSessions, "session"), w.cardioSec - w.prevCardioSec, Modifier.weight(1f))
    }
    Spacer(Modifier.height(Space.md))
    Group {
        Row(Modifier.padding(Space.md)) {
            for (i in 0L until 7L) {
                val day = monday.plusDays(i)
                val active = day in w.activeDays
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(day.dayOfWeek.name.take(1), Type.caption, color = p.fg2)
                    Spacer(Modifier.height(Space.xs))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(if (active) p.accent else Color.Transparent), contentAlignment = Alignment.Center) {
                        Txt(
                            "${day.dayOfMonth}", Type.subhead.tabular().copy(fontWeight = if (active || day == today) FontWeight.SemiBold else FontWeight.Normal),
                            color = when {
                                active -> p.onAccent
                                day == today -> p.accent
                                day > today -> p.fg3
                                else -> p.fg
                            },
                        )
                    }
                }
            }
        }
    }
    if (w.byActivity.isNotEmpty()) {
        GroupHeader("Cardio")
        ActivityTotals(w.byActivity)
    }
    if (w.lifts.isNotEmpty()) {
        GroupHeader("Lifts")
        Group {
            w.lifts.forEachIndexed { i, l ->
                GroupRow(first = i == 0, onClick = { onExercise(l.exercise) }) {
                    Column(Modifier.weight(1f)) {
                        val slot = if (l.tier.cyclic) "" else "${l.tier.label} "
                        Txt("${state.name(l.exercise)} · $slot${l.stage.replace("x", "×")}", Type.body, maxLines = 1)
                        Txt(top(l.now) + (l.prev?.let { " vs ${top(it)}, ${Fmt.shortDate(it.at)}" } ?: ""), Type.footnote.tabular(), color = p.fg2, maxLines = 1)
                    }
                    val d = l.prev?.let { l.now.e1rm - it.e1rm }
                    Txt(
                        if (d == null) "First time" else signed(d.toLong()) + " lb", Type.subhead.tabular(), Modifier.padding(start = Space.sm),
                        when {
                            d == null || d == 0 -> p.fg2
                            d > 0 -> p.accent
                            else -> p.red
                        },
                    )
                    Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
                }
            }
        }
    }
    val bw = w.bodyWeight
    if (bw != null || mealCount > 0) {
        Spacer(Modifier.height(Space.xxl))
        Group {
            if (bw != null) ValueRow(true, "Body weight", Fmt.lb(bw), w.prevBodyWeight?.let { signed(bw - it) + " lb" })
            if (mealCount > 0) ValueRow(bw == null, "Meals", "$mealCount", signed((mealCount - mealsIn(monday.minusDays(7))).toLong()))
        }
    }
}

@Composable
private fun Tile(title: String, sec: Long, count: String, change: Long, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(Radius.lg)).background(p.elevated).padding(Space.lg)) {
        Txt(title, Type.footnote, color = p.fg2)
        Spacer(Modifier.height(Space.xs))
        Txt(Fmt.minutes(sec), Type.title2.tabular(), maxLines = 1)
        Spacer(Modifier.height(Space.xs))
        Txt(count, Type.footnote, color = p.fg2)
        Txt(signedMinutes(change), Type.footnote.tabular(), color = p.fg2)
    }
}

@Composable
private fun ValueRow(first: Boolean, label: String, value: String, change: String?) {
    val p = LocalPalette.current
    GroupRow(first) {
        Txt(label, Type.body, Modifier.weight(1f), maxLines = 1)
        Column(horizontalAlignment = Alignment.End) {
            Txt(value, Type.body.tabular())
            if (change != null) Txt(change, Type.footnote.tabular(), color = p.fg2)
        }
    }
}

/** This week, Last week, Nov 2–8, Oct 26–Nov 1. */
private fun weekLabel(monday: LocalDate, thisWeek: LocalDate): String {
    val sunday = monday.plusDays(6)
    return when (monday) {
        thisWeek -> "This week"
        thisWeek.minusDays(7) -> "Last week"
        else -> Fmt.shortDate(monday) + "–" + if (sunday.month == monday.month) "${sunday.dayOfMonth}" else Fmt.shortDate(sunday)
    }
}

private fun count(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"

private fun top(b: SessionBest) = "${b.topWeight} × ${b.topReps}"

/** +5, −3, ±0. */
private fun signed(n: Long) = when {
    n > 0 -> "+$n"
    n < 0 -> "−${-n}"
    else -> "±0"
}

/** +0.4, −0.6, ±0.0 for body weight. */
private fun signed(lb: Double): String {
    val t = Fmt.tenths(abs(lb))
    return when {
        t == "0.0" -> "±0.0"
        lb > 0 -> "+$t"
        else -> "−$t"
    }
}

/** +25 min, −1 hr 5 min, ±0 min. */
private fun signedMinutes(sec: Long): String {
    val m = (sec / 60.0).roundToLong()
    return if (m == 0L) "±0 min" else (if (m > 0) "+" else "−") + Fmt.minutes(abs(m) * 60)
}

/** A week's cardio per activity: sessions, distance, and time. Shared with the Cardio page. */
@Composable
fun ActivityTotals(totals: List<ActivityTotal>) {
    val p = LocalPalette.current
    Group {
        totals.forEachIndexed { i, a ->
            GroupRow(first = i == 0) {
                Column(Modifier.weight(1f)) {
                    Txt(a.name, Type.body, maxLines = 1)
                    Txt(listOfNotNull(count(a.sessions, "session"), distance(a.activity, a.distanceM)).joinToString(" · "), Type.footnote, color = p.fg2, maxLines = 1)
                }
                Txt(Fmt.minutes(a.sec), Type.subhead.tabular(), Modifier.padding(start = Space.sm), p.fg2)
            }
        }
    }
}

/** 6.2 mi, 1,500 yd, 5,000 m in the activity's own unit; null when it has none or no distance was given. */
private fun distance(activity: String, meters: Int?): String? {
    val unit = Activities.of(activity)?.unit ?: return null
    val v = (meters ?: return null) / unit.meters
    return if (unit == DistanceUnit.MI) "%.1f mi".format(Locale.US, v) else "%,d %s".format(Locale.US, v.roundToLong(), unit.label)
}
