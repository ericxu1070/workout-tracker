package dev.gzclp.tracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import dev.gzclp.tracker.engine.Cycle
import dev.gzclp.tracker.engine.Schemes
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.Wave
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.ExerciseLog
import dev.gzclp.tracker.log.ExerciseMeta
import dev.gzclp.tracker.log.exerciseLogs
import dev.gzclp.tracker.log.BodyWeight
import dev.gzclp.tracker.log.Deletion
import androidx.compose.runtime.key
import dev.gzclp.tracker.log.SessionBest
import dev.gzclp.tracker.log.bodyweightFactor
import dev.gzclp.tracker.log.calisthenicsBests
import dev.gzclp.tracker.log.calisthenicsProgression
import dev.gzclp.tracker.log.dailyBodyWeight
import dev.gzclp.tracker.log.defaultBodyweightFactor
import dev.gzclp.tracker.log.isCalisthenics
import dev.gzclp.tracker.log.ratios
import dev.gzclp.tracker.log.sevenDayAverage
import dev.gzclp.tracker.log.weighIns
import dev.gzclp.tracker.log.MuscleSpan
import dev.gzclp.tracker.log.baselineDays
import dev.gzclp.tracker.log.muscleWindow
import dev.gzclp.tracker.log.muscleWork
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.time.LocalDate
import dev.gzclp.tracker.ui.Type.tabular
import kotlin.math.roundToInt

@Composable
fun ExercisesScreen(
    state: AppState, onOpen: (String) -> Unit, onBodyWeight: () -> Unit, onLogWeight: () -> Unit,
    today: LocalDate = LocalDate.now(), female: Boolean = false,
) {
    val p = LocalPalette.current
    val logs = state.exerciseLogs()
    val daily = state.dailyBodyWeight()
    var span by rememberSaveable { mutableStateOf(MuscleSpan.Week) }
    ScreenFrame(bottom = { BottomBar { PillButton("Log weight", onLogWeight, Modifier.weight(1f), primary = true, icon = Icons.plus) } }) {
        Spacer(Modifier.height(52.dp))
        Txt("Exercises", Type.largeTitle)
        Spacer(Modifier.height(Space.lg))
        // A new span fades in through a short blur in place; the map never slides.
        AnimatedContent(span, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "span") { s ->
            val blur by transition.animateDp({ tween(200) }, label = "blur") { if (it == EnterExitState.Visible) 0.dp else 8.dp }
            val window = muscleWindow(today, s)
            val work = state.muscleWork(window.first, window.second)
            val days = baselineDays(s, window)
            Column(Modifier.blur(blur, BlurredEdgeTreatment.Unbounded)) {
                MuscleChart(work, days, female, Modifier.fillMaxWidth().height(340.dp))
                Spacer(Modifier.height(Space.sm))
                MuscleScale(work, days, Modifier.align(Alignment.CenterHorizontally))
            }
        }
        // The span switch sits with the map it changes, under its scale.
        Spacer(Modifier.height(Space.md))
        Segmented(MuscleSpan.entries.map { it.name }, span.ordinal, { span = MuscleSpan.entries[it] })
        Spacer(Modifier.height(Space.xxl))
        Group {
            GroupRow(first = true, onClick = onBodyWeight) {
                Column(Modifier.weight(1f)) {
                    Txt("Body weight", Type.headline, maxLines = 1)
                    Txt(
                        sevenDayAverage(daily).lastEntry()?.let { "7-day average ${Fmt.lb(it.value)}" } ?: "No weigh-ins yet",
                        Type.footnote, color = p.fg2, maxLines = 1,
                    )
                }
                daily.lastEntry()?.let { (day, lb) ->
                    Column(Modifier.padding(start = Space.sm), horizontalAlignment = Alignment.End) {
                        Txt(Fmt.lb(lb), Type.subhead.tabular())
                        Txt(Fmt.shortDate(day), Type.caption, color = p.fg2)
                    }
                }
                Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
            }
        }
        if (logs.isEmpty()) {
            Spacer(Modifier.height(Space.xxl))
            Txt("No exercises yet", Type.headline)
            return@ScreenFrame
        }
        // "est. 1RM" once, its right edge on the values' right edge (row padding plus the chevron).
        Row(Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg + Space.xs + 20.dp, top = Space.xxl, bottom = Space.sm)) {
            Txt("Lifts", Type.footnote, Modifier.weight(1f), p.fg2)
            Txt("est. 1RM", Type.footnote, color = p.fg2)
        }
        Group {
            logs.forEachIndexed { i, log ->
                val best = (if (isCalisthenics(log.id)) calisthenicsBests(state, log) else log.bests).lastOrNull()
                GroupRow(first = i == 0, onClick = { onOpen(log.id) }) {
                    Column(Modifier.weight(1f)) {
                        Txt(state.name(log.id), Type.headline, maxLines = 1)
                        Txt(
                            (best?.let { "Top set ${Fmt.lb(it.topWeight)} × ${it.topReps}, " } ?: "") + Fmt.shortDate(log.last.first.startedAt),
                            Type.footnote, color = p.fg2, maxLines = 1,
                        )
                    }
                    if (best != null) Txt(Fmt.lb(best.e1rm), Type.subhead.tabular(), Modifier.padding(start = Space.sm))
                    Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
                }
            }
        }
    }
}

@Composable
fun ExerciseScreen(
    state: AppState,
    log: ExerciseLog,
    onBack: () -> Unit,
    onLogWeight: () -> Unit,
    initialRelative: Boolean = false,
    onSaveMeta: ((ExerciseMeta) -> Unit)? = null,
) {
    val p = LocalPalette.current
    val isBodyweight = isCalisthenics(log.id)
    val bests = if (isBodyweight) calisthenicsBests(state, log) else log.bests
    var relative by rememberSaveable { mutableStateOf(initialRelative) }
    val factor = bodyweightFactor(log.id, state.exerciseMeta[log.id]) ?: 1.0
    val defaultFactor = defaultBodyweightFactor(log.id) ?: 1.0
    var editingFactor by rememberSaveable { mutableStateOf(false) }
    var factorInput by rememberSaveable { mutableStateOf((factor * 100).roundToInt().toString()) }

    ScreenFrame {
        NavRow(onBack)
        Txt(state.name(log.id), Type.largeTitle)
        FormToggle(log.id)
        if (bests.isNotEmpty()) {
            Spacer(Modifier.height(Space.lg))
            Segmented(listOf("Weight", "× body weight"), if (relative) 1 else 0, { relative = it == 1 })
        }
        if (bests.isNotEmpty() && relative) RelativeStrength(state, bests, onLogWeight)
        else if (bests.isNotEmpty()) {
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
                listOf(bests.map { Pt(it.at, it.e1rm.toFloat()) } to p.accent, bests.map { Pt(it.at, it.topWeight.toFloat()) } to p.fg2),
                listOf("1RM", "Top"),
            )
        }

        if (isBodyweight) {
            GroupHeader("Bodyweight factor")
            Group {
                if (editingFactor) {
                    GroupRow(first = true) {
                        Column(Modifier.weight(1f)) {
                            Txt("Factor (%)", Type.headline)
                            Txt("Default ${(defaultFactor * 100).roundToInt()}%", Type.footnote, color = p.fg2)
                        }
                        BasicTextField(
                            value = factorInput,
                            onValueChange = { factorInput = it.filter { ch -> ch.isDigit() } },
                            textStyle = Type.subhead.tabular().copy(color = p.fg, textAlign = TextAlign.End),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val pct = factorInput.toIntOrNull()
                                if (pct != null && pct > 0) {
                                    val newFactor = pct / 100.0
                                    onSaveMeta?.invoke(ExerciseMeta(exercise = log.id, bodyweightFactor = newFactor))
                                }
                                editingFactor = false
                            }),
                            modifier = Modifier.width(60.dp),
                        )
                    }
                    GroupRow(first = false, onClick = {
                        val pct = factorInput.toIntOrNull()
                        if (pct != null && pct > 0) {
                            val newFactor = pct / 100.0
                            onSaveMeta?.invoke(ExerciseMeta(exercise = log.id, bodyweightFactor = newFactor))
                        }
                        editingFactor = false
                    }) {
                        Txt("Save factor", Type.body, color = p.accent)
                    }
                } else {
                    GroupRow(first = true, onClick = { editingFactor = true }) {
                        Txt("Bodyweight factor", Type.headline, Modifier.weight(1f))
                        Txt("${(factor * 100).roundToInt()}%", Type.subhead.tabular(), color = p.fg2)
                        Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
                    }
                }
            }
        }

        val now = buildList {
            addAll(nextTime(state, log.id))
            if (isBodyweight) {
                val prog = calisthenicsProgression(log.id, log.last.second)
                add("Progression" to prog.suggestion)
            }
        }
        if (now.isNotEmpty()) {
            GroupHeader("Next time")
            Group {
                now.forEachIndexed { i, (k, v) ->
                    GroupRow(first = i == 0) {
                        Txt(k, Type.body, Modifier.weight(1f), maxLines = 1)
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

/** What [ex] does next in the program being run: GZCLP's tiers, a split's waves, or the preset cycle; other programs' progress stays hidden. */
fun nextTime(state: AppState, ex: String): List<Pair<String, String>> = buildList {
    val program = state.program
    when {
        program.gzclp -> listOf(Tier.T1, Tier.T2, Tier.T3).forEach { t ->
            state.gz[ex to t]?.let { add("GZCLP ${t.label}" to "${Schemes.of(t)[it.stage].display} at ${Fmt.lb(it.weight)}") }
        }
        // A split's cycle per sets × reps, labeled by its wave, e.g. "5 × 8·6·5".
        program.custom -> state.custom.filterKeys { it.first == ex }.forEach { (k, c) ->
            val wave = Wave.of(k.second) ?: return@forEach
            add(wave.display to "Week ${c.week + 1} of ${wave.weeks.size}, ${wave.weeks[c.week].display} at ${Fmt.lb(Cycle.weight(c, state.step(ex)))}")
        }
        else -> state.cycle[ex]?.let { c ->
            add("Cycle" to "Week ${c.week + 1} of ${Cycle.weeks.size}, ${Cycle.weeks[c.week].display} at ${Fmt.lb(Cycle.weight(c, state.step(ex)))}")
        }
    }
}

/** The lift's estimated 1RM as a multiple of body weight over time. */
@Composable
private fun RelativeStrength(state: AppState, bests: List<SessionBest>, onLogWeight: () -> Unit) {
    val p = LocalPalette.current
    val daily = state.dailyBodyWeight()
    val rs = ratios(bests, daily)
    Spacer(Modifier.height(Space.lg))
    if (rs.isEmpty()) {
        Txt("No body weight yet", Type.headline)
        if (daily.isNotEmpty()) Txt("All before your first weigh-in on ${Fmt.shortDate(state.weighIns().first().at)}", Type.subhead, Modifier.padding(top = Space.xs), p.fg2)
        Spacer(Modifier.height(Space.lg))
        PillButton("Log weight", onLogWeight, Modifier.fillMaxWidth(), primary = true, icon = Icons.plus)
        return
    }
    val first = rs.first()
    val last = rs.last()
    val delta = last.ratio - first.ratio
    Column {
        Txt(Fmt.ratio(last.ratio), Type.title1)
        Txt(
            "body weight" + when {
                rs.size == 1 -> ""
                delta >= 0.005 -> ", up ${"%.2f".format(delta)} since ${Fmt.shortDate(first.at)}"
                delta <= -0.005 -> ", down ${"%.2f".format(-delta)} since ${Fmt.shortDate(first.at)}"
                else -> ", same as ${Fmt.shortDate(first.at)}"
            },
            Type.subhead, color = p.fg2,
        )
    }
    Spacer(Modifier.height(Space.md))
    LineChart(listOf(rs.map { Pt(it.at, it.ratio.toFloat()) } to p.accent), listOf(""), unit = "×", minSpan = 0.3f, value = { Fmt.ratio(it.toDouble()) })
    GroupFooter("Estimated 1RM ${Fmt.lb(last.e1rm)} at ${Fmt.lb(last.bodyWeight)} body weight")
    val left = bests.size - rs.size
    if (left > 0) GroupFooter("$left earlier ${if (left == 1) "workout" else "workouts"} before your first weigh-in")
}

/** An accent "+ label" row at the end of a group. */
@Composable
fun AddRow(text: String, first: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    GroupRow(first = first, onClick = onClick) {
        Icon(Icons.plus, p.accent, 20.dp)
        Spacer(Modifier.width(Space.sm))
        Txt(text, Type.body, color = p.accent)
    }
}

@Composable
fun BodyWeightScreen(
    state: AppState, today: LocalDate, onBack: () -> Unit, onLog: () -> Unit, onEdit: (BodyWeight) -> Unit,
    deleting: Deletion? = null, onDelete: (Deletion) -> Unit = {},
) {
    val p = LocalPalette.current
    val entries = state.weighIns()
    val daily = state.dailyBodyWeight()
    ScreenFrame(bottom = { BottomBar { PillButton("Log weight", onLog, Modifier.weight(1f), primary = true, icon = Icons.plus) } }) {
        NavRow(onBack)
        Txt("Body weight", Type.largeTitle)
        if (entries.isEmpty()) {
            Spacer(Modifier.height(Space.lg))
            Txt("No weigh-ins yet", Type.headline)
            return@ScreenFrame
        }
        val avg = sevenDayAverage(daily)
        // The chart plots each day at its counted weigh-in's time.
        val dayAt = entries.groupBy { it.at.toLocalDate() }.mapValues { it.value.first().at }
        val firstAvg = avg.firstEntry()
        val lastAvg = avg.lastEntry()
        if (firstAvg != null && lastAvg != null) {
            val delta = lastAvg.value - firstAvg.value
            Spacer(Modifier.height(Space.lg))
            Column {
                Txt(Fmt.lb(lastAvg.value), Type.title1)
                Txt(
                    "7-day average" + when {
                        avg.size == 1 -> ""
                        delta >= 0.05 -> ", up ${Fmt.tenths(delta)} lb since ${Fmt.shortDate(dayAt.getValue(firstAvg.key))}"
                        delta <= -0.05 -> ", down ${Fmt.tenths(-delta)} lb since ${Fmt.shortDate(dayAt.getValue(firstAvg.key))}"
                        else -> ", same as ${Fmt.shortDate(dayAt.getValue(firstAvg.key))}"
                    },
                    Type.subhead, color = p.fg2,
                )
            }
        }
        Spacer(Modifier.height(Space.md))
        LineChart(
            listOf(
                daily.map { (d, v) -> Pt(dayAt.getValue(d), v.toFloat()) } to p.fg2,
                avg.map { (d, v) -> Pt(dayAt.getValue(d), v.toFloat()) } to p.accent,
            ),
            listOf("Day", "Avg"), minSpan = 6f, value = { Fmt.tenths(it.toDouble()) },
        )
        GroupHeader("Weigh-ins")
        Group {
            entries.asReversed().forEachIndexed { i, w ->
                val counted = dayAt[w.at.toLocalDate()] == w.at
                val target = Deletion.WeighIn(w.id)
                key(target) {
                    SwipeToDelete("Delete", deleting == target, { onDelete(target) }) {
                        GroupRow(first = i == 0, onClick = { onEdit(w) }) {
                            Column(Modifier.weight(1f)) {
                                Txt(Fmt.weighInTime(w.at, today), Type.body, maxLines = 1)
                                if (!counted) Txt("Later that day, not counted", Type.footnote, color = p.fg2, maxLines = 1)
                            }
                            Txt(Fmt.lb(w.lb), Type.body.tabular(), Modifier.padding(start = Space.sm))
                            Icon(Icons.chevronRight, p.fg3, 20.dp, Modifier.padding(start = Space.xs))
                        }
                    }
                }
            }
        }
    }
}
