package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.NormLift
import dev.gzclp.tracker.engine.Norms
import dev.gzclp.tracker.engine.Peer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class StandingTest {
    private val t0 = OffsetDateTime.parse("2026-06-01T07:00:00-07:00")
    private fun best(day: Long, e1rm: Int) = SessionBest(t0.plusDays(day), e1rm - 20, 5, e1rm)
    private val peer = Peer(female = false, bodyKg = 82.0, age = 34.0)

    @Test fun recentBestIsTheBestOfTheNinetyDaysBeforeTheLatestSession() {
        assertNull(recentBest(emptyList()))
        assertEquals(250, recentBest(listOf(best(0, 250))))
        // 300 is 100 days before the latest session, so it no longer counts; 280 at 90 days does.
        assertEquals(280, recentBest(listOf(best(0, 300), best(10, 280), best(60, 260), best(100, 255))))
    }

    @Test fun peerNeedsAWeighInAndTakesAgeFromTheBirthYear() {
        val today = LocalDate.of(2026, 11, 27)
        assertNull(AppState().peer(false, 1992, today))
        val at = OffsetDateTime.parse("2026-11-20T07:00:00-08:00")
        val weighed = AppState(bodyWeights = (0L..2L).associate { "w$it" to BodyWeight("w$it", at.plusDays(it), 180.0 + it) })
        val p = weighed.peer(true, 1992, today)!!
        assertTrue(p.female)
        assertEquals(34.0, p.age!!, 0.0)
        // The latest 7-day average: 180, 181, 182.
        assertEquals(181.0 * Norms.KG_PER_LB, p.bodyKg, 1e-9)
        assertNull(weighed.peer(false, null, today)!!.age)
    }

    @Test fun standingRanksInWholePercentsFromOneToNinetyNine() {
        val mid = standing(NormLift.BENCH, 265, peer)
        assertEquals(Norms.of(false, NormLift.BENCH).percentile(265 * Norms.KG_PER_LB, peer), mid.percentile, 0.0)
        assertTrue(mid.rank in 30..70)
        assertEquals(1, standing(NormLift.BENCH, 45, peer).rank)
        assertEquals(99, standing(NormLift.BENCH, 700, peer).rank)
    }

    @Test fun overallNeedsAllThreeLiftsAndRanksTheirSum() {
        val two = mapOf(NormLift.SQUAT to 315, NormLift.BENCH to 225)
        assertNull(overall(two, peer))
        val total = overall(two + (NormLift.DEADLIFT to 405), peer)!!
        assertEquals(NormLift.TOTAL, total.lift)
        assertEquals(945, total.lb)
        assertEquals(standing(NormLift.TOTAL, 945, peer), total)
    }
}
