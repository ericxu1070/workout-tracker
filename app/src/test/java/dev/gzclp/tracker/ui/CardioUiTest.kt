package dev.gzclp.tracker.ui

import dev.gzclp.tracker.engine.DistanceUnit
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Cardio
import dev.gzclp.tracker.log.CardioRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class CardioUiTest {
    private val start = OffsetDateTime.parse("2026-11-12T07:05:23-08:00")

    @Test fun minutesDropZeroMinutesOnWholeHours() {
        assertEquals("52 min", Fmt.minutes(52 * 60))
        assertEquals("1 hr 20 min", Fmt.minutes(80 * 60))
        assertEquals("3 hr", Fmt.minutes(180 * 60))
        assertEquals("1 hr", Fmt.minutes(59 * 60 + 45))
    }

    @Test fun timeRangeSharesTheSuffixWithinHalfADay() {
        assertEquals("7:05 – 7:46 AM", Fmt.timeRange(start, start.withMinute(46)))
        assertEquals("11:40 AM – 12:20 PM", Fmt.timeRange(start.withHour(11).withMinute(40), start.withHour(12).withMinute(20)))
    }

    @Test fun distanceSliderReachesTenMilesOrABitPastTheLongest() {
        assertEquals(10.0, distanceMax(DistanceUnit.MI, null, 0.0), 1e-9)
        assertEquals(10.0, distanceMax(DistanceUnit.MI, 8047, 0.0), 1e-9) // 5 mi stays inside 10
        assertEquals(15.6, distanceMax(DistanceUnit.MI, 20921, 0.0), 1e-9) // 13.0 mi × 1.2
        assertEquals(26.2, distanceMax(DistanceUnit.MI, 8047, 26.2), 1e-9) // typing stretches it
        assertEquals(2000.0, distanceMax(DistanceUnit.YD, 1509, 0.0), 1e-9)
        assertEquals(12000.0, distanceMax(DistanceUnit.M, 10000, 0.0), 1e-9)
    }

    @Test fun distanceTexts() {
        assertEquals("5.0", distanceValue(5.0000001, DistanceUnit.MI))
        assertEquals("3.12", distanceValue(3.12, DistanceUnit.MI))
        assertEquals("1,650", distanceValue(1650.0, DistanceUnit.YD))
        assertEquals("5.00 mi", distanceText(8047, DistanceUnit.MI))
        assertEquals("1,650 yd", distanceText(1509, DistanceUnit.YD))
    }

    @Test fun endTimePickerSetsTheDurationWithinBounds() {
        assertEquals(40 * 60 + 37L, durationEndingAt(start, 7, 46, 4 * 3600))
        assertEquals(3600L, durationEndingAt(start, 9, 30, 3600)) // past the stop tap: clamped
        assertEquals(5 * 3600 + 54 * 60 + 37L, durationEndingAt(start.withHour(19), 1, 0, 86_400)) // crosses midnight
        assertEquals(60L, durationEndingAt(start, 7, 6, 3600)) // 37 s: lifted to the minute
    }

    @Test fun rpeSliderStops() {
        val run = CardioRun("c1", "running", "Running", start)
        assertNull(SheetState.CardioStop(run, 60).rpeValue)
        assertEquals(1.0, SheetState.CardioStop(run, 60, rpe = 1).rpeValue!!, 0.0)
        assertEquals(7.5, SheetState.CardioStop(run, 60, rpe = 14).rpeValue!!, 0.0)
        assertEquals(10.0, SheetState.CardioStop(run, 60, rpe = 19).rpeValue!!, 0.0)
        assertEquals(8047, SheetState.CardioStop(run, 60, distance = 5.0).distanceM)
        assertNull(SheetState.CardioStop(run.copy(activity = "yoga"), 60, distance = 5.0).distanceM)
    }

    @Test fun weekRunsMondayToSundayByEachSessionsOwnDate() {
        fun c(id: String, at: String) = Cardio(id, "running", "Running", OffsetDateTime.parse(at), 1800)
        val s = AppState(cardio = listOf(
            c("sun", "2026-11-08T23:30:00-08:00"), c("mon", "2026-11-09T00:10:00-08:00"),
            c("sun2", "2026-11-15T23:59:00-08:00"), c("next", "2026-11-16T00:00:00-08:00"),
        ).associateBy { it.id })
        assertEquals(listOf("sun2", "mon"), s.cardioWeek(LocalDate.parse("2026-11-09")).map { it.id })
    }
}
