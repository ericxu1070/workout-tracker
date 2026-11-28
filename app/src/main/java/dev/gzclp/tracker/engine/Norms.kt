package dev.gzclp.tracker.engine

import kotlin.math.exp
import kotlin.math.ln

/** The lifts the norms cover: the three competition lifts and their sum. [id] is the exercise id for the first three. */
enum class NormLift(val id: String, val label: String) {
    SQUAT("squat", "Squat"), BENCH("bench", "Bench press"), DEADLIFT("deadlift", "Deadlift"), TOTAL("total", "Total");

    companion object {
        val lifts = listOf(SQUAT, BENCH, DEADLIFT)
        fun of(exercise: String): NormLift? = lifts.firstOrNull { it.id == exercise }
    }
}

/** Who a lift is compared with: lifters of this sex, body weight and age. With no [age] it is the prime years, 24 to 34. */
data class Peer(val female: Boolean, val bodyKg: Double, val age: Double? = null)

/**
 * One sex and lift from strength-norms.txt, fitted by tools/strength-norms.py:
 * ln(lift kg) = m(body weight) + a(age) + c(age, body weight) + s(age) × z, with z's quantiles [q] at [probs].
 */
class Norm(
    private val bw: DoubleArray, private val m: DoubleArray,
    private val ages: DoubleArray, private val a: DoubleArray, private val s: DoubleArray,
    private val cAges: DoubleArray, private val cBw: DoubleArray, private val c: Array<DoubleArray>,
    private val probs: DoubleArray, private val q: DoubleArray,
) {
    private fun center(peer: Peer): Double {
        val age = peer.age ?: PRIME_AGE
        return at(peer.bodyKg, bw, m) + at(age, ages, a) + at(peer.bodyKg, cBw, DoubleArray(cBw.size) { j -> at(age, cAges, DoubleArray(cAges.size) { i -> c[i][j] }) })
    }

    private fun scale(peer: Peer): Double = at(peer.age ?: PRIME_AGE, ages, s)

    /** The share of [peer]s who lift less than [liftKg], from 0.001 to 0.999. */
    fun percentile(liftKg: Double, peer: Peer): Double =
        if (liftKg <= 0) probs.first() else at((ln(liftKg) - center(peer)) / scale(peer), q, probs)

    /** The lift, in kg, that the share [p] of [peer]s stay under. */
    fun liftAt(p: Double, peer: Peer): Double = exp(center(peer) + scale(peer) * at(p, probs, q))

    /**
     * How thick the peers are around [liftKg]: the share per kg, averaged over 0.3 of their spread each side, which rounds the
     * corners a table of quantiles would draw.
     */
    fun density(liftKg: Double, peer: Peer): Double {
        val h = 0.3 * scale(peer)
        val (lo, hi) = liftKg * exp(-h) to liftKg * exp(h)
        return (percentile(hi, peer) - percentile(lo, peer)) / (hi - lo)
    }

    companion object {
        /** The age a [Peer] without one is read at: the middle of the prime years. */
        const val PRIME_AGE = 29.0

        /** [ys] read at [x] between its [xs] knots, held flat past the ends. */
        internal fun at(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
            if (x <= xs.first()) return ys.first()
            if (x >= xs.last()) return ys.last()
            val i = xs.indexOfFirst { it > x }
            val t = (x - xs[i - 1]) / (xs[i] - xs[i - 1])
            return ys[i - 1] + t * (ys[i] - ys[i - 1])
        }
    }
}

/** The norms the app ships, read once from strength-norms.txt. */
object Norms {
    const val KG_PER_LB = 0.45359237

    /** The meets counted run from [fromYear] to [toYear], one per lifter: [lifters] in all. */
    val fromYear: Int
    val toYear: Int
    val lifters: Int
    private val table: Map<Pair<Boolean, NormLift>, Norm>

    init {
        val lines = Norms::class.java.getResourceAsStream("strength-norms.txt")!!.bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        fun nums(line: String, key: String): DoubleArray {
            check(line.startsWith("$key ")) { "strength-norms.txt: expected $key, got ${line.take(20)}" }
            return line.substring(key.length + 1).split(' ').map { it.toDouble() }.toDoubleArray()
        }
        val meta = nums(lines[0], "meta")
        fromYear = meta[0].toInt()
        toYear = meta[1].toInt()
        lifters = meta[2].toInt()
        val probs = nums(lines[1], "p")
        val out = HashMap<Pair<Boolean, NormLift>, Norm>()
        var i = 2
        while (i < lines.size) {
            val (sex, lift) = lines[i].split(' ')
            val cAges = nums(lines[i + 6], "cage")
            out[(sex == "F") to NormLift.entries.first { it.id == lift }] = Norm(
                nums(lines[i + 1], "bw"), nums(lines[i + 2], "m"), nums(lines[i + 3], "age"), nums(lines[i + 4], "a"), nums(lines[i + 5], "s"),
                cAges, nums(lines[i + 7], "cbw"), Array(cAges.size) { nums(lines[i + 8 + it], "c") },
                probs, nums(lines[i + 8 + cAges.size], "q"),
            )
            i += 9 + cAges.size
        }
        table = out
    }

    fun of(female: Boolean, lift: NormLift): Norm = table.getValue(female to lift)
}
