package dev.gzclp.tracker.ui

import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.Event
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.ProgressionEvent
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.WaterLog
import dev.gzclp.tracker.log.bouts
import dev.gzclp.tracker.log.dailyBodyWeight
import dev.gzclp.tracker.log.dayMix
import dev.gzclp.tracker.stretch.StretchRoutines
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** The demo half-year every screenshot renders from: a valid log, the same every run, with every screen's data in it. */
class DemoTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file by lazy { File(tmp.root, "demo.jsonl").also { Demo.build(it) } }
    private val lines by lazy { file.readLines() }
    private val state by lazy { Store(file).state.value }
    private val days = generateSequence(Demo.first) { it.plusDays(1) }.takeWhile { it <= Demo.today }.toList()
    private val progressions by lazy { lines.mapNotNull { LogCodec.decode(it)?.event as? ProgressionEvent } }

    @Test fun sameBytesEveryBuild() {
        assertArrayEquals(file.readBytes(), File(tmp.root, "again.jsonl").also { Demo.build(it) }.readBytes())
    }

    @Test fun readsBackFromTheLog() {
        assertEquals(lines.size, lines.mapNotNull { LogCodec.decode(it) }.size)
        assertEquals(lines.size.toLong(), state.lastSeq)
        assertEquals(Demo.state, state)
        assertTrue(lines.map { LogCodec.decode(it)!!.ts }.zipWithNext().all { (a, b) -> !b.isBefore(a) })
    }

    @Test fun writesEveryKindOfLine() {
        val written = lines.map { Json.parseToJsonElement(it).jsonObject.getValue("type").jsonPrimitive.content }.toSet()
        val all = Event.serializer().descriptor.getElementDescriptor(1).elementNames.toSet()
        // Switching programs, changing increments, merging exercises and bodyweight metadata would change what every screen shows.
        val left = setOf("program", "exercise_remap", "exercise_meta")
        assertEquals(all - left, written)
    }

    @Test fun fillsSixMonthsWithEveryKind() {
        val mix = dayMix(state.bouts())
        assertTrue(mix.keys.all { it in days })
        // Rest days, and days of one, two and three kinds.
        assertEquals(setOf(0, 1, 2, 3), days.map { mix[it].orEmpty().size }.toSet())
        for (m in 6..11) assertTrue("month $m", state.sessions.count { YearMonth.from(it.startedAt) == YearMonth.of(2026, m) } >= 6)
        assertNotNull(state.active)
        assertTrue(lines.any { "\"mode\":\"open\"" in it })
        assertTrue(state.cardio.values.map { it.activity }.toSet().size >= 8)
        assertTrue(state.bodyWeights.size >= 140)
        assertTrue(days.filter { it !in sick }.all { WaterLog.totalMlForDay(state, it) > 0 })
    }

    /** The flu week, which the empty History week shot shows, has nothing in it. */
    private val sick = LocalDate.of(2026, 9, 21)..LocalDate.of(2026, 9, 27)

    @Test fun aMissedWeekIsEmpty() {
        assertTrue(dayMix(state.bouts()).keys.none { it in sick })
        assertTrue(state.bodyWeights.values.none { it.at.toLocalDate() in sick })
        assertTrue(sick.let { w -> generateSequence(w.start) { it.plusDays(1) }.takeWhile { it <= w.endInclusive } }.all { WaterLog.totalMlForDay(state, it) == 0 })
    }

    @Test fun todayIsAnADayWithFacePulls() {
        assertTrue(state.exercises(state.active!!.program, state.active!!.day).any { it.id == "face_pull" })
    }

    @Test fun liftsStallResetAndClimb() {
        for (tier in listOf(Tier.T1, Tier.T2)) for (kind in listOf("stage_change", "reset", "weight_increase")) {
            assertTrue("$tier $kind", progressions.any { it.tier == tier.id && it.kind == kind })
        }
        assertTrue(progressions.any { it.tier == Tier.T3.id && it.kind == "weight_increase" })
        assertTrue(progressions.any { it.kind == "manual_edit" })
        assertTrue(progressions.any { it.kind == "reset" && it.fiveRm != null })
        // Every T1 lift ends heavier than it started.
        for (lift in listOf("squat", "bench", "deadlift", "ohp")) {
            val t1 = progressions.filter { it.exercise == lift && it.tier == Tier.T1.id }
            assertTrue(lift, t1.last().toWeight > t1.first().toWeight)
        }
    }

    @Test fun bodyWeightBulksThenCuts() {
        val daily = state.dailyBodyWeight()
        val june = daily.filterKeys { it.monthValue == 6 }.values.average()
        val sept = daily.filterKeys { it.monthValue == 9 && it.dayOfMonth < 18 }.values.average()
        val nov = daily.filterKeys { it.monthValue == 11 }.values.average()
        assertTrue(sept > june + 5)
        assertTrue(nov < sept)
    }

    @Test fun usesBuiltInAndCustomStretchRoutines() {
        val routines = state.stretches.values.map { it.routine }.toSet()
        assertTrue(StretchRoutines.ID_AFTER_WORKOUT in routines)
        assertTrue(StretchRoutines.popular.first().id in routines)
        assertTrue(state.customStretches.keys.any { it in routines })
        assertTrue(state.stretches.values.all { it.moves.isNotEmpty() })
    }

    @Test fun waterGoalFollowsTheDay() {
        val rest = LocalDate.of(2026, 11, 22)
        val busy = LocalDate.of(2026, 11, 18)
        assertTrue(WaterLog.recommendedMl(state, busy) > WaterLog.recommendedMl(state, rest))
    }
}
