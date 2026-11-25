package dev.gzclp.tracker.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.log.AppState
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

/** A month of days; days with a finished workout are filled and open that workout. */
@Composable
fun MonthGrid(state: AppState, today: LocalDate, initialMonth: YearMonth, onOpenSession: (String) -> Unit) {
    val p = LocalPalette.current
    var month by remember { mutableStateOf(initialMonth) }
    val byDay = state.sessions.filter { it.sets.isNotEmpty() }.associateBy { it.startedAt.toLocalDate() }
    val count = byDay.keys.count { YearMonth.from(it) == month }
    Group {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(monthFmt.format(month), Type.headline)
                    Txt(if (count == 1) "1 workout" else "$count workouts", Type.footnote, color = p.fg2)
                }
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
                                val session = byDay[date]
                                val isToday = date == today
                                val style = Type.subhead.tabular().copy(fontWeight = if (isToday || session != null) FontWeight.SemiBold else FontWeight.Normal)
                                if (session != null) {
                                    Pressable({ onOpenSession(session.id) }, Modifier.size(40.dp), CircleShape, p.fg, p.fg2, label = "${session.day.title} on ${Fmt.date(session.startedAt)}") {
                                        Txt("$d", style, color = p.bg)
                                    }
                                } else {
                                    Box(Modifier.size(40.dp).then(if (isToday) Modifier.border(1.5.dp, p.fg, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                                        Txt("$d", style, color = if (date > today) p.fg2 else p.fg)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
