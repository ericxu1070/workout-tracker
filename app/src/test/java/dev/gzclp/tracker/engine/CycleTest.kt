package dev.gzclp.tracker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CycleTest {
    private val full = listOf(12, 12, 12)

    @Test fun weeksAreLightToHeavy() {
        assertEquals(listOf("3x12", "3x10", "3x8"), Cycle.weeks.map { it.label })
        val s = CycleState(100)
        assertEquals(listOf(100, 105, 110), (0..2).map { Cycle.weight(s.copy(week = it), 5) })
        assertEquals(listOf(100, 110, 120), (0..2).map { Cycle.weight(s.copy(week = it), 10) })
    }

    @Test fun baseIsSixtyFivePercentOfFiveRmRoundedToFive() {
        assertEquals(145, Cycle.baseFromFiveRm(225)) // 146.25
        assertEquals(160, Cycle.baseFromFiveRm(245)) // 159.25
    }

    @Test fun nothingLoggedKeepsTheWeek() {
        assertNull(Cycle.advance(CycleState(100, 0), listOf(null, null, null), 5))
        assertNull(Cycle.advance(CycleState(100, 2), listOf(null, null, null), 5))
    }

    @Test fun weekOneAndTwoAdvanceWhenDoneWhateverTheReps() {
        assertEquals(ChangeKind.WEEK_ADVANCE to CycleState(100, 1), Cycle.advance(CycleState(100, 0), full, 5))
        assertEquals(ChangeKind.WEEK_ADVANCE to CycleState(100, 1), Cycle.advance(CycleState(100, 0), listOf(9, null, null), 5))
        assertEquals(ChangeKind.WEEK_ADVANCE to CycleState(100, 2), Cycle.advance(CycleState(100, 1), listOf(10, 10, 7), 5))
    }

    @Test fun heavyWeekAtEightOrMoreStartsTheNextCycleHeavier() {
        assertEquals(ChangeKind.CYCLE_UP to CycleState(105, 0), Cycle.advance(CycleState(100, 2), listOf(8, 8, 8), 5))
        assertEquals(ChangeKind.CYCLE_UP to CycleState(110, 0), Cycle.advance(CycleState(100, 2), listOf(10, 9, 8), 10))
    }

    @Test fun heavyWeekShortRepeatsTheCycleAtTheSameBase() {
        assertEquals(ChangeKind.CYCLE_REPEAT to CycleState(100, 0), Cycle.advance(CycleState(100, 2), listOf(8, 8, 7), 5))
        assertEquals(ChangeKind.CYCLE_REPEAT to CycleState(100, 0), Cycle.advance(CycleState(100, 2), listOf(8, 8, null), 5))
    }

    @Test fun manualEditMovesTheBaseUnderThisWeek() {
        assertEquals(CycleState(120, 2), Cycle.manualEdit(CycleState(100, 2), 141, 10)) // 140 this week
        assertEquals(CycleState(95, 0), Cycle.manualEdit(CycleState(100, 0), 96, 5))
        assertEquals(CycleState(0, 2), Cycle.manualEdit(CycleState(100, 2), 5, 10))
    }

    @Test fun everyPresetDayHasThreeToSixKnownExercises() {
        val known = Catalog.all.map { it.id }.toSet()
        assertEquals(listOf(4, 5, 6, 4, 3, 3, 3), Programs.all.map { it.days.size })
        for (p in Programs.all) for (d in p.days) {
            assert(d.slots.size in 3..6) { "${p.id}/${d.id} has ${d.slots.size}" }
            assert(d.slots.all { it.ex in known }) { "${p.id}/${d.id} names an unknown exercise" }
            assert(d.slots.all { (it.tier == Tier.CYCLE) != p.gzclp }) { "${p.id} mixes engines" }
        }
    }

    @Test fun aCustomWaveUsesItsOwnHeavyWeekReps() {
        val weeks = Wave(5, listOf(8, 6, 5)).weeks
        assertEquals(listOf("5x8", "5x6", "5x5"), weeks.map { it.label })
        assertEquals("5x8,6,5", Wave(5, listOf(8, 6, 5)).key)
        assertEquals(ChangeKind.WEEK_ADVANCE to CycleState(100, 1), Cycle.advance(CycleState(100, 0), listOf(3, null, null, null, null), 5, weeks))
        assertEquals(ChangeKind.CYCLE_UP to CycleState(105), Cycle.advance(CycleState(100, 2), List(5) { 5 }, 5, weeks))
        assertEquals(ChangeKind.CYCLE_REPEAT to CycleState(100), Cycle.advance(CycleState(100, 2), listOf(5, 5, 5, 5, 4), 5, weeks))
        assertEquals("a set not logged misses", ChangeKind.CYCLE_REPEAT to CycleState(100), Cycle.advance(CycleState(100, 2), listOf(5, 5, 5, 5), 5, weeks))
        assertEquals(Wave.DEFAULT.weeks, Cycle.weeks)
    }
}
