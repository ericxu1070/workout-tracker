package dev.gzclp.tracker.ui

import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.ExerciseMeta
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.ProgressionEvent
import dev.gzclp.tracker.log.SessionEnd
import dev.gzclp.tracker.log.SessionStart
import dev.gzclp.tracker.log.SetLogged
import dev.gzclp.tracker.log.mondayOf
import dev.gzclp.tracker.log.strengthModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class OpenSessionTest {

    private val now = OffsetDateTime.parse("2026-11-24T10:00:00-08:00")
    private val today = LocalDate.parse("2026-11-24")

    private fun setupGzclp(): AppState {
        val setupEvents = Commands.setup(
            mapOf(
                Lift.SQUAT to 245,
                Lift.BENCH to 175,
                Lift.DEADLIFT to 295,
                Lift.OHP to 115,
            ),
            Programs.GZCLP,
        )
        return setupEvents.mapIndexed { i, e -> Entry(i.toLong(), now.minusDays(2), e) }
            .fold(AppState()) { s, e -> s.apply(e) }
    }

    @Test
    fun openSessionNonCountingLeavesProgramUnchangedButUpdatesStrength() {
        var state = setupGzclp()

        val initialSquatGz = state.gz[Lift.SQUAT.id to Tier.T1]
        assertNotNull(initialSquatGz)
        val initialWeight = initialSquatGz!!.weight // 210 lb
        val initialStage = initialSquatGz.stage // 0 (5x3+)
        val initialFiveRm = state.fiveRm[Lift.SQUAT.id]
        val strengthBefore = state.strengthModel().estimate(Lift.SQUAT.id)

        // 1. Start open session
        val startEvents = Commands.startOpen(state, now, state.nextDay, counts = false)
        state = startEvents.fold(state) { s, e -> s.apply(Entry(100, now, e)) }

        // 2. Log sets for Squat with 5 reps at 250 lb (above current 1RM estimate)
        val squatEx = state.exercises(state.nextDay).first { it.id == Lift.SQUAT.id }
        val setEvents = Commands.logSet(state, squatEx, setIndex = 1, reps = 5, weight = 250, now = now.plusMinutes(5), restTargets = emptyMap())
        state = setEvents.fold(state) { s, e -> s.apply(Entry(101, now.plusMinutes(5), e)) }

        // 3. End session with counts = false
        val endEvents = Commands.endOpen(state, now.plusMinutes(20), counts = false)
        state = endEvents.fold(state) { s, e -> s.apply(Entry(102, now.plusMinutes(20), e)) }

        // Assert: program state (weights, stages, 5RM) is completely unchanged
        val afterSquatGz = state.gz[Lift.SQUAT.id to Tier.T1]
        assertEquals("Squat weight should remain unchanged when counts = false", initialWeight, afterSquatGz?.weight)
        assertEquals("Squat stage should remain unchanged when counts = false", initialStage, afterSquatGz?.stage)
        assertEquals("Squat 5RM should remain unchanged when counts = false", initialFiveRm, state.fiveRm[Lift.SQUAT.id])

        // Assert: strength model DID learn from the session's sets
        val strengthAfter = state.strengthModel().estimate(Lift.SQUAT.id)
        assertNotNull("Strength model should have an estimate after sets", strengthAfter)
        assertTrue("Strength estimate should update after open session sets ($strengthAfter > $strengthBefore)", strengthAfter!! > (strengthBefore ?: 0.0))
    }

    @Test
    fun openSessionCountingAdvancesMatchingProgramSlots() {
        var state = setupGzclp()
        val initialSquatWeight = state.gz[Lift.SQUAT.id to Tier.T1]!!.weight // 210

        // Start open session
        val startEvents = Commands.startOpen(state, now, state.nextDay, counts = true)
        state = startEvents.fold(state) { s, e -> s.apply(Entry(200, now, e)) }

        // Perform all 5 sets of Squat 5x3+ hitting targets
        val squatEx = state.exercises(state.nextDay).first { it.id == Lift.SQUAT.id }
        for (i in 1..5) {
            val setEvents = Commands.logSet(state, squatEx, setIndex = i, reps = 3, weight = 210, now = now.plusMinutes(i.toLong() * 3), restTargets = emptyMap())
            state = setEvents.fold(state) { s, e -> s.apply(Entry(200L + i, now.plusMinutes(i.toLong() * 3), e)) }
        }

        // End session with counts = true
        val endEvents = Commands.endOpen(state, now.plusMinutes(30), counts = true)
        state = endEvents.fold(state) { s, e -> s.apply(Entry(210, now.plusMinutes(30), e)) }

        // Assert: Squat T1 progressed by +10 lb to 220
        val afterSquatWeight = state.gz[Lift.SQUAT.id to Tier.T1]!!.weight
        assertEquals("Squat weight should advance when counts = true", initialSquatWeight + 10, afterSquatWeight)
    }

    @Test
    fun muscleGroupWeeklySetCounting() {
        var state = setupGzclp()
        val monday = mondayOf(today)

        // Session 1: Monday this week
        val s1Time = monday.atTime(10, 0).atOffset(now.offset)
        val s1Start = SessionStart("s1", Programs.GZCLP, "A1")
        val s1Bench = (1..4).map { SetLogged("s1", "bench", "t2", "3x10", it, 10, 10, 150, 150, false) } // Chest: 4
        val s1Pull = (1..3).map { SetLogged("s1", "lat_pulldown", "t3", "3x15+", it, 15, 15, 100, 100, false) } // Pull: 3
        val s1End = SessionEnd("s1", Programs.GZCLP, "A1", LogCodec.TS.format(s1Time), 3600)
        val s1Events = listOf(s1Start) + s1Bench + s1Pull + listOf(s1End)
        state = s1Events.mapIndexed { i, e -> Entry(300L + i, s1Time, e) }.fold(state) { s, e -> s.apply(e) }

        // Session 2: Tuesday (today)
        val s2Time = today.atTime(11, 0).atOffset(now.offset)
        val s2Start = SessionStart("s2", Programs.GZCLP, "B1")
        val s2Squat = (1..5).map { SetLogged("s2", "squat", "t1", "5x3+", it, 3, 3, 210, 210, false) } // Legs: 5
        val s2Deadlift = (1..3).map { SetLogged("s2", "deadlift", "t2", "3x10", it, 10, 10, 200, 200, false) } // Pull: 3
        val s2Ohp = (1..3).map { SetLogged("s2", "ohp", "t1", "5x3+", it, 3, 3, 100, 100, false) } // Shoulders: 3
        val s2Arms = (1..2).map { SetLogged("s2", "barbell_curl", "t3", "3x15+", it, 15, 15, 60, 60, false) } // Arms: 2
        val s2End = SessionEnd("s2", Programs.GZCLP, "B1", LogCodec.TS.format(s2Time), 3600)
        val s2Events = listOf(s2Start) + s2Squat + s2Deadlift + s2Ohp + s2Arms + listOf(s2End)
        state = s2Events.mapIndexed { i, e -> Entry(400L + i, s2Time, e) }.fold(state) { s, e -> s.apply(e) }

        // Session 3: Past week (should NOT be counted)
        val pastWeekTime = monday.minusDays(3).atTime(10, 0).atOffset(now.offset)
        val s3Start = SessionStart("s3", Programs.GZCLP, "A1")
        val s3BenchPast = (1..10).map { SetLogged("s3", "bench", "t2", "3x10", it, 10, 10, 150, 150, false) }
        val s3End = SessionEnd("s3", Programs.GZCLP, "A1", LogCodec.TS.format(pastWeekTime), 3600)
        val s3Events = listOf(s3Start) + s3BenchPast + listOf(s3End)
        state = s3Events.mapIndexed { i, e -> Entry(500L + i, pastWeekTime, e) }.fold(state) { s, e -> s.apply(e) }

        val weeklyCounts = state.weeklyMuscleGroupSets(today)

        assertEquals("Chest sets count for this week", 4, weeklyCounts[MuscleGroup.Chest])
        assertEquals("Pull sets count for this week (3 lat_pulldown + 3 deadlift)", 6, weeklyCounts[MuscleGroup.Pull])
        assertEquals("Legs sets count for this week", 5, weeklyCounts[MuscleGroup.Legs])
        assertEquals("Shoulders sets count for this week", 3, weeklyCounts[MuscleGroup.Shoulders])
        assertEquals("Arms sets count for this week", 2, weeklyCounts[MuscleGroup.Arms])
        assertEquals("Core sets count for this week", 0, weeklyCounts[MuscleGroup.Core])
    }

    @Test
    fun targetRepsRecalculatesWeightImmediately() {
        var state = setupGzclp()

        // Give squat a strong baseline in strength model
        val squatObs = dev.gzclp.tracker.engine.e1rm(250.0, 5, 2.0)
        val start = SessionStart("s_heavy", Programs.GZCLP, "A1")
        val set = SetLogged("s_heavy", "squat", "t1", "5x3+", 1, 5, 5, 250, 250, false)
        val end = SessionEnd("s_heavy", Programs.GZCLP, "A1", LogCodec.TS.format(now), 1800)
        state = listOf(start, set, end).mapIndexed { i, e -> Entry(600L + i, now, e) }.fold(state) { s, e -> s.apply(e) }

        // Start open session manager with blank session
        OpenSessionManager.start(fromToday = false, state, onStartWorkout = {})
        OpenSessionManager.addExercise(state, "squat")

        val initialEx = OpenSessionManager.exercises.first { it.exercise.id == "squat" }
        val initialReps = initialEx.targetReps
        val initialWeight = initialEx.targetWeight

        // Changing target reps to 3 (heavier)
        OpenSessionManager.updateReps(state, "squat", 3)
        val heavyWeight = OpenSessionManager.exercises.first().targetWeight
        assertTrue("Lower reps should yield higher suggested weight ($heavyWeight > $initialWeight)", heavyWeight >= initialWeight)

        // Changing target reps to 12 (lighter)
        OpenSessionManager.updateReps(state, "squat", 12)
        val lightWeight = OpenSessionManager.exercises.first().targetWeight
        assertTrue("Higher reps should yield lower suggested weight ($lightWeight < $heavyWeight)", lightWeight < heavyWeight)

        OpenSessionManager.reset()
    }

    @Test
    fun weightPrefillFromParentRatioForExerciseWithNoHistory() {
        var state = setupGzclp()

        // Bench press history in strength model: 200 lb x 5 reps -> e1RM = 200 * (1 + 7/30) = 246.67 lb
        val start = SessionStart("s_bench", Programs.GZCLP, "A1")
        val set = SetLogged("s_bench", "bench", "t1", "5x3+", 1, 5, 5, 200, 200, false)
        val end = SessionEnd("s_bench", Programs.GZCLP, "A1", LogCodec.TS.format(now), 1800)
        state = listOf(start, set, end).mapIndexed { i, e -> Entry(700L + i, now, e) }.fold(state) { s, e -> s.apply(e) }

        // Exercise with parent = "bench", parentRatio = 0.8
        val meta = ExerciseMeta("incline_db_press", parent = "bench", parentRatio = 0.8)
        state = state.apply(Entry(705, now, meta))

        val benchWeightFor10 = Commands.suggestWeight(state, "bench", reps = 10)
        val inclineWeightFor10 = Commands.suggestWeight(state, "incline_db_press", reps = 10)

        // Incline DB press should be scaled by parentRatio (0.8) and rounded to 5
        val expected = dev.gzclp.tracker.engine.round5(benchWeightFor10 * 0.8)
        assertEquals(expected, inclineWeightFor10)
    }

    @Test
    fun recentExercisesReturnsPastSessionExercises() {
        var state = setupGzclp()
        val s1Start = SessionStart("s_recents", Programs.GZCLP, "A1")
        val s1Sets = listOf(
            SetLogged("s_recents", "lat_pulldown", "t3", "3x15+", 1, 15, 15, 100, 100, false),
            SetLogged("s_recents", "barbell_curl", "t3", "3x15+", 1, 15, 15, 50, 50, false),
        )
        val s1End = SessionEnd("s_recents", Programs.GZCLP, "A1", LogCodec.TS.format(now), 1800)
        state = (listOf(s1Start) + s1Sets + listOf(s1End)).mapIndexed { i, e -> Entry(800L + i, now, e) }.fold(state) { s, e -> s.apply(e) }

        val recents = recentExercises(state)
        assertTrue(recents.contains("lat_pulldown"))
        assertTrue(recents.contains("barbell_curl"))
    }
}
