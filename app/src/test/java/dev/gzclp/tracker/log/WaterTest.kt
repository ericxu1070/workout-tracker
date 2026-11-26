package dev.gzclp.tracker.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

class WaterTest {
    private val t0 = OffsetDateTime.parse("2026-12-01T09:30:00-08:00")
    private val day = LocalDate.parse("2026-12-01")

    @Test
    fun logAndClearWater() {
        val state = AppState()
        val events = WaterLog.logWater(state, 350, t0, "wt1795836600")
        assertEquals(1, events.size)
        val event = events.first() as WaterLogged
        assertEquals("wt1795836600", event.id)
        assertEquals(350, event.ml)

        val applied = state.apply(Entry(1, t0, event))
        assertEquals(1, applied.water.size)
        assertEquals(350, WaterLog.totalMlForDay(applied, day))

        val clearEvents = WaterLog.clearWater("wt1795836600")
        assertEquals(1, clearEvents.size)
        val cleared = applied.apply(Entry(2, t0.plusMinutes(5), clearEvents.first()))
        assertEquals(0, cleared.water.size)
        assertEquals(0, WaterLog.totalMlForDay(cleared, day))
    }

    @Test
    fun expectedIntakeLinearPace() {
        val wake = LocalTime.of(7, 0)
        val goal = 2800

        // Before wake: 0 expected
        assertEquals(0, WaterLog.expectedIntakeMl(LocalTime.of(6, 30), goal, wake, activeHours = 14))

        // At wake: 0 expected
        assertEquals(0, WaterLog.expectedIntakeMl(LocalTime.of(7, 0), goal, wake, activeHours = 14))

        // 7 hours after wake (halfway, 14:00): half goal = 1400 ml
        assertEquals(1400, WaterLog.expectedIntakeMl(LocalTime.of(14, 0), goal, wake, activeHours = 14))

        // 10.5 hours after wake (3/4 of 14h, 17:30): 2100 ml
        assertEquals(2100, WaterLog.expectedIntakeMl(LocalTime.of(17, 30), goal, wake, activeHours = 14))

        // At wake + 14h (21:00): full goal = 2800 ml
        assertEquals(2800, WaterLog.expectedIntakeMl(LocalTime.of(21, 0), goal, wake, activeHours = 14))

        // After wake + 14h (22:00): full goal
        assertEquals(2800, WaterLog.expectedIntakeMl(LocalTime.of(22, 0), goal, wake, activeHours = 14))
    }

    @Test
    fun deficitCalculation() {
        val wake = LocalTime.of(7, 0)
        val goal = 2800 // 200 ml per hour over 14h

        // At 12:00 (5 hours in): expected = 1000 ml
        // If intake is 400 ml: deficit = 600 ml
        assertEquals(600, WaterLog.intakeDeficitMl(400, LocalTime.of(12, 0), goal, wake, activeHours = 14))

        // If intake is 1200 ml: deficit = -200 ml (ahead of pace)
        assertEquals(-200, WaterLog.intakeDeficitMl(1200, LocalTime.of(12, 0), goal, wake, activeHours = 14))
    }

    @Test
    fun reminderLogic() {
        val wake = LocalTime.of(7, 0)
        val goal = 2800 // 200 ml/hr

        // Outside active window: before wake
        assertFalse(WaterLog.shouldRemind(0, LocalTime.of(6, 0), goal, wake))

        // Outside active window: after wake + 14h (21:00)
        assertFalse(WaterLog.shouldRemind(1000, LocalTime.of(21, 30), goal, wake))

        // Inside window at 12:00 (expected 1000 ml)
        // Intake 400 ml -> deficit 600 ml > 500 ml -> should remind
        assertTrue(WaterLog.shouldRemind(400, LocalTime.of(12, 0), goal, wake))

        // Intake 600 ml -> deficit 400 ml <= 500 ml -> do not remind
        assertFalse(WaterLog.shouldRemind(600, LocalTime.of(12, 0), goal, wake))

        // Cooldown test: notified at 11:00, now 12:00 (only 60 min elapsed < 90 min) -> false
        assertFalse(
            WaterLog.shouldRemind(
                currentIntakeMl = 400,
                now = LocalTime.of(12, 0),
                goalMl = goal,
                wakeTime = wake,
                lastReminderTime = LocalTime.of(11, 0),
            )
        )

        // Cooldown test: notified at 10:15, now 12:00 (105 min elapsed >= 90 min) -> true
        assertTrue(
            WaterLog.shouldRemind(
                currentIntakeMl = 400,
                now = LocalTime.of(12, 0),
                goalMl = goal,
                wakeTime = wake,
                lastReminderTime = LocalTime.of(10, 15),
            )
        )
    }

    @Test
    fun volumeShowsMlAndOz() {
        assertEquals("300 ml", WaterLog.formatVolume(300))
    }

    @Test
    fun recommendedGoalFollowsBodyWeightAndTheDaysActivity() {
        assertEquals(WaterLog.DEFAULT_GOAL_ML, WaterLog.recommendedMl(null, 0, 0))
        val kg80 = 80 / 0.45359237
        assertEquals(2400, WaterLog.recommendedMl(kg80, 0, 0))
        assertEquals(2900, WaterLog.recommendedMl(kg80, 3600, 0))
        // Cardio counts twice what lifting does per minute.
        assertEquals(2900, WaterLog.recommendedMl(kg80, 0, 1800))
        assertEquals(4400, WaterLog.recommendedMl(kg80, 10 * 3600, 10 * 3600))
        assertEquals(8000, WaterLog.recommendedMl(900.0, 0, 0))

        val base = AppState(bodyWeights = mapOf("b" to BodyWeight("b", t0.minusDays(3), kg80)))
        val set = SetLogged("s", "squat", "t1", "5x3+", 1, 3, 3, 200, 200, false)
        val lift = SessionRecord("s", base.program, base.nextDay, t0, 3600, listOf(set))
        val run = Cardio("c", "run", "Run", t0.withHour(18), 1800)
        val yesterday = SessionRecord("y", base.program, base.nextDay, t0.minusDays(1), 3600, listOf(set))
        // A workout whose sets were all deleted is not a workout.
        val deleted = SessionRecord("d", base.program, base.nextDay, t0, 3600, emptyList())
        val state = base.copy(sessions = listOf(lift, yesterday, deleted), cardio = mapOf("c" to run))
        assertEquals(2400, WaterLog.recommendedMl(base, day))
        assertEquals(3400, WaterLog.recommendedMl(state, day))
        assertEquals(2500, WaterLog.recommendedMl(state, day.minusDays(4)))
        assertEquals(3000, WaterLog.goalMl(state, day, 3000))
        assertEquals(3400, WaterLog.goalMl(state, day, null))
    }
}
