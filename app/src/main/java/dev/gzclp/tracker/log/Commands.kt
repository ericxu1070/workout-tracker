package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.ChangeKind
import dev.gzclp.tracker.engine.Day
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Outcome
import dev.gzclp.tracker.engine.Progression
import dev.gzclp.tracker.engine.Schemes
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.round5
import java.time.Duration
import java.time.OffsetDateTime

/** Turns user intent plus current state into the events to append. Pure. */
object Commands {
    val defaultT3 = listOf(Triple("lat_pulldown", "Lat pulldown", 50), Triple("dumbbell_row", "Dumbbell row", 30))

    fun setup(fiveRm: Map<Lift, Int>): List<Event> = buildList {
        add(ProgramStart(fiveRm.mapKeys { it.key.id }))
        for ((lift, rm) in fiveRm) {
            add(ProgressionEvent(lift.id, Tier.T1.id, ChangeKind.INITIAL.id, toWeight = Progression.t1FromFiveRm(rm), toStage = Schemes.t1[0].label, fiveRm = rm))
            add(ProgressionEvent(lift.id, Tier.T2.id, ChangeKind.INITIAL.id, toWeight = Progression.t2FromFiveRm(rm), toStage = Schemes.t2[0].label))
        }
        for ((id, name, weight) in defaultT3) {
            add(T3Define(id, name))
            add(ProgressionEvent(id, Tier.T3.id, ChangeKind.INITIAL.id, toWeight = weight, toStage = Schemes.t3[0].label))
        }
        Day.entries.forEach { add(T3Day(it.name, listOf(it.defaultT3))) }
    }

    fun start(state: AppState, now: OffsetDateTime): List<Event> =
        if (state.active != null || !state.started) emptyList()
        else listOf(SessionStart("s" + now.toEpochSecond(), state.nextDay.name))

    fun logSet(state: AppState, ex: Exercise, setIndex: Int, reps: Int, now: OffsetDateTime, restTargets: Map<Tier, Int>): List<Event> {
        val a = state.active ?: return emptyList()
        val key = SetKey(ex.liftId, ex.tier, setIndex)
        val existing = a.sets[key]?.event
        val prev = (a.sets - key).values.maxByOrNull { it.ts }
        val scheme = ex.scheme
        return listOf(
            SetLogged(
                sessionId = a.id, lift = ex.liftId, tier = ex.tier.id, stage = scheme.label, setIndex = setIndex,
                targetReps = scheme.reps, actualReps = reps.coerceAtLeast(0), weight = ex.state.weight,
                amrap = scheme.amrap && setIndex == scheme.sets,
                restSec = if (existing != null) existing.restSec else prev?.let { Duration.between(it.ts, now).seconds.toInt() },
                restTargetSec = if (existing != null) existing.restTargetSec else prev?.let { restTargets[Tier.of(it.event.tier)] },
            ),
        )
    }

    fun clearSet(state: AppState, ex: Exercise, setIndex: Int): List<Event> {
        val a = state.active ?: return emptyList()
        if (SetKey(ex.liftId, ex.tier, setIndex) !in a.sets) return emptyList()
        return listOf(SetCleared(a.id, ex.liftId, ex.tier.id, setIndex))
    }

    fun editWeight(ex: Exercise, weight: Int): List<Event> {
        val to = Progression.manualEdit(ex.state, weight)
        if (to.weight == ex.state.weight) return emptyList()
        return listOf(ProgressionEvent(ex.liftId, ex.tier.id, ChangeKind.MANUAL_EDIT.id, ex.state.weight, to.weight, ex.scheme.label, ex.scheme.label))
    }

    fun reps(state: AppState, ex: Exercise): List<Int?> {
        val sets = state.active?.sets.orEmpty()
        return (1..ex.scheme.sets).map { sets[SetKey(ex.liftId, ex.tier, it)]?.event?.actualReps }
    }

    private fun outcome(state: AppState, ex: Exercise): Outcome {
        val lift = Lift.of(ex.liftId)
        return Progression.progress(ex.tier, ex.state, reps(state, ex), ex.increment, lift?.t2ResetBump ?: 0)
    }

    /** The T1 lift that failed 10x1+ this session and needs a new 5RM before the session can end. */
    fun pendingFiveRm(state: AppState): Exercise? {
        val a = state.active ?: return null
        return state.exercises(a.day).firstOrNull { outcome(state, it) == Outcome.NeedsFiveRm }
    }

    fun end(state: AppState, now: OffsetDateTime, newFiveRm: Int?): List<Event> {
        val a = state.active ?: return emptyList()
        val changes = state.exercises(a.day).mapNotNull { ex ->
            val from = ex.scheme.label
            when (val o = outcome(state, ex)) {
                Outcome.Unchanged -> null
                is Outcome.Change -> ProgressionEvent(ex.liftId, ex.tier.id, o.kind.id, ex.state.weight, o.to.weight, from, Schemes.of(ex.tier)[o.to.stage].label, a.id)
                Outcome.NeedsFiveRm -> {
                    val rm = round5((newFiveRm ?: ex.state.weight).toDouble())
                    val to = Progression.t1Reset(rm)
                    ProgressionEvent(ex.liftId, ex.tier.id, ChangeKind.RESET.id, ex.state.weight, to.weight, from, Schemes.t1[0].label, a.id, rm)
                }
            }
        }
        val duration = Duration.between(a.startedAt, now).seconds.coerceAtLeast(0)
        return changes + SessionEnd(a.id, a.day.name, LogCodec.TS.format(a.startedAt), duration)
    }

    fun addT3(state: AppState, day: Day, name: String, weight: Int): List<Event> {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return emptyList()
        val existing = state.t3.values.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        val dayList = state.dayT3[day].orEmpty()
        return buildList {
            val id = existing?.id ?: run {
                val base = clean.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "t3" }
                val id = generateSequence(1) { it + 1 }.map { if (it == 1) base else "${base}_$it" }.first { it !in state.t3 }
                add(T3Define(id, clean))
                add(ProgressionEvent(id, Tier.T3.id, ChangeKind.INITIAL.id, toWeight = round5(weight.toDouble()), toStage = Schemes.t3[0].label))
                id
            }
            if (id !in dayList) add(T3Day(day.name, dayList + id))
        }
    }

    fun removeT3(state: AppState, day: Day, id: String): List<Event> {
        val dayList = state.dayT3[day].orEmpty()
        return if (id in dayList) listOf(T3Day(day.name, dayList - id)) else emptyList()
    }
}
