package dev.gzclp.tracker.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/** A barbell and the plates on hand, in lb. [pairs] maps each plate size in [Plates.SIZES] to how many pairs you have. */
data class Plates(val bar: Int = 45, val pairs: Map<Double, Int> = DEFAULT) {
    /** Every loadable total, mapped to the fewest plates per side that make it, heaviest plates first. */
    private val loads: Map<Int, List<Double>> by lazy {
        var best = mapOf(bar to emptyList<Double>())
        for (size in SIZES) repeat(pairs[size] ?: 0) {
            val next = best.toMutableMap()
            for ((total, side) in best) {
                val t = total + (size * 2).roundToInt()
                val load = side + size
                val have = next[t]
                if (have == null || load.size < have.size || load.size == have.size && heavier(load, have)) next[t] = load
            }
            best = next
        }
        best
    }

    /** The loadable total nearest [target]; a tie goes to the lighter one. */
    fun round(target: Int): Int = loads.keys.minWith(compareBy({ abs(it - target) }, { it }))

    /** Plates for one side, heaviest first, or null when [total] can't be loaded. */
    fun perSide(total: Int): List<Double>? = loads[total]

    companion object {
        /** Standard lb plates. Every size is a multiple of 2.5, so with a bar in 5 lb steps every total is a whole lb. */
        val SIZES = listOf(55.0, 45.0, 35.0, 25.0, 15.0, 10.0, 5.0, 2.5)
        val DEFAULT = mapOf(55.0 to 0, 45.0 to 4, 35.0 to 1, 25.0 to 1, 15.0 to 0, 10.0 to 2, 5.0 to 1, 2.5 to 1)

        /** 45, 2.5. */
        fun label(size: Double): String = if (size % 1.0 == 0.0) size.toInt().toString() else size.toString()

        /** Both lists sorted heaviest first: true when [a] uses heavier plates at the first place they differ. */
        private fun heavier(a: List<Double>, b: List<Double>): Boolean =
            a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x > y } ?: false
    }
}
