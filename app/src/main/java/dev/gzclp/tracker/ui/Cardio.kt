package dev.gzclp.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.Activity
import dev.gzclp.tracker.engine.DistanceUnit
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Cardio
import dev.gzclp.tracker.log.CardioRun
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.mondayOf
import dev.gzclp.tracker.log.week
import dev.gzclp.tracker.health.HealthRecords
import dev.gzclp.tracker.ui.Type.tabular
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The Workout tab's Lifting, Cardio and Stretching screens as pages under one header, the gear and their switch:
 * swipe the pages, or tap or drag the switch. The header stays put while the pages slide or scroll.
 */
@Composable
fun WorkoutPages(mode: Int, onMode: (Int) -> Unit, onSettings: () -> Unit, page: @Composable (Int) -> Unit) {
    val p = LocalPalette.current
    val pager = rememberPages(mode, 3, onMode)
    // All three stay composed: a thumb dragged from Lifting to Stretching outlives its page sliding off screen.
    Pages(pager, beyond = 2, header = {
        NavRow { IconButton(Icons.settings, "Settings", onSettings, tint = p.fg2) }
        Segmented(listOf("Lifting", "Cardio", "Stretching"), mode, { if (it != mode) onMode(it) }, pager = pager)
    }, page = page)
}

/** Finished sessions whose own local start date falls in the week from [monday], newest first. */
fun AppState.cardioWeek(monday: LocalDate): List<Cardio> =
    cardio.values.filter { it.startedAt.toLocalDate() >= monday && it.startedAt.toLocalDate() < monday.plusDays(7) }.sortedByDescending { it.startedAt }

/** Where the slider snaps for each unit. */
val DistanceUnit.step: Double get() = when (this) {
    DistanceUnit.MI -> 0.1
    DistanceUnit.YD -> 25.0
    DistanceUnit.M -> 100.0
}

/** The distance slider's right end: its default reach, a bit past [longestM] for this activity, or [typed], whichever is biggest. */
fun distanceMax(unit: DistanceUnit, longestM: Int?, typed: Double): Double {
    val reach = when (unit) {
        DistanceUnit.MI -> 10.0
        DistanceUnit.YD -> 2000.0
        DistanceUnit.M -> 10000.0
    }
    return maxOf(reach, ceil((longestM ?: 0) / unit.meters * 1.2 / unit.step) * unit.step, typed)
}

/** 5.0, 3.12 (miles show a second decimal only when typed); 1,500 for yards and meters. */
fun distanceValue(v: Double, unit: DistanceUnit): String = when {
    unit != DistanceUnit.MI -> "%,d".format(Locale.US, Math.round(v))
    Math.abs(v * 10 - Math.round(v * 10)) < 1e-4 -> "%.1f".format(Locale.US, v)
    else -> "%.2f".format(Locale.US, v)
}

/** 3.10 mi, 1,650 yd from meters, in the activity's unit. */
fun distanceText(meters: Int, unit: DistanceUnit): String =
    if (unit == DistanceUnit.MI) "%.2f mi".format(Locale.US, meters / unit.meters) else "%,d ${unit.label}".format(Locale.US, Math.round(meters / unit.meters))

/** The sheet for logging a past [a] session: 30 minutes that ended [now]. */
fun cardioPast(a: Activity, now: OffsetDateTime) =
    SheetState.CardioStop(CardioRun("", a.id, a.name, now.minusMinutes(30).withSecond(0).withNano(0)), Commands.CARDIO_DURATION_SEC.last, 30 * 60L, now = now)

/** The sheet for editing the finished session [c], prefilled. */
fun cardioEdit(c: Cardio, now: OffsetDateTime): SheetState.CardioStop {
    val d = Activities.of(c.activity)?.unit?.let { u -> c.distanceM?.let { it / u.meters } } ?: 0.0
    return SheetState.CardioStop(
        CardioRun(c.id, c.activity, c.name, c.startedAt), Commands.CARDIO_DURATION_SEC.last, c.durationSec, d,
        rpe = c.rpe?.let { (it * 2 - 1).roundToInt() } ?: 0, notes = c.notes ?: "", reach = d, now = now,
    )
}

/** [s] switched to activity [a]; a distance in another unit no longer means anything, so it is dropped. */
fun SheetState.CardioStop.withActivity(a: Activity): SheetState.CardioStop =
    if (Activities.of(a.id)?.unit == unit) copy(run = run.copy(activity = a.id, name = a.name))
    else copy(run = run.copy(activity = a.id, name = a.name), distance = 0.0, distanceText = null, reach = 0.0)

/** The time [run]'s clock shows: [now], or the moment Stop was tapped while its stop [sheet] is open. */
fun cardioClock(run: CardioRun, sheet: SheetState?, now: OffsetDateTime): OffsetDateTime =
    (sheet as? SheetState.CardioStop)?.takeIf { it.now == null && it.run.id == run.id }?.let { run.startedAt.plusSeconds(it.maxSec) } ?: now

/** The duration that ends at the first [hour]:[minute] after [start], kept within a minute and [maxSec]. */
fun durationEndingAt(start: OffsetDateTime, hour: Int, minute: Int, maxSec: Long): Long {
    val end = start.withHour(hour).withMinute(minute).withSecond(0).let { if (it.isAfter(start)) it else it.plusDays(1) }
    return Duration.between(start, end).seconds.coerceIn(minOf(60, maxSec), maxSec)
}

@Composable
fun CardioScreen(
    state: AppState,
    today: LocalDate,
    goalMin: Int,
    onStart: () -> Unit,
    onQuick: (Activity) -> Unit,
    onLogPast: () -> Unit,
    onEdit: (Cardio) -> Unit,
    lifting: Boolean = false,
) {
    val p = LocalPalette.current
    val week = mondayOf(today)
    val summary = state.week(week)
    val total = summary.cardioSec
    ScreenFrame(bottom = { BottomBar { PillButton(if (lifting) "Lift in progress" else "Start cardio", onStart, Modifier.weight(1f), primary = true, enabled = !lifting) } }) {
        Spacer(Modifier.height(Space.xxl))
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(Fmt.minutes(total), Type.title1)
            Spacer(Modifier.width(Space.sm))
            Txt("of ${Fmt.minutes(goalMin * 60L)}", Type.subhead, Modifier.padding(bottom = 4.dp), p.fg2)
        }
        Spacer(Modifier.height(Space.md))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(p.fill)) {
            Box(Modifier.fillMaxWidth((total / 60f / goalMin).coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(p.accent))
        }
        val weeks = (3 downTo 0).map { week.minusWeeks(it.toLong()) }
        val mins = weeks.map { w -> (if (w == week) total else state.week(w).cardioSec) / 60f }
        if (mins.any { it > 0f }) {
            Spacer(Modifier.height(Space.xl))
            WeekBars(weeks.map { if (it == week) "This week" else Fmt.shortDate(it) }, mins)
        }
        // One session runs at a time: during a lift only logging a past session works.
        val recent = if (lifting) emptyList() else Commands.recentActivities(state, 3)
        if (recent.isNotEmpty()) {
            GroupHeader("Quick start")
            Group {
                recent.forEachIndexed { i, a ->
                    GroupRow(first = i == 0, onClick = { onQuick(a) }) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(p.fill), contentAlignment = Alignment.Center) { Icon(Icons.play, p.fg, 14.dp) }
                        Txt(a.name, Type.body, Modifier.weight(1f).padding(start = Space.md), maxLines = 1)
                    }
                }
            }
        }
        if (summary.byActivity.isEmpty()) {
            Spacer(Modifier.height(Space.xl))
            Group { AddRow("Log past session", first = true, onClick = onLogPast) }
            return@ScreenFrame
        }
        GroupHeader("Sessions")
        Group {
            state.cardioWeek(week).forEachIndexed { i, c ->
                val unit = Activities.of(c.activity)?.unit ?: DistanceUnit.MI
                GroupRow(first = i == 0, onClick = { onEdit(c) }) {
                    Column(Modifier.weight(1f)) {
                        Txt(c.name, Type.body, maxLines = 1)
                        Txt(
                            listOfNotNull(
                                "${Fmt.date(c.startedAt)}, ${Fmt.time(c.startedAt)}",
                                c.distanceM?.let { distanceText(it, unit) },
                                c.distanceM?.let { HealthRecords.pace(c.activity, unit, it, c.durationSec) },
                            ).joinToString(" · "),
                            Type.footnote, color = p.fg2, maxLines = 1,
                        )
                    }
                    Txt(Fmt.minutes(c.durationSec), Type.subhead.tabular(), Modifier.padding(start = Space.sm), p.fg2)
                }
            }
            AddRow("Log past session", first = false, onClick = onLogPast)
        }
    }
}

/** Minutes per week as bars, the last one (this week) in the accent, on gridlines a multiple of 15 min apart. */
@Composable
private fun WeekBars(labels: List<String>, mins: List<Float>) {
    val p = LocalPalette.current
    val tm = rememberTextMeasurer()
    val top = mins.max()
    val step = generateSequence(15f) { it * 2 }.first { it * 3 >= top }
    val hi = ceil(top / step) * step
    fun read(i: Int) = labels[i] + ", " + Fmt.minutes((mins[i] * 60).roundToLong())
    val pick = remember(labels, mins) { mutableStateOf<Int?>(null) }
    Canvas(Modifier.fillMaxWidth().height(140.dp).chartPick(pick, mins.size, mins.indices.joinToString("; ") { read(it) }, ::read) { i, w ->
        val left = 48.dp.toPx()
        left + (w - left) / mins.size * (i + 0.5f)
    }) {
        val left = 48.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        fun y(v: Float) = bottom - (bottom - 8.dp.toPx()) * v / hi
        var g = 0f
        while (g <= hi + 0.1f) {
            drawLine(p.separator, Offset(left, y(g)), Offset(size.width, y(g)), 1.dp.toPx())
            label(tm, if (g + step > hi + 0.1f) "${g.toInt()} min" else "${g.toInt()}", Offset(0f, y(g)), p.fg2, alignY = 0.5f)
            g += step
        }
        val slot = (size.width - left) / mins.size
        val barW = slot * 0.42f
        mins.forEachIndexed { i, m ->
            val x = left + slot * i + (slot - barW) / 2
            val h = (bottom - y(m)).coerceAtLeast(2.dp.toPx())
            val color = if (i == mins.lastIndex) p.accent else p.fg2
            if (m > 0f) drawRoundRect(if (pick.value.let { it != null && it != i }) color.copy(alpha = color.alpha * 0.35f) else color, Offset(x, bottom - h), Size(barW, h), CornerRadius(3.dp.toPx()))
            label(tm, labels[i], Offset(x + barW / 2, bottom + 6.dp.toPx()), p.fg2, alignX = 0.5f)
        }
        pick.value?.takeIf { it < mins.size }?.let { i ->
            callout(tm, p, left + slot * (i + 0.5f), labels[i], listOf(Fmt.minutes((mins[i] * 60).roundToLong()) to p.fg), barW / 2)
        }
    }
}

/** The Workout tab while a cardio session runs: a big clock counting up from its logged start. */
@Composable
fun CardioRunScreen(run: CardioRun, now: OffsetDateTime, onSettings: () -> Unit, onDiscard: () -> Unit, onStop: () -> Unit) {
    val p = LocalPalette.current
    val sec = Duration.between(run.startedAt, now).seconds
    ScreenFrame(bottom = {
        BottomBar {
            PillButton("Discard cardio", onDiscard, Modifier.weight(1f))
            PillButton("Stop cardio", onStop, Modifier.weight(1f), primary = true)
        }
    }) {
        NavRow { IconButton(Icons.settings, "Settings", onSettings, tint = p.fg2) }
        Spacer(Modifier.height(96.dp))
        Txt(run.name, Type.title2, Modifier.fillMaxWidth(), maxLines = 1, align = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        // Ten hours and up needs seven digits, so the clock steps down a size to stay on one line.
        val size = if (sec >= 36_000) 72.sp else 96.sp
        Txt(Fmt.clock(sec), Type.display.copy(fontSize = size, lineHeight = size * 1.12f), Modifier.fillMaxWidth(), maxLines = 1, align = TextAlign.Center)
        Txt("Started ${Fmt.time(run.startedAt)}", Type.subhead, Modifier.fillMaxWidth(), p.fg2, align = TextAlign.Center)
    }
}

/** Picks the activity to start: recents, then all, or search results with a row to add what was typed. */
@Composable
fun ActivitiesScreen(state: AppState, onBack: () -> Unit, onPick: (Activity) -> Unit, initial: String = "", title: String = "Start cardio") {
    var q by rememberSaveable { mutableStateOf(initial) }
    ScreenFrame {
        NavRow(onBack)
        Txt(title, Type.largeTitle)
        Spacer(Modifier.height(Space.lg))
        NameField(q, "Search", Activities.NAME_MAX) { q = it }
        val t = q.trim()
        if (t.isEmpty()) {
            val recent = Commands.recentActivities(state, 5)
            if (recent.isNotEmpty()) {
                GroupHeader("Recent")
                ActivityList(recent, onPick)
            }
            GroupHeader("All")
            ActivityList(Activities.search(""), onPick)
            return@ScreenFrame
        }
        val custom = state.cardio.values.filter { Activities.of(it.activity) == null }.distinctBy { it.activity }.map { Activity(it.activity, it.name) }
        val found = Activities.search(t) + custom.filter { it.name.contains(t, ignoreCase = true) }
        val typed = Activities.typed(t)?.takeIf { a -> found.none { it.name.equals(a.name, ignoreCase = true) } }
        Spacer(Modifier.height(Space.lg))
        ActivityList(found, onPick, typed)
    }
}

@Composable
private fun ActivityList(items: List<Activity>, onPick: (Activity) -> Unit, add: Activity? = null) {
    Group {
        items.forEachIndexed { i, a -> GroupRow(first = i == 0, onClick = { onPick(a) }) { Txt(a.name, Type.body, Modifier.weight(1f), maxLines = 1) } }
        if (add != null) AddRow("Add \"${add.name}\"", first = items.isEmpty()) { onPick(add) }
    }
}

/** What the distance field accepts while typing: whole yards or meters, miles with up to 2 decimals. */
private fun distanceInput(unit: DistanceUnit) = if (unit == DistanceUnit.MI) Regex("\\d{0,3}(\\.\\d{0,2})?") else Regex("\\d{0,6}")

/**
 * The stop sheet below its title: duration, start – end, distance, RPE, notes, Save. Logging or editing a finished session adds
 * the activity and day above, makes the start time tappable, and puts Delete beside Save for an edit. Scrolls when the keyboard leaves too little room.
 */
@Composable
fun CardioStopSheet(
    s: SheetState.CardioStop, state: AppState, update: (SheetState) -> Unit,
    onPickEnd: () -> Unit, onPickStart: () -> Unit, onActivity: () -> Unit, onSave: () -> Unit,
) {
    val p = LocalPalette.current
    val now = s.now
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        if (now != null) Column {
            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Activity", Type.body, Modifier.weight(1f))
                Pressable(onActivity, Modifier.heightIn(min = 44.dp), label = "Change activity") { Txt(s.run.name, Type.headline, color = p.accent, maxLines = 1) }
            }
            val start = s.run.startedAt
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniStep(Icons.chevronLeft, "Day before") { update(s.copy(run = s.run.copy(startedAt = start.minusDays(1)))) }
                Txt(Fmt.day(start.toLocalDate(), now.toLocalDate()), Type.body.tabular(), Modifier.weight(1f), align = TextAlign.Center)
                MiniStep(Icons.chevronRight, "Day after", enabled = !start.plusDays(1).isAfter(now)) { update(s.copy(run = s.run.copy(startedAt = start.plusDays(1)))) }
            }
        }
        Column {
            Stepper(
                Fmt.clock(s.durationSec),
                { update(s.copy(durationSec = maxOf(s.durationSec - 60, minOf(60, s.maxSec)))) },
                { update(s.copy(durationSec = minOf(s.durationSec + 60, s.maxSec))) },
                repeat = true,
            )
            val end = s.run.startedAt.plusSeconds(s.durationSec)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val startText = Fmt.timeRange(s.run.startedAt, end).substringBefore(" – ")
                if (now == null) Txt(startText, Type.subhead.tabular(), color = p.fg2)
                else Pressable(onPickStart, Modifier.heightIn(min = 44.dp), label = "Change start time") { Txt(startText, Type.subhead.tabular(), color = p.accent) }
                Txt(" – ", Type.subhead.tabular(), color = p.fg2)
                Pressable(onPickEnd, Modifier.heightIn(min = 44.dp), label = "Change end time") { Txt(Fmt.time(end), Type.subhead.tabular(), color = p.accent) }
            }
        }
        val unit = s.unit
        if (unit != null) Column {
            val longest = state.cardio.values.filter { it.activity == s.run.activity }.mapNotNull { it.distanceM }.maxOrNull()
            val max = distanceMax(unit, longest, maxOf(s.reach, if (s.distanceText != null) s.distance else 0.0))
            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Distance", Type.body, Modifier.weight(1f))
                if (s.distanceText != null) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    NumberField(
                        s.distanceText, { if (distanceInput(unit).matches(it)) update(s.copy(distanceText = it, distance = it.toDoubleOrNull() ?: 0.0)) },
                        modifier = Modifier.focusRequester(focus), keyboardType = KeyboardType.Decimal, label = "Distance in ${unit.label}",
                        onCommit = { update(s.copy(distanceText = null, reach = maxOf(s.reach, s.distance))) },
                    )
                    Txt(" ${unit.label}", Type.body, color = p.fg2)
                } else Pressable({ update(s.copy(distanceText = if (s.distance > 0) distanceValue(s.distance, unit) else "")) }, Modifier.heightIn(min = 44.dp), label = "Type the distance") {
                    if (s.distance > 0) Txt("${distanceValue(s.distance, unit)} ${unit.label}", Type.headline.tabular(), color = p.accent)
                    else Txt("Not set", Type.body, color = p.fg2)
                }
            }
            StepSlider(
                Math.round(s.distance / unit.step).toInt(), Math.round(max / unit.step).toInt(),
                { update(s.copy(distance = Math.round(it * unit.step * 1000) / 1000.0, distanceText = null)) }, "Distance in ${unit.label}",
            )
            Row {
                Txt("0", Type.caption, Modifier.weight(1f), p.fg2)
                Txt("${distanceValue(max, unit).removeSuffix(".0")} ${unit.label}", Type.caption, color = p.fg2)
            }
        }
        Column {
            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("RPE", Type.body, Modifier.weight(1f))
                s.rpeValue?.let { Txt(Fmt.rpe(it), Type.headline.tabular(), color = p.accent) } ?: Txt("Not set", Type.body, color = p.fg2)
            }
            StepSlider(s.rpe, 19, { update(s.copy(rpe = it)) }, "RPE")
            Row {
                Txt("Not set", Type.caption, Modifier.weight(1f), p.fg2)
                Txt("10", Type.caption, color = p.fg2)
            }
        }
        NameField(s.notes, "Notes (optional)", Commands.CARDIO_NOTES_MAX) { update(s.copy(notes = it)) }
        val ok = s.durationSec in Commands.CARDIO_DURATION_SEC && (now == null || !s.run.startedAt.isAfter(now))
        if (now == null || s.run.id.isEmpty()) PillButton("Save cardio", onSave, Modifier.fillMaxWidth(), primary = true, enabled = ok)
        else Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            PillButton("Delete cardio", { update(SheetState.CardioDiscard(s.run)) }, Modifier.weight(1f), onSurface = true, danger = true)
            PillButton("Save cardio", onSave, Modifier.weight(1f), primary = true, enabled = ok)
        }
    }
}
