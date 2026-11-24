package dev.gzclp.tracker.engine

/**
 * Estimated 1RM using Epley with RIR: `load * (1 + (reps + rir) / 30)`.
 * Sets over 12 reps are ignored (returns 0.0).
 */
fun e1rm(loadLb: Double, reps: Int, rir: Double = 0.0): Double {
    if (reps > 12 || reps <= 0 || loadLb <= 0.0) return 0.0
    return loadLb * (1.0 + (reps + rir) / 30.0)
}

interface StrengthModel {
    fun estimate(exercise: String): Double?
    fun suggest(exercise: String, reps: Int, targetRir: Double): Int?
}
