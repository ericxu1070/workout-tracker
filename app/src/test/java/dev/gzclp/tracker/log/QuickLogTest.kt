package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

class QuickLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-27T18:48:00-08:00")
    private val file get() = File(tmp.root, "log.jsonl")

    /** [program] set up with every starting weight in and a weigh-in, so a bodyweight move has a load. */
    private fun ready(program: String = Programs.GZCLP): Store = Store(file).apply {
        dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105), program) }
        while (true) {
            val ex = Commands.missingWeight(state.value) ?: break
            dispatch(t0) { Commands.startingWeight(it, ex, 50) }
        }
        dispatch(t0) { Commands.logBodyWeight(it, null, t0, 180.0) }
    }

    private fun Store.quick(name: String, sets: Int, reps: Int, weight: Int?, at: OffsetDateTime = t0) =
        dispatch(at) { Commands.quickLog(it, name, sets, reps, weight, at) }.map { it.event }

    @Test fun aQuickLogIsAWholeSessionOfPlainSetsThatCountsForNothing() {
        val s = ready()
        val events = s.quick("Handstand press", 3, 5, null)
        val start = events.first() as SessionStart
        assertEquals("quick" to false, start.mode to start.counts)
        val sets = events.filterIsInstance<SetLogged>()
        assertEquals(listOf(1, 2, 3), sets.map { it.setIndex })
        sets.forEach {
            assertEquals("handstand_press", it.exercise)
            assertEquals(Tier.CUSTOM.id, it.tier)
            assertEquals("3x5" to 5, it.stage to it.actualReps)
            assertNull("sets logged together rest after nothing", it.restSec)
            assertNull(it.restTargetSec)
            assertNull("plain sets, not a top-sets line", it.sets)
            assertFalse(it.amrap)
            // A bodyweight move snapshots the body weight, so it is the load.
            assertEquals(0.0 to 180.0, it.addedLb to it.bodyweightLb)
        }
        val end = events.last() as SessionEnd
        assertEquals(false, end.counts)
        assertTrue(events.none { it is ProgressionEvent })

        val st = s.state.value
        assertNull(st.active)
        val record = st.sessions.last()
        assertTrue(record.quick)
        assertEquals("Quick log", record.title)
        assertEquals("it ends now, its estimated length ago", t0.minusSeconds(end.durationSec), record.startedAt)
        // Read back from the file, it is the same.
        assertEquals(st, Store(file).state.value)
        assertTrue(Store(file).state.value.sessions.last().quick)
    }

    @Test fun itCountsForStatsAtItsWeight() {
        val s = ready()
        s.quick("Barbell curl", 3, 10, 50)
        val st = s.state.value
        val curl = st.exerciseLogs().first { it.id == "barbell_curl" }
        assertEquals(50 to 10, curl.bests.last().topWeight to curl.bests.last().topReps)
        assertEquals(3, st.muscleWork(t0.toLocalDate(), t0.toLocalDate()).getValue(Muscle.BICEPS).sets)
    }

    @Test fun aNewNameIsDefinedAndBusyOrBadInputWritesNothing() {
        val s = ready()
        val events = s.quick("Wrist roller", 2, 12, null)
        val define = events.first() as ExerciseDefine
        assertEquals("wrist_roller" to "Wrist roller", define.exercise to define.name)
        assertEquals("it logs at no weight", 0, events.filterIsInstance<SetLogged>().first().weight)
        assertTrue(s.quick("Push-up", 0, 10, null).isEmpty())
        assertTrue(s.quick("Push-up", 11, 10, null).isEmpty())
        assertTrue(s.quick("Push-up", 3, 0, null).isEmpty())
        assertTrue(s.quick("  ", 3, 10, null).isEmpty())
        s.dispatch(t0) { Commands.start(it, t0) }
        assertTrue("a workout in progress would be replaced", s.quick("Push-up", 3, 10, null).isEmpty())
    }

    @Test fun twoInOneSecondGetTheirOwnSessions() {
        val s = ready()
        s.quick("Push-up", 3, 10, null)
        s.quick("Pull-up", 3, 5, null)
        val (a, b) = s.state.value.sessions.takeLast(2)
        assertNotEquals(a.id, b.id)
        assertEquals(listOf("push_up", "pull_up"), listOf(a, b).map { it.sets.first().exercise })
    }

    /** Everything a program runs on, which a quick log may never move. */
    private fun AppState.program() = listOf(gz, cycle, custom, next, fiveRm, strength, programId)

    /** A planned exercise, done far heavier than prescribed in a quick log, then deleted: the program sees neither. */
    private fun assertProgramUntouched(s: Store) {
        val before = s.state.value
        val planned = before.exercises(before.nextDay).first()
        val suggestions = before.adaptSessionStartEvents()
        val events = s.quick(planned.name, 5, 12, planned.weight!! + 100, t0.plusHours(1))
        assertTrue(events.isNotEmpty())
        val after = s.state.value
        assertEquals(before.program(), after.program())
        assertEquals(before.nextDay, after.nextDay)
        assertEquals(planned.weight, after.exercises(after.nextDay).first().weight)
        assertEquals(suggestions, after.adaptSessionStartEvents())
        assertEquals(before.strengthSuggestion(planned), after.strengthSuggestion(planned))
        assertEquals(Commands.start(before, t0.plusHours(2)), Commands.start(after, t0.plusHours(2)))
        assertEquals("the file folds to the same", before.program(), Store(file).state.value.program())
        // Deleting it from History takes back nothing, as it moved nothing.
        s.dispatch(t0.plusHours(3)) { Commands.delete(it, Deletion.Workout((events.first() as SessionStart).sessionId)) }
        assertEquals(before.program(), s.state.value.program())
        assertEquals(before.program(), Store(file).state.value.program())
    }

    @Test fun itNeverChangesGzclpState() = assertProgramUntouched(ready())

    @Test fun itNeverChangesACycleProgram() = assertProgramUntouched(ready(Programs.all.first { !it.gzclp }.id))

    @Test fun itNeverChangesACustomSplit() {
        val s = ready(Programs.all.first { !it.gzclp }.id)
        s.dispatch(t0) { Commands.copySplit(it, it.programId, "Mine") }
        s.dispatch(t0) { Commands.chooseProgram(it, it.customPrograms.keys.single()) }
        while (true) {
            val ex = Commands.missingWeight(s.state.value) ?: break
            s.dispatch(t0) { Commands.startingWeight(it, ex, 50) }
        }
        assertTrue(s.state.value.program.custom)
        assertProgramUntouched(s)
    }

    @Test fun afterAWorkoutItLeavesTheNextOneWhereItWas() {
        val s = ready()
        s.dispatch(t0) { Commands.start(it, t0) }
        val ex = s.state.value.sessionExercises().first()
        s.dispatch(t0) { Commands.logSet(it, ex, 1, ex.scheme.reps, ex.weight!!, t0, emptyMap()) }
        s.dispatch(t0) { Commands.end(it, t0, null) }
        assertProgramUntouched(s)
    }
}
