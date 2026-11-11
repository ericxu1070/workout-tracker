package dev.gzclp.tracker.engine

import kotlin.math.roundToInt

enum class Lift(val id: String, val label: String, val upper: Boolean) {
    SQUAT("squat", "Squat", false),
    BENCH("bench", "Bench press", true),
    DEADLIFT("deadlift", "Deadlift", false),
    OHP("ohp", "Overhead press", true);

    val increment: Int get() = if (upper) 5 else 10
    val t2ResetBump: Int get() = if (upper) 15 else 20

    companion object {
        fun of(id: String): Lift? = entries.firstOrNull { it.id == id }
    }
}

/** GZCLP tiers, [CYCLE] for exercises in the 3-week cycle programs, and [CUSTOM] for custom splits, which run the cycle per [Wave]. */
enum class Tier(val id: String, val defaultRestSec: Int, val label: String) {
    T1("t1", 180, "T1"), T2("t2", 120, "T2"), T3("t3", 60, "T3"), CYCLE("cycle", 90, "Cycle"), CUSTOM("custom", 120, "Custom");

    /** Runs the 3-week cycle rule rather than the GZCLP engine. */
    val cyclic: Boolean get() = this == CYCLE || this == CUSTOM

    companion object {
        fun of(id: String): Tier? = entries.firstOrNull { it.id == id }
    }
}

/** Sets x reps; the last set is AMRAP when [amrap]. [label] is the log's stage string, e.g. "5x3+". */
data class Scheme(val sets: Int, val reps: Int, val amrap: Boolean) {
    val label: String get() = "${sets}x$reps" + if (amrap) "+" else ""
    val display: String get() = "$sets×$reps" + if (amrap) "+" else ""
}

object Schemes {
    val t1 = listOf(Scheme(5, 3, true), Scheme(6, 2, true), Scheme(10, 1, true))
    val t2 = listOf(Scheme(3, 10, false), Scheme(3, 8, false), Scheme(3, 6, false))
    val t3 = listOf(Scheme(3, 15, true))
    const val T3_BUMP_REPS = 25
    const val T3_INCREMENT = 5

    fun of(tier: Tier): List<Scheme> = when (tier) {
        Tier.T1 -> t1
        Tier.T2 -> t2
        Tier.T3 -> t3
        // A custom slot carries its own weeks (Slot.wave); these are its default.
        Tier.CYCLE, Tier.CUSTOM -> Cycle.weeks
    }

    fun stageIndex(tier: Tier, label: String): Int =
        of(tier).indexOfFirst { it.label == label }.coerceAtLeast(0)
}

fun round5(lb: Double): Int = (lb / 5.0).roundToInt() * 5

/** Working state of one lift at one tier. [cycleStart] is the weight the current T2 3x10 run began at. */
data class LiftState(val weight: Int, val stage: Int = 0, val cycleStart: Int = weight)

enum class ChangeKind(val id: String) {
    INITIAL("initial"),
    WEIGHT_INCREASE("weight_increase"),
    STAGE_CHANGE("stage_change"),
    RESET("reset"),
    MANUAL_EDIT("manual_edit"),
    WEEK_ADVANCE("week_advance"),
    CYCLE_UP("cycle_up"),
    CYCLE_REPEAT("cycle_repeat");

    companion object {
        fun of(id: String): ChangeKind? = entries.firstOrNull { it.id == id }
    }
}

sealed interface Outcome {
    /** Nothing logged for this exercise: it was skipped, so its state stays. */
    data object Unchanged : Outcome
    data class Change(val kind: ChangeKind, val to: LiftState) : Outcome
    /** T1 failed at 10x1+: needs a new 5RM, then [Progression.t1Reset]. */
    data object NeedsFiveRm : Outcome
}

object Progression {
    fun t1FromFiveRm(fiveRm: Int): Int = round5(fiveRm * 0.85)
    fun t2FromFiveRm(fiveRm: Int): Int = round5(fiveRm * 0.65)

    fun t1Reset(fiveRm: Int): LiftState = LiftState(t1FromFiveRm(fiveRm), 0)

    /**
     * [reps] has one entry per set of the current scheme; null is a set that was not logged.
     * [increment] is the weight added on success (lift-dependent for T1/T2).
     */
    fun progress(tier: Tier, state: LiftState, reps: List<Int?>, increment: Int, t2ResetBump: Int): Outcome {
        if (reps.all { it == null }) return Outcome.Unchanged
        val stages = Schemes.of(tier)
        val scheme = stages[state.stage]
        if (tier == Tier.T3) {
            val last = reps.getOrNull(scheme.sets - 1) ?: return Outcome.Unchanged
            return if (last >= Schemes.T3_BUMP_REPS)
                Outcome.Change(ChangeKind.WEIGHT_INCREASE, state.copy(weight = state.weight + Schemes.T3_INCREMENT))
            else Outcome.Unchanged
        }
        val success = reps.size >= scheme.sets && reps.take(scheme.sets).all { it != null && it >= scheme.reps }
        if (success) return Outcome.Change(ChangeKind.WEIGHT_INCREASE, state.copy(weight = state.weight + increment))
        if (state.stage < stages.lastIndex) return Outcome.Change(ChangeKind.STAGE_CHANGE, state.copy(stage = state.stage + 1))
        return when (tier) {
            Tier.T1 -> Outcome.NeedsFiveRm
            else -> {
                val w = state.cycleStart + t2ResetBump
                Outcome.Change(ChangeKind.RESET, LiftState(w, 0, w))
            }
        }
    }

    /** A hand edit; editing a fresh 3x10 run also moves where that run started. */
    fun manualEdit(state: LiftState, weight: Int): LiftState {
        val w = round5(weight.toDouble()).coerceAtLeast(0)
        val startMoves = state.stage == 0 && state.weight == state.cycleStart
        return state.copy(weight = w, cycleStart = if (startMoves) w else state.cycleStart)
    }
}
