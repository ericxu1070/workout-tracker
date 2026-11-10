package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.ChangeKind
import dev.gzclp.tracker.engine.Day
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.LiftState
import dev.gzclp.tracker.engine.Progression
import dev.gzclp.tracker.engine.Scheme
import dev.gzclp.tracker.engine.Schemes
import dev.gzclp.tracker.engine.Tier
import java.time.OffsetDateTime

data class SetKey(val lift: String, val tier: Tier, val setIndex: Int)

data class LoggedSet(val event: SetLogged, val ts: OffsetDateTime)

data class ActiveSession(val id: String, val day: Day, val startedAt: OffsetDateTime, val sets: Map<SetKey, LoggedSet>) {
    val lastSet: LoggedSet? get() = sets.values.maxByOrNull { it.ts }
}

data class SessionRecord(val id: String, val day: Day, val startedAt: OffsetDateTime, val durationSec: Long, val sets: List<SetLogged>)

data class T3Lift(val id: String, val name: String, val state: LiftState)

/** One exercise as it appears in a workout. [liftId] is a [Lift.id] for T1/T2 or a T3 id. */
data class Exercise(val liftId: String, val name: String, val tier: Tier, val state: LiftState) {
    val scheme: Scheme get() = Schemes.of(tier)[state.stage]
    val increment: Int get() = Lift.of(liftId)?.increment ?: Schemes.T3_INCREMENT
}

/** Everything the app knows, rebuilt by folding the log from the start. */
data class AppState(
    val fiveRm: Map<Lift, Int> = emptyMap(),
    val main: Map<Pair<Lift, Tier>, LiftState> = emptyMap(),
    val t3: Map<String, T3Lift> = emptyMap(),
    val dayT3: Map<Day, List<String>> = emptyMap(),
    val nextDay: Day = Day.A1,
    val active: ActiveSession? = null,
    val sessions: List<SessionRecord> = emptyList(),
    val lastSeq: Long = 0,
) {
    val started: Boolean get() = fiveRm.isNotEmpty()

    fun exercises(day: Day): List<Exercise> = buildList {
        main[day.t1 to Tier.T1]?.let { add(Exercise(day.t1.id, day.t1.label, Tier.T1, it)) }
        main[day.t2 to Tier.T2]?.let { add(Exercise(day.t2.id, day.t2.label, Tier.T2, it)) }
        dayT3[day].orEmpty().mapNotNull { t3[it] }.forEach { add(Exercise(it.id, it.name, Tier.T3, it.state)) }
    }

    fun apply(entry: Entry): AppState {
        val s = copy(lastSeq = maxOf(lastSeq, entry.seq))
        return when (val e = entry.event) {
            is ProgramStart -> s.copy(fiveRm = e.fiveRm.mapNotNull { (k, v) -> Lift.of(k)?.let { it to v } }.toMap())
            is ProgressionEvent -> s.progress(e)
            is T3Define -> s.copy(t3 = t3 + (e.lift to (t3[e.lift]?.copy(name = e.name) ?: T3Lift(e.lift, e.name, LiftState(0)))))
            is T3Day -> Day.of(e.day)?.let { s.copy(dayT3 = dayT3 + (it to e.lifts)) } ?: s
            is SessionStart -> Day.of(e.day)?.let { s.copy(active = ActiveSession(e.sessionId, it, entry.ts, emptyMap())) } ?: s
            is SetLogged -> {
                val a = active?.takeIf { it.id == e.sessionId } ?: return s
                val tier = Tier.of(e.tier) ?: return s
                s.copy(active = a.copy(sets = a.sets + (SetKey(e.lift, tier, e.setIndex) to LoggedSet(e, entry.ts))))
            }
            is SetCleared -> {
                val a = active?.takeIf { it.id == e.sessionId } ?: return s
                val tier = Tier.of(e.tier) ?: return s
                s.copy(active = a.copy(sets = a.sets - SetKey(e.lift, tier, e.setIndex)))
            }
            is SessionEnd -> {
                val a = active?.takeIf { it.id == e.sessionId } ?: return s
                val record = SessionRecord(a.id, a.day, a.startedAt, e.durationSec, a.sets.values.map { it.event })
                s.copy(
                    active = null,
                    sessions = sessions + record,
                    nextDay = if (a.sets.isEmpty()) nextDay else a.day.next,
                )
            }
        }
    }

    private fun progress(e: ProgressionEvent): AppState {
        val tier = Tier.of(e.tier) ?: return this
        val kind = ChangeKind.of(e.kind)
        val stage = Schemes.stageIndex(tier, e.toStage)
        val restarts = kind == ChangeKind.INITIAL || kind == ChangeKind.RESET
        if (tier == Tier.T3) {
            val cur = t3[e.lift] ?: T3Lift(e.lift, e.lift, LiftState(e.toWeight))
            return copy(t3 = t3 + (e.lift to cur.copy(state = LiftState(e.toWeight, 0, e.toWeight))))
        }
        val lift = Lift.of(e.lift) ?: return this
        val prev = main[lift to tier]
        val state = when {
            prev == null || restarts -> LiftState(e.toWeight, stage, e.toWeight)
            kind == ChangeKind.MANUAL_EDIT -> Progression.manualEdit(prev, e.toWeight).copy(stage = stage)
            else -> prev.copy(weight = e.toWeight, stage = stage)
        }
        val next = main + ((lift to tier) to state)
        val fiveRms = if (e.fiveRm != null && tier == Tier.T1) fiveRm + (lift to e.fiveRm) else fiveRm
        return copy(main = next, fiveRm = fiveRms)
    }

    companion object {
        fun fold(entries: Iterable<Entry>): AppState = entries.fold(AppState()) { s, e -> s.apply(e) }
    }
}
