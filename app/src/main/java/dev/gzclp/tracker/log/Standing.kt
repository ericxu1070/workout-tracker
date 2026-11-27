package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.NormLift
import dev.gzclp.tracker.engine.Norms
import dev.gzclp.tracker.engine.Peer
import java.time.LocalDate
import kotlin.math.roundToInt

/** How far back a lift's best estimated 1RM still counts as current strength. */
const val STANDING_DAYS = 90L

/** Where [lb] of [lift] falls among [peer]s: [percentile] is the share of them who lift less, 0.001 to 0.999. */
data class Standing(val lift: NormLift, val lb: Int, val percentile: Double, val peer: Peer) {
    /** The whole percentile, 1 to 99. */
    val rank: Int get() = (percentile * 100).roundToInt().coerceIn(1, 99)
}

/**
 * Who this lifter is compared with on [today]: their sex, the latest 7-day average body weight, and their age this year,
 * left out with no [birthYear]. Null before the first weigh-in.
 */
fun AppState.peer(female: Boolean, birthYear: Int?, today: LocalDate): Peer? {
    val lb = sevenDayAverage(dailyBodyWeight()).lastEntry()?.value ?: return null
    return Peer(female, lb * Norms.KG_PER_LB, birthYear?.let { (today.year - it).toDouble() })
}

/** Current strength from a lift's session [bests]: the best estimated 1RM in the [STANDING_DAYS] up to the latest session. */
fun recentBest(bests: List<SessionBest>): Int? {
    val from = bests.lastOrNull()?.at?.minusDays(STANDING_DAYS) ?: return null
    return bests.filter { !it.at.isBefore(from) }.maxOf { it.e1rm }
}

fun standing(lift: NormLift, lb: Int, peer: Peer): Standing =
    Standing(lift, lb, Norms.of(peer.female, lift).percentile(lb * Norms.KG_PER_LB, peer), peer)

/** Each competition lift's current estimated 1RM in lb; a lift never logged is left out. */
fun AppState.currentLifts(): Map<NormLift, Int> {
    val logs = exerciseLogs().associateBy { it.id }
    return NormLift.lifts.mapNotNull { l -> logs[l.id]?.let { recentBest(it.bests) }?.let { l to it } }.toMap()
}

/** The three lifts summed and ranked as a competition total; null until all three are logged. */
fun overall(lifts: Map<NormLift, Int>, peer: Peer): Standing? =
    if (NormLift.lifts.all { it in lifts }) standing(NormLift.TOTAL, NormLift.lifts.sumOf { lifts.getValue(it) }, peer) else null
