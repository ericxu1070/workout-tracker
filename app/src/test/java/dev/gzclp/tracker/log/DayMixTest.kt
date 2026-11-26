package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth

class DayMixTest {
    private val gz = Programs.of(Programs.GZCLP)
    private val squat = SetLogged("s", "squat", Tier.T1.id, "5x3+", 1, 3, 3, 200, 200, false)
    private fun lift(at: String, sec: Long) = SessionRecord("s$at", gz, gz.days[0], OffsetDateTime.parse(at), sec, listOf(squat))
    private fun run(id: String, at: String, sec: Long, rpe: Double? = null) = id to Cardio(id, "running", "Running", OffsetDateTime.parse(at), sec, rpe = rpe)
    private fun stretch(id: String, at: String, sec: Long) = id to StretchLogged(id, at, "after_workout", sec)
    private val day = LocalDate.parse("2026-11-10")

    @Test fun eachKindTakesItsShareOfTheDayInKindOrder() {
        val s = AppState(
            sessions = listOf(lift("2026-11-10T18:00:00-08:00", 3600), lift("2026-11-11T18:00:00-08:00", 0)),
            cardio = mapOf(run("c1", "2026-11-10T07:00:00-08:00", 1800, 7.0)),
            stretches = mapOf(stretch("x1", "2026-11-10T19:10:00-08:00", 600), stretch("x2", "not a time", 600)),
        )
        val mix = dayMix(s.bouts())
        // A session with no time draws nothing; a stretch with a stamp the log never writes is skipped.
        assertEquals(setOf(day), mix.keys)
        val segs = mix.getValue(day)
        assertEquals(listOf(Kind.LIFT, Kind.CARDIO, Kind.STRETCH), segs.map { it.kind })
        assertEquals(listOf(0.6f, 0.3f, 0.1f), segs.map { it.share })
        // 60 of 75 lifting minutes, 30 minutes at RPE 7 against an hour, 10 of 30 stretching minutes.
        assertEquals(listOf(0.8f, 0.5f, 1 / 3f), segs.map { it.intensity })
    }

    @Test fun oneKindFillsTheDayAndALongHardDayCapsAtFull() {
        val s = AppState(cardio = mapOf(run("c1", "2026-11-10T07:00:00-08:00", 3600, 9.0), run("c2", "2026-11-10T17:00:00-08:00", 3600)))
        val segs = dayMix(s.bouts()).getValue(day)
        assertEquals(listOf(Segment(Kind.CARDIO, 7200, 1f, 1f)), segs)
        // No RPE counts as 6: half an hour is 3/7 of the full mark.
        val easy = AppState(cardio = mapOf(run("c1", "2026-11-10T07:00:00-08:00", 1800)))
        assertEquals(3 / 7f, dayMix(easy.bouts()).getValue(day).single().intensity, 1e-6f)
    }

    @Test fun monthCountsEverySessionAndAveragesEachKind() {
        val s = AppState(
            sessions = listOf(lift("2026-11-03T18:00:00-08:00", 3000), lift("2026-11-05T18:00:00-08:00", 4200), lift("2026-10-30T18:00:00-08:00", 9999)),
            cardio = mapOf(run("c1", "2026-11-04T07:00:00-08:00", 1800)),
        )
        val now = s.bouts()
        val today = LocalDate.parse("2026-11-14")
        val m = monthStats(now, YearMonth.of(2026, 11), today)
        assertEquals(3, m.sessions)
        // 3 sessions over the 14 days so far: 1.5 a week.
        assertEquals(1.5, m.perWeek, 1e-9)
        assertEquals(3600L, m.liftSec)
        assertEquals(1800L, m.cardioSec)
        assertNull(m.stretchSec)
        // A past month counts all its days; a later one has none.
        assertEquals(7.0 / 31, monthStats(now, YearMonth.of(2026, 10), today).perWeek, 1e-9)
        assertEquals(MonthStats(0, 0.0, null, null, null), monthStats(now, YearMonth.of(2026, 12), today))
    }
}
