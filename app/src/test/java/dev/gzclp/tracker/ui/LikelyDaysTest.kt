package dev.gzclp.tracker.ui

import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.SessionRecord
import dev.gzclp.tracker.log.SetLogged
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

/** Upcoming's likely day for each workout, read from the gaps between lifting days (finding 21). */
class LikelyDaysTest {
    private val gz = Programs.of(Programs.GZCLP)
    private val squat = SetLogged("s", "squat", Tier.T1.id, "5x3+", 1, 3, 3, 200, 200, false)
    private fun lifts(vararg days: String) = AppState(sessions = days.map { SessionRecord("s$it", gz, gz.days[0], OffsetDateTime.parse("${it}T18:00:00-08:00"), 3600, listOf(squat)) })
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun theUsualGapCountsOnFromTheLastLift() {
        // Gaps 2, 3, 2, 2: the median is 2, so the next lift falls two days after Nov 19.
        val s = lifts("2026-11-10", "2026-11-12", "2026-11-15", "2026-11-17", "2026-11-19")
        assertEquals(listOf(d("2026-11-21"), d("2026-11-23"), d("2026-11-25")), likelyDays(s, d("2026-11-20"), 3))
    }

    @Test fun anOverdueLiftIsToday() {
        val s = lifts("2026-11-10", "2026-11-13")
        assertEquals(listOf(d("2026-11-20"), d("2026-11-23")), likelyDays(s, d("2026-11-20"), 2))
    }

    @Test fun withNoHistoryItIsTodayThenEveryOtherDay() {
        assertEquals(listOf(d("2026-11-20"), d("2026-11-22")), likelyDays(AppState(), d("2026-11-20"), 2))
    }
}
