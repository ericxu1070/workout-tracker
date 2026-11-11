package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Program
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class WeekTest {
    private val gz = Programs.of(Programs.GZCLP)
    private val ppl = Programs.of("ppl")
    private val monday = LocalDate.parse("2026-11-09")

    private fun t(s: String) = OffsetDateTime.parse(s)
    private fun set(ex: String, tier: Tier, stage: String, w: Int, reps: Int, i: Int = 1) =
        SetLogged("s", ex, tier.id, stage, i, reps, reps, w, w, false)
    private fun session(at: String, sec: Long, vararg sets: SetLogged, program: Program = gz) =
        SessionRecord("s$at", program, program.days[0], t(at), sec, sets.toList())
    private fun run(id: String, activity: String, at: String, sec: Long, m: Int? = null) = id to Cardio(id, activity, activity.replaceFirstChar { it.uppercase() }, t(at), sec, m)
    private val squat = set("squat", Tier.T1, "5x3+", 200, 3)

    @Test fun weekRunsMondayToSundayByEachRecordsOwnDate() {
        assertEquals(monday, mondayOf(LocalDate.parse("2026-11-15")))
        assertEquals(monday, mondayOf(monday))
        assertEquals(monday.minusDays(7), mondayOf(LocalDate.parse("2026-11-08")))
        val s = AppState(sessions = listOf(
            session("2026-11-08T23:30:00-08:00", 3000, squat),
            session("2026-11-09T06:00:00-08:00", 3600, squat),
            session("2026-11-10T06:00:00-08:00", 900), // no sets: started and ended
            session("2026-11-15T23:50:00-08:00", 1800, squat),
            session("2026-11-16T00:10:00-08:00", 4000, squat),
        ))
        val w = s.week(monday)
        assertEquals(5400L, w.gymSec)
        assertEquals(2, w.workouts)
        assertEquals(3000L, w.prevGymSec)
        assertEquals(setOf(monday, LocalDate.parse("2026-11-15")), w.activeDays)
        assertEquals(LocalDate.parse("2026-11-08"), s.firstDay())
    }

    @Test fun cardioSumsByActivityAndAgainstLastWeek() {
        val s = AppState(cardio = mapOf(
            run("c1", "running", "2026-11-10T07:00:00-08:00", 1800, 4828),
            run("c2", "running", "2026-11-12T07:00:00-08:00", 2400),
            run("c3", "yoga", "2026-11-14T09:00:00-08:00", 3600),
            run("c4", "running", "2026-11-03T07:00:00-08:00", 2000, 5000),
        ))
        val w = s.week(monday)
        assertEquals(7800L, w.cardioSec)
        assertEquals(2000L, w.prevCardioSec)
        assertEquals(3, w.cardioSessions)
        assertEquals(listOf(ActivityTotal("running", "Running", 2, 4200, 4828), ActivityTotal("yoga", "Yoga", 1, 3600, null)), w.byActivity)
        assertEquals(3, w.activeDays.size)
    }

    @Test fun gzclpComparesWithTheLastTimeAtThatTierAcrossStages() {
        val s = AppState(sessions = listOf(
            session("2026-10-28T18:00:00-07:00", 3600, set("squat", Tier.T1, "5x3+", 215, 6)),
            session("2026-11-03T18:00:00-08:00", 3600, set("squat", Tier.T2, "3x10", 155, 10)),
            session("2026-11-11T18:00:00-08:00", 3600,
                set("squat", Tier.T1, "6x2+", 225, 2), set("squat", Tier.T1, "6x2+", 225, 5, 6),
                set("bench", Tier.T2, "3x10", 120, 0)), // no reps: nothing to compare
        ))
        val lift = s.week(monday).lifts.single()
        assertEquals("squat" to Tier.T1, lift.exercise to lift.tier)
        assertEquals("6x2+", lift.stage)
        assertEquals(SessionBest(t("2026-11-11T18:00:00-08:00"), 225, 5, 263), lift.now) // 225 × (1 + 5/30) = 262.5
        assertEquals(SessionBest(t("2026-10-28T18:00:00-07:00"), 215, 6, 258), lift.prev)
    }

    @Test fun cycleComparesWithTheSameWeekOfTheLastCycleAndSwapsByTheirOwnExercise() {
        val s = AppState(sessions = listOf(
            session("2026-10-20T18:00:00-07:00", 3600, set("bench", Tier.CYCLE, "3x8", 185, 8), program = ppl),
            session("2026-11-03T18:00:00-08:00", 3600, set("bench", Tier.CYCLE, "3x12", 165, 12), program = ppl),
            session("2026-11-10T18:00:00-08:00", 3600,
                set("bench", Tier.CYCLE, "3x8", 190, 8), set("incline_db_press", Tier.CYCLE, "3x8", 60, 8), program = ppl),
        ))
        val (bench, sub) = s.week(monday).lifts
        assertEquals(190 to 185, bench.now.topWeight to bench.prev?.topWeight)
        assertEquals("incline_db_press", sub.exercise)
        assertNull("First time", sub.prev)
    }

    @Test fun twiceInOneWeekComparesTheLatestWithTheEarlier() {
        val s = AppState(sessions = listOf(
            session("2026-11-09T18:00:00-08:00", 3600, set("squat", Tier.T1, "5x3+", 200, 5)),
            session("2026-11-13T18:00:00-08:00", 3600, set("squat", Tier.T1, "5x3+", 205, 4)),
        ))
        val lift = s.week(monday).lifts.single()
        assertEquals(205 to 200, lift.now.topWeight to lift.prev?.topWeight)
    }

    @Test fun bodyWeightIsTheMeanOfDayValues() {
        fun bw(id: String, at: String, lb: Double) = id to BodyWeight(id, t(at), lb)
        val s = AppState(bodyWeights = mapOf(
            bw("b1", "2026-11-09T06:40:00-08:00", 182.0),
            bw("b2", "2026-11-09T21:00:00-08:00", 185.0), // evening: the day value is the morning one
            bw("b3", "2026-11-12T06:40:00-08:00", 181.0),
            bw("b4", "2026-11-04T06:40:00-08:00", 182.4),
            bw("b5", "2026-11-06T06:40:00-08:00", 182.0),
        ))
        val w = s.week(monday)
        assertEquals(181.5, w.bodyWeight!!, 1e-9)
        assertEquals(182.2, w.prevBodyWeight!!, 1e-9)
        assertNull(s.week(monday.plusDays(7)).bodyWeight)
        assertEquals(emptySet<LocalDate>(), w.activeDays)
    }
}
