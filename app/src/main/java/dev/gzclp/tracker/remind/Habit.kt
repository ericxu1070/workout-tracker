package dev.gzclp.tracker.remind

import dev.gzclp.tracker.food.Meal
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.LogCodec
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Alarm request codes and notification ids are fixed per kind; WEIGH_IN's id 2 matches the old single weigh-in alarm. */
enum class HabitKind(val requestCode: Int, val channelId: String, val channelName: String) {
    WEIGH_IN(1, "weigh_in", "Weigh-in reminder"),
    MEAL(2, "meal", "Meal reminder"),
    STRETCH(3, "stretch", "Stretch reminder"),
    WATER(4, "water", "Water reminder");

    val notificationId: Int get() = requestCode + 1
}

const val DAY_START_HOUR = 4
const val MINUTES_PER_DAY = 24 * 60
const val ALPHA_WEEKDAY = 0.3
const val ALPHA_ALL = 0.15
const val MEAL_SLOT_WINDOW_MIN = 90
const val MEAL_SLOT_EXPIRY_DAYS = 21L
const val MAX_MEAL_SLOTS = 4

/**
 * Time of day is minutes since a 4:00 a.m. day start (so 00:30 is 1230), avoiding midnight wrap:
 * minutesFrom4am = (minuteOfDay - 4 * 60 + 24 * 60) % (24 * 60)
 */
fun timeToMinutesFrom4am(time: LocalTime): Int {
    val minuteOfDay = time.hour * 60 + time.minute
    return (minuteOfDay - DAY_START_HOUR * 60 + MINUTES_PER_DAY) % MINUTES_PER_DAY
}

/**
 * Converts minutes since 4:00 a.m. back to [LocalTime]:
 * (predictedMinutes.roundToInt() + 4 * 60) % (24 * 60)
 */
fun minutesFrom4amToTime(minutes: Double): LocalTime {
    val totalMinutes = (minutes.roundToInt() + DAY_START_HOUR * 60).mod(MINUTES_PER_DAY)
    return LocalTime.of(totalMinutes / 60, totalMinutes % 60)
}

/** The logical day starting at 4:00 a.m. */
fun logicalDateOf(local: LocalDateTime): LocalDate =
    if (local.hour < DAY_START_HOUR) local.toLocalDate().minusDays(1) else local.toLocalDate()

data class WeekdayEma(
    val mean: DoubleArray = DoubleArray(7),
    val n: IntArray = IntArray(7),
    val allMean: Double = 0.0,
    val allN: Int = 0,
) {
    /** Updates weekday EMA with alpha=0.3 and all-days EMA with alpha=0.15. */
    fun update(dayOfWeek: DayOfWeek, minutesFrom4am: Double): WeekdayEma {
        val d = dayOfWeek.value - 1
        val newMean = mean.clone()
        val newN = n.clone()
        if (newN[d] == 0) {
            newMean[d] = minutesFrom4am
            newN[d] = 1
        } else {
            newMean[d] = (1.0 - ALPHA_WEEKDAY) * newMean[d] + ALPHA_WEEKDAY * minutesFrom4am
            newN[d] += 1
        }
        val newAllMean = if (allN == 0) minutesFrom4am else (1.0 - ALPHA_ALL) * allMean + ALPHA_ALL * minutesFrom4am
        val newAllN = allN + 1
        return WeekdayEma(newMean, newN, newAllMean, newAllN)
    }

    /**
     * Prediction shrinks toward the all-days mean while a weekday is new:
     * predictedMinutes = (n_d * mu_d + 2.0 * mu_all) / (n_d + 2.0)
     */
    fun predictMinutes(dayOfWeek: DayOfWeek): Double? {
        if (allN == 0) return null
        val d = dayOfWeek.value - 1
        val nd = n[d].toDouble()
        val muD = mean[d]
        return (nd * muD + 2.0 * allMean) / (nd + 2.0)
    }

    fun predictTime(dayOfWeek: DayOfWeek): LocalTime? {
        val mins = predictMinutes(dayOfWeek) ?: return null
        return minutesFrom4amToTime(mins)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeekdayEma) return false
        return mean.contentEquals(other.mean) && n.contentEquals(other.n) && allMean == other.allMean && allN == other.allN
    }

    override fun hashCode(): Int {
        var result = mean.contentHashCode()
        result = 31 * result + n.contentHashCode()
        result = 31 * result + allMean.hashCode()
        result = 31 * result + allN
        return result
    }
}

/** One meal slot on a weekday. */
data class MealSlot(
    val mean: Double,
    val n: Int,
    val lastDate: LocalDate,
) {
    fun time(): LocalTime = minutesFrom4amToTime(mean)
}

/**
 * MEAL keeps up to 4 slots per weekday.
 * A photo updates the nearest slot within 90 minutes, else creates one.
 * Drop a slot not hit in 21 days.
 */
data class MealTracker(
    val slots: Map<DayOfWeek, List<MealSlot>> = emptyMap(),
    val totalCount: Int = 0,
) {
    fun addMeal(dateTime: OffsetDateTime, zone: ZoneId = ZoneId.systemDefault()): MealTracker {
        val local = dateTime.atZoneSameInstant(zone).toLocalDateTime()
        val logicalDate = logicalDateOf(local)
        val minutes = timeToMinutesFrom4am(local.toLocalTime()).toDouble()
        return addMeal(logicalDate, minutes)
    }

    fun addMeal(logicalDate: LocalDate, minutes: Double): MealTracker {
        val dayOfWeek = logicalDate.dayOfWeek
        val currentSlots = slots[dayOfWeek].orEmpty()
        val activeSlots = currentSlots.filter { ChronoUnit.DAYS.between(it.lastDate, logicalDate) <= MEAL_SLOT_EXPIRY_DAYS }.toMutableList()

        var nearestIdx = -1
        var nearestDist = Double.MAX_VALUE
        for (i in activeSlots.indices) {
            val diff = abs(minutes - activeSlots[i].mean)
            val dist = minOf(diff, MINUTES_PER_DAY - diff)
            if (dist < nearestDist) {
                nearestDist = dist
                nearestIdx = i
            }
        }

        if (nearestIdx >= 0 && nearestDist <= MEAL_SLOT_WINDOW_MIN) {
            val s = activeSlots[nearestIdx]
            val updated = s.copy(
                mean = (1.0 - ALPHA_WEEKDAY) * s.mean + ALPHA_WEEKDAY * minutes,
                n = s.n + 1,
                lastDate = logicalDate,
            )
            activeSlots[nearestIdx] = updated
        } else {
            val created = MealSlot(mean = minutes, n = 1, lastDate = logicalDate)
            activeSlots.add(created)
            if (activeSlots.size > MAX_MEAL_SLOTS) {
                val oldest = activeSlots.minByOrNull { it.lastDate }
                activeSlots.remove(oldest)
            }
        }
        activeSlots.sortBy { it.mean }
        return MealTracker(slots + (dayOfWeek to activeSlots), totalCount + 1)
    }

    fun predict(date: LocalDate): List<LocalTime> {
        val activeSlots = slots[date.dayOfWeek].orEmpty()
            .filter { ChronoUnit.DAYS.between(it.lastDate, date) <= MEAL_SLOT_EXPIRY_DAYS }
        return activeSlots.map { it.time() }.sorted()
    }
}

/** Complete learned habits model for all kinds. */
data class HabitModel(
    val weighIn: WeekdayEma = WeekdayEma(),
    val meals: MealTracker = MealTracker(),
    val stretch: WeekdayEma = WeekdayEma(),
) {
    fun predict(kind: HabitKind, date: LocalDate): List<LocalTime> = when (kind) {
        HabitKind.WEIGH_IN -> weighIn.predictTime(date.dayOfWeek)?.let { listOf(it) }.orEmpty()
        HabitKind.MEAL -> meals.predict(date)
        HabitKind.STRETCH -> stretch.predictTime(date.dayOfWeek)?.let { listOf(it) }.orEmpty()
        // Water reminders follow intake pace from the wake time, not a learned drinking time.
        HabitKind.WATER -> emptyList()
    }

    fun count(kind: HabitKind): Int = when (kind) {
        HabitKind.WEIGH_IN -> weighIn.allN
        HabitKind.MEAL -> meals.totalCount
        HabitKind.STRETCH -> stretch.allN
        HabitKind.WATER -> 0
    }
}

/** Only each logical day's first event trains the EMA. */
private fun firstPerDay(times: List<LocalDateTime>): WeekdayEma =
    times.groupBy(::logicalDateOf).toSortedMap().entries.fold(WeekdayEma()) { ema, (day, ts) ->
        ema.update(day.dayOfWeek, ts.minOf { timeToMinutesFrom4am(it.toLocalTime()) }.toDouble())
    }

/** Rebuilds habits model from app state events and food folder photos. */
fun rebuildHabits(state: AppState, meals: List<Meal>, zone: ZoneId = ZoneId.systemDefault()): HabitModel {
    fun local(at: String) = runCatching { OffsetDateTime.parse(at, LogCodec.TS).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
    return HabitModel(
        weighIn = firstPerDay(state.bodyWeights.values.map { it.at.atZoneSameInstant(zone).toLocalDateTime() }),
        meals = meals.sortedBy { it.at }.fold(MealTracker()) { t, m -> t.addMeal(m.at, zone) },
        stretch = firstPerDay(state.stretches.values.mapNotNull { local(it.at) }),
    )
}

fun AppState.hasStretch(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    stretches.values.any { runCatching { OffsetDateTime.parse(it.at, LogCodec.TS).atZoneSameInstant(zone).toLocalDate() == day }.getOrDefault(false) }

/** In-memory cache for habit models. */
@Volatile
var cachedHabits: HabitModel = HabitModel()

fun updateHabitCache(state: AppState, meals: List<Meal>, zone: ZoneId = ZoneId.systemDefault()): HabitModel {
    val model = rebuildHabits(state, meals, zone)
    cachedHabits = model
    return model
}

/** Global predict function delegating to in-memory cached habit model. */
fun predict(kind: HabitKind, date: LocalDate): List<LocalTime> = cachedHabits.predict(kind, date)
