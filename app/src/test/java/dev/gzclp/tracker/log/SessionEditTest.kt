package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

class SessionEditTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-20T18:00:00-08:00")
    private val rest = Tier.entries.associateWith { it.defaultRestSec }
    private val file get() = File(tmp.root, "log.jsonl")

    /** A finished workout with one logged set that ran for [sec] because the timer was left going. */
    private fun finished(sec: Long): Store = Store(file).apply {
        dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105), Programs.GZCLP) }
        while (true) {
            val ex = Commands.missingWeight(state.value) ?: break
            dispatch(t0) { Commands.startingWeight(it, ex, 50) }
        }
        dispatch(t0) { Commands.start(it, t0) }
        val ex = state.value.exercises(state.value.active!!.day).first()
        val at = t0.plusMinutes(5)
        dispatch(at) { Commands.logSet(it, ex, 1, 3, ex.weight!!, at, rest) }
        val end = t0.plusSeconds(sec)
        dispatch(end) { Commands.end(it, end, null) }
    }

    @Test fun editSetsTheDurationAndSurvivesARebuild() {
        val s = finished(22 * 3600L)
        val id = s.state.value.sessions.single().id
        val rotation = s.state.value.next
        val line = s.dispatch(t0.plusDays(1)) { Commands.editSession(it, id, 3900) }.single()
        assertEquals(SessionEdit(id, 3900), line.event)
        assertEquals(3900L, s.state.value.sessions.single().durationSec)
        assertEquals("editing does not move the rotation", rotation, s.state.value.next)
        assertEquals(s.state.value, Store(file).state.value)

        s.dispatch(t0.plusDays(1)) { Commands.editSession(it, id, 4200) }
        assertEquals("newest edit wins", 4200L, Store(file).state.value.sessions.single().durationSec)
    }

    @Test fun rejectsUnknownUnchangedAndOutOfRange() {
        val s = finished(3000)
        val id = s.state.value.sessions.single().id
        val now = t0.plusDays(1)
        assertTrue(s.dispatch(now) { Commands.editSession(it, "s1", 1800) }.isEmpty())
        assertTrue(s.dispatch(now) { Commands.editSession(it, id, 3000) }.isEmpty())
        assertTrue(s.dispatch(now) { Commands.editSession(it, id, 59) }.isEmpty())
        assertTrue(s.dispatch(now) { Commands.editSession(it, id, 86_401) }.isEmpty())
        assertEquals(3000L, s.state.value.sessions.single().durationSec)
    }

    @Test fun lineRoundTripsInTheDocumentedShape() {
        val e = Entry(120, t0, SessionEdit("s1795312800", 3900))
        val line = LogCodec.encode(e)
        assertEquals("""{"v":2,"seq":120,"ts":"2026-11-20T18:00:00-08:00","type":"session_edit","session_id":"s1795312800","duration_sec":3900}""", line)
        assertEquals(e, LogCodec.decode(line))
    }
}
