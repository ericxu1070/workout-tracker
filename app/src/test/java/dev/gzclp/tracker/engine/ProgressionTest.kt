package dev.gzclp.tracker.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionTest {
    private fun t1(state: LiftState, reps: List<Int?>, lift: Lift = Lift.SQUAT) =
        Progression.progress(Tier.T1, state, reps, lift.increment, lift.t2ResetBump)

    private fun t2(state: LiftState, reps: List<Int?>, lift: Lift = Lift.BENCH) =
        Progression.progress(Tier.T2, state, reps, lift.increment, lift.t2ResetBump)

    private fun t3(state: LiftState, reps: List<Int?>) = Progression.progress(Tier.T3, state, reps, 5, 0)

    @Test fun startingWeightsAreRoundedPercentagesOfFiveRm() {
        assertEquals(190, Progression.t1FromFiveRm(225)) // 191.25
        assertEquals(145, Progression.t2FromFiveRm(225)) // 146.25
        assertEquals(130, Progression.t1FromFiveRm(150)) // 127.5 rounds up
        assertEquals(100, round5(97.5))
        assertEquals(95, round5(97.4))
    }

    @Test fun t1SuccessAddsLiftIncrementAtSameStage() {
        assertEquals(Outcome.Change(ChangeKind.WEIGHT_INCREASE, LiftState(235, 0, 225)), t1(LiftState(225), listOf(3, 3, 3, 3, 7)))
        assertEquals(Outcome.Change(ChangeKind.WEIGHT_INCREASE, LiftState(140, 0, 135)), t1(LiftState(135), listOf(3, 3, 3, 3, 3), Lift.BENCH))
        assertEquals(Outcome.Change(ChangeKind.WEIGHT_INCREASE, LiftState(245, 1, 225)), t1(LiftState(235, 1, 225), List(6) { 2 }))
    }

    @Test fun t1FailureWalksStagesAtSameWeight() {
        assertEquals(Outcome.Change(ChangeKind.STAGE_CHANGE, LiftState(225, 1)), t1(LiftState(225), listOf(3, 3, 3, 2, 2)))
        assertEquals(Outcome.Change(ChangeKind.STAGE_CHANGE, LiftState(225, 2)), t1(LiftState(225, 1), listOf(2, 2, 2, 2, 2, 1)))
    }

    @Test fun t1UnloggedSetCountsAsMissed() {
        assertEquals(Outcome.Change(ChangeKind.STAGE_CHANGE, LiftState(225, 1)), t1(LiftState(225), listOf(3, 3, 3, 3, null)))
    }

    @Test fun t1FailureAtTenByOneNeedsNewFiveRmAndResets() {
        assertEquals(Outcome.NeedsFiveRm, t1(LiftState(225, 2), List(10) { if (it < 9) 1 else 0 }))
        assertEquals(LiftState(190, 0, 190), Progression.t1Reset(225))
    }

    @Test fun t1TenByOneSuccessStillAddsWeight() {
        assertEquals(Outcome.Change(ChangeKind.WEIGHT_INCREASE, LiftState(235, 2, 225)), t1(LiftState(225, 2), List(10) { 1 }))
    }

    @Test fun nothingLoggedLeavesStateAlone() {
        assertEquals(Outcome.Unchanged, t1(LiftState(225), List(5) { null }))
        assertEquals(Outcome.Unchanged, t2(LiftState(100), List(3) { null }))
        assertEquals(Outcome.Unchanged, t3(LiftState(50), List(3) { null }))
    }

    @Test fun t2SuccessAndStageWalk() {
        assertEquals(Outcome.Change(ChangeKind.WEIGHT_INCREASE, LiftState(105, 0, 100)), t2(LiftState(100), listOf(10, 10, 10)))
        assertEquals(Outcome.Change(ChangeKind.WEIGHT_INCREASE, LiftState(160, 1, 120)), t2(LiftState(150, 1, 120), listOf(8, 8, 8), Lift.SQUAT))
        assertEquals(Outcome.Change(ChangeKind.STAGE_CHANGE, LiftState(130, 1, 100)), t2(LiftState(130, 0, 100), listOf(10, 10, 9)))
        assertEquals(Outcome.Change(ChangeKind.STAGE_CHANGE, LiftState(130, 2, 100)), t2(LiftState(130, 1, 100), listOf(8, 7, 6)))
    }

    @Test fun t2FailureAtThreeBySixRestartsFromCycleStartPlusBump() {
        assertEquals(Outcome.Change(ChangeKind.RESET, LiftState(115, 0, 115)), t2(LiftState(130, 2, 100), listOf(6, 5, 4)))
        assertEquals(Outcome.Change(ChangeKind.RESET, LiftState(170, 0, 170)), t2(LiftState(190, 2, 150), listOf(6, 6, 5), Lift.DEADLIFT))
    }

    @Test fun t3AddsFiveOnlyWhenLastSetHitsTwentyFive() {
        assertEquals(Outcome.Change(ChangeKind.WEIGHT_INCREASE, LiftState(55, 0, 50)), t3(LiftState(50), listOf(15, 15, 25)))
        assertEquals(Outcome.Unchanged, t3(LiftState(50), listOf(15, 15, 24)))
        assertEquals(Outcome.Unchanged, t3(LiftState(50), listOf(15, 15, null)))
    }

    @Test fun manualEditRoundsAndMovesFreshCycleStart() {
        assertEquals(LiftState(105, 0, 105), Progression.manualEdit(LiftState(100), 103))
        assertEquals(LiftState(140, 0, 100), Progression.manualEdit(LiftState(130, 0, 100), 140))
        assertEquals(LiftState(120, 1, 100), Progression.manualEdit(LiftState(130, 1, 100), 120))
    }

    @Test fun rotationAndSchemeLabels() {
        assertEquals(listOf("A1", "B1", "A2", "B2"), Programs.of(Programs.GZCLP).days.map { it.id })
        assertEquals(listOf("5x3+", "6x2+", "10x1+"), Schemes.t1.map { it.label })
        assertEquals(listOf("3x10", "3x8", "3x6"), Schemes.t2.map { it.label })
        assertEquals("3x15+", Schemes.t3[0].label)
        assertEquals(2, Schemes.stageIndex(Tier.T2, "3x6"))
    }
}
