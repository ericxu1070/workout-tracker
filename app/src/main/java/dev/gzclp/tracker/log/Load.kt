package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.e1rm
import dev.gzclp.tracker.engine.rirForSet
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/**
 * Default bodyweight factors per spec: approximations for effective load.
 * pull-up / chin-up / muscle-up: 1.0
 * dip: 0.95
 * push-up: 0.65
 * inverted row: 0.5
 * pistol squat: 0.8
 */
val DEFAULT_BODYWEIGHT_FACTORS: Map<String, Double> = mapOf(
    "pull_up" to 1.0,
    "weighted_pull_up" to 1.0,
    "assisted_pull_up" to 1.0,
    "chin_up" to 1.0,
    "weighted_chin_up" to 1.0,
    "assisted_chin_up" to 1.0,
    "muscle_up" to 1.0,
    "weighted_muscle_up" to 1.0,
    "assisted_muscle_up" to 1.0,
    "dip" to 0.95,
    "weighted_dip" to 0.95,
    "assisted_dip" to 0.95,
    "push_up" to 0.65,
    "weighted_push_up" to 0.65,
    "assisted_push_up" to 0.65,
    "inverted_row" to 0.5,
    "weighted_inverted_row" to 0.5,
    "assisted_inverted_row" to 0.5,
    "pistol_squat" to 0.8,
    "weighted_pistol_squat" to 0.8,
    "assisted_pistol_squat" to 0.8,
)

/** Returns the default bodyweight factor for known calisthenics exercises and their variants. */
fun defaultBodyweightFactor(exercise: String): Double? {
    val clean = exercise.lowercase().trim().replace('-', '_')
    DEFAULT_BODYWEIGHT_FACTORS[clean]?.let { return it }
    val base = clean.removePrefix("weighted_").removePrefix("assisted_")
    return DEFAULT_BODYWEIGHT_FACTORS[base]
}

/** Whether the given exercise ID or name is a calisthenics movement. */
fun isCalisthenics(exercise: String): Boolean =
    defaultBodyweightFactor(exercise) != null || exercise in Catalog.calisthenics

/**
 * Effective bodyweight factor: uses explicit [meta] factor if present,
 * or meta's parent default factor, or the exercise default factor.
 */
fun bodyweightFactor(exercise: String, meta: ExerciseMeta? = null): Double? =
    meta?.bodyweightFactor ?: (meta?.parent?.let { defaultBodyweightFactor(it) }) ?: defaultBodyweightFactor(exercise)

/**
 * Effective load for an exercise set: bodyweight contribution + added weight.
 * `effectiveLoad = factor * (bodyweight in effect that day) + added weight`
 * Added weight can be negative for band or machine assistance.
 */
fun effectiveLoad(
    exercise: String,
    weightLb: Double,
    addedLb: Double?,
    bodyweightLb: Double?,
    meta: ExerciseMeta? = null,
): Double {
    val factor = bodyweightFactor(exercise, meta)
    return if (factor != null && bodyweightLb != null) {
        val added = addedLb ?: weightLb
        factor * bodyweightLb + added
    } else {
        weightLb
    }
}

/**
 * Score a set through Strength.e1rm on effective load.
 */
fun scoreSet(
    exercise: String,
    actualReps: Int,
    weightLb: Double = 0.0,
    addedLb: Double? = null,
    bodyweightLb: Double? = null,
    meta: ExerciseMeta? = null,
    rpe: Double? = null,
    amrap: Boolean = false,
): Double {
    val load = effectiveLoad(exercise, weightLb, addedLb, bodyweightLb, meta)
    val rir = rirForSet(rpe, amrap)
    return e1rm(load, actualReps, rir)
}

/** Score a logged set through Strength.e1rm on effective load. */
fun scoreSet(
    set: SetLogged,
    bodyweightLb: Double? = null,
    meta: ExerciseMeta? = null,
): Double {
    val bw = set.bodyweightLb ?: bodyweightLb
    return scoreSet(
        exercise = set.exercise,
        actualReps = set.actualReps,
        weightLb = set.weight.toDouble(),
        addedLb = set.addedLb,
        bodyweightLb = bw,
        meta = meta,
        rpe = set.rpe,
        amrap = set.amrap,
    )
}

/** Snapshot the bodyweight in effect for an entry timestamp. */
fun AppState.snapshotBodyweight(at: OffsetDateTime): Double? =
    dailyBodyWeight().inEffect(at.toLocalDate())

/** Copy a SetLogged with a snapshotted bodyweight if not already set. */
fun SetLogged.withSnapshot(bodyweight: Double?, added: Double? = null): SetLogged =
    copy(bodyweightLb = this.bodyweightLb ?: bodyweight, addedLb = this.addedLb ?: added)

/** Progression recommendations for bodyweight movements. */
sealed interface CalisthenicsProgression {
    val suggestion: String

    data class AddReps(val currentReps: List<Int>) : CalisthenicsProgression {
        override val suggestion: String
            get() = "Add reps up to 3×12"
    }

    data class NextVariation(val addedLb: Int = 5, val harderMove: String?) : CalisthenicsProgression {
        override val suggestion: String
            get() = if (harderMove != null) {
                "Suggest +${addedLb} lb or $harderMove"
            } else {
                "Suggest +${addedLb} lb"
            }
    }
}

/** Next harder variation for bodyweight movements. */
fun harderVariation(exercise: String): String? {
    val clean = exercise.lowercase().trim().replace('-', '_')
    return when (clean) {
        "push_up" -> "weighted push-up or dip"
        "assisted_push_up" -> "push-up"
        "pull_up" -> "weighted pull-up or muscle-up"
        "assisted_pull_up" -> "pull-up"
        "chin_up" -> "weighted chin-up or pull-up"
        "assisted_chin_up" -> "chin-up"
        "dip" -> "weighted dip or muscle-up"
        "assisted_dip" -> "dip"
        "inverted_row" -> "pull-up or weighted inverted row"
        "assisted_inverted_row" -> "inverted row"
        "pistol_squat" -> "weighted pistol squat"
        "assisted_pistol_squat" -> "pistol squat"
        "muscle_up" -> "weighted muscle-up"
        "assisted_muscle_up" -> "muscle-up"
        else -> null
    }
}

/**
 * Progression logic:
 * Add reps up to 3x12, then suggest +5 lb or the next harder variation.
 */
fun calisthenicsProgression(
    exercise: String,
    sets: List<SetLogged>,
): CalisthenicsProgression {
    val done = sets.filter { it.actualReps > 0 }
    val repsList = done.map { it.actualReps }
    val hit3x12 = repsList.size >= 3 && repsList.takeLast(3).all { it >= 12 }
    return if (hit3x12) {
        CalisthenicsProgression.NextVariation(
            addedLb = 5,
            harderMove = harderVariation(exercise),
        )
    } else {
        CalisthenicsProgression.AddReps(repsList)
    }
}

/**
 * Muscle-up keeps its own chart and is excluded from cross-exercise comparisons.
 */
fun isMuscleUp(exercise: String): Boolean {
    val clean = exercise.lowercase().trim().replace('-', '_')
    return clean == "muscle_up" || clean == "weighted_muscle_up" || clean == "assisted_muscle_up"
}

/** Muscle-up is excluded from cross-exercise comparisons. */
fun isExcludedFromCrossExercise(exercise: String): Boolean = isMuscleUp(exercise)

/** Exclude muscle-up from cross-exercise comparisons. */
fun filterCrossExerciseComparison(exercises: Iterable<String>): List<String> =
    exercises.filterNot(::isExcludedFromCrossExercise)

/** Computes session bests for a calisthenics exercise based on effective load and Strength.e1rm. */
fun calisthenicsBests(state: AppState, log: ExerciseLog): List<SessionBest> {
    val daily = state.dailyBodyWeight()
    val meta = state.exerciseMeta[log.id]
    return log.sessions.mapNotNull { (session, sets) ->
        val done = sets.filter { it.actualReps > 0 }
        if (done.isEmpty()) return@mapNotNull null
        val sessionBw = daily.inEffect(session.startedAt.toLocalDate())
        val setsWithLoad = done.map { set ->
            val bw = set.bodyweightLb ?: sessionBw
            val load = effectiveLoad(set.exercise, set.weight.toDouble(), set.addedLb, bw, meta)
            val rir = rirForSet(set.rpe, set.amrap)
            val score = e1rm(load, set.actualReps, rir).roundToInt()
            Triple(set, load.roundToInt(), score)
        }
        val top = setsWithLoad.maxWithOrNull(compareBy({ it.second }, { it.first.actualReps })) ?: return@mapNotNull null
        val bestE1rm = setsWithLoad.maxOf { it.third }
        SessionBest(session.startedAt, top.second, top.first.actualReps, bestE1rm)
    }
}
