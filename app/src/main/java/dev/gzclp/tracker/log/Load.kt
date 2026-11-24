package dev.gzclp.tracker.log

/**
 * Effective load for an exercise set: bodyweight contribution + added weight.
 */
fun effectiveLoad(
    exercise: String,
    weightLb: Double,
    addedLb: Double?,
    bodyweightLb: Double?,
    meta: ExerciseMeta? = null,
): Double {
    val factor = meta?.bodyweightFactor
    return if (factor != null && bodyweightLb != null) {
        factor * bodyweightLb + (addedLb ?: 0.0)
    } else {
        weightLb
    }
}
