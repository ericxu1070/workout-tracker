package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Day
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.LiftState
import dev.gzclp.tracker.engine.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

class LogTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-10T18:00:00-08:00")
    private val rest = Tier.entries.associateWith { it.defaultRestSec }
    private val fiveRm = mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105)

    private fun store(f: File = File(tmp.root, "log.jsonl")) = Store(f)

    /** Logs every set of the active workout, using [reps] for a lift id or the target. */
    private fun Store.doWorkout(start: OffsetDateTime, reps: (Exercise, Int) -> Int?) {
        dispatch(start) { Commands.start(it, start) }
        var t = start
        for (ex in state.value.exercises(state.value.active!!.day)) for (i in 1..ex.scheme.sets) {
            val r = reps(ex, i) ?: continue
            t = t.plusSeconds(150)
            dispatch(t) { Commands.logSet(it, ex, i, r, t, rest) }
        }
        val end = t.plusSeconds(60)
        dispatch(end) { Commands.end(it, end, null) }
    }

    @Test fun everyEventTypeRoundTripsThroughJson() {
        val events = listOf(
            ProgramStart(mapOf("squat" to 225)),
            ProgressionEvent("squat", "t1", "reset", 225, 190, "10x1+", "5x3+", "s1", 225),
            T3Define("face_pull", "Face pull"),
            T3Day("A1", listOf("lat_pulldown", "face_pull")),
            SessionStart("s1", "A1"),
            SetLogged("s1", "squat", "t1", "5x3+", 5, 3, 7, 225, true, 182, 180),
            SetLogged("s1", "squat", "t1", "5x3+", 1, 3, 3, 225, false),
            SetCleared("s1", "squat", "t1", 5),
            SessionEnd("s1", "A1", "2026-11-10T18:00:00-08:00", 3120),
        )
        events.forEachIndexed { i, e ->
            val line = LogCodec.encode(Entry(i + 1L, t0, e))
            assertEquals(Entry(i + 1L, t0, e), LogCodec.decode(line))
        }
    }

    @Test fun lineShapeMatchesTheDocumentedContract() {
        val line = LogCodec.encode(Entry(7, t0, SetLogged("s1", "squat", "t1", "5x3+", 5, 3, 7, 225, true, 182, 180)))
        assertEquals(
            """{"v":1,"seq":7,"ts":"2026-11-10T18:00:00-08:00","type":"set","session_id":"s1","lift":"squat","tier":"t1","stage":"5x3+","set_index":5,"target_reps":3,"actual_reps":7,"weight":225,"amrap":true,"rest_sec":182,"rest_target_sec":180}""",
            line,
        )
        val first = LogCodec.encode(Entry(1, t0, SetLogged("s1", "squat", "t1", "5x3+", 1, 3, 3, 225, false)))
        assertTrue("first set omits rest fields", "rest_sec" !in first)
    }

    @Test fun unknownTypesAndNewerVersionsAreSkipped() {
        assertNull(LogCodec.decode("""{"v":1,"seq":1,"ts":"2026-11-10T18:00:00-08:00","type":"future_thing"}"""))
        assertNull(LogCodec.decode("""{"v":2,"seq":1,"ts":"2026-11-10T18:00:00-08:00","type":"session_start","session_id":"s","day":"A1"}"""))
        assertNotNull(LogCodec.decode("""{"v":1,"seq":1,"ts":"2026-11-10T18:00:00-08:00","type":"session_start","session_id":"s","day":"A1","extra":3}"""))
    }

    @Test fun setupDerivesStartingWeights() {
        val s = store()
        s.dispatch(t0) { Commands.setup(fiveRm) }
        val st = s.state.value
        assertEquals(LiftState(190, 0, 190), st.main[Lift.SQUAT to Tier.T1])
        assertEquals(LiftState(145, 0, 145), st.main[Lift.SQUAT to Tier.T2])
        assertEquals(LiftState(140), st.main[Lift.BENCH to Tier.T1])
        assertEquals(listOf("Squat", "Bench press", "Lat pulldown"), st.exercises(Day.A1).map { it.name })
        assertEquals(listOf("Overhead press", "Deadlift", "Dumbbell row"), st.exercises(Day.B1).map { it.name })
    }

    @Test fun fullRotationProgressesAndRebuildsIdenticallyFromTheLogAlone() {
        val f = File(tmp.root, "log.jsonl")
        val s = store(f)
        s.dispatch(t0) { Commands.setup(fiveRm) }
        // A1: everything hit, T3 last set 25 -> all go up.
        s.doWorkout(t0.plusDays(1)) { ex, i -> if (ex.tier == Tier.T3 && i == 3) 25 else ex.scheme.reps }
        // B1: OHP T1 misses a set -> 6x2+; deadlift T2 skipped entirely -> unchanged.
        s.doWorkout(t0.plusDays(3)) { ex, i -> when { ex.liftId == "deadlift" -> null; ex.liftId == "ohp" && i == 4 -> 2; else -> ex.scheme.reps } }
        val st = s.state.value
        assertEquals(LiftState(200, 0, 190), st.main[Lift.SQUAT to Tier.T1])
        assertEquals(LiftState(110, 0, 105), st.main[Lift.BENCH to Tier.T2])
        assertEquals(55, st.t3.getValue("lat_pulldown").state.weight)
        assertEquals(LiftState(90, 1, 90), st.main[Lift.OHP to Tier.T1])
        assertEquals(LiftState(180, 0, 180), st.main[Lift.DEADLIFT to Tier.T2])
        assertEquals(Day.A2, st.nextDay)
        assertEquals(2, st.sessions.size)
        assertEquals(listOf(null, 150, 150, 150, 150, 150, 150, 150), st.sessions[0].sets.take(8).map { it.restSec })
        assertEquals(180, st.sessions[0].sets[5].restTargetSec)

        assertEquals("rebuild from the log equals live state", st, Store(f).state.value)
        val copy = File(tmp.root, "restored.jsonl")
        val restored = Store(copy)
        restored.restore(f.readText())
        assertEquals("restore from the synced copy equals live state", st, restored.state.value)
    }

    @Test fun t1ResetUsesTheEnteredFiveRm() {
        val s = store()
        s.dispatch(t0) { Commands.setup(fiveRm) }
        val squat = s.state.value.exercises(Day.A1)[0]
        s.dispatch(t0) { listOf(ProgressionEvent("squat", "t1", "stage_change", 190, 190, "5x3+", "10x1+")) }
        s.dispatch(t0) { Commands.start(it, t0) }
        val ex = s.state.value.exercises(Day.A1)[0]
        assertEquals(10, ex.scheme.sets)
        for (i in 1..10) s.dispatch(t0) { Commands.logSet(it, ex, i, if (i == 10) 0 else 1, t0, rest) }
        assertEquals(squat.liftId, Commands.pendingFiveRm(s.state.value)?.liftId)
        s.dispatch(t0) { Commands.end(it, t0.plusMinutes(40), 215) }
        val st = s.state.value
        assertEquals(LiftState(185, 0, 185), st.main[Lift.SQUAT to Tier.T1])
        assertEquals(215, st.fiveRm[Lift.SQUAT])
        assertEquals(2400L, st.sessions.last().durationSec)
    }

    @Test fun relogAndClearUseLatestEvent() {
        val s = store()
        s.dispatch(t0) { Commands.setup(fiveRm) }
        s.dispatch(t0) { Commands.start(it, t0) }
        val ex = s.state.value.exercises(Day.A1)[0]
        s.dispatch(t0.plusSeconds(10)) { Commands.logSet(it, ex, 1, 3, t0.plusSeconds(10), rest) }
        s.dispatch(t0.plusSeconds(200)) { Commands.logSet(it, ex, 2, 3, t0.plusSeconds(200), rest) }
        s.dispatch(t0.plusSeconds(300)) { Commands.logSet(it, ex, 2, 2, t0.plusSeconds(300), rest) }
        assertEquals("edit keeps original rest", 190, s.state.value.active!!.sets[SetKey("squat", Tier.T1, 2)]!!.event.restSec)
        assertEquals(listOf(3, 2, null, null, null), Commands.reps(s.state.value, ex))
        s.dispatch(t0) { Commands.clearSet(it, ex, 2) }
        assertEquals(listOf(3, null, null, null, null), Commands.reps(s.state.value, ex))
    }

    @Test fun emptySessionDoesNotAdvanceRotation() {
        val s = store()
        s.dispatch(t0) { Commands.setup(fiveRm) }
        s.dispatch(t0) { Commands.start(it, t0) }
        s.dispatch(t0) { Commands.end(it, t0.plusMinutes(1), null) }
        assertEquals(Day.A1, s.state.value.nextDay)
        assertEquals(1, s.state.value.sessions.size)
    }

    @Test fun manualEditIsLoggedAndCustomT3sAreSharedByName() {
        val s = store()
        s.dispatch(t0) { Commands.setup(fiveRm) }
        val ex = s.state.value.exercises(Day.A1)[1]
        s.dispatch(t0) { Commands.editWeight(ex, 132) }
        assertEquals(LiftState(130, 0, 130), s.state.value.main[Lift.BENCH to Tier.T2])
        s.dispatch(t0) { Commands.addT3(it, Day.A1, "Face pull", 40) }
        s.dispatch(t0) { Commands.addT3(it, Day.B2, " face  PULL ", 99) }
        s.dispatch(t0) { Commands.removeT3(it, Day.B2, "dumbbell_row") }
        val st = s.state.value
        assertEquals(listOf("Lat pulldown", "Face pull"), st.exercises(Day.A1).filter { it.tier == Tier.T3 }.map { it.name })
        assertEquals(listOf("face_pull"), st.dayT3[Day.B2])
        assertEquals(40, st.t3.getValue("face_pull").state.weight)
    }

    @Test fun tornTailIsDroppedAndAppendsContinueCleanly() {
        val f = File(tmp.root, "log.jsonl")
        val s = store(f)
        s.dispatch(t0) { Commands.setup(fiveRm) }
        val good = s.state.value
        f.appendText("""{"v":1,"seq":99,"ts":"2026-11""")
        val s2 = Store(f)
        assertEquals(good, s2.state.value)
        s2.dispatch(t0) { Commands.start(it, t0) }
        assertEquals(f.readLines().size, f.readLines().count { LogCodec.decode(it) != null })
        assertNotNull(Store(f).state.value.active)
    }
}
