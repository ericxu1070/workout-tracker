package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UpcomingScreen(
    state: AppState,
    today: LocalDate,
    onOpenSession: (String) -> Unit,
    onUseSuggestion: (Exercise, Int) -> Unit = { _, _ -> },
) {
    val p = LocalPalette.current
    ScreenFrame {
        Spacer(Modifier.height(52.dp))
        Txt("Upcoming", Type.largeTitle)
        Commands.upcoming(state, 8).forEachIndexed { n, (day, exercises) ->
            val tag = when {
                n > 0 -> ""
                state.active != null -> " · in progress"
                else -> " · next"
            }
            GroupHeader(day.title + tag)
            Group {
                exercises.map(LocalPlates.current::prescribe).forEachIndexed { i, ex ->
                    val plan = ex.weight
                    val suggested = if (plan != null) state.strengthSuggestion(ex) else null
                    GroupRow(first = i == 0) {
                        Column(Modifier.weight(1f)) {
                            Txt(ex.name, Type.body, maxLines = 1)
                            if (plan != null && suggested != null && state.shouldSuggest(ex, suggested)) {
                                Spacer(Modifier.height(Space.xs))
                                StrengthSuggestionChip(plan, suggested, { onUseSuggestion(ex, suggested) })
                            }
                        }
                        Txt(ex.scheme.display, Type.body.tabular(), Modifier.padding(start = Space.sm), p.fg2)
                        Txt(ex.weight?.let(Fmt::lb) ?: "No weight yet", Type.body.tabular(), Modifier.padding(start = Space.md).widthIn(min = 64.dp), if (ex.weight == null) p.fg2 else p.fg, align = TextAlign.End)
                    }
                }
            }
        }
    }
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
                IconButton(Icons.chevronLeft, "Previous month", { month = month.minusMonths(1) }, tint = p.accent)
                IconButton(Icons.chevronRight, "Next month", { month = month.plusMonths(1) }, tint = p.accent)
            }
            Spacer(Modifier.height(Space.md))
            Row {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Txt(it, Type.caption, Modifier.weight(1f), p.fg2, align = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(Space.xs))
            val lead = month.atDay(1).dayOfWeek.value % 7 // Sunday first
            val cells = lead + month.lengthOfMonth()
            for (week in 0 until (cells + 6) / 7) {
                Row {
                    for (col in 0 until 7) {
                        val d = week * 7 + col - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (d in 1..month.lengthOfMonth()) {
                                val date = month.atDay(d)
                                val mix = mixes[date]
                                val isToday = date == today
                                val style = Type.subhead.tabular().copy(fontWeight = if (isToday || mix != null) FontWeight.SemiBold else FontWeight.Normal)
                                val open = sessionOn[date]?.let { s -> { onOpenSession(s.id) } } ?: cardioOn[date]?.let { c -> { onCardio(c) } }
                                if (mix != null && open != null) {
                                    Pressable(open, Modifier.size(40.dp), CircleShape, label = "${dayLabel(mix)} on ${Fmt.shortDate(date)}") {
                                        Box(Modifier.matchParentSize().dayMarker(mix, kinds))
                                        Txt("$d", style, color = kinds.on)
                                    }
                                } else {
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape)
                                            .then(if (mix != null) Modifier.dayMarker(mix, kinds).semantics { contentDescription = "${dayLabel(mix)} on ${Fmt.shortDate(date)}" } else Modifier)
                                            .then(if (isToday && mix == null) Modifier.border(1.5.dp, p.fg, CircleShape) else Modifier),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Txt("$d", style, color = if (mix != null) kinds.on else if (date > today) p.fg2 else p.fg)
                                    }
                                }
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
