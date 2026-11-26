package dev.gzclp.tracker.stretch

import dev.gzclp.tracker.form.Motions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StretchScheduleTest {
    private val fold = StretchMoves.STANDING_FORWARD_FOLD
    private val pacing = Pacing(2.0, 1.0, 2.0)

    private fun Cue.say() = "$atMs ${words ?: "tone"}"

    @Test fun aStillHoldSaysItsNameStartAndCountsDownItsLastThreeSeconds() {
        val s = MoveSchedule(fold.copy(holdSec = 30), null)
        assertEquals(listOf(Span(Stage.READY, 0, 0, 8000), Span(Stage.HOLD, 0, 8000, 30_000)), s.spans)
        assertEquals(listOf("0 ${fold.name}", "8000 Start", "35000 3", "36000 2", "37000 1"), s.cues.map { it.say() })
        assertNull(s.repAt(20_000))
    }

    @Test fun aOneSidedMoveSwitchesThenStartsAgainAndShortHoldsCountOnlyWhatFits() {
        val calf = StretchMoves.WALL_CALF_STRETCH.copy(holdSec = 5)
        val s = MoveSchedule(calf, null)
        assertEquals(
            listOf(Span(Stage.READY, 0, 0, 8000), Span(Stage.HOLD, 0, 8000, 3000), Span(Stage.SWITCH, 1, 11_000, 3000), Span(Stage.HOLD, 1, 14_000, 2000)),
            s.spans,
        )
        // A count never speaks over "Start": a 3-second side counts "2, 1", a 2-second one just "1".
        assertEquals(
            listOf("0 ${calf.name}", "8000 Start", "9000 2", "10000 1", "11000 Switch", "14000 Start", "15000 1"),
            s.cues.map { it.say() },
        )
    }

    @Test fun aDynamicHoldIsWholeRepsWithAToneAtEachPhaseUntilTheCount() {
        val s = MoveSchedule(fold.copy(holdSec = 20), pacing)
        assertEquals(4, s.reps(20_000))
        // Reps of 5 s from 8 s: stretch, hold at +2 s, release at +3 s. The count owns the last 3.5 s.
        assertEquals(
            listOf(
                "0 ${fold.name}", "8000 Start", "10000 tone", "11000 tone", "13000 tone", "15000 tone", "16000 tone",
                "18000 tone", "20000 tone", "21000 tone", "23000 tone", "25000 3", "26000 2", "27000 1",
            ),
            s.cues.map { it.say() },
        )
        assertEquals(RepPosition(1, 4, Phase.HOLD, 0.5f, 0.5f), s.repAt(8000 + 7500))
        assertEquals(Phase.STRETCH, s.repAt(8000)?.phase)
        assertEquals(Phase.RELEASE, s.repAt(28_000 - 1)?.phase)
        assertNull(s.repAt(7999))
    }

    @Test fun repsFitTheHoldByRoundingThenTheTempoStretchesToEndOnTime() {
        val s = MoveSchedule(fold.copy(holdSec = 20), Pacing(2.5, 2.0, 3.0))
        // 20 s holds 2.67 reps of 7.5 s: three reps of 6.67 s, so the last ends with the hold.
        assertEquals(3, s.reps(20_000))
        assertEquals(1 to Phase.RELEASE, s.repAt(8000 + 13_333)?.let { it.rep to it.phase })
        assertEquals(2 to Phase.STRETCH, s.repAt(8000 + 13_334)?.let { it.rep to it.phase })
        assertEquals(Phase.RELEASE, s.repAt(28_000 - 1)?.phase)
        assertEquals(1, s.reps(2000))
    }

    @Test fun aFlowIsOnePhaseWithAToneAtEachNewRep() {
        val s = MoveSchedule(fold.copy(holdSec = 30), Pacing(6.0, 0.0, 0.0))
        assertEquals(listOf(Phase.STRETCH to 6.0), s.pacing!!.phases)
        assertEquals(listOf(14_000L, 20_000L, 26_000L, 32_000L), s.cues.filter { it.words == null }.map { it.atMs })
    }

    @Test fun pacingComesFromTheMoveMotionSoTheFigureKeepsStep() {
        // A still stretch's slow tempo plays brisker as reps; a drill keeps its own; the catalog can set a move's outright.
        assertEquals(Pacing.DYNAMIC, Motions.pacing(StretchMoves.CAT_COW_STRETCH))
        assertEquals(Pacing(1.6, 0.3, 2.1), Motions.pacing(StretchMoves.BAND_DISLOCATES))
        assertEquals(Pacing(6.0, 0.0, 0.0), Motions.pacing(StretchMoves.MOUNTAIN_CLIMBERS))
        assertEquals(Pacing(2.0, 3.0, 2.0), Motions.pacing(StretchMoves.RFE_HIP_FLEXOR))
        // Athletics' 10 s sides fit more than one rep.
        assertTrue(MoveSchedule(StretchMoves.LIZARD_LUNGE.copy(holdSec = 20), Pacing.DYNAMIC).reps(10_000) > 1)

        // Through a whole dynamic move, the figure is at the far end whenever the timer says hold, and at the start as each rep begins.
        val move = StretchMoves.CAT_COW_STRETCH.copy(holdSec = 20, isDynamic = true)
        val player = StretchPlayerState(StretchRoutine("t", "T", "", "", listOf(move, fold)))
        val tempo = Motions.of(move).tempo
        var holds = 0
        var lastRep = -1
        while (player.moveIndex == 0) {
            val rep = player.rep
            if (rep != null && rep.phase == Phase.HOLD) {
                assertEquals(1.0, tempo.at(player.motionSec), 1e-6)
                holds++
            }
            if (rep != null && rep.rep != lastRep) {
                assertTrue("rep ${rep.rep} opens near the start", tempo.at(player.motionSec) < 0.01)
                lastRep = rep.rep
            }
            player.advance(10)
        }
        assertEquals(4, lastRep)
        assertTrue(holds > 0)

        // The figure holds at the far end for the catalog's longer hold too, though its motion pauses there only 2 s.
        val flexor = StretchMoves.RFE_HIP_FLEXOR.copy(holdSec = 40, isDynamic = true)
        val p2 = StretchPlayerState(StretchRoutine("t", "T", "", "", listOf(flexor)))
        val t2 = Motions.of(flexor).tempo
        var held = 0
        while (!p2.isFinished) {
            if (p2.rep?.phase == Phase.HOLD) { assertEquals(1.0, t2.at(p2.motionSec), 1e-6); held++ }
            p2.advance(10)
        }
        assertTrue(held > 0)
    }

    @Test fun thePlayerSpeaksEachCueOnceAndTheNextNameOnlyAsItsMoveBegins() {
        val routine = StretchRoutine("t", "T", "", "", listOf(fold.copy(holdSec = 4), StretchMoves.BUTTERFLY_STRETCH.copy(holdSec = 2)))
        val expected = listOf(fold.name, "Start", "3", "2", "1", StretchMoves.BUTTERFLY_STRETCH.name, "Start", "1")
        for (step in listOf(17L, 100L, 1000L)) {
            val player = StretchPlayerState(routine)
            val said = mutableListOf<String?>()
            while (!player.isFinished) {
                said += player.drainCues().map { it.words }
                player.advance(step)
            }
            said += player.drainCues().map { it.words }
            assertEquals("in steps of $step ms", expected, said)
        }
    }

    @Test fun skippingCuesTheNextMoveName() {
        val player = StretchPlayerState(StretchRoutine("t", "T", "", "", listOf(fold, StretchMoves.BUTTERFLY_STRETCH)))
        player.drainCues()
        player.skipNext()
        assertEquals(listOf(StretchMoves.BUTTERFLY_STRETCH.name), player.drainCues().map { it.words })
    }
}
