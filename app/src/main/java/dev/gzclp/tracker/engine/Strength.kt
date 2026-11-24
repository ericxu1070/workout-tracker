package dev.gzclp.tracker.engine

/**
 * Estimated 1RM using Epley with RIR: `load * (1 + (reps + rir) / 30)`.
 * Sets over 12 reps are ignored (returns 0.0).
 */
fun e1rm(loadLb: Double, reps: Int, rir: Double = 0.0): Double {
    if (reps > 12 || reps <= 0 || loadLb <= 0.0) return 0.0
    return loadLb * (1.0 + (reps + rir) / 30.0)
}

/**
 * Invert Epley: load = e1rm / (1 + (reps + rir) / 30), then round5.
 */
fun suggestLoad(e1rm: Double, reps: Int, targetRir: Double): Int {
    if (reps <= 0 || e1rm <= 0.0) return 0
    val load = e1rm / (1.0 + (reps + targetRir) / 30.0)
    return round5(load)
}

/**
 * Target RIR by tier: 2 for T1 and T2, 3 for T3 and cycle weeks.
 */
fun targetRir(tier: Tier): Double = when (tier) {
    Tier.T1, Tier.T2 -> 2.0
    Tier.T3, Tier.CYCLE, Tier.CUSTOM -> 3.0
}

/**
 * Asymmetric EMA update:
 * α_up = 0.4 when observation >= current, α_down = 0.15 when observation < current.
 */
fun updateEma(current: Double?, observation: Double, alphaUp: Double = 0.4, alphaDown: Double = 0.15): Double {
    if (observation <= 0.0) return current ?: 0.0
    if (current == null || current <= 0.0) return observation
    val alpha = if (observation >= current) alphaUp else alphaDown
    return current + alpha * (observation - current)
}

/**
 * Manual weight edit update with α = 0.6.
 */
fun updateEmaManualEdit(current: Double?, observation: Double): Double {
    if (observation <= 0.0) return current ?: 0.0
    if (current == null || current <= 0.0) return observation
    return current + 0.6 * (observation - current)
}

/**
 * RIR for a set:
 * Uses RIR = 10 - RPE when RPE is logged; otherwise 0 for an AMRAP last set, 2 for fixed sets.
 */
fun rirForSet(rpe: Double?, amrap: Boolean): Double = when {
    rpe != null -> (10.0 - rpe).coerceAtLeast(0.0)
    amrap -> 0.0
    else -> 2.0
}

interface StrengthModel {
    fun estimate(exercise: String): Double?
    fun suggest(exercise: String, reps: Int, targetRir: Double): Int?
    fun suggest(exercise: String, reps: Int, targetRir: Double, currentWeight: Int?): Int? =
        suggest(exercise, reps, targetRir)?.let { raw ->
            if (currentWeight != null && currentWeight > 0) {
                val minAllowed = round5(currentWeight * 0.90)
                maxOf(raw, minAllowed)
            } else raw
        }
}
