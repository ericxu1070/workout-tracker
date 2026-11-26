package dev.gzclp.tracker.stretch

import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.StretchRoutineDefine
import dev.gzclp.tracker.log.StretchRoutineMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

class StretchLibraryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-25T20:00:00-08:00")
    private val builtIns = listOf(StretchRoutines.afterWorkout()) + StretchRoutines.popular + StretchRoutines.more

    @Test fun afterWorkoutLeadsThenAFewPopularThenTheLibrary() {
        assertTrue(StretchRoutines.popular.size in 3..5)
        assertTrue(StretchRoutines.more.size > StretchRoutines.popular.size)
        assertEquals(builtIns.map { it.name }, StretchRoutines.all().map { it.name })
        assertEquals(StretchRoutines.ID_AFTER_WORKOUT, StretchRoutines.all().first().id)
    }

    @Test fun everyBuiltInSaysWhatItIsForAndWhenToPickIt() {
        for (r in builtIns) {
            assertTrue(r.name, r.description.isNotBlank() && r.goal.isNotBlank() && r.pick.isNotBlank())
            assertFalse(r.name, r.custom)
        }
    }

    @Test fun everyMoveIsInTheCatalogWithItsOwnMotionAndEveryHoldFitsTheEditor() {
        for (r in builtIns) {
            assertTrue(r.name, r.moves.isNotEmpty())
            for (m in r.moves) {
                assertTrue("${r.name}: ${m.id}", StretchMoves.of(m.id) != null)
                assertTrue("${r.name}: ${m.id} has no motion", Motions.known(m.id))
                assertTrue("${r.name}: ${m.id} ${m.holdSec}", m.holdSec in StretchRoutines.HOLD_SEC && m.holdSec % StretchRoutines.HOLD_STEP == 0)
            }
        }
    }

    @Test fun everyLibraryIdOpensItsOwnRoutine() {
        for (r in StretchRoutines.popular + StretchRoutines.more) {
            assertTrue(r.id, r.id in StretchRoutines.IDS)
            if (r.id != StretchRoutines.ID_FLEXIBILITY) assertEquals(r.name, StretchRoutines.of(r.id).name)
        }
    }

    @Test fun aCustomRoutineNeverCollidesWithALibraryId() {
        val store = Store(File(tmp.root, "log.jsonl"))
        // A routine defined before the library existed, under the id a library name would make.
        store.dispatch(t0) { listOf(StretchRoutineDefine("runner_s_cooldown", "Runner's cooldown", listOf(StretchRoutineMove(StretchMoves.PIGEON_POSE.id, 60)))) }
        val created = (store.dispatch(t0) { Commands.newStretchRoutine(it, "Desk reset") }.single().event as StretchRoutineDefine).routine
        val state = Store(File(tmp.root, "log.jsonl")).state.value
        assertEquals(setOf("runner_s_cooldown", created), state.customStretches.keys)
        for (id in state.customStretches.keys) {
            assertFalse(id, id in StretchRoutines.IDS)
            assertTrue(StretchRoutines.of(id, state).custom)
        }
    }
}
