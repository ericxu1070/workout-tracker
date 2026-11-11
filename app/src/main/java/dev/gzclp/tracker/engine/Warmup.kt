package dev.gzclp.tracker.engine

/** Warm-up sets before a T1 lift. Shown only, never logged, so they cannot touch progression. */
object Warmup {
    const val BAR = 45
    private val scheme = listOf(0.4 to 5, 0.6 to 5, 0.8 to 3)

    /** (weight, reps) for a first working set of [work] lb: rounded to 5, at least the bar, lighter than [work], no repeats. */
    fun sets(work: Int): List<Pair<Int, Int>> =
        scheme.map { (f, reps) -> round5(work * f).coerceAtLeast(BAR) to reps }
            .filter { it.first < work }
            .distinctBy { it.first }
}
