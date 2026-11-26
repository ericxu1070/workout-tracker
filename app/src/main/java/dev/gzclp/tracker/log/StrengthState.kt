package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.ChangeKind
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.engine.Schemes
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.e1rm
import dev.gzclp.tracker.engine.rirForSet
import dev.gzclp.tracker.engine.round5
import dev.gzclp.tracker.engine.suggestLoad
import dev.gzclp.tracker.engine.targetRir
import dev.gzclp.tracker.engine.updateEma

/**
 * In-memory state of estimated 1RMs per exercise, rebuilt dynamically from the log.
 */
data class StrengthState(
    val estimates: Map<String, Double> = emptyMap()
) {

    fun estimate(exercise: String): Double? = estimates[exercise]

    fun suggest(exercise: String, reps: Int, targetRir: Double): Int? {
        val e1rm = estimate(exercise) ?: return null
        return suggestLoad(e1rm, reps, targetRir)
    }

    /** Never suggests more than a 10% drop from [currentWeight] at once. */
    fun suggest(exercise: String, reps: Int, targetRir: Double, currentWeight: Int?): Int? {
        val raw = suggest(exercise, reps, targetRir) ?: return null
        if (currentWeight != null && currentWeight > 0) {
            val minAllowed = round5(currentWeight * 0.90)
            return maxOf(raw, minAllowed)
        }
        return raw
    }

    fun observeSession(exercise: String, bestE1rm: Double): StrengthState {
        if (bestE1rm <= 0.0) return this
        val prev = estimates[exercise]
        val updated = updateEma(prev, bestE1rm)
        return copy(estimates = estimates + (exercise to updated))
    }

    fun observeManualEdit(exercise: String, loadLb: Double, reps: Int): StrengthState {
        if (reps > 12 || reps <= 0 || loadLb <= 0.0) return this
        val obs = e1rm(loadLb, reps, rir = 2.0)
        if (obs <= 0.0) return this
        val prev = estimates[exercise]
        val updated = updateEma(prev, obs, alphaUp = 0.6, alphaDown = 0.6)
        return copy(estimates = estimates + (exercise to updated))
    }

    companion object {
        private val STAGE_REPS_REGEX = Regex("""\d+x(\d+)""")

        fun parsePrescribedReps(stage: String, tier: String? = null): Int? {
            STAGE_REPS_REGEX.find(stage)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            val t = tier?.let(Tier::of) ?: return null
            return Schemes.of(t).firstOrNull()?.reps
        }
    }
}

/** Folds [sets] in as one session: each exercise's best e1RM on effective load updates its EMA. */
fun StrengthState.observeSets(sets: List<SetLogged>, meta: Map<String, ExerciseMeta>): StrengthState =
    sets.groupBy { it.exercise }.entries.fold(this) { st, (ex, list) ->
        val best = list.maxOfOrNull { set ->
            e1rm(effectiveLoad(set.exercise, set.weight.toDouble(), set.addedLb, set.bodyweightLb, meta[set.exercise]), set.actualReps, rirForSet(set.rpe, set.amrap))
        } ?: 0.0
        st.observeSession(ex, best)
    }

/**
 * A manual edit is a strong observation at its prescribed reps and RIR 2, up or down.
 * An initial weight seeds the estimate as a prior, and only raises one that already exists.
 */
fun StrengthState.observeProgression(e: ProgressionEvent): StrengthState {
    val reps = StrengthState.parsePrescribedReps(e.toStage, e.tier)?.takeIf { it in 1..12 } ?: return this
    return when (e.kind) {
        ChangeKind.MANUAL_EDIT.id -> observeManualEdit(e.exercise, e.toWeight.toDouble(), reps)
        ChangeKind.INITIAL.id -> {
            val obs = e1rm(e.toWeight.toDouble(), reps, 2.0)
            val current = estimates[e.exercise]
            when {
                obs <= 0.0 -> this
                current == null -> copy(estimates = estimates + (e.exercise to obs))
                obs > current -> observeManualEdit(e.exercise, e.toWeight.toDouble(), reps)
                else -> this
            }
        }
        else -> this
    }
}

/** The model as of the log, plus the sets of the session in progress. */
fun AppState.strengthModel(): StrengthState =
    active?.let { a -> strength.observeSets(a.sets.values.map { it.event }, exerciseMeta) } ?: strength

fun AppState.strengthSuggestion(ex: Exercise): Int? {
    val model = strengthModel()
    val plan = ex.weight ?: return null
    val target = targetRir(ex.tier)
    return model.suggest(ex.id, ex.scheme.reps, target, currentWeight = plan)
}

fun AppState.shouldSuggest(ex: Exercise, suggestedWeight: Int): Boolean {
    val plan = ex.weight ?: return false
    val step = step(ex.id)
    return suggestedWeight >= plan + step
}

fun AppState.adaptSessionStartEvents(day: ProgramDay = nextDay): List<Event> {
    val model = strengthModel()
    val exercises = exercises(day)
    val events = mutableListOf<Event>()
    for (ex in exercises) {
        val plan = ex.weight ?: continue
        val step = step(ex.id)
        val target = targetRir(ex.tier)
        val suggested = model.suggest(ex.id, ex.scheme.reps, target, currentWeight = plan) ?: continue
        if (suggested >= plan + step) {
            events.addAll(Commands.editWeight(this, ex, suggested))
        }
    }
    return events
}
