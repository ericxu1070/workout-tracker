package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Cardio
import dev.gzclp.tracker.log.Kind
import dev.gzclp.tracker.log.bouts
import dev.gzclp.tracker.log.dayMix
import dev.gzclp.tracker.log.monthStats
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.shouldSuggest
import dev.gzclp.tracker.log.strengthSuggestion
import dev.gzclp.tracker.ui.Type.tabular
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The next workout in full with its likely day, then the ones after it a row each: day, lead lift and weight, likely day. */
@Composable
fun UpcomingScreen(
    state: AppState,
    today: LocalDate,
    onOpenSession: (String) -> Unit,
    onUseSuggestion: (Exercise, Int) -> Unit = { _, _ -> },
) {
    val p = LocalPalette.current
    val plates = LocalPlates.current
    val upcoming = Commands.upcoming(state, 8)
    val dates = likelyDays(state, today, upcoming.size)
    ScreenFrame {
        Spacer(Modifier.height(52.dp))
        Txt("Upcoming", Type.largeTitle)
        val (next, exercises) = upcoming.first()
        Spacer(Modifier.height(Space.xxl))
        Txt(next.title, Type.title2)
        Txt(if (state.active != null) "In progress" else Fmt.day(dates.first(), today), Type.subhead, color = p.fg2)
        Spacer(Modifier.height(Space.md))
        Group {
            exercises.map(plates::prescribe).forEachIndexed { i, ex ->
                val plan = ex.weight
                val suggested = if (plan != null) state.strengthSuggestion(ex) else null
                GroupRow(first = i == 0) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Txt(ex.name, Type.body, Modifier.weight(1f), maxLines = 1)
                            Txt(ex.scheme.display, Type.body.tabular(), Modifier.padding(start = Space.sm), p.fg2)
                            Txt(ex.weight?.let(Fmt::lb) ?: "No weight yet", Type.body.tabular(), Modifier.padding(start = Space.md).widthIn(min = 64.dp), if (ex.weight == null) p.fg2 else p.fg, align = TextAlign.End)
                        }
                        if (plan != null && suggested != null && state.shouldSuggest(ex, suggested)) {
                            Spacer(Modifier.height(Space.xs))
                            StrengthSuggestion(suggested, { onUseSuggestion(ex, suggested) })
                        }
                    }
                }
            }
        }
        GroupHeader("After that")
        Group {
            upcoming.drop(1).forEachIndexed { i, (day, list) ->
                val lead = list.firstOrNull()?.let(plates::prescribe)
                GroupRow(first = i == 0) {
                    Column(Modifier.weight(1f)) {
                        Txt(day.title, Type.body, maxLines = 1)
                        lead?.let { Txt(listOfNotNull(it.name, it.weight?.let(Fmt::lb)).joinToString(" "), Type.footnote.tabular(), color = p.fg2, maxLines = 1) }
                    }
                    Txt(Fmt.day(dates[i + 1], today), Type.body.tabular(), Modifier.padding(start = Space.sm), maxLines = 1)
                }
            }
        }
    }
}

/**
 * The day each of the next [n] workouts likely falls on. The app keeps no schedule, so it is read from the log: the
 * median gap in days between the last nine lifting days (two days before there are two), counted on from the last
 * one, and never before today. A workout in progress is today's.
 */
internal fun likelyDays(state: AppState, today: LocalDate, n: Int): List<LocalDate> {
    val days = state.sessions.filter { it.sets.isNotEmpty() }.map { it.startedAt.toLocalDate() }.distinct().sorted()
    val gaps = days.takeLast(9).zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.sorted()
    val gap = if (gaps.isEmpty()) 2L else gaps[gaps.size / 2]
    val first = if (state.active != null || days.isEmpty()) today else maxOf(today, days.last().plusDays(gap))
    return List(n) { first.plusDays(it * gap) }
}

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

/**
 * A month of days, each day with training marked by what it held, then the month's numbers. A day opens its workout,
 * or its cardio when that is all it held.
 */
@Composable
fun MonthGrid(state: AppState, today: LocalDate, initialMonth: YearMonth, onOpenSession: (String) -> Unit, onCardio: (Cardio) -> Unit = {}) {
    val p = LocalPalette.current
    val kinds = p.kinds
    var month by remember { mutableStateOf(initialMonth) }
    val bouts = remember(state) { state.bouts() }
    val mixes = remember(bouts) { dayMix(bouts) }
    val sessionOn = state.sessions.filter { it.sets.isNotEmpty() }.associateBy { it.startedAt.toLocalDate() }
    val cardioOn = state.cardio.values.associateBy { it.startedAt.toLocalDate() }
    Group {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(monthFmt.format(month), Type.headline, Modifier.weight(1f))
                MiniStep(Icons.chevronLeft, "Previous month") { month = month.minusMonths(1) }
                Spacer(Modifier.width(Space.sm))
                MiniStep(Icons.chevronRight, "Next month") { month = month.plusMonths(1) }
            }
            Spacer(Modifier.height(Space.md))
            Row {
                java.time.DayOfWeek.entries.forEach {
                    Txt(weekday(it), Type.caption, Modifier.weight(1f), p.fg2, align = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(Space.xs))
            val lead = month.atDay(1).dayOfWeek.value - 1 // Monday first, as the Week view
            val cells = lead + month.lengthOfMonth()
            for (week in 0 until (cells + 6) / 7) {
                Row {
                    for (col in 0 until 7) {
                        val d = week * 7 + col - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (d in 1..month.lengthOfMonth()) {
                                val date = month.atDay(d)
                                val mix = mixes[date]
                                val open = if (mix == null) null else sessionOn[date]?.let { s -> { onOpenSession(s.id) } } ?: cardioOn[date]?.let { c -> { onCardio(c) } }
                                DayCell(date, today, mix, open, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
    val stats = monthStats(bouts, month, today)
    Spacer(Modifier.height(Space.xxl))
    Group {
        GroupRow(first = true) {
            Txt("Sessions", Type.body, Modifier.weight(1f))
            Txt("${stats.sessions}", Type.body.tabular(), color = p.fg2)
        }
        GroupRow(first = false) {
            Txt("Per week", Type.body, Modifier.weight(1f))
            Txt(String.format(Locale.US, "%.1f", stats.perWeek), Type.body.tabular(), color = p.fg2)
        }
    }
    GroupHeader("Average session")
    Group {
        listOf(Kind.LIFT to stats.liftSec, Kind.CARDIO to stats.cardioSec, Kind.STRETCH to stats.stretchSec).forEachIndexed { i, (kind, sec) ->
            GroupRow(first = i == 0) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(kinds.of(kind)))
                Txt(kind.label, Type.body, Modifier.weight(1f).padding(start = Space.md))
                Txt(sec?.let(Fmt::minutes) ?: "None", Type.body.tabular(), color = p.fg2)
            }
        }
    }
}
