package dev.gzclp.tracker.log

import dev.gzclp.tracker.remind.HabitKind
import dev.gzclp.tracker.remind.predict
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Water tracking logic, commands, and pace reminder evaluation.
 */
object WaterLog {
    const val DEFAULT_GOAL_ML = 2500
    const val DEFAULT_CUP_ML = 250
    const val DEFICIT_THRESHOLD_ML = 500
    const val MIN_REMINDER_INTERVAL_MINUTES = 90L
    const val ACTIVE_HOURS = 14
    val DEFAULT_WAKE_TIME: LocalTime = LocalTime.of(7, 0)

    const val ML_PER_KG = 30
    const val LIFT_ML_PER_HOUR = 500
    const val CARDIO_ML_PER_HOUR = 1000
    const val MAX_ACTIVITY_ML = 2000
    val GOAL_RANGE = 500..8000
    private const val KG_PER_LB = 0.45359237

    /**
     * A day's water goal: 30 ml per kg of body weight (NICE CG174's 25 to 30 ml/kg/day), or [DEFAULT_GOAL_ML] with no
     * weigh-in, plus 0.5 L per hour of lifting and 1 L per hour of cardio (at most [MAX_ACTIVITY_ML]) to replace sweat
     * (ACSM 2007, Exercise and Fluid Replacement). Rounded to 100 ml so the Settings stepper can land on it.
     */
    fun recommendedMl(weightLb: Double?, liftSec: Long, cardioSec: Long): Int {
        val base = weightLb?.let { it * KG_PER_LB * ML_PER_KG } ?: DEFAULT_GOAL_ML.toDouble()
        val activity = (liftSec * LIFT_ML_PER_HOUR + cardioSec * CARDIO_ML_PER_HOUR) / 3600.0
        val ml = base + activity.coerceAtMost(MAX_ACTIVITY_ML.toDouble())
        return (Math.round(ml / 100) * 100).toInt().coerceIn(GOAL_RANGE)
    }

    /** [recommendedMl] from the body weight in effect on [day] and that day's logged workouts and cardio. */
    fun recommendedMl(state: AppState, day: LocalDate): Int = recommendedMl(
        state.dailyBodyWeight().inEffect(day),
        state.sessions.filter { it.sets.isNotEmpty() && it.startedAt.toLocalDate() == day }.sumOf { it.durationSec },
        state.cardio.values.filter { it.startedAt.toLocalDate() == day }.sumOf { it.durationSec },
    )

    /** The goal on [day]: the user's [override] when set, otherwise [recommendedMl]. */
    fun goalMl(state: AppState, day: LocalDate, override: Int?): Int = override ?: recommendedMl(state, day)

    /** A volume as "250 ml · 8.5 oz". */
    fun formatVolume(ml: Int): String = "$ml ml"

    /** Returns all water entries for [day] (or all if null), chronologically ordered. */
    fun entriesForDay(state: AppState, day: LocalDate? = null): List<WaterLogged> {
        val list = state.water.values.toList().sortedBy { it.at }
        if (day == null) return list
        return list.filter {
            try {
                OffsetDateTime.parse(it.at, LogCodec.TS).toLocalDate() == day
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Total water consumed in ml on [day]. */
    fun totalMlForDay(state: AppState, day: LocalDate): Int {
        return entriesForDay(state, day).sumOf { it.ml }
    }

    /** Command to log water intake. Releasing above 10% logs [ml]. */
    fun logWater(
        state: AppState,
        ml: Int,
        at: OffsetDateTime = OffsetDateTime.now(),
        id: String? = null,
    ): List<Event> {
        if (ml <= 0) return emptyList()
        val entryId = id ?: generateSequence(0) { it + 1 }
            .map { "wt${at.toEpochSecond()}" + if (it == 0) "" else "_$it" }
            .first { it !in state.water }
        return listOf(WaterLogged(entryId, LogCodec.TS.format(at), ml))
    }

    /** Command to clear/undo a water entry. */
    fun clearWater(id: String): List<Event> {
        return listOf(WaterCleared(id))
    }

    /**
     * Wake time proxy: predicted weigh-in time on [date], or 7:00 AM if none.
     */
    fun predictedWakeTime(date: LocalDate = LocalDate.now()): LocalTime {
        return predict(HabitKind.WEIGH_IN, date).firstOrNull() ?: DEFAULT_WAKE_TIME
    }

    /**
     * Linear pace calculation:
     * Intake pace begins at [wakeTime] and runs for [activeHours] (default 14h, e.g. 7am -> 9pm).
     * At [now], expected intake = goalMl * (elapsedMinutes / (activeHours * 60)).
     */
    fun expectedIntakeMl(
        now: LocalTime,
        goalMl: Int = DEFAULT_GOAL_ML,
        wakeTime: LocalTime = DEFAULT_WAKE_TIME,
        activeHours: Int = ACTIVE_HOURS,
    ): Int {
        val totalActiveMinutes = activeHours * 60L
        val minutesFromWake = Duration.between(wakeTime, now).toMinutes().let {
            if (it < 0) it + 24 * 60 else it
        }
        if (minutesFromWake > totalActiveMinutes) {
            // Before wake time or after active window
            return if (now.isBefore(wakeTime)) 0 else goalMl
        }
        return ((minutesFromWake.toDouble() / totalActiveMinutes) * goalMl).toInt()
    }

    /**
     * Deficit in ml: expected intake at [now] minus [currentIntakeMl].
     * Positive means behind pace; negative or zero means on/ahead of pace.
     */
    fun intakeDeficitMl(
        currentIntakeMl: Int,
        now: LocalTime,
        goalMl: Int = DEFAULT_GOAL_ML,
        wakeTime: LocalTime = DEFAULT_WAKE_TIME,
        activeHours: Int = ACTIVE_HOURS,
    ): Int {
        val expected = expectedIntakeMl(now, goalMl, wakeTime, activeHours)
        return expected - currentIntakeMl
    }

    /**
     * Reminder evaluation:
     * If intake is behind a linear pace from wake proxy to wake + 14h by more than 500 ml,
     * notify, at most every 90 minutes.
     * Outside the active window (before wake or after wake + 14h), returns false.
     */
    fun shouldRemind(
        currentIntakeMl: Int,
        now: LocalTime,
        goalMl: Int = DEFAULT_GOAL_ML,
        wakeTime: LocalTime = DEFAULT_WAKE_TIME,
        lastReminderTime: LocalTime? = null,
        activeHours: Int = ACTIVE_HOURS,
        deficitThresholdMl: Int = DEFICIT_THRESHOLD_ML,
        minIntervalMinutes: Long = MIN_REMINDER_INTERVAL_MINUTES,
    ): Boolean {
        val totalActiveMinutes = activeHours * 60L
        val minutesFromWake = Duration.between(wakeTime, now).toMinutes().let {
            if (it < 0) it + 24 * 60 else it
        }
        // Outside active window: no reminders while sleeping
        if (minutesFromWake > totalActiveMinutes || now.isBefore(wakeTime)) return false

        // Check if behind pace by more than threshold (500 ml)
        val deficit = intakeDeficitMl(currentIntakeMl, now, goalMl, wakeTime, activeHours)
        if (deficit <= deficitThresholdMl) return false

        // Check 90-minute cooldown
        if (lastReminderTime != null) {
            val minutesSinceLast = ChronoUnit.MINUTES.between(lastReminderTime, now)
            if (minutesSinceLast in 0 until minIntervalMinutes) return false
        }

        return true
    }

    /** Overload using OffsetDateTime. */
    fun shouldRemind(
        state: AppState,
        now: OffsetDateTime = OffsetDateTime.now(),
        goalMl: Int = recommendedMl(state, now.toLocalDate()),
        lastRemindedAt: OffsetDateTime? = null,
        wakeTime: LocalTime = predictedWakeTime(now.toLocalDate()),
    ): Boolean {
        val intake = totalMlForDay(state, now.toLocalDate())
        val lastTime = lastRemindedAt?.takeIf { it.toLocalDate() == now.toLocalDate() }?.toLocalTime()
        return shouldRemind(
            currentIntakeMl = intake,
            now = now.toLocalTime(),
            goalMl = goalMl,
            wakeTime = wakeTime,
            lastReminderTime = lastTime,
        )
    }
}
