package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.CycleState
import dev.gzclp.tracker.engine.Increments
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.LiftState
import dev.gzclp.tracker.engine.Programs
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
    private val gzclp = Programs.of(Programs.GZCLP)

    private fun store(f: File = File(tmp.root, "log.jsonl"), program: String = Programs.GZCLP) =
        Store(f).apply { dispatch(t0) { Commands.setup(fiveRm, program) } }

    /** Gives every exercise of the next day without a weight [startWeight]. */
    private fun Store.fillWeights(startWeight: Int = 50) {
        while (true) {
            val ex = Commands.missingWeight(state.value) ?: return
            dispatch(t0) { Commands.startingWeight(it, ex, startWeight) }
        }
    }

    /** Logs every set of the next workout at its prescribed weight, with [reps] per set or the target; null skips a set. */
    private fun Store.doWorkout(start: OffsetDateTime, reps: (Exercise, Int) -> Int? = { ex, _ -> ex.scheme.reps }) {
        fillWeights()
        dispatch(start) { Commands.start(it, start) }
        var t = start
        val a = state.value.active!!
        for (ex in state.value.exercises(a.program, a.day)) for (i in 1..ex.scheme.sets) {
            val r = reps(ex, i) ?: continue
            t = t.plusSeconds(150)
            dispatch(t) { Commands.logSet(it, ex, i, r, ex.weight!!, t, rest) }
        }
        val end = t.plusSeconds(60)
        dispatch(end) { Commands.end(it, end, null) }
    }

    @Test fun everyEventTypeRoundTripsThroughJson() {
        val events = listOf(
            ProgramStart(mapOf("squat" to 225), "ppl"),
            ProgramChosen("bro"),
            IncrementsSet(5, 10, 5),
            ExerciseDefine("face_pull", "Face pull", false),
            DayExercises("ppl", "push_a", listOf("bench", "face_pull")),
            ProgressionEvent("squat", "t1", "reset", 225, 190, "10x1+", "5x3+", "s1", 225),
            ProgressionEvent("squat", "cycle", "cycle_up", 180, 165, "3x8", "3x12", "s1", cycleWeek = 1, cycleBase = 165),
            SessionStart("s1", "gzclp", "A1"),
            SetLogged("s1", "squat", "t1", "5x3+", 5, 3, 7, 225, 230, true, 182, 180),
            SetLogged("s1", "squat", "t1", "5x3+", 4, 3, 3, 225, 225, false, 150, 180, 8.5),
            SetCleared("s1", "squat", "t1", 5),
            ExerciseSwap("s1", "squat", "t1", "leg_press"),
            ExerciseSwap("s1", "squat", "t1"),
            SessionEnd("s1", "gzclp", "A1", "2026-11-10T18:00:00-08:00", 3120),
            BodyWeightLogged("w1794326400", "2026-11-10T07:02:00-08:00", 182.4),
            BodyWeightCleared("w1794326400"),
        )
        events.forEachIndexed { i, e ->
            val line = LogCodec.encode(Entry(i + 1L, t0, e))
            assertEquals(Entry(i + 1L, t0, e), LogCodec.decode(line))
        }
    }

    @Test fun lineShapeMatchesTheDocumentedContract() {
        val line = LogCodec.encode(Entry(7, t0, SetLogged("s1", "squat", "t1", "5x3+", 5, 3, 7, 225, 230, true, 182, 180)))
        assertEquals(
            """{"v":2,"seq":7,"ts":"2026-11-10T18:00:00-08:00","type":"set","session_id":"s1","exercise":"squat","tier":"t1","stage":"5x3+","set_index":5,"target_reps":3,"actual_reps":7,"target_weight":225,"weight":230,"amrap":true,"rest_sec":182,"rest_target_sec":180}""",
            line,
        )
        val first = LogCodec.encode(Entry(1, t0, SetLogged("s1", "squat", "t1", "5x3+", 1, 3, 3, 225, 225, false)))
        assertTrue("first set omits rest fields", "rest_sec" !in first)
        assertTrue("a set without RPE omits it", "rpe" !in first)
        assertTrue(LogCodec.encode(Entry(2, t0, SetLogged("s1", "squat", "t1", "5x3+", 2, 3, 3, 225, 225, false, rpe = 9.0))).endsWith(",\"rpe\":9.0}"))
    }

    @Test fun unknownTypesAndOtherVersionsAreSkipped() {
        assertNull(LogCodec.decode("""{"v":2,"seq":1,"ts":"2026-11-10T18:00:00-08:00","type":"future_thing"}"""))
        assertNull(LogCodec.decode("""{"v":3,"seq":1,"ts":"2026-11-10T18:00:00-08:00","type":"program","program":"bro"}"""))
        assertNull(LogCodec.decode("""{"v":1,"seq":1,"ts":"2026-11-10T18:00:00-08:00","type":"session_start","session_id":"s","day":"A1"}"""))
        assertNotNull(LogCodec.decode("""{"v":2,"seq":1,"ts":"2026-11-10T18:00:00-08:00","type":"program","program":"bro","extra":3}"""))
    }

    @Test fun setupDerivesGzclpAndCycleStartingWeights() {
        val st = store().state.value
        assertEquals(LiftState(190, 0, 190), st.gz["squat" to Tier.T1])
        assertEquals(LiftState(145, 0, 145), st.gz["squat" to Tier.T2])
        assertEquals(CycleState(145), st.cycle["squat"])
        assertEquals(CycleState(70), st.cycle["ohp"]) // 68.25
        assertEquals(listOf("Squat", "Bench press", "Lat pulldown"), st.exercises(gzclp.days[0]).map { it.name })
        assertEquals("T3s ask for a starting weight", "lat_pulldown", Commands.missingWeight(st)?.id)
        assertTrue("cannot start with a weight missing", Commands.start(st, t0).isEmpty())
    }

    @Test fun gzclpRotationProgressesAndRebuildsIdenticallyFromTheLogAlone() {
        val f = File(tmp.root, "log.jsonl")
        val s = store(f)
        // A1: everything hit, T3 last set 25 -> all go up.
        s.doWorkout(t0.plusDays(1)) { ex, i -> if (ex.tier == Tier.T3 && i == 3) 25 else ex.scheme.reps }
        // B1: OHP T1 misses a set -> 6x2+; deadlift T2 skipped entirely -> unchanged.
        s.doWorkout(t0.plusDays(3)) { ex, i -> when { ex.id == "deadlift" -> null; ex.id == "ohp" && i == 4 -> 2; else -> ex.scheme.reps } }
        val st = s.state.value
        assertEquals(LiftState(200, 0, 190), st.gz["squat" to Tier.T1])
        assertEquals(LiftState(110, 0, 105), st.gz["bench" to Tier.T2])
        assertEquals(55, st.gz.getValue("lat_pulldown" to Tier.T3).weight)
        assertEquals(LiftState(90, 1, 90), st.gz["ohp" to Tier.T1])
        assertEquals(LiftState(180, 0, 180), st.gz["deadlift" to Tier.T2])
        assertEquals("A2", st.nextDay.id)
        assertEquals(2, st.sessions.size)
        assertEquals(listOf(null, 150, 150, 150, 150, 150, 150, 150), st.sessions[0].sets.take(8).map { it.restSec })
        assertEquals(180, st.sessions[0].sets[5].restTargetSec)

        assertEquals("rebuild from the log equals live state", st, Store(f).state.value)
        val restored = Store(File(tmp.root, "restored.jsonl"))
        restored.restore(f.readText())
        assertEquals("restore from the synced copy equals live state", st, restored.state.value)
    }

    @Test fun cycleProgramWalksTheWeeksAndBumpsTheBase() {
        val s = store(program = "chest_back_legs")
        assertEquals(listOf("bench", "incline_db_press", "chest_fly", "tricep_pushdown"), s.state.value.exercises(s.state.value.nextDay).map { it.id })
        val bench = { s.state.value.exercise(dev.gzclp.tracker.engine.Slot("bench", Tier.CYCLE)) }
        assertEquals(listOf(3, 12, 105, 0), bench().let { listOf(it.scheme.sets, it.scheme.reps, it.weight, it.week) })
        repeat(3) { k -> s.doWorkout(t0.plusDays(1L + k * 3)) } // chest, back, legs: week 1 done
        assertEquals(listOf(10, 110, 1), bench().let { listOf(it.scheme.reps, it.weight, it.week) })
        repeat(3) { k -> s.doWorkout(t0.plusDays(10L + k * 3)) }
        assertEquals(listOf(8, 115, 2), bench().let { listOf(it.scheme.reps, it.weight, it.week) })
        assertEquals("squat steps 10 lb", 165, s.state.value.exercise(dev.gzclp.tracker.engine.Slot("squat", Tier.CYCLE)).weight)
        // Heavy week: bench all 8s -> next cycle at 105 + 5; incline misses one -> repeats at its base.
        s.doWorkout(t0.plusDays(20)) { ex, i -> if (ex.id == "incline_db_press" && i == 3) 7 else ex.scheme.reps }
        val st = s.state.value
        assertEquals(CycleState(110, 0), st.cycle["bench"])
        assertEquals(CycleState(50, 0), st.cycle["incline_db_press"])
        val kinds = s.bytes().decodeToString().lines().mapNotNull { LogCodec.decode(it)?.event as? ProgressionEvent }.filter { it.sessionId != null && it.exercise in setOf("bench", "incline_db_press") }.map { it.kind }
        assertEquals(listOf("week_advance", "week_advance", "week_advance", "week_advance", "cycle_up", "cycle_repeat"), kinds)
    }

    @Test fun switchingProgramsKeepsHistoryAndEachExercisesCycle() {
        val s = store(program = "full_body")
        s.doWorkout(t0.plusDays(1)) // full body A: squat, bench, row, face pull -> week 2
        s.dispatch(t0) { Commands.chooseProgram(it, "upper_lower") }
        val st = s.state.value
        assertEquals("upper_a", st.nextDay.id)
        assertEquals(1, st.exercise(dev.gzclp.tracker.engine.Slot("bench", Tier.CYCLE)).week)
        assertEquals(1, st.sessions.size)
        s.dispatch(t0) { Commands.chooseProgram(it, "full_body") }
        assertEquals("rotation resumes where it was", "b", s.state.value.nextDay.id)
    }

    @Test fun editedIncrementsChangeTheSteps() {
        val s = store(program = "bro")
        s.dispatch(t0) { Commands.setIncrements(it, Increments(upperStep = 10, lowerStep = 20, cycleBump = 10)) }
        s.dispatch(t0) { listOf(ProgressionEvent("bench", "cycle", "week_advance", toWeight = 0, toStage = "3x10", cycleWeek = 2, cycleBase = 105)) }
        assertEquals(115, s.state.value.exercise(dev.gzclp.tracker.engine.Slot("bench", Tier.CYCLE)).weight)
    }

    @Test fun perSetWeightIsLoggedAndPrefillsTheNextSet() {
        val s = store(program = "bro")
        s.fillWeights()
        s.dispatch(t0) { Commands.start(it, t0) }
        val bench = s.state.value.exercises(s.state.value.nextDay)[0]
        assertEquals(105, Commands.setWeight(s.state.value, bench, 1))
        s.dispatch(t0) { Commands.logSet(it, bench, 1, 12, 115, t0, rest) }
        assertEquals("next set starts from the last logged weight", 115, Commands.setWeight(s.state.value, bench, 2))
        val logged = s.state.value.active!!.sets.getValue(SetKey("bench", Tier.CYCLE, 1)).event
        assertEquals(listOf(105, 115), listOf(logged.targetWeight, logged.weight))
        assertTrue("re-logging the same values writes nothing", Commands.logSet(s.state.value, bench, 1, 12, 115, t0.plusSeconds(9), rest).isEmpty())
    }

    @Test fun rpeIsOptionalRoundedToAHalfAndLeavesProgressionAlone() {
        val plain = store(File(tmp.root, "a.jsonl"))
        val rated = store(File(tmp.root, "b.jsonl"))
        for (s in listOf(plain, rated)) {
            s.fillWeights()
            s.dispatch(t0) { Commands.start(it, t0) }
        }
        val ex = rated.state.value.exercises(gzclp.days[0])[0]
        val rpes = listOf(7.0, 8.4, 0.0, 11.0, null)
        for (i in 1..5) {
            plain.dispatch(t0) { Commands.logSet(it, ex, i, 3, 190, t0, rest) }
            rated.dispatch(t0) { Commands.logSet(it, ex, i, 3, 190, t0, rest, rpes[i - 1]) }
        }
        val sets = rated.state.value.active!!.sets
        assertEquals(listOf(7.0, 8.5, null, null, null), (1..5).map { sets.getValue(SetKey("squat", Tier.T1, it)).event.rpe })
        rated.dispatch(t0) { Commands.logSet(it, ex, 1, 3, 190, t0, rest, 9.5) }
        assertEquals("a correction can add or change RPE", 9.5, rated.state.value.active!!.sets.getValue(SetKey("squat", Tier.T1, 1)).event.rpe)
        for (s in listOf(plain, rated)) s.dispatch(t0) { Commands.end(it, t0.plusMinutes(30), null) }
        assertEquals(plain.state.value.gz, rated.state.value.gz)
        assertEquals(plain.state.value.cycle, rated.state.value.cycle)
    }

    @Test fun upcomingProjectsTheRotationAssumingEveryTargetIsHit() {
        val s = store(program = "chest_back_legs")
        s.fillWeights()
        val up = Commands.upcoming(s.state.value, 8)
        assertEquals(listOf("chest", "back", "legs", "chest", "back", "legs", "chest", "back"), up.map { it.first.id })
        assertEquals(listOf(105 to "3x12", 110 to "3x10", 115 to "3x8"), listOf(0, 3, 6).map { up[it].second[0].let { e -> e.weight to e.scheme.label } })
        val g = store(File(tmp.root, "g.jsonl"))
        g.fillWeights()
        val gup = Commands.upcoming(g.state.value, 5)
        assertEquals(listOf("A1", "B1", "A2", "B2", "A1"), gup.map { it.first.id })
        assertEquals("squat T1 +10 after one success", listOf(190, 200), listOf(gup[0].second[0].weight, gup[4].second[0].weight))
    }

    @Test fun t1ResetUsesTheEnteredFiveRm() {
        val s = store()
        s.fillWeights()
        s.dispatch(t0) { listOf(ProgressionEvent("squat", "t1", "stage_change", 190, 190, "5x3+", "10x1+")) }
        s.dispatch(t0) { Commands.start(it, t0) }
        val ex = s.state.value.exercises(gzclp.days[0])[0]
        assertEquals(10, ex.scheme.sets)
        for (i in 1..10) s.dispatch(t0) { Commands.logSet(it, ex, i, if (i == 10) 0 else 1, 190, t0, rest) }
        assertEquals("squat", Commands.pendingFiveRm(s.state.value)?.id)
        s.dispatch(t0) { Commands.end(it, t0.plusMinutes(40), 215) }
        val st = s.state.value
        assertEquals(LiftState(185, 0, 185), st.gz["squat" to Tier.T1])
        assertEquals(215, st.fiveRm["squat"])
        assertEquals(2400L, st.sessions.last().durationSec)
    }

    @Test fun relogAndClearUseLatestEvent() {
        val s = store()
        s.fillWeights()
        s.dispatch(t0) { Commands.start(it, t0) }
        val ex = s.state.value.exercises(gzclp.days[0])[0]
        s.dispatch(t0.plusSeconds(10)) { Commands.logSet(it, ex, 1, 3, 190, t0.plusSeconds(10), rest) }
        s.dispatch(t0.plusSeconds(200)) { Commands.logSet(it, ex, 2, 3, 190, t0.plusSeconds(200), rest) }
        s.dispatch(t0.plusSeconds(300)) { Commands.logSet(it, ex, 2, 2, 190, t0.plusSeconds(300), rest) }
        assertEquals("edit keeps original rest", 190, s.state.value.active!!.sets[SetKey("squat", Tier.T1, 2)]!!.event.restSec)
        assertEquals(listOf(3, 2, null, null, null), Commands.reps(s.state.value, ex))
        s.dispatch(t0) { Commands.clearSet(it, ex, 2) }
        assertEquals(listOf(3, null, null, null, null), Commands.reps(s.state.value, ex))
    }

    @Test fun aSwapLastsOneSessionAndLeavesTheSlotsLiftAlone() {
        val f = File(tmp.root, "log.jsonl")
        val s = store(f)
        s.fillWeights()
        s.dispatch(t0) { Commands.addExercise(it, gzclp, gzclp.days[0], "Face pull") }
        s.fillWeights()
        val before = s.state.value
        s.dispatch(t0) { Commands.start(it, t0) }
        val (squat, _, lat) = s.state.value.sessionExercises()
        assertTrue("another exercise of this session at that tier", Commands.swap(s.state.value, lat, "Face pull").isEmpty())
        s.dispatch(t0) { Commands.swap(it, squat, "Hack squat") }
        val hack = s.state.value.sessionExercises().first()
        assertEquals(listOf("hack_squat", "squat", "Hack squat"), listOf(hack.id, hack.swappedFrom, hack.name))
        assertEquals(squat.scheme to Tier.T1, hack.scheme to hack.tier)
        assertNull("never logged, so no suggestion", hack.weight)
        assertTrue("a substitute has no prescription to edit", Commands.editWeight(s.state.value, hack, 300).isEmpty())
        // Every set short: this would stall squat if it counted for squat.
        for (i in 1..hack.scheme.sets) s.dispatch(t0.plusMinutes(i.toLong())) { Commands.logSet(it, hack, i, 1, 200, t0.plusMinutes(i.toLong()), rest) }
        assertTrue("no swapping back once sets are logged", Commands.swap(s.state.value, hack, "Squat").isEmpty())
        assertNull("only the 10x1+ failure asks for a 5RM", Commands.pendingFiveRm(s.state.value))
        val end = t0.plusHours(1)
        s.dispatch(end) { Commands.end(it, end, null) }

        val st = s.state.value
        assertEquals(before.gz["squat" to Tier.T1], st.gz["squat" to Tier.T1])
        assertNull(st.gz["hack_squat" to Tier.T1])
        val lines = f.readLines().mapNotNull { LogCodec.decode(it)?.event }
        assertTrue(lines.filterIsInstance<ProgressionEvent>().none { it.sessionId != null && it.exercise in setOf("squat", "hack_squat") })
        assertEquals(setOf("hack_squat"), st.sessions.last().sets.filter { it.tier == "t1" }.map { it.exercise }.toSet())
        assertEquals("B1", st.nextDay.id)

        // The next session goes back to the original lift.
        s.fillWeights()
        s.dispatch(end) { Commands.start(it, end) }
        assertEquals(listOf("ohp", "deadlift"), s.state.value.sessionExercises().take(2).map { it.id })
        assertTrue(s.state.value.sessionExercises().none { it.swappedFrom != null })
        assertEquals("rebuild from the log equals live state", s.state.value, Store(f).state.value)
    }

    @Test fun aSwapCanBeUndoneAndSuggestsTheLastWeight() {
        val s = store()
        s.doWorkout(t0.plusDays(1)) // logs lat_pulldown at 50
        s.doWorkout(t0.plusDays(2))
        s.fillWeights()
        s.dispatch(t0.plusDays(3)) { Commands.start(it, t0.plusDays(3)) }
        val t1 = s.state.value.sessionExercises().first()
        s.dispatch(t0) { Commands.swap(it, t1, "lat pulldown") }
        val sub = s.state.value.sessionExercises().first()
        assertEquals("lat_pulldown" to 50, sub.id to sub.weight)
        assertTrue("same swap twice writes nothing", Commands.swap(s.state.value, sub, "Lat pulldown").isEmpty())
        s.dispatch(t0) { Commands.swap(it, sub, "Bench press") }
        assertEquals(t1, s.state.value.sessionExercises().first())
    }

    @Test fun emptySessionDoesNotAdvanceRotation() {
        val s = store()
        s.fillWeights()
        s.dispatch(t0) { Commands.start(it, t0) }
        s.dispatch(t0) { Commands.end(it, t0.plusMinutes(1), null) }
        assertEquals("A1", s.state.value.nextDay.id)
        assertEquals(1, s.state.value.sessions.size)
    }

    @Test fun manualEditsAndCustomExercisesSharedByName() {
        val s = store()
        val ex = s.state.value.exercises(gzclp.days[0])[1]
        s.dispatch(t0) { Commands.editWeight(it, ex, 132) }
        assertEquals(LiftState(130, 0, 130), s.state.value.gz["bench" to Tier.T2])
        s.dispatch(t0) { Commands.addExercise(it, gzclp, gzclp.days[0], "Face pull") }
        s.dispatch(t0) { Commands.addExercise(it, gzclp, gzclp.days[3], "Band  PULL apart ") }
        s.dispatch(t0) { Commands.addExercise(it, gzclp, gzclp.days[1], " band pull APART") }
        s.dispatch(t0) { Commands.removeExercise(it, gzclp, gzclp.days[3], "dumbbell_row") }
        val st = s.state.value
        assertEquals(listOf("Lat pulldown", "Face pull"), st.exercises(gzclp.days[0]).filter { it.tier == Tier.T3 }.map { it.name })
        assertEquals(listOf("band_pull_apart"), Commands.dayList(st, gzclp, gzclp.days[3]))
        assertEquals(listOf("dumbbell_row", "band_pull_apart"), Commands.dayList(st, gzclp, gzclp.days[1]))
        val bro = Programs.of("bro")
        s.dispatch(t0) { Commands.removeExercise(it, bro, bro.days[0], "bench") }
        assertEquals(listOf("incline_db_press", "chest_fly"), Commands.dayList(s.state.value, bro, bro.days[0]))
    }

    @Test fun tornTailIsDroppedAndAppendsContinueCleanly() {
        val f = File(tmp.root, "log.jsonl")
        val s = store(f)
        s.fillWeights()
        val good = s.state.value
        f.appendText("""{"v":2,"seq":99,"ts":"2026-11""")
        val s2 = Store(f)
        assertEquals(good, s2.state.value)
        s2.dispatch(t0) { Commands.start(it, t0) }
        assertEquals(f.readLines().size, f.readLines().count { LogCodec.decode(it) != null })
        assertNotNull(Store(f).state.value.active)
    }

    @Test fun aVersionOneLogIsSetAsideNotMixedIn() {
        val f = File(tmp.root, "log.jsonl")
        f.writeText("""{"v":1,"seq":1,"ts":"2026-11-01T09:00:00-08:00","type":"program_start","five_rm":{"squat":245}}""" + "\n")
        val s = Store(f)
        assertTrue(!s.state.value.started && !f.exists() && File(tmp.root, "log.jsonl.old").exists())
        s.dispatch(t0) { Commands.setup(fiveRm, Programs.GZCLP) }
        assertEquals(1L, LogCodec.decode(f.readLines().first())!!.seq)
    }
}
