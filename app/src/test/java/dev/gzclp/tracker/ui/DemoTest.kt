package dev.gzclp.tracker.ui

import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.ProgressionEvent
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.WaterLog
import dev.gzclp.tracker.log.bouts
import dev.gzclp.tracker.log.dayMix
import dev.gzclp.tracker.stretch.StretchRoutines
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/** The demo month every screenshot renders from: a valid log, the same every run, with every screen's data in it. */
class DemoTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file by lazy { File(tmp.root, "demo.jsonl").also { Demo.build(it) } }
    private val state by lazy { Store(file).state.value }
    private val days = (1..30).map { LocalDate.of(2026, 11, it) }

    @Test fun sameBytesEveryBuild() {
        assertArrayEquals(file.readBytes(), File(tmp.root, "again.jsonl").also { Demo.build(it) }.readBytes())
    }

    @Test fun readsBackFromTheLog() {
        val lines = file.readLines()
        assertEquals(lines.size, lines.mapNotNull { LogCodec.decode(it) }.size)
        assertEquals(lines.size.toLong(), state.lastSeq)
        assertEquals(Demo.state, state)
        assertTrue(lines.map { LogCodec.decode(it)!!.ts }.zipWithNext().all { (a, b) -> !b.isBefore(a) })
    }

    @Test fun fillsNovemberWithEveryKind() {
        val mix = dayMix(state.bouts())
        assertTrue(mix.keys.all { it in days })
        // Rest days, and days of one, two and three kinds.
        assertEquals(setOf(0, 1, 2, 3), days.map { mix[it].orEmpty().size }.toSet())
        assertEquals(12, state.sessions.size)
        assertNotNull(state.active)
        assertTrue(state.cardio.values.map { it.activity }.toSet().size >= 5)
        assertTrue(state.bodyWeights.size >= 25)
        assertTrue(days.all { WaterLog.totalMlForDay(state, it) > 0 })
    }

    @Test fun usesBuiltInAndCustomStretchRoutines() {
        val routines = state.stretches.values.map { it.routine }.toSet()
        assertTrue(StretchRoutines.ID_AFTER_WORKOUT in routines)
        assertTrue(StretchRoutines.popular.first().id in routines)
        assertTrue(state.customStretches.keys.any { it in routines })
        assertTrue(state.stretches.values.all { it.moves.isNotEmpty() })
    }

    @Test fun aMissedT1SetTurnsAStage() {
        val stages = file.readLines().mapNotNull { LogCodec.decode(it)?.event as? ProgressionEvent }.filter { it.kind == "stage_change" }
        assertTrue(stages.any { it.tier == Tier.T1.id })
        assertTrue(stages.any { it.tier == Tier.T2.id })
    }

    @Test fun waterGoalFollowsTheDay() {
        val rest = LocalDate.of(2026, 11, 22)
        val busy = LocalDate.of(2026, 11, 18)
        assertTrue(WaterLog.recommendedMl(state, busy) > WaterLog.recommendedMl(state, rest))
    }
}
