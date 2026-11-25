package dev.gzclp.tracker.stretch

import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.StretchRoutineDefine
import dev.gzclp.tracker.log.StretchRoutineMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

class CustomStretchTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-25T20:00:00-08:00")
    private val file get() = File(tmp.root, "log.jsonl")

    private fun Store.create(name: String, moves: List<StretchMove> = emptyList()): String =
        (dispatch(t0) { Commands.newStretchRoutine(it, name, moves) }.single().event as StretchRoutineDefine).routine

    private fun Store.routine(id: String) = state.value.customStretches.getValue(id)

    @Test fun defineEditAndDeleteReplayFromTheLog() {
        val store = Store(file)
        val id = store.create("Hips  daily ")
        assertEquals("hips_daily", id)
        store.dispatch(t0) { Commands.addStretchMove(it, id, StretchMoves.PIGEON_POSE.id) }
        store.dispatch(t0) { Commands.addStretchMove(it, id, StretchMoves.BUTTERFLY_STRETCH.id) }
        store.dispatch(t0) { Commands.addStretchMove(it, id, StretchMoves.COUCH_STRETCH.id) }
        store.dispatch(t0) { Commands.setStretchHold(it, id, 0, 60) }
        store.dispatch(t0) { Commands.moveStretchMove(it, id, 2, 0) }
        store.dispatch(t0) { Commands.removeStretchMove(it, id, 2) }
        store.dispatch(t0) { Commands.renameStretchRoutine(it, id, "Hips") }

        val expected = listOf(StretchMoves.COUCH_STRETCH.id to StretchMoves.COUCH_STRETCH.holdSec, StretchMoves.PIGEON_POSE.id to 60)
        for (s in listOf(store, Store(file))) {
            val r = s.routine(id)
            assertEquals("Hips", r.name)
            assertTrue(r.custom)
            assertEquals(expected, r.moves.map { it.id to it.holdSec })
            // A custom move keeps its catalog cue, focus and sides.
            assertTrue(r.moves[1].isUnilateral)
            assertEquals(StretchMoves.PIGEON_POSE.focus, r.moves[1].focus)
        }

        store.dispatch(t0) { Commands.deleteStretchRoutine(it, id) }
        assertTrue(Store(file).state.value.customStretches.isEmpty())
    }

    @Test fun editsThatChangeNothingWriteNothing() {
        val store = Store(file)
        val id = store.create("Mine", listOf(StretchMoves.PIGEON_POSE))
        val s = store.state.value
        assertTrue(Commands.renameStretchRoutine(s, id, " Mine ").isEmpty())
        assertTrue(Commands.renameStretchRoutine(s, id, "  ").isEmpty())
        assertTrue(Commands.setStretchHold(s, id, 0, StretchMoves.PIGEON_POSE.holdSec).isEmpty())
        assertTrue(Commands.setStretchHold(s, id, 0, 301).isEmpty())
        assertTrue(Commands.moveStretchMove(s, id, 0, 3).isEmpty())
        assertTrue(Commands.addStretchMove(s, id, "no_such_move").isEmpty())
        assertTrue(Commands.deleteStretchRoutine(s, "dance").isEmpty())
        assertTrue(Commands.newStretchRoutine(s, " ").isEmpty())
    }

    @Test fun idsNeverReuseABuiltInOrALoggedRoutine() {
        val store = Store(file)
        assertEquals("dance_2", store.create("Dance"))
        val id = store.create("Evening")
        store.dispatch(t0) { listOf(dev.gzclp.tracker.log.StretchLogged("st1", "2026-11-25T20:00:00-08:00", id, 60)) }
        store.dispatch(t0) { Commands.deleteStretchRoutine(it, id) }
        assertEquals("evening_2", store.create("Evening"))
    }

    @Test fun readerSkipsBuiltInIdsUnknownMovesAndBadHolds() {
        val line = { e: StretchRoutineDefine, seq: Long -> LogCodec.encode(Entry(seq, t0, e)) }
        val forged = StretchRoutineDefine("dance", "Not dance", listOf(StretchRoutineMove(StretchMoves.PIGEON_POSE.id, 30)))
        val mixed = StretchRoutineDefine(
            "mine", "Mine",
            listOf(StretchRoutineMove("from_a_newer_app", 30), StretchRoutineMove(StretchMoves.PIGEON_POSE.id, 0), StretchRoutineMove(StretchMoves.COBRA_POSE.id, 45)),
        )
        val text = line(mixed, 2)
        assertTrue(text, text.contains("\"type\":\"stretch_routine_define\"") && text.contains("\"hold_sec\":45"))
        file.writeText(line(forged, 1) + "\n" + text + "\n")
        val s = Store(file).state.value
        assertEquals(setOf("mine"), s.customStretches.keys)
        assertEquals(listOf(StretchMoves.COBRA_POSE.id to 45), s.customStretches.getValue("mine").moves.map { it.id to it.holdSec })
        assertEquals("Dance Mobility", StretchRoutines.of("dance", s).name)
    }

    @Test fun duplicateCopiesABuiltInsMovesAndHolds() {
        val store = Store(file)
        val dynamic = StretchRoutines.athleticsDynamic()
        val id = store.create("My dynamic", dynamic.moves)
        assertEquals(dynamic.moves.map { it.id to it.holdSec }, store.routine(id).moves.map { it.id to it.holdSec })
    }

    @Test fun customRoutinesAreListedPickedAsDefaultAndPlayed() {
        val store = Store(file)
        val id = store.create("Quick", listOf(StretchMoves.COBRA_POSE))
        store.dispatch(t0) { Commands.addStretchMove(it, id, StretchMoves.PIGEON_POSE.id) }
        store.dispatch(t0) { Commands.setStretchHold(it, id, 1, 10) }
        val s = store.state.value

        val all = StretchRoutines.all(s)
        assertEquals(8, all.size)
        assertEquals(id, all.last().key)
        assertEquals(all.size, all.map { it.key }.toSet().size)
        val routine = StretchRoutines.of(id, s)
        assertEquals("Quick", routine.name)
        // A deleted default falls back to After workout.
        assertEquals(StretchRoutines.ID_AFTER_WORKOUT, StretchRoutines.of("gone", s).id)

        val player = StretchPlayerState(routine)
        val ready = StretchPlayerState.READY_SEC
        repeat(ready + StretchMoves.COBRA_POSE.holdSec) { player.tick() }
        assertEquals(1, player.moveIndex)
        assertTrue(player.isGettingReady)
        // Pigeon is one-sided: its 10 s split 5 and 5 around the switch.
        repeat(ready) { player.tick() }
        assertEquals(5, player.secondsRemaining)
        repeat(5) { player.tick() }
        assertTrue(player.isSecondSide)
        assertEquals(StretchPlayerState.SWITCH_SEC, player.readyRemaining)
        repeat(StretchPlayerState.SWITCH_SEC + 5) { player.tick() }
        assertTrue(player.isFinished)

        val logged = player.createLoggedEvent("st9", t0)
        assertEquals(id, logged.routine)
        assertEquals(listOf(StretchMoves.COBRA_POSE.id, StretchMoves.PIGEON_POSE.id), logged.moves)
        assertFalse(store.state.value.customStretches.isEmpty())
        assertNull(s.customStretches["dance"])
    }
}
