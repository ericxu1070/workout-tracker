package dev.gzclp.tracker.remind

import dev.gzclp.tracker.food.Meal
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.BodyWeightLogged
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.StretchLogged
import dev.gzclp.tracker.log.WaterLogged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class HabitTest {

    @Test
    fun midnightCrossingMeals() {
        // 1. Time of day representation: 4:00 a.m. day start
        // 00:30 is 1230 minutes from 4:00 a.m.
        val t0030 = LocalTime.of(0, 30)
        assertEquals(1230, timeToMinutesFrom4am(t0030))

        // 23:30 is 1170 minutes from 4:00 a.m.
        val t2330 = LocalTime.of(23, 30)
        assertEquals(1170, timeToMinutesFrom4am(t2330))

        // Conversion back from minutesFrom4am to LocalTime
        assertEquals(t0030, minutesFrom4amToTime(1230.0))
        assertEquals(t2330, minutesFrom4amToTime(1170.0))

        // Boundary cases: 04:00 is 0, 03:59 is 1439
        assertEquals(0, timeToMinutesFrom4am(LocalTime.of(4, 0)))
        assertEquals(LocalTime.of(4, 0), minutesFrom4amToTime(0.0))
        assertEquals(1439, timeToMinutesFrom4am(LocalTime.of(3, 59)))
        assertEquals(LocalTime.of(3, 59), minutesFrom4amToTime(1439.0))

        // Logical date attribution: events before 04:00 count towards yesterday's logical day
        val monNight = LocalDateTime.of(2026, 11, 23, 23, 30)
        val tueEarly = LocalDateTime.of(2026, 11, 24, 0, 30)
        assertEquals(LocalDate.of(2026, 11, 23), logicalDateOf(monNight))
        assertEquals(LocalDate.of(2026, 11, 23), logicalDateOf(tueEarly))
        assertEquals(DayOfWeek.MONDAY, logicalDateOf(monNight).dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, logicalDateOf(tueEarly).dayOfWeek)

        // Across midnight distance: 1230 - 1170 = 60 minutes (< 90 minutes)
        val m1 = timeToMinutesFrom4am(t2330).toDouble()
        val m2 = timeToMinutesFrom4am(t0030).toDouble()
        val diff = kotlin.math.abs(m2 - m1)
        assertEquals(60.0, diff, 0.001)

        // In MealTracker: adding 23:30 then 00:30 next Monday updates the same slot rather than creating a second one
        var tracker = MealTracker()
        val monday1 = LocalDate.of(2026, 11, 23)
        val monday2 = LocalDate.of(2026, 11, 30)
        tracker = tracker.addMeal(monday1, m1)
        assertEquals(1, tracker.slots[DayOfWeek.MONDAY]?.size)

        tracker = tracker.addMeal(monday2, m2)
        // Since distance is 60 min <= 90 min, it updates the nearest slot
        val monSlots = tracker.slots[DayOfWeek.MONDAY]
        assertNotNull(monSlots)
        assertEquals(1, monSlots!!.size)
        assertEquals(2, monSlots[0].n)
        // EMA: 0.7 * 1170 + 0.3 * 1230 = 819 + 369 = 1188.0 (23:48)
        assertEquals(1188.0, monSlots[0].mean, 0.001)
        assertEquals(LocalTime.of(23, 48), monSlots[0].time())
    }

    @Test
    fun shrinkageCalculationTowardsAllDaysMean() {
        // Spec 3: predictedMinutes = (n_d * mu_d + 2.0 * mu_all) / (n_d + 2.0)
        // Convert back to LocalTime: (predictedMinutes.roundToInt() + 4 * 60) % (24 * 60)
        val ema = WeekdayEma(
            mean = doubleArrayOf(300.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), // Monday mu = 300 (9:00 AM)
            n = intArrayOf(1, 0, 0, 0, 0, 0, 0),                        // Monday n = 1
            allMean = 240.0,                                            // allMean = 240 (8:00 AM)
            allN = 5,
        )

        // Monday: n_d = 1, mu_d = 300.0, mu_all = 240.0
        // predictedMinutes = (1.0 * 300.0 + 2.0 * 240.0) / (1.0 + 2.0) = (300.0 + 480.0) / 3.0 = 260.0
        val monPred = ema.predictMinutes(DayOfWeek.MONDAY)
        assertNotNull(monPred)
        assertEquals(260.0, monPred!!, 0.0001)
        // 260 minutes from 4:00 a.m. -> 8:20 AM
        assertEquals(LocalTime.of(8, 20), ema.predictTime(DayOfWeek.MONDAY))

        // Tuesday: new weekday (n_d = 0)
        // predictedMinutes = (0.0 * 0.0 + 2.0 * 240.0) / (0.0 + 2.0) = 240.0 (exact allMean)
        val tuePred = ema.predictMinutes(DayOfWeek.TUESDAY)
        assertNotNull(tuePred)
        assertEquals(240.0, tuePred!!, 0.0001)
        assertEquals(LocalTime.of(8, 0), ema.predictTime(DayOfWeek.TUESDAY))

        // When Monday has n_d = 2 and mu_d = 300.0
        val ema2 = ema.copy(n = intArrayOf(2, 0, 0, 0, 0, 0, 0))
        // predictedMinutes = (2.0 * 300.0 + 2.0 * 240.0) / (2.0 + 2.0) = 1080.0 / 4.0 = 270.0
        assertEquals(270.0, ema2.predictMinutes(DayOfWeek.MONDAY)!!, 0.0001)
        assertEquals(LocalTime.of(8, 30), ema2.predictTime(DayOfWeek.MONDAY))

        // When allN is 0, no prediction
        val emptyEma = WeekdayEma()
        assertNull(emptyEma.predictMinutes(DayOfWeek.MONDAY))
        assertNull(emptyEma.predictTime(DayOfWeek.MONDAY))
    }

    @Test
    fun slotAssignmentAndExpiry() {
        var tracker = MealTracker()
        val monday1 = LocalDate.of(2026, 1, 5)

        // 1. Initial meal at 12:00 PM (480 min from 4am) -> creates Slot 1
        tracker = tracker.addMeal(monday1, 480.0)
        var slots = tracker.slots[DayOfWeek.MONDAY].orEmpty()
        assertEquals(1, slots.size)
        assertEquals(480.0, slots[0].mean, 0.001)
        assertEquals(1, slots[0].n)
        assertEquals(LocalTime.of(12, 0), slots[0].time())

        // 2. Meal at 13:00 PM (540 min, delta = 60 min <= 90 min) -> updates nearest slot
        tracker = tracker.addMeal(monday1, 540.0)
        slots = tracker.slots[DayOfWeek.MONDAY].orEmpty()
        assertEquals(1, slots.size) // still 1 slot!
        assertEquals(2, slots[0].n)
        // 0.7 * 480 + 0.3 * 540 = 336 + 162 = 498 (12:18 PM)
        assertEquals(498.0, slots[0].mean, 0.001)
        assertEquals(LocalTime.of(12, 18), slots[0].time())

        // 3. Meal at 18:00 PM (840 min, delta = 342 min > 90 min) -> creates a new slot
        tracker = tracker.addMeal(monday1, 840.0)
        slots = tracker.slots[DayOfWeek.MONDAY].orEmpty()
        assertEquals(2, slots.size)
        assertEquals(LocalTime.of(12, 18), slots[0].time())
        assertEquals(LocalTime.of(18, 0), slots[1].time())

        // Predictions on Monday1
        assertEquals(listOf(LocalTime.of(12, 18), LocalTime.of(18, 0)), tracker.predict(monday1))

        // 4. Expiry: 21-day limit
        // 14 days later (Jan 19): not expired
        val monday3 = monday1.plusDays(14)
        assertEquals(listOf(LocalTime.of(12, 18), LocalTime.of(18, 0)), tracker.predict(monday3))

        // 21 days later (Jan 26): exactly 21 days, retained
        val monday4 = monday1.plusDays(21)
        assertEquals(listOf(LocalTime.of(12, 18), LocalTime.of(18, 0)), tracker.predict(monday4))

        // 22 days later (Jan 27): more than 21 days with no hit -> expired and dropped!
        val pastExpiry = monday1.plusDays(22)
        assertEquals(emptyList<LocalTime>(), tracker.predict(pastExpiry))
        // A meal on day 21 keeps both slots and adds one; a week later they are pruned.
        assertEquals(3, tracker.addMeal(monday4, 180.0).slots[DayOfWeek.MONDAY]?.size)
        assertEquals(1, tracker.addMeal(monday1.plusDays(28), 180.0).slots[DayOfWeek.MONDAY]?.size)

        // 5. Max 4 slots per weekday: adding 5 distinct slots drops the oldest
        var cappedTracker = MealTracker()
        val d0 = LocalDate.of(2026, 1, 5)
        cappedTracker = cappedTracker.addMeal(d0, 120.0) // 06:00
        cappedTracker = cappedTracker.addMeal(d0, 360.0) // 10:00
        cappedTracker = cappedTracker.addMeal(d0, 600.0) // 14:00
        cappedTracker = cappedTracker.addMeal(d0, 840.0) // 18:00
        assertEquals(4, cappedTracker.slots[DayOfWeek.MONDAY]?.size)

        // 5th meal on next week (d0 + 7) at 22:00 (1080 min)
        val d1 = d0.plusDays(7)
        cappedTracker = cappedTracker.addMeal(d1, 1080.0)
        assertEquals(4, cappedTracker.slots[DayOfWeek.MONDAY]?.size)
    }

    @Test
    fun mondayPatternDiffersFromTuesdayStayingSeparate() {
        var tracker = MealTracker()
        val mon = LocalDate.of(2026, 11, 23) // Monday
        val tue = LocalDate.of(2026, 11, 24) // Tuesday

        // Monday has lunch at 11:30 AM (450 min from 4am)
        tracker = tracker.addMeal(mon, 450.0)
        // Tuesday has lunch at 13:30 PM (570 min from 4am)
        tracker = tracker.addMeal(tue, 570.0)

        // Check predictions for Monday and Tuesday
        val monPred = tracker.predict(mon)
        val tuePred = tracker.predict(tue)

        assertEquals(1, monPred.size)
        assertEquals(1, tuePred.size)
        assertEquals(LocalTime.of(11, 30), monPred[0])
        assertEquals(LocalTime.of(13, 30), tuePred[0])

        // Add second week: Monday at 11:45 AM (465 min), Tuesday at 13:15 PM (555 min)
        tracker = tracker.addMeal(mon.plusDays(7), 465.0)
        tracker = tracker.addMeal(tue.plusDays(7), 555.0)

        val monPred2 = tracker.predict(mon.plusDays(7))
        val tuePred2 = tracker.predict(tue.plusDays(7))

        // Monday EMA: 0.7 * 450 + 0.3 * 465 = 454.5 (11:34 AM)
        assertEquals(LocalTime.of(11, 35), monPred2[0])
        // Tuesday EMA: 0.7 * 570 + 0.3 * 555 = 565.5 (13:25 PM)
        assertEquals(LocalTime.of(13, 26), tuePred2[0])

        // Ensure Monday and Tuesday slots stayed completely isolated
        assertTrue(monPred2[0].isBefore(LocalTime.of(12, 0)))
        assertTrue(tuePred2[0].isAfter(LocalTime.of(13, 0)))
    }

    @Test
    fun rebuildFromAppStateAndFoodLog() {
        val zone = ZoneOffset.ofHours(-8)
        val t0 = OffsetDateTime.of(2026, 11, 23, 7, 0, 0, 0, zone) // Monday 7:00 AM

        val entries = listOf(
            Entry(1, t0, BodyWeightLogged("w1", LogCodec.TS.format(t0), 180.0)),
            Entry(2, t0.plusDays(1).withHour(7).withMinute(15), BodyWeightLogged("w2", LogCodec.TS.format(t0.plusDays(1).withHour(7).withMinute(15)), 180.5)),
            Entry(3, t0.plusDays(2).withHour(7).withMinute(30), BodyWeightLogged("w3", LogCodec.TS.format(t0.plusDays(2).withHour(7).withMinute(30)), 181.0)),
            Entry(4, t0.withHour(20).withMinute(0), StretchLogged("s1", LogCodec.TS.format(t0.withHour(20).withMinute(0)), "full", 600)),
            Entry(5, t0.withHour(9).withMinute(0), WaterLogged("wt1", LogCodec.TS.format(t0.withHour(9).withMinute(0)), 250)),
        )
        val state = AppState.fold(entries)

        val meals = listOf(
            Meal(File("/dummy/meal1.jpg"), t0.withHour(12).withMinute(0)),
            Meal(File("/dummy/meal2.jpg"), t0.withHour(18).withMinute(0)),
        )

        val model = rebuildHabits(state, meals, zone)

        // WeighIn has 3 events
        assertEquals(3, model.count(HabitKind.WEIGH_IN))
        val predWeighIn = model.predict(HabitKind.WEIGH_IN, t0.toLocalDate())
        assertEquals(1, predWeighIn.size)

        // Meals has 2 events
        assertEquals(2, model.count(HabitKind.MEAL))
        val predMeals = model.predict(HabitKind.MEAL, t0.toLocalDate())
        assertEquals(listOf(LocalTime.of(12, 0), LocalTime.of(18, 0)), predMeals)

        // Stretch has 1 event
        assertEquals(1, model.count(HabitKind.STRETCH))
        assertEquals(1, model.predict(HabitKind.STRETCH, t0.toLocalDate()).size)
    }

    @Test
    fun aLateNightWeighInIsNotTheDaysFirst() {
        val zone = ZoneOffset.UTC
        // Logical Monday: 22:00 Monday, then 00:30 Tuesday morning.
        val evening = OffsetDateTime.of(LocalDateTime.of(2026, 11, 23, 22, 0), zone)
        val night = OffsetDateTime.of(LocalDateTime.of(2026, 11, 24, 0, 30), zone)
        val state = listOf(BodyWeightLogged("a", LogCodec.TS.format(night), 180.0), BodyWeightLogged("b", LogCodec.TS.format(evening), 180.0))
            .mapIndexed { i, e -> Entry(i.toLong(), evening, e) }.fold(AppState()) { s, e -> s.apply(e) }
        val model = rebuildHabits(state, emptyList(), zone)
        assertEquals(LocalTime.of(22, 0), model.weighIn.predictTime(DayOfWeek.MONDAY))
    }

    @Test
    fun aLearnedSlotAfterMidnightFiresThatNightNotADayLate() {
        val mondayEvening = java.time.ZonedDateTime.of(2026, 11, 23, 21, 0, 0, 0, ZoneOffset.UTC)
        // Only Monday's logical day has the slot, so it must land on Tuesday's calendar date.
        val monday = mondayEvening.toLocalDate()
        val next = HabitAlarm.nextTime(mondayEvening, 20) { d -> if (d == monday) listOf(LocalTime.of(0, 30)) else emptyList() }
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 24, 0, 50, 0, 0, ZoneOffset.UTC), next)
        val afternoon = HabitAlarm.nextTime(mondayEvening) { listOf(LocalTime.of(14, 0)) }
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 24, 14, 0, 0, 0, ZoneOffset.UTC), afternoon)
    }
}
