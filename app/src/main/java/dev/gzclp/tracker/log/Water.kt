package dev.gzclp.tracker.log

import dev.gzclp.tracker.remind.HabitKind
import dev.gzclp.tracker.remind.predict
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

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
    const val ML_PER_OZ = 29.5735

    fun ozToMl(oz: Double): Int = Math.round(oz * ML_PER_OZ).toInt()
    fun mlToOz(ml: Int): Double = ml / ML_PER_OZ

    /** Formats a volume in ml and optional oz. */
    fun formatVolume(ml: Int, isLb: Boolean = true): String {
        return if (isLb) {
            val oz = ml / ML_PER_OZ
            "%.1f oz (%d ml)".format(Locale.US, oz, ml)
        } else {
            "$ml ml"
        }
    }

    /** Formats cup size for slider labels. */
    fun formatCupSize(ml: Int, isLb: Boolean = true): String {
        return if (isLb) {
            val oz = ml / ML_PER_OZ
            "$ml ml · %.1f oz".format(Locale.US, oz)
        } else {
            "$ml ml"
        }
    }

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

    /** Last logged water entry on [day] (or overall if null). */
    fun lastEntry(state: AppState, day: LocalDate? = null): WaterLogged? {
        return entriesForDay(state, day).lastOrNull()
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

    /** Undo the most recent water entry on [day]. */
    fun undoLast(state: AppState, day: LocalDate = LocalDate.now()): List<Event> {
        val last = lastEntry(state, day) ?: return emptyList()
        return clearWater(last.id)
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
        goalMl: Int = DEFAULT_GOAL_ML,
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
