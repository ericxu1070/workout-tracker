package dev.gzclp.tracker.log

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.TreeMap

data class BodyWeight(val id: String, val at: OffsetDateTime, val lb: Double)

/** A lift's estimated 1RM as a multiple of the body weight in effect that day. */
data class Ratio(val at: OffsetDateTime, val e1rm: Int, val bodyWeight: Double, val ratio: Double)

/** Weigh-ins oldest first. */
fun AppState.weighIns(): List<BodyWeight> = bodyWeights.values.sortedBy { it.at }

/** Each day's body weight: its earliest weigh-in, the morning one. */
fun AppState.dailyBodyWeight(): TreeMap<LocalDate, Double> =
    weighIns().groupBy { it.at.toLocalDate() }.mapValuesTo(TreeMap()) { (_, ws) -> ws.first().lb }

/** Mean of the day values in the 7 days ending on each day that has one. */
fun sevenDayAverage(daily: TreeMap<LocalDate, Double>): TreeMap<LocalDate, Double> =
    daily.keys.associateWithTo(TreeMap()) { d -> daily.subMap(d.minusDays(6), true, d, true).values.average() }

/** Body weight in effect on [day]: the latest day value on or before it, null before the first weigh-in. */
fun TreeMap<LocalDate, Double>.inEffect(day: LocalDate): Double? = floorEntry(day)?.value

/** Each session best divided by the body weight in effect that day; sessions before the first weigh-in are left out. */
fun ratios(bests: List<SessionBest>, daily: TreeMap<LocalDate, Double>): List<Ratio> =
    bests.mapNotNull { b -> daily.inEffect(b.at.toLocalDate())?.let { Ratio(b.at, b.e1rm, it, b.e1rm / it) } }

fun AppState.hasWeighIn(day: LocalDate): Boolean = bodyWeights.values.any { it.at.toLocalDate() == day }
