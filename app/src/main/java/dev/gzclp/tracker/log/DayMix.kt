package dev.gzclp.tracker.log

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth

/** What a day's training was; each kind keeps one color on every day marker. */
enum class Kind(val label: String) { LIFT("Lifting"), CARDIO("Cardio"), STRETCH("Stretching") }

/**
 * One kind's part of a day. [share] is its fraction of the day's training time, so the day's shares sum to 1;
 * [intensity] is its load against [Kind]'s full mark, 0 to 1: lifting and stretching by time, cardio by time × RPE.
 */
data class Segment(val kind: Kind, val sec: Long, val share: Float, val intensity: Float)

/** One logged session of any kind, on its own local date. */
data class Bout(val kind: Kind, val day: LocalDate, val sec: Long, val load: Double)

/** A full mark: 75 min of lifting, 30 of stretching, or an hour of cardio at RPE 7. */
private val FULL = mapOf(Kind.LIFT to 75 * 60.0, Kind.CARDIO to 60 * 60.0 * 7, Kind.STRETCH to 30 * 60.0)

/** Cardio with no RPE counts as moderate. */
private const val DEFAULT_RPE = 6.0

/** Every lifting session with sets, cardio session and stretch; a stretch stamped in a form the log never writes is left out. */
fun AppState.bouts(): List<Bout> =
    sessions.filter { it.sets.isNotEmpty() }.map { Bout(Kind.LIFT, it.startedAt.toLocalDate(), it.durationSec, it.durationSec.toDouble()) } +
        cardio.values.map { Bout(Kind.CARDIO, it.startedAt.toLocalDate(), it.durationSec, it.durationSec * (it.rpe ?: DEFAULT_RPE)) } +
        stretches.values.mapNotNull { s ->
            runCatching { OffsetDateTime.parse(s.at, LogCodec.TS).toLocalDate() }.getOrNull()?.let { Bout(Kind.STRETCH, it, s.durationSec, s.durationSec.toDouble()) }
        }

/** Each day's segments in [Kind] order. A day logged with no time at all splits evenly between its kinds. */
fun dayMix(bouts: List<Bout>): Map<LocalDate, List<Segment>> =
    bouts.groupBy { it.day }.mapValues { (_, day) ->
        val total = day.sumOf { it.sec }.toFloat()
        val kinds = day.groupBy { it.kind }.toSortedMap()
        kinds.map { (kind, bs) ->
            val sec = bs.sumOf { it.sec }
            Segment(kind, sec, if (total > 0) sec / total else 1f / kinds.size, (bs.sumOf { it.load } / FULL.getValue(kind)).coerceIn(0.0, 1.0).toFloat())
        }
    }

/** A month at a glance. Each average is per session of that kind, null with none. */
data class MonthStats(
    val sessions: Int, val perWeek: Double,
    val liftSec: Long?, val cardioSec: Long?, val stretchSec: Long?,
)

/**
 * [month]'s sessions of every kind and their weekly rate over the month's days so far: all of a past month,
 * up to [today] in this one, none in a later one.
 */
fun monthStats(bouts: List<Bout>, month: YearMonth, today: LocalDate): MonthStats {
    val inMonth = bouts.filter { YearMonth.from(it.day) == month }
    val days = when {
        month < YearMonth.from(today) -> month.lengthOfMonth()
        month == YearMonth.from(today) -> today.dayOfMonth
        else -> 0
    }
    fun avg(kind: Kind) = inMonth.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { bs -> bs.sumOf { it.sec } / bs.size }
    return MonthStats(
        inMonth.size, if (days == 0) 0.0 else inMonth.size * 7.0 / days,
        avg(Kind.LIFT), avg(Kind.CARDIO), avg(Kind.STRETCH),
    )
}
