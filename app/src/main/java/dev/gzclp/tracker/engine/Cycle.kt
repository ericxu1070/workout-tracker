package dev.gzclp.tracker.engine

/** Cycle progression increments, in lb. Editable in Settings and recorded in the log. */
data class Increments(val upperStep: Int = 5, val lowerStep: Int = 10, val cycleBump: Int = 5) {
    fun step(lower: Boolean) = if (lower) lowerStep else upperStep
}

/**
 * A custom split slot's cycle: [sets] each workout and the target [reps] of each cycle week, light to heavy.
 * [key] is the log's `scheme` field, e.g. "5x8,6,5", and keys the slot's cycle state.
 */
data class Wave(val sets: Int, val reps: List<Int>) {
    val weeks: List<Scheme> get() = reps.map { Scheme(sets, it, false) }
    val key: String get() = "${sets}x${reps.joinToString(",")}"
    val valid: Boolean get() = sets in SETS && reps.size == Cycle.weeks.size && reps.all { it in REPS }

    companion object {
        /** The built-in cycle, 3 x 12·10·8: where a copied preset starts. */
        val DEFAULT = Wave(3, listOf(12, 10, 8))
        val SETS = 1..10
        val REPS = 1..50
    }
}

/** Where one exercise is in its 3-week cycle: [week] 0..2, at [base] + week * step. */
data class CycleState(val base: Int, val week: Int = 0)

/**
 * The 3-week light-to-heavy cycle: 3x12 at base, 3x10 at base + step, 3x8 at base + 2 * step.
 * A week advances each time the exercise is done in a session.
 */
object Cycle {
    val weeks = listOf(Scheme(3, 12, false), Scheme(3, 10, false), Scheme(3, 8, false))

    fun weight(s: CycleState, step: Int): Int = s.base + s.week * step
    fun baseFromFiveRm(fiveRm: Int): Int = round5(fiveRm * 0.65)

    /** [reps] has one entry per set; null is a set not logged. Null result: nothing logged, state stays. */
    fun advance(s: CycleState, reps: List<Int?>, bump: Int, weeks: List<Scheme> = Cycle.weeks): Pair<ChangeKind, CycleState>? {
        if (reps.all { it == null }) return null
        if (s.week < weeks.lastIndex) return ChangeKind.WEEK_ADVANCE to s.copy(week = s.week + 1)
        val heavy = weeks.last()
        // The rule: every heavy-week set at its target reps (8 in the built-in cycle) earns the next cycle at base + bump; anything less repeats the cycle.
        val earned = reps.size >= heavy.sets && reps.take(heavy.sets).all { it != null && it >= heavy.reps }
        return if (earned) ChangeKind.CYCLE_UP to CycleState(s.base + bump) else ChangeKind.CYCLE_REPEAT to CycleState(s.base)
    }

    /** A hand edit sets this week's weight; the base moves so the rest of the cycle keeps its steps. */
    fun manualEdit(s: CycleState, weight: Int, step: Int): CycleState =
        s.copy(base = (round5(weight.toDouble()) - s.week * step).coerceAtLeast(0))
}
