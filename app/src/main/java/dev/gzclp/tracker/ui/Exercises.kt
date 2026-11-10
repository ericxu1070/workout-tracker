package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Cycle
import dev.gzclp.tracker.engine.Schemes
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.ExerciseLog
import dev.gzclp.tracker.log.exerciseLogs
import dev.gzclp.tracker.ui.Type.tabular

@Composable
fun ExercisesScreen(state: AppState, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    val logs = state.exerciseLogs()
    ScreenFrame {
        Spacer(Modifier.height(52.dp))
        Txt("Exercises", Type.largeTitle)
        Spacer(Modifier.height(Space.xxl))
        if (logs.isEmpty()) {
            Txt("No exercises yet", Type.headline)
            Txt("Every exercise you log shows up here with its weight over time.", Type.subhead, Modifier.padding(vertical = Space.xs), p.fg2)
            return@ScreenFrame
        }
        Group {
            logs.forEachIndexed { i, log ->
                val best = log.bests.lastOrNull()
                GroupRow(first = i == 0, onClick = { onOpen(log.id) }) {
                    Column(Modifier.weight(1f)) {
                        Txt(state.name(log.id), Type.headline, maxLines = 1)
                        Txt(
                            (best?.let { "Top set ${Fmt.lb(it.topWeight)} × ${it.topReps}, " } ?: "") + Fmt.shortDate(log.last.first.startedAt),
                            Type.footnote, color = p.fg2, maxLines = 1,
                        )
                    }
                    if (best != null) Column(Modifier.padding(start = Space.sm), horizontalAlignment = Alignment.End) {
                        Txt(Fmt.lb(best.e1rm), Type.subhead.tabular())
                        Txt("est. 1RM", Type.caption, color = p.fg2)
                    }
                    Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
                }
            }
        }
    }
}

@Composable
fun ExerciseScreen(state: AppState, log: ExerciseLog, onBack: () -> Unit) {
    val p = LocalPalette.current
    val bests = log.bests
    ScreenFrame {
        NavRow(onBack)
        Txt(state.name(log.id), Type.largeTitle)
        if (bests.isNotEmpty()) {
            val first = bests.first()
            val last = bests.last()
            val delta = last.e1rm - first.e1rm
            Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(Fmt.lb(last.e1rm), Type.title1)
                Spacer(Modifier.width(Space.sm))
                Txt(
                    "estimated 1RM" + when {
                        bests.size == 1 -> ""
                        delta > 0 -> ", up $delta lb since ${Fmt.shortDate(first.at)}"
                        delta < 0 -> ", down ${-delta} lb since ${Fmt.shortDate(first.at)}"
                        else -> ", same as ${Fmt.shortDate(first.at)}"
                    },
                    Type.subhead, Modifier.padding(bottom = 4.dp), p.fg2,
                )
            }
            Spacer(Modifier.height(Space.md))
            LineChart(
                listOf(bests.map { Pt(it.at, it.e1rm.toFloat()) } to p.fg2, bests.map { Pt(it.at, it.topWeight.toFloat()) } to p.accent),
                listOf("1RM", "Top"),
            )
            GroupFooter("Top: heaviest set each workout. 1RM: best Epley estimate, weight × (1 + reps / 30).")
        }

        val now = buildList {
            state.cycle[log.id]?.let { c ->
                val w = Cycle.weight(c, state.step(log.id))
                add("Cycle" to "Week ${c.week + 1} of ${Cycle.weeks.size}, ${Cycle.weeks[c.week].display} at ${Fmt.lb(w)}")
            }
            listOf(Tier.T1, Tier.T2, Tier.T3).forEach { t ->
                state.gz[log.id to t]?.let { add("GZCLP ${t.label}" to "${Schemes.of(t)[it.stage].display} at ${Fmt.lb(it.weight)}") }
            }
        }
        if (now.isNotEmpty()) {
            GroupHeader("Next time")
            Group {
                now.forEachIndexed { i, (k, v) ->
                    GroupRow(first = i == 0) {
                        Txt(k, Type.body, Modifier.weight(1f))
                        Txt(v, Type.body.tabular(), color = p.fg2)
                    }
                }
            }
        }

        val (session, sets) = log.last
        GroupHeader("Last workout · ${session.day.title}, ${Fmt.date(session.startedAt)}")
        SetList(sets.sortedWith(compareBy({ it.tier }, { it.setIndex })))
    }
}
