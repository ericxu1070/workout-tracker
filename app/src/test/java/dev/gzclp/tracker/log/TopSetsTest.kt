package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.ChangeKind
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

class TopSetsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-27T18:48:00-08:00")
    private val rest = Tier.entries.associateWith { it.defaultRestSec }
    private val file get() = File(tmp.root, "log.jsonl")

    /** GZCLP with every starting weight in, and a top-sets session of its first day started at [t0]. */
    private fun started(): Store = Store(file).apply {
        dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105), Programs.GZCLP) }
        while (true) {
            val ex = Commands.missingWeight(state.value) ?: break
            dispatch(t0) { Commands.startingWeight(it, ex, 50) }
        }
        dispatch(t0) { Commands.startTop(it, t0) }
    }

    private fun Store.line(ex: Exercise, weight: Int, reps: Int, sets: Int) = dispatch(t0) { Commands.logSet(it, ex, 1, reps, weight, t0, rest, sets = sets) }

    @Test fun aLineIsOneSetWithItsExercisesCountAndNoRestOrAmrap() {
        val s = started()
        assertTrue(s.state.value.active!!.top)
        val (t1, t2) = s.state.value.sessionExercises()
        s.line(t1, t1.weight!!, 3, 5)
        val e = s.line(t2, t2.weight!!, 10, 3).single().event as SetLogged
        assertEquals(3, e.sets)
        assertEquals(1, e.setIndex)
        assertNull("the line before it is not a set it rested after", e.restSec)
        assertFalse((s.state.value.active!!.sets.getValue(SetKey(t1.id, t1.tier, 1)).event).amrap)
        assertEquals("a correction replaces the line", 4, (s.line(t2, t2.weight, 9, 4).single().event as SetLogged).sets)
        assertEquals(2, s.state.value.active!!.sets.size)
    }

    @Test fun endingSavesTheEstimatedTimeAndTheTopSetStandsInForEverySet() {
        val s = started()
        val day = s.state.value.active!!.day
        val (t1, t2, t3) = s.state.value.sessionExercises()
        s.line(t1, t1.weight!!, 3, 5)
        // Lighter than prescribed: says nothing about the prescription.
        s.line(t2, t2.weight!! - 10, 10, 3)
        val events = s.dispatch(t0) { Commands.endTop(it, t0, 40 * 60) }.map { it.event }
        val up = events.filterIsInstance<ProgressionEvent>().single()
        assertEquals(t1.id to ChangeKind.WEIGHT_INCREASE.id, up.exercise to up.kind)
        assertTrue(up.toWeight > t1.weight)

        val r = s.state.value.sessions.single()
        assertTrue(r.top)
        assertEquals(t0.minusMinutes(40), r.startedAt)
        assertEquals(40 * 60L, r.durationSec)
        assertEquals(listOf(t1.id, t2.id), r.sets.map { it.exercise })
        assertEquals("the unlogged exercise stays put", t3.weight, s.state.value.exercises(day).first { it.key == t3.key }.weight)
        assertEquals(t2.weight, s.state.value.exercises(day).first { it.key == t2.key }.weight)
        assertTrue("it counts: the rotation moves on", s.state.value.nextDay.id != day.id)
        assertEquals(s.state.value, Store(file).state.value)
    }

    @Test fun aTopSetShortOfItsRepsFailsTheStage() {
        val s = started()
        val t1 = s.state.value.sessionExercises().first()
        s.line(t1, t1.weight!!, 2, 5)
        s.dispatch(t0) { Commands.endTop(it, t0, 1800) }
        val g = s.state.value.gz.getValue(t1.key)
        assertEquals(1 to t1.weight, g.stage to g.weight)
    }

    @Test fun musclesCountEverySetOfALineAndTheChartMarksIt() {
        val s = started()
        val t1 = s.state.value.sessionExercises().first()
        s.line(t1, t1.weight!!, 3, 5)
        val day = t0.toLocalDate()
        assertEquals("counted while it is open too", 5, s.state.value.muscleWork(day, day).values.maxOf { it.sets })
        s.dispatch(t0) { Commands.endTop(it, t0, 1800) }
        assertEquals(5, s.state.value.muscleWork(day.minusDays(1), day).values.maxOf { it.sets })
        val best = s.state.value.exerciseLogs().single().bests.single()
        assertTrue(best.topOnly)
        assertEquals(t1.weight to 3, best.topWeight to best.topReps)
    }

    @Test fun theEstimateGrowsWithTheSets() {
        fun line(reps: Int, sets: Int) = SetLogged("s1", "bench", "custom", "4x8", 1, reps, reps, 0, 100, false, sets = sets)
        // Four exercises, 14 sets: 2510 seconds of sets, rests and changeovers.
        assertEquals(40 * 60L, Commands.topDurationSec(listOf(line(8, 4), line(11, 4), line(10, 3), line(12, 3))))
        assertEquals(15 * 60L, Commands.topDurationSec(listOf(line(8, 4))))
        assertEquals("never under one step", 5 * 60L, Commands.topDurationSec(listOf(line(15, 1))))
        assertTrue(Commands.topDurationSec(listOf(line(8, 4), line(8, 4))) > Commands.topDurationSec(listOf(line(8, 4), line(8, 2))))
    }

    @Test fun anAddedExerciseStartsFromItsLastLine() {
        val s = started()
        val curl = Exercise("barbell_curl", "Barbell curl", Tier.CUSTOM, dev.gzclp.tracker.engine.Scheme(4, 12, false), null)
        s.line(curl, 60, 12, 4)
        assertNull("not until its session is finished", Commands.lastTop(s.state.value, curl.id))
        s.dispatch(t0) { Commands.endTop(it, t0, 1800) }
        val last = Commands.lastTop(s.state.value, curl.id)!!
        assertEquals(Triple(60, 12, 4), Triple(last.weight, last.actualReps, last.sets))
    }

    @Test fun linesRoundTripInTheDocumentedShape() {
        val set = Entry(71, t0, SetLogged("s1795834080", "bench", "custom", "4x8", 1, 8, 8, 185, 190, false, sets = 4))
        assertEquals(
            """{"v":2,"seq":71,"ts":"2026-11-27T18:48:00-08:00","type":"set","session_id":"s1795834080","exercise":"bench","tier":"custom","stage":"4x8","set_index":1,"target_reps":8,"actual_reps":8,"target_weight":185,"weight":190,"amrap":false,"sets":4}""",
            LogCodec.encode(set),
        )
        assertEquals(set, LogCodec.decode(LogCodec.encode(set)))
        val start = Entry(70, t0, SessionStart("s1795834080", "gzclp", "A1", mode = "top"))
        assertEquals(start, LogCodec.decode(LogCodec.encode(start)))
    }
}
