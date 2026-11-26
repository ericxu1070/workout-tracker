package dev.gzclp.tracker.log

import androidx.health.connect.client.records.ExerciseSessionRecord
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.health.HealthRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

/** Deleting an exercise or a whole workout from History, on a synthetic GZCLP log. */
class HistoryDeleteTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-02T18:00:00-08:00")
    private val rest = Tier.entries.associateWith { it.defaultRestSec }
    private val file get() = File(tmp.root, "log.jsonl")
    private val squatT1 = "squat" to Tier.T1

    private fun store() = Store(file).apply {
        dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105), Programs.GZCLP) }
    }

    /** The next day in rotation, every set at its target reps, [day] days after [t0]. */
    private fun Store.workout(day: Long): String {
        val at = t0.plusDays(day)
        while (true) {
            val ex = Commands.missingWeight(state.value) ?: break
            dispatch(at) { Commands.startingWeight(it, ex, 50) }
        }
        dispatch(at) { Commands.start(it, at) }
        var t = at
        for (ex in state.value.sessionExercises()) for (i in 1..ex.scheme.sets) {
            t = t.plusMinutes(2)
            dispatch(t) { Commands.logSet(it, ex, i, ex.scheme.reps, ex.weight!!, t, rest) }
        }
        val end = t.plusMinutes(5)
        dispatch(end) { Commands.end(it, end, null) }
        return state.value.sessions.last().id
    }

    private fun Store.delete(day: Long, target: Deletion) = dispatch(t0.plusDays(day)) { Commands.delete(it, target) }

    @Test fun deletingAnExerciseFromTheLatestWorkoutTakesBackItsProgression() {
        val s = store()
        s.workout(0); s.workout(2); s.workout(4); s.workout(6)
        val prior = s.state.value.gz[squatT1]!!
        val a1 = s.workout(8)
        val after = s.state.value
        assertTrue("A1 moved squat T1 up", after.gz[squatT1]!!.weight > prior.weight)

        val lines = s.delete(9, Deletion.Exercise(a1, "squat", "t1"))
        assertEquals(after.sessions.last().sets.count { it.exercise == "squat" && it.tier == "t1" }, lines.size)
        assertTrue(lines.all { it.event is SetCleared })
        val now = s.state.value
        assertEquals("back to what it was before that workout", prior, now.gz[squatT1])
        val left = now.sessions.last().sets
        assertTrue("the workout keeps its other exercises", left.isNotEmpty() && left.none { it.exercise == "squat" && it.tier == "t1" })
        assertEquals("the rotation still counts the workout", after.next, now.next)
        assertEquals("dispatch matches a rebuild from the file", now, Store(file).state.value)
    }

    @Test fun deletingTheLatestWorkoutRewindsTheRotationAndEveryProgression() {
        val s = store()
        s.workout(0)
        val before = s.state.value
        val id = s.workout(2)
        assertTrue(before.next != s.state.value.next)

        s.delete(3, Deletion.Workout(id))
        val now = s.state.value
        assertTrue(now.sessions.single { it.id == id }.sets.isEmpty())
        assertEquals(before.next, now.next)
        // Starting weights typed for that workout's new exercises are kept.
        assertEquals(before.gz, now.gz.filterKeys { it in before.gz })
        assertEquals(before.strength, now.strength)
        assertEquals(now, Store(file).state.value)
        assertTrue("nothing left to delete", s.delete(4, Deletion.Workout(id)).isEmpty())
    }

    @Test fun deletingAnOlderWorkoutLeavesWhatLaterOnesMoved() {
        val s = store()
        val first = s.workout(0)
        s.workout(2); s.workout(4); s.workout(6)
        s.workout(8) // A1 again: squat T1 moved by a later workout
        val current = s.state.value
        s.delete(9, Deletion.Workout(first))
        val now = s.state.value
        assertEquals(current.gz[squatT1], now.gz[squatT1])
        assertEquals(current.next, now.next)
        assertEquals(now, Store(file).state.value)
    }

    @Test fun healthConnectRewritesATrimmedWorkoutAndDropsAnEmptiedOne() {
        val s = store()
        val id = s.workout(0)
        fun write(target: Deletion) = s.state.value.let { before -> HealthRecords.of(before, s.delete(1, target)) }

        val trimmed = write(Deletion.Exercise(id, "squat", "t1"))
        val record = trimmed.upserts.single() as ExerciseSessionRecord
        assertEquals(id, record.metadata.clientRecordId)
        assertEquals(s.state.value.sessions.single().sets.size, record.segments.size)
        assertTrue(trimmed.deletedSessions.isEmpty())

        val emptied = write(Deletion.Workout(id))
        assertTrue(emptied.upserts.isEmpty())
        assertEquals(listOf(id), emptied.deletedSessions)
    }

    @Test fun cardioAndWeighInsDeleteThroughTheSameCommand() {
        val s = store()
        s.dispatch(t0) { Commands.logBodyWeight(it, null, t0, 180.0) }
        val w = s.state.value.bodyWeights.keys.single()
        s.delete(1, Deletion.WeighIn(w))
        assertTrue(s.state.value.bodyWeights.isEmpty())
        assertTrue(s.delete(1, Deletion.Cardio("c1")).isEmpty())
    }
}
