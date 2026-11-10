package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

class BodyWeightTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-01T07:00:00-08:00")
    private fun day(n: Long, h: Int = 7, m: Int = 0) = t0.plusDays(n).withHour(h).withMinute(m)

    private fun store(f: File = File(tmp.root, "log.jsonl")) = Store(f).apply {
        dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105), Programs.GZCLP) }
    }

    private fun Store.weigh(at: OffsetDateTime, lb: Double, id: String? = null) = dispatch(at) { Commands.logBodyWeight(it, id, at, lb) }

    @Test fun dayValueIsTheEarliestWeighIn() {
        val s = store()
        s.weigh(day(0, 21), 184.0)
        s.weigh(day(0, 6, 45), 181.2)
        s.weigh(day(0, 12), 183.0)
        s.weigh(day(1), 180.86)
        val daily = s.state.value.dailyBodyWeight()
        assertEquals(mapOf(LocalDate.of(2026, 11, 1) to 181.2, LocalDate.of(2026, 11, 2) to 180.9), daily)
    }

    @Test fun sevenDayAverageCoversTheTrailingWeek() {
        val s = store()
        listOf(0L to 180.0, 1L to 182.0, 3L to 184.0, 7L to 186.0, 8L to 190.0).forEach { (d, w) -> s.weigh(day(d), w) }
        val avg = sevenDayAverage(s.state.value.dailyBodyWeight())
        assertEquals(181.0, avg.getValue(LocalDate.of(2026, 11, 2)), 1e-9)
        assertEquals((182.0 + 184.0 + 186.0) / 3, avg.getValue(LocalDate.of(2026, 11, 8)), 1e-9) // Nov 1 falls outside the 7 days ending Nov 8
        assertEquals((184.0 + 186.0 + 190.0) / 3, avg.getValue(LocalDate.of(2026, 11, 9)), 1e-9)
    }

    @Test fun ratioUsesTheWeightInEffectAndDropsEarlierSessions() {
        val daily = java.util.TreeMap<LocalDate, Double>().apply {
            put(LocalDate.of(2026, 11, 3), 200.0)
            put(LocalDate.of(2026, 11, 6), 180.0)
        }
        val bests = listOf(
            SessionBest(day(1, 18), 200, 5, 233),
            SessionBest(day(3, 18), 200, 5, 240),
            SessionBest(day(4, 18), 200, 5, 250),
            SessionBest(day(6, 18), 200, 5, 288),
        )
        val r = ratios(bests, daily)
        assertEquals(listOf(1.2, 1.25, 1.6), r.map { it.ratio })
        assertEquals(listOf(200.0, 200.0, 180.0), r.map { it.bodyWeight })
    }

    @Test fun editAndDeleteSurviveARebuildAndARestore() {
        val f = File(tmp.root, "log.jsonl")
        val s = store(f)
        s.weigh(day(0), 181.0)
        s.weigh(day(1), 182.0)
        val (a, b) = s.state.value.weighIns().map { it.id }
        s.weigh(day(0, 6), 179.5, id = a)
        s.dispatch(day(2)) { Commands.clearBodyWeight(it, b) }
        assertTrue(s.dispatch(day(2)) { Commands.clearBodyWeight(it, b) }.isEmpty())
        assertTrue(s.dispatch(day(2)) { Commands.logBodyWeight(it, null, day(2), 20.0) }.isEmpty())

        val want = listOf(BodyWeight(a, day(0, 6), 179.5))
        assertEquals(want, s.state.value.weighIns())
        assertEquals(want, Store(f).state.value.weighIns())
        val restored = Store(File(tmp.root, "restored.jsonl"))
        restored.restore(f.readText())
        assertEquals(want, restored.state.value.weighIns())
        assertEquals(s.state.value, restored.state.value)
    }

    @Test fun twoWeighInsInOneSecondGetDistinctIds() {
        val s = store()
        s.weigh(day(0), 181.0)
        s.weigh(day(0), 181.4)
        assertEquals(2, s.state.value.bodyWeights.size)
    }
}
