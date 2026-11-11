package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.ChangeKind
import dev.gzclp.tracker.engine.Cycle
import dev.gzclp.tracker.engine.CycleState
import dev.gzclp.tracker.engine.Increments
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Outcome
import dev.gzclp.tracker.engine.Program
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.engine.Progression
import dev.gzclp.tracker.engine.Schemes
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.round5
import java.time.Duration
import java.time.OffsetDateTime

/** Turns user intent plus current state into the events to append. Pure. */
object Commands {
    fun setup(fiveRm: Map<Lift, Int>, program: String): List<Event> = buildList {
        add(ProgramStart(fiveRm.mapKeys { it.key.id }, program))
        Increments().let { add(IncrementsSet(it.upperStep, it.lowerStep, it.cycleBump)) }
        Catalog.all.forEach { add(ExerciseDefine(it.id, it.name, it.lower)) }
        for ((lift, rm) in fiveRm) {
            add(ProgressionEvent(lift.id, Tier.T1.id, ChangeKind.INITIAL.id, toWeight = Progression.t1FromFiveRm(rm), toStage = Schemes.t1[0].label, fiveRm = rm))
            add(ProgressionEvent(lift.id, Tier.T2.id, ChangeKind.INITIAL.id, toWeight = Progression.t2FromFiveRm(rm), toStage = Schemes.t2[0].label))
            add(cycleEvent(lift.id, ChangeKind.INITIAL, null, CycleState(Cycle.baseFromFiveRm(rm)), Increments().step(!lift.upper), null))
        }
    }

    private fun cycleEvent(id: String, kind: ChangeKind, from: Exercise?, to: CycleState, step: Int, sessionId: String?) =
        ProgressionEvent(
            id, Tier.CYCLE.id, kind.id, from?.weight, Cycle.weight(to, step), from?.scheme?.label, Cycle.weeks[to.week].label,
            sessionId, cycleWeek = to.week + 1, cycleBase = to.base,
        )

    private fun cycleEvent(state: AppState, ex: Exercise, kind: ChangeKind, to: CycleState, sessionId: String?) =
        cycleEvent(ex.id, kind, ex, to, state.step(ex.id), sessionId)

    fun chooseProgram(state: AppState, id: String): List<Event> = if (id == state.programId) emptyList() else listOf(ProgramChosen(id))

    fun setIncrements(state: AppState, to: Increments): List<Event> =
        if (to == state.increments) emptyList() else listOf(IncrementsSet(to.upperStep, to.lowerStep, to.cycleBump))

    /** The first exercise of [day] that still needs a starting weight. */
    fun missingWeight(state: AppState, day: ProgramDay = state.nextDay): Exercise? = state.exercises(day).firstOrNull { it.weight == null }

    fun startingWeight(state: AppState, ex: Exercise, weight: Int): List<Event> {
        if (ex.weight != null) return emptyList()
        val w = round5(weight.toDouble()).coerceAtLeast(0)
        return listOf(
            if (ex.tier == Tier.CYCLE) cycleEvent(ex.id, ChangeKind.INITIAL, null, CycleState(w), state.step(ex.id), null)
            else ProgressionEvent(ex.id, ex.tier.id, ChangeKind.INITIAL.id, toWeight = w, toStage = Schemes.of(ex.tier)[0].label),
        )
    }

    fun start(state: AppState, now: OffsetDateTime): List<Event> =
        if (state.active != null || !state.started || missingWeight(state) != null) emptyList()
        else listOf(SessionStart("s" + now.toEpochSecond(), state.programId, state.nextDay.id))

    /** Logs a set, or corrects one already logged (keeping its rest fields). [rpe] outside 1 to 10 is dropped, others round to a half. */
    fun logSet(state: AppState, ex: Exercise, setIndex: Int, reps: Int, weight: Int, now: OffsetDateTime, restTargets: Map<Tier, Int>, rpe: Double? = null): List<Event> {
        val a = state.active ?: return emptyList()
        val key = SetKey(ex.id, ex.tier, setIndex)
        val existing = a.sets[key]?.event
        val prev = (a.sets - key).values.maxByOrNull { it.ts }
        val scheme = ex.scheme
        val target = ex.weight ?: 0
        val event = SetLogged(
            sessionId = a.id, exercise = ex.id, tier = ex.tier.id, stage = scheme.label, setIndex = setIndex,
            targetReps = scheme.reps, actualReps = reps.coerceAtLeast(0), targetWeight = target, weight = weight.coerceAtLeast(0),
            amrap = scheme.amrap && setIndex == scheme.sets,
            restSec = if (existing != null) existing.restSec else prev?.let { Duration.between(it.ts, now).seconds.toInt() },
            restTargetSec = if (existing != null) existing.restTargetSec else prev?.let { restTargets[Tier.of(it.event.tier)] },
            rpe = rpe?.let { Math.round(it * 2) / 2.0 }?.takeIf { it in 1.0..10.0 },
        )
        return if (event == existing) emptyList() else listOf(event)
    }

    fun clearSet(state: AppState, ex: Exercise, setIndex: Int): List<Event> {
        val a = state.active ?: return emptyList()
        if (SetKey(ex.id, ex.tier, setIndex) !in a.sets) return emptyList()
        return listOf(SetCleared(a.id, ex.id, ex.tier.id, setIndex))
    }

    /** The weight to prefill for an unlogged set: the latest weight logged for this exercise this session, else the prescription. */
    fun setWeight(state: AppState, ex: Exercise, setIndex: Int): Int {
        val sets = state.active?.sets.orEmpty()
        return (setIndex - 1 downTo 1).firstNotNullOfOrNull { sets[SetKey(ex.id, ex.tier, it)]?.event?.weight } ?: ex.weight ?: 0
    }

    fun editWeight(state: AppState, ex: Exercise, weight: Int): List<Event> {
        if (ex.swappedFrom != null) return emptyList()
        val from = ex.weight ?: return startingWeight(state, ex, weight)
        if (ex.tier == Tier.CYCLE) {
            val c = state.cycle[ex.id] ?: return emptyList()
            val to = Cycle.manualEdit(c, weight, state.step(ex.id))
            return if (to == c) emptyList() else listOf(cycleEvent(state, ex, ChangeKind.MANUAL_EDIT, to, null))
        }
        val g = state.gz[ex.key] ?: return emptyList()
        val to = Progression.manualEdit(g, weight)
        if (to.weight == from) return emptyList()
        return listOf(ProgressionEvent(ex.id, ex.tier.id, ChangeKind.MANUAL_EDIT.id, from, to.weight, ex.scheme.label, ex.scheme.label))
    }

    fun reps(state: AppState, ex: Exercise): List<Int?> {
        val sets = state.active?.sets.orEmpty()
        return (1..ex.scheme.sets).map { sets[SetKey(ex.id, ex.tier, it)]?.event?.actualReps }
    }

    private fun outcome(state: AppState, ex: Exercise, reps: List<Int?>): Outcome {
        val g = state.gz[ex.key] ?: return Outcome.Unchanged
        val lift = Lift.of(ex.id)
        return Progression.progress(ex.tier, g, reps, lift?.increment ?: Schemes.T3_INCREMENT, lift?.t2ResetBump ?: 0)
    }

    /** The T1 lift that failed 10x1+ this session and needs a new 5RM before the session can end. */
    fun pendingFiveRm(state: AppState): Exercise? {
        val a = state.active ?: return null
        return state.exercises(a.program, a.day).firstOrNull { it.tier != Tier.CYCLE && outcome(state, it, reps(state, it)) == Outcome.NeedsFiveRm }
    }

    /** Progression lines for finishing [day] with [repsOf] per exercise. */
    private fun progressions(state: AppState, program: Program, day: ProgramDay, sessionId: String?, newFiveRm: Int?, repsOf: (Exercise) -> List<Int?>): List<ProgressionEvent> =
        state.exercises(program, day).distinctBy { it.key }.mapNotNull { ex ->
            val from = ex.weight ?: return@mapNotNull null
            val reps = repsOf(ex)
            if (ex.tier == Tier.CYCLE) {
                val c = state.cycle[ex.id] ?: return@mapNotNull null
                val (kind, to) = Cycle.advance(c, reps, state.increments.cycleBump) ?: return@mapNotNull null
                return@mapNotNull cycleEvent(state, ex, kind, to, sessionId)
            }
            when (val o = outcome(state, ex, reps)) {
                Outcome.Unchanged -> null
                is Outcome.Change -> ProgressionEvent(ex.id, ex.tier.id, o.kind.id, from, o.to.weight, ex.scheme.label, Schemes.of(ex.tier)[o.to.stage].label, sessionId)
                Outcome.NeedsFiveRm -> {
                    val rm = round5((newFiveRm ?: from).toDouble())
                    val to = Progression.t1Reset(rm)
                    ProgressionEvent(ex.id, ex.tier.id, ChangeKind.RESET.id, from, to.weight, ex.scheme.label, Schemes.t1[0].label, sessionId, rm)
                }
            }
        }

    fun end(state: AppState, now: OffsetDateTime, newFiveRm: Int?): List<Event> {
        val a = state.active ?: return emptyList()
        val changes = progressions(state, a.program, a.day, a.id, newFiveRm) { reps(state, it) }
        val duration = Duration.between(a.startedAt, now).seconds.coerceAtLeast(0)
        return changes + SessionEnd(a.id, a.program.id, a.day.id, LogCodec.TS.format(a.startedAt), duration)
    }

    /** The next [n] workouts in rotation order, each prescribed as if every earlier one hit every target. */
    fun upcoming(state: AppState, n: Int): List<Pair<ProgramDay, List<Exercise>>> {
        var s = state
        val program = state.active?.program ?: state.program
        var ix = state.active?.let { program.days.indexOf(it.day) } ?: (state.next[program.id] ?: 0)
        return List(n) {
            val day = program.days[ix % program.days.size]
            val list = s.exercises(program, day)
            val events = progressions(s, program, day, null, null) { ex -> List(ex.scheme.sets) { ex.scheme.reps } }
            s = events.fold(s) { acc, e -> acc.apply(Entry(0, OffsetDateTime.MIN, e)) }
            ix++
            day to list
        }
    }

    /** The id of the exercise named [name] and, for a name not seen before, the line that defines it. Null for a blank name. */
    private fun findOrDefine(state: AppState, name: String): Pair<String, List<Event>>? {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return null
        state.moves.values.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it.id to emptyList() }
        val base = clean.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "exercise" }
        val id = generateSequence(1) { it + 1 }.map { if (it == 1) base else "${base}_$it" }.first { it !in state.moves }
        return id to listOf(ExerciseDefine(id, clean, false))
    }

    /** Adds [name] to a day: an existing exercise with that name, or a new one. */
    fun addExercise(state: AppState, program: Program, day: ProgramDay, name: String): List<Event> {
        val (id, define) = findOrDefine(state, name) ?: return emptyList()
        val list = dayList(state, program, day)
        return if (id in list) emptyList() else define + DayExercises(program.id, day.id, list + id)
    }

    /**
     * Does [ex] (as performed in the active session) as the exercise named [name], for this session only;
     * the slot's own name puts it back. Only before any set of it is logged. The substitute may not be an exercise
     * this session already has at that tier, so the slot's own lift never sees a set and keeps its progression.
     */
    fun swap(state: AppState, ex: Exercise, name: String): List<Event> {
        val a = state.active ?: return emptyList()
        if ((1..ex.scheme.sets).any { SetKey(ex.id, ex.tier, it) in a.sets }) return emptyList()
        val original = ex.swappedFrom ?: ex.id
        val (id, define) = findOrDefine(state, name) ?: return emptyList()
        if (id == ex.id) return emptyList()
        if (id == original) return listOf(ExerciseSwap(a.id, original, ex.tier.id, null))
        val taken = (state.exercises(a.program, a.day) + state.sessionExercises()).any { it.id == id && it.tier == ex.tier }
        return if (taken) emptyList() else define + ExerciseSwap(a.id, original, ex.tier.id, id)
    }

    fun removeExercise(state: AppState, program: Program, day: ProgramDay, id: String): List<Event> {
        val list = dayList(state, program, day)
        return if (id in list) listOf(DayExercises(program.id, day.id, list - id)) else emptyList()
    }

    /** The editable list for a day: the T3s in GZCLP, every exercise otherwise. */
    fun dayList(state: AppState, program: Program, day: ProgramDay): List<String> =
        state.slots(program, day).filter { !program.gzclp || it.tier == Tier.T3 }.map { it.ex }

    /** Logs a weigh-in, or edits the one with [id]. [lb] is kept to one decimal; nonsense weights write nothing. */
    fun logBodyWeight(state: AppState, id: String?, at: OffsetDateTime, lb: Double): List<Event> {
        val w = Math.round(lb * 10) / 10.0
        if (w !in BODY_WEIGHT_RANGE) return emptyList()
        val entryId = id ?: generateSequence(0) { it + 1 }.map { "w${at.toEpochSecond()}" + if (it == 0) "" else "_$it" }.first { it !in state.bodyWeights }
        val event = BodyWeightLogged(entryId, LogCodec.TS.format(at), w)
        val old = state.bodyWeights[entryId]
        return if (old != null && old.lb == w && old.at == at) emptyList() else listOf(event)
    }

    fun clearBodyWeight(state: AppState, id: String): List<Event> =
        if (id in state.bodyWeights) listOf(BodyWeightCleared(id)) else emptyList()

    val BODY_WEIGHT_RANGE = 50.0..1000.0
}
