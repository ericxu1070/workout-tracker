package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.ChangeKind
import dev.gzclp.tracker.engine.Cycle
import dev.gzclp.tracker.engine.CycleState
import dev.gzclp.tracker.engine.Increments
import dev.gzclp.tracker.engine.LiftState
import dev.gzclp.tracker.engine.Move
import dev.gzclp.tracker.engine.Program
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Progression
import dev.gzclp.tracker.engine.Scheme
import dev.gzclp.tracker.engine.Schemes
import dev.gzclp.tracker.engine.Slot
import dev.gzclp.tracker.engine.Tier
import java.time.OffsetDateTime

data class SetKey(val exercise: String, val tier: Tier, val setIndex: Int)

data class LoggedSet(val event: SetLogged, val ts: OffsetDateTime)

data class ActiveSession(val id: String, val program: Program, val day: ProgramDay, val startedAt: OffsetDateTime, val sets: Map<SetKey, LoggedSet>)

data class SessionRecord(val id: String, val program: Program, val day: ProgramDay, val startedAt: OffsetDateTime, val durationSec: Long, val sets: List<SetLogged>)

/**
 * One exercise as prescribed in a workout. [weight] is null until the user gives its starting weight;
 * [week] is the 0-based cycle week for [Tier.CYCLE].
 */
data class Exercise(val id: String, val name: String, val tier: Tier, val scheme: Scheme, val weight: Int?, val week: Int? = null) {
    val key: Pair<String, Tier> get() = id to tier
}

/** Everything the app knows, rebuilt by folding the log from the start. */
data class AppState(
    val fiveRm: Map<String, Int> = emptyMap(),
    val programId: String = Programs.GZCLP,
    val moves: Map<String, Move> = Catalog.all.associateBy { it.id },
    val increments: Increments = Increments(),
    /** GZCLP state per (exercise, tier). */
    val gz: Map<Pair<String, Tier>, LiftState> = emptyMap(),
    /** Cycle state per exercise, shared by every cycle program. */
    val cycle: Map<String, CycleState> = emptyMap(),
    /** Custom exercise lists per (program, day): T3s for GZCLP, every exercise for cycle programs. */
    val dayLists: Map<Pair<String, String>, List<String>> = emptyMap(),
    /** Next day index per program. */
    val next: Map<String, Int> = emptyMap(),
    val active: ActiveSession? = null,
    val sessions: List<SessionRecord> = emptyList(),
    val lastSeq: Long = 0,
) {
    val started: Boolean get() = fiveRm.isNotEmpty()
    val program: Program get() = Programs.of(programId)
    val nextDay: ProgramDay get() = program.days[(next[programId] ?: 0) % program.days.size]

    fun name(id: String): String = moves[id]?.name ?: id
    fun step(id: String): Int = increments.step(moves[id]?.lower ?: false)

    fun slots(program: Program, day: ProgramDay): List<Slot> {
        val custom = dayLists[program.id to day.id]
        return if (program.gzclp) day.slots.filter { it.tier != Tier.T3 } + (custom ?: day.slots.filter { it.tier == Tier.T3 }.map { it.ex }).map { Slot(it, Tier.T3) }
        else custom?.map { Slot(it, Tier.CYCLE) } ?: day.slots
    }

    fun exercise(slot: Slot): Exercise {
        val (id, tier) = slot
        if (tier == Tier.CYCLE) {
            val c = cycle[id]
            return Exercise(id, name(id), tier, Cycle.weeks[c?.week ?: 0], c?.let { Cycle.weight(it, step(id)) }, c?.week ?: 0)
        }
        val g = gz[id to tier]
        return Exercise(id, name(id), tier, Schemes.of(tier)[g?.stage ?: 0], g?.weight)
    }

    fun exercises(program: Program, day: ProgramDay): List<Exercise> = slots(program, day).map(::exercise)
    fun exercises(day: ProgramDay): List<Exercise> = exercises(program, day)

    fun apply(entry: Entry): AppState {
        val s = copy(lastSeq = maxOf(lastSeq, entry.seq))
        return when (val e = entry.event) {
            is ProgramStart -> s.copy(fiveRm = e.fiveRm, programId = e.program)
            is ProgramChosen -> s.copy(programId = e.program)
            is IncrementsSet -> s.copy(increments = Increments(e.upperStep, e.lowerStep, e.cycleBump))
            is ExerciseDefine -> s.copy(moves = moves + (e.exercise to Move(e.exercise, e.name, e.lower)))
            is DayExercises -> s.copy(dayLists = dayLists + ((e.program to e.day) to e.exercises))
            is ProgressionEvent -> s.progress(e)
            is SessionStart -> {
                val program = Programs.of(e.program)
                program.day(e.day)?.let { s.copy(active = ActiveSession(e.sessionId, program, it, entry.ts, emptyMap())) } ?: s
            }
            is SetLogged -> {
                val a = active?.takeIf { it.id == e.sessionId } ?: return s
                val tier = Tier.of(e.tier) ?: return s
                s.copy(active = a.copy(sets = a.sets + (SetKey(e.exercise, tier, e.setIndex) to LoggedSet(e, entry.ts))))
            }
            is SetCleared -> {
                val a = active?.takeIf { it.id == e.sessionId } ?: return s
                val tier = Tier.of(e.tier) ?: return s
                s.copy(active = a.copy(sets = a.sets - SetKey(e.exercise, tier, e.setIndex)))
            }
            is SessionEnd -> {
                val a = active?.takeIf { it.id == e.sessionId } ?: return s
                val record = SessionRecord(a.id, a.program, a.day, a.startedAt, e.durationSec, a.sets.values.sortedBy { it.ts }.map { it.event })
                s.copy(
                    active = null,
                    sessions = sessions + record,
                    next = if (a.sets.isEmpty()) next else next + (a.program.id to a.program.days.indexOf(a.day) + 1),
                )
            }
        }
    }

    private fun progress(e: ProgressionEvent): AppState {
        val tier = Tier.of(e.tier) ?: return this
        if (tier == Tier.CYCLE) {
            val base = e.cycleBase ?: return this
            return copy(cycle = cycle + (e.exercise to CycleState(base, (e.cycleWeek ?: 1) - 1)))
        }
        val kind = ChangeKind.of(e.kind)
        val stage = Schemes.stageIndex(tier, e.toStage)
        val prev = gz[e.exercise to tier]
        val state = when {
            prev == null || kind == ChangeKind.INITIAL || kind == ChangeKind.RESET -> LiftState(e.toWeight, stage, e.toWeight)
            kind == ChangeKind.MANUAL_EDIT -> Progression.manualEdit(prev, e.toWeight).copy(stage = stage)
            else -> prev.copy(weight = e.toWeight, stage = stage)
        }
        val fiveRms = if (e.fiveRm != null && tier == Tier.T1) fiveRm + (e.exercise to e.fiveRm) else fiveRm
        return copy(gz = gz + ((e.exercise to tier) to state), fiveRm = fiveRms)
    }

    companion object {
        fun fold(entries: Iterable<Entry>): AppState = entries.fold(AppState()) { s, e -> s.apply(e) }
    }
}
