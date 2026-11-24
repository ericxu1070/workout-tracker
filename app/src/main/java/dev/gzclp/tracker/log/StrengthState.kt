package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.ChangeKind
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.engine.Schemes
import dev.gzclp.tracker.engine.StrengthModel
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.e1rm
import dev.gzclp.tracker.engine.rirForSet
import dev.gzclp.tracker.engine.round5
import dev.gzclp.tracker.engine.suggestLoad
import dev.gzclp.tracker.engine.targetRir
import dev.gzclp.tracker.engine.updateEma
import dev.gzclp.tracker.engine.updateEmaManualEdit

/**
 * In-memory state of estimated 1RMs per exercise, rebuilt dynamically from the log.
 */
data class StrengthState(
    val estimates: Map<String, Double> = emptyMap()
) : StrengthModel {

    override fun estimate(exercise: String): Double? = estimates[exercise]

    override fun suggest(exercise: String, reps: Int, targetRir: Double): Int? {
        val e1rm = estimate(exercise) ?: return null
        return suggestLoad(e1rm, reps, targetRir)
    }

    override fun suggest(exercise: String, reps: Int, targetRir: Double, currentWeight: Int?): Int? {
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
        val updated = updateEma(prev, bestE1rm, alphaUp = 0.4, alphaDown = 0.15)
        return copy(estimates = estimates + (exercise to updated))
    }

    fun observeManualEdit(exercise: String, loadLb: Double, reps: Int): StrengthState {
        if (reps > 12 || reps <= 0 || loadLb <= 0.0) return this
        val obs = e1rm(loadLb, reps, rir = 2.0)
        if (obs <= 0.0) return this
        val prev = estimates[exercise]
        val updated = updateEmaManualEdit(prev, obs)
        return copy(estimates = estimates + (exercise to updated))
    }

    companion object {
        private val STAGE_REPS_REGEX = Regex("""\d+x(\d+)""")

        fun parsePrescribedReps(stage: String, tier: String? = null): Int? {
            STAGE_REPS_REGEX.find(stage)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            val t = tier?.let(Tier::of) ?: return null
            return Schemes.of(t).firstOrNull()?.reps
        }

        /**
         * Rebuilds the strength model by replaying entries in log order.
         */
        fun fromEntries(entries: Iterable<Entry>): StrengthState {
            var state = StrengthState()
            val metaMap = mutableMapOf<String, ExerciseMeta>()
            val sessionSets = mutableMapOf<String, MutableMap<String, MutableList<SetLogged>>>()
            val sessionOrder = mutableListOf<String>()

            fun flushSession(sessionId: String) {
                val exMap = sessionSets.remove(sessionId) ?: return
                for ((exercise, sets) in exMap) {
                    val best = sets.map { s ->
                        val rir = rirForSet(s.rpe, s.amrap)
                        val load = effectiveLoad(s.exercise, s.weight.toDouble(), s.addedLb, s.bodyweightLb, metaMap[s.exercise])
                        e1rm(load, s.actualReps, rir)
                    }.filter { it > 0.0 }.maxOrNull()
                    if (best != null && best > 0.0) {
                        state = state.observeSession(exercise, best)
                    }
                }
            }

            for (entry in entries) {
                when (val e = entry.event) {
                    is ExerciseMeta -> metaMap[e.exercise] = e
                    is SetLogged -> {
                        val map = sessionSets.getOrPut(e.sessionId) {
                            sessionOrder.add(e.sessionId)
                            mutableMapOf()
                        }
                        val list = map.getOrPut(e.exercise) { mutableListOf() }
                        val idx = list.indexOfFirst { it.tier == e.tier && it.setIndex == e.setIndex }
                        if (idx >= 0) list[idx] = e else list.add(e)
                    }
                    is SetCleared -> {
                        sessionSets[e.sessionId]?.get(e.exercise)?.removeAll { it.tier == e.tier && it.setIndex == e.setIndex }
                    }
                    is SessionEnd -> {
                        flushSession(e.sessionId)
                        sessionOrder.remove(e.sessionId)
                    }
                    is ProgressionEvent -> {
                        if (e.kind == "manual_edit" || e.kind == ChangeKind.MANUAL_EDIT.id) {
                            val reps = parsePrescribedReps(e.toStage, e.tier)
                            if (reps != null && reps in 1..12) {
                                state = state.observeManualEdit(e.exercise, e.toWeight.toDouble(), reps)
                            }
                        }
                    }
                    else -> {}
                }
            }
            for (sessionId in sessionOrder.toList()) {
                flushSession(sessionId)
            }
            return state
        }

        /**
         * Rebuilds from AppState: folding past sessions, active session, and current lift states.
         */
        fun from(appState: AppState): StrengthState {
            var state = StrengthState()
            for (s in appState.sessions) {
                val byEx = s.sets.groupBy { it.exercise }
                for ((ex, sets) in byEx) {
                    val best = sets.map { set ->
                        val rir = rirForSet(set.rpe, set.amrap)
                        val load = effectiveLoad(set.exercise, set.weight.toDouble(), set.addedLb, set.bodyweightLb, appState.exerciseMeta[set.exercise])
                        e1rm(load, set.actualReps, rir)
                    }.filter { it > 0.0 }.maxOrNull()
                    if (best != null && best > 0.0) {
                        state = state.observeSession(ex, best)
                    }
                }
            }
            appState.active?.let { a ->
                val byEx = a.sets.values.map { it.event }.groupBy { it.exercise }
                for ((ex, sets) in byEx) {
                    val best = sets.map { set ->
                        val rir = rirForSet(set.rpe, set.amrap)
                        val load = effectiveLoad(set.exercise, set.weight.toDouble(), set.addedLb, set.bodyweightLb, appState.exerciseMeta[set.exercise])
                        e1rm(load, set.actualReps, rir)
                    }.filter { it > 0.0 }.maxOrNull()
                    if (best != null && best > 0.0) {
                        state = state.observeSession(ex, best)
                    }
                }
            }
            for ((key, liftState) in appState.gz) {
                val (ex, tier) = key
                val stage = Schemes.of(tier).getOrNull(liftState.stage)
                val reps = stage?.reps
                if (reps != null && reps in 1..12) {
                    val obs = e1rm(liftState.weight.toDouble(), reps, 2.0)
                    val current = state.estimates[ex]
                    if (current == null) {
                        state = state.copy(estimates = state.estimates + (ex to obs))
                    } else if (obs > current) {
                        state = state.observeManualEdit(ex, liftState.weight.toDouble(), reps)
                    }
                }
            }
            return state
        }
    }
}

fun AppState.strengthModel(): StrengthState = StrengthState.from(this)

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

fun AppState.suggestionChipText(ex: Exercise): String? {
    val plan = ex.weight ?: return null
    val suggested = strengthSuggestion(ex) ?: return null
    return if (shouldSuggest(ex, suggested)) "Plan $plan · recent sets say $suggested · Use" else null
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
