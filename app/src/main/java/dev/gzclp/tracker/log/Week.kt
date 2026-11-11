package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Tier
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The Monday that starts [day]'s week. */
fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** One activity's cardio in a week; [distanceM] is null when no session gave one. */
data class ActivityTotal(val activity: String, val name: String, val sessions: Int, val sec: Long, val distanceM: Int?)

/** A lift done this week, its latest best against the latest earlier best of the same slot; [prev] is null the first time. */
data class LiftChange(val exercise: String, val tier: Tier, val stage: String, val now: SessionBest, val prev: SessionBest?)

data class WeekSummary(
    val monday: LocalDate,
    val gymSec: Long, val prevGymSec: Long, val workouts: Int,
    val cardioSec: Long, val prevCardioSec: Long, val cardioSessions: Int,
    val byActivity: List<ActivityTotal>,
    val activeDays: Set<LocalDate>,
    val lifts: List<LiftChange>,
    val bodyWeight: Double?, val prevBodyWeight: Double?,
)

/** The first day anything was logged: a workout with sets, a cardio session, or a weigh-in. */
fun AppState.firstDay(): LocalDate? =
    (sessions.filter { it.sets.isNotEmpty() }.map { it.startedAt.toLocalDate() } +
        cardio.values.map { it.startedAt.toLocalDate() } + bodyWeights.values.map { it.at.toLocalDate() }).minOrNull()

/**
 * The Monday to Sunday week from [monday]. Each record counts on its own local date. A lift's slot is
 * (exercise, tier) for GZCLP and (exercise, stage) for the cycle, so GZCLP compares with the last time at
 * that tier and a cycle stage with the same week of the last cycle.
 */
fun AppState.week(monday: LocalDate): WeekSummary {
    fun LocalDate.inWeek(m: LocalDate) = this >= m && this < m.plusDays(7)
    val prevMonday = monday.minusDays(7)
    val lifted = sessions.filter { it.sets.isNotEmpty() }
    fun gym(m: LocalDate) = lifted.filter { it.startedAt.toLocalDate().inWeek(m) }
    fun runs(m: LocalDate) = cardio.values.filter { it.startedAt.toLocalDate().inWeek(m) }.sortedBy { it.startedAt }
    val gym = gym(monday)
    val runs = runs(monday)
    val daily = dailyBodyWeight()
    fun mean(m: LocalDate) = daily.subMap(m, true, m.plusDays(6), true).values.takeIf { it.isNotEmpty() }?.average()

    fun slot(s: SetLogged) = Triple(s.exercise, s.tier, if (s.tier == Tier.CYCLE.id) s.stage else "")
    val lifts = gym.flatMap { s -> s.sets.map(::slot) }.distinct().mapNotNull { key ->
        val history = lifted.mapNotNull { s -> best(s.startedAt, s.sets.filter { slot(it) == key })?.let { s to it } }
        val i = history.indexOfLast { it.first.startedAt.toLocalDate().inWeek(monday) }.takeIf { it >= 0 } ?: return@mapNotNull null
        val (session, now) = history[i]
        val stage = session.sets.first { slot(it) == key }.stage
        LiftChange(key.first, Tier.of(key.second) ?: return@mapNotNull null, stage, now, history.getOrNull(i - 1)?.second)
    }

    return WeekSummary(
        monday,
        gym.sumOf { it.durationSec }, gym(prevMonday).sumOf { it.durationSec }, gym.size,
        runs.sumOf { it.durationSec }, runs(prevMonday).sumOf { it.durationSec }, runs.size,
        runs.groupBy { it.activity }.map { (id, cs) ->
            ActivityTotal(id, cs.last().name, cs.size, cs.sumOf { it.durationSec }, cs.mapNotNull { it.distanceM }.takeIf { it.isNotEmpty() }?.sum())
        }.sortedByDescending { it.sec },
        (gym.map { it.startedAt.toLocalDate() } + runs.map { it.startedAt.toLocalDate() }).toSet(),
        lifts,
        mean(monday), mean(prevMonday),
    )
}
