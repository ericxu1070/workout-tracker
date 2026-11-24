package dev.gzclp.tracker.remind

import java.time.LocalDate
import java.time.LocalTime

enum class HabitKind { WEIGH_IN, MEAL, STRETCH, WATER }

data class WeekdayEma(
    val mean: DoubleArray,
    val n: IntArray,
    val allMean: Double,
    val allN: Int,
) {
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

fun predict(kind: HabitKind, date: LocalDate): List<LocalTime> = emptyList()
