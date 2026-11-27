package dev.gzclp.tracker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shipped norms read back: sane sizes, and a percentile and its lift agree with each other. */
class NormsTest {
    private val man = Peer(female = false, bodyKg = 90.0, age = 30.0)

    @Test fun theTableCoversBothSexesAndEveryLift() {
        assertTrue(Norms.lifters > 100_000)
        assertTrue(Norms.fromYear in 2000..Norms.toYear)
        for (female in listOf(false, true)) for (lift in NormLift.entries) {
            val kg = Norms.of(female, lift).liftAt(0.5, Peer(female, 75.0, 30.0))
            assertTrue("$female $lift median $kg", kg in 30.0..700.0)
        }
    }

    @Test fun aMedianManAtNinetyKiloLiftsWhatMeetsShow() {
        // First-meet medians for a 90 kg man of 30: about 185, 120 and 220 kg.
        assertEquals(185.0, Norms.of(false, NormLift.SQUAT).liftAt(0.5, man), 20.0)
        assertEquals(120.0, Norms.of(false, NormLift.BENCH).liftAt(0.5, man), 15.0)
        assertEquals(220.0, Norms.of(false, NormLift.DEADLIFT).liftAt(0.5, man), 20.0)
        // A total is close to its three lifts' medians summed.
        val sum = NormLift.lifts.sumOf { Norms.of(false, it).liftAt(0.5, man) }
        assertEquals(sum, Norms.of(false, NormLift.TOTAL).liftAt(0.5, man), sum * 0.03)
    }

    @Test fun percentileRisesWithTheLiftAndInvertsLiftAt() {
        for (female in listOf(false, true)) for (lift in NormLift.entries) {
            val norm = Norms.of(female, lift)
            val peer = Peer(female, if (female) 65.0 else 85.0, 41.0)
            var last = 0.0
            for (p in listOf(0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99)) {
                val kg = norm.liftAt(p, peer)
                assertTrue(kg > last)
                last = kg
                assertEquals(p, norm.percentile(kg, peer), 1e-6)
            }
        }
    }

    @Test fun percentileStaysInsideTheTable() {
        val norm = Norms.of(false, NormLift.BENCH)
        assertEquals(0.001, norm.percentile(1.0, man), 1e-9)
        assertEquals(0.001, norm.percentile(0.0, man), 1e-9)
        assertEquals(0.999, norm.percentile(1000.0, man), 1e-9)
    }

    @Test fun theSameLiftRanksHigherForALighterOlderOrFemaleLifter() {
        val norm = Norms.of(false, NormLift.SQUAT)
        val here = norm.percentile(160.0, man)
        assertTrue(norm.percentile(160.0, man.copy(bodyKg = 70.0)) > here)
        assertTrue(norm.percentile(160.0, man.copy(age = 62.0)) > here)
        assertTrue(norm.percentile(160.0, man.copy(age = 15.0)) > here)
        assertTrue(Norms.of(true, NormLift.SQUAT).percentile(160.0, man.copy(female = true)) > here)
    }

    @Test fun noAgeReadsAsThePrimeYears() {
        val norm = Norms.of(true, NormLift.DEADLIFT)
        val peer = Peer(true, 63.0)
        assertEquals(norm.liftAt(0.5, peer.copy(age = Norm.PRIME_AGE)), norm.liftAt(0.5, peer), 1e-9)
    }

    @Test fun bodyWeightAndAgePastTheKnotsHoldTheEndValue() {
        val norm = Norms.of(false, NormLift.TOTAL)
        assertEquals(norm.liftAt(0.5, man.copy(bodyKg = 155.0)), norm.liftAt(0.5, man.copy(bodyKg = 220.0)), 1e-9)
        assertEquals(norm.liftAt(0.5, man.copy(age = 75.0)), norm.liftAt(0.5, man.copy(age = 88.0)), 1e-9)
    }

    @Test fun theDensityIsOneHumpThatHoldsEveryone() {
        val norm = Norms.of(false, NormLift.DEADLIFT)
        val (lo, hi) = norm.liftAt(0.001, man) * 0.7 to norm.liftAt(0.999, man) * 1.3
        val n = 400
        val d = DoubleArray(n + 1) { norm.density(lo + (hi - lo) * it / n, man) }
        assertEquals(1.0, d.sum() * (hi - lo) / n, 0.02)
        // It climbs to one peak near the median and falls away: no second hump worth 5% of the peak.
        val peak = d.indices.maxBy { d[it] }
        assertEquals(norm.liftAt(0.5, man), lo + (hi - lo) * peak / n, 25.0)
        for (i in 1..peak) assertTrue(d[i] >= d[i - 1] - d[peak] * 0.05)
        for (i in peak + 1..n) assertTrue(d[i] <= d[i - 1] + d[peak] * 0.05)
    }

    @Test fun readsBetweenKnotsAndHoldsTheEnds() {
        val xs = doubleArrayOf(0.0, 10.0, 20.0)
        val ys = doubleArrayOf(1.0, 3.0, 2.0)
        assertEquals(1.0, Norm.at(-5.0, xs, ys), 0.0)
        assertEquals(2.0, Norm.at(5.0, xs, ys), 1e-12)
        assertEquals(3.0, Norm.at(10.0, xs, ys), 1e-12)
        assertEquals(2.5, Norm.at(15.0, xs, ys), 1e-12)
        assertEquals(2.0, Norm.at(99.0, xs, ys), 0.0)
    }
}
