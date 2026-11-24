package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.e1rm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class CalisthenicsTest {

    private val now = OffsetDateTime.parse("2026-11-24T10:00:00-08:00")

    @Test
    fun catalogContainsAllSevenCalisthenicsMovesAndVariants() {
        val allIds = Catalog.all.associateBy { it.id }

        val baseMoves = listOf(
            "push_up" to "Push-up",
            "pull_up" to "Pull-up",
            "chin_up" to "Chin-up",
            "dip" to "Dip",
            "muscle_up" to "Muscle-up",
            "inverted_row" to "Inverted row",
            "pistol_squat" to "Pistol squat",
        )

        for ((id, name) in baseMoves) {
            val base = allIds[id]
            assertNotNull("Catalog missing $id", base)
            assertEquals(name, base!!.name)

            val weightedId = "weighted_$id"
            val weighted = allIds[weightedId]
            assertNotNull("Catalog missing $weightedId", weighted)
            assertEquals("weighted ${name.lowercase()}", weighted!!.name.lowercase())

            val assistedId = "assisted_$id"
            val assisted = allIds[assistedId]
            assertNotNull("Catalog missing $assistedId", assisted)
            assertEquals("assisted ${name.lowercase()}", assisted!!.name.lowercase())
        }

        // Pistol squat and variants must be flagged as lower-body compounds
        assertTrue(allIds["pistol_squat"]!!.lower)
        assertTrue(allIds["weighted_pistol_squat"]!!.lower)
        assertTrue(allIds["assisted_pistol_squat"]!!.lower)

        // Upper body moves must have lower = false
        assertFalse(allIds["push_up"]!!.lower)
        assertFalse(allIds["pull_up"]!!.lower)
        assertFalse(allIds["chin_up"]!!.lower)
        assertFalse(allIds["dip"]!!.lower)
        assertFalse(allIds["muscle_up"]!!.lower)
        assertFalse(allIds["inverted_row"]!!.lower)

        // None of the calisthenics moves should be in barbell
        for ((id, _) in baseMoves) {
            assertFalse(id in Catalog.barbell)
            assertFalse("weighted_$id" in Catalog.barbell)
            assertFalse("assisted_$id" in Catalog.barbell)
        }

        // Catalog.calisthenics contains all 21 entries
        assertEquals(21, Catalog.calisthenics.size)
        assertTrue("pull_up" in Catalog.calisthenics)
        assertTrue("weighted_dip" in Catalog.calisthenics)
        assertTrue("assisted_muscle_up" in Catalog.calisthenics)
    }

    @Test
    fun defaultBodyweightFactorsFollowSpec() {
        // pull-up / chin-up / muscle-up: 1.0
        assertEquals(1.0, defaultBodyweightFactor("pull_up")!!, 0.001)
        assertEquals(1.0, defaultBodyweightFactor("weighted_pull_up")!!, 0.001)
        assertEquals(1.0, defaultBodyweightFactor("assisted_pull_up")!!, 0.001)

        assertEquals(1.0, defaultBodyweightFactor("chin_up")!!, 0.001)
        assertEquals(1.0, defaultBodyweightFactor("weighted_chin_up")!!, 0.001)
        assertEquals(1.0, defaultBodyweightFactor("assisted_chin_up")!!, 0.001)

        assertEquals(1.0, defaultBodyweightFactor("muscle_up")!!, 0.001)
        assertEquals(1.0, defaultBodyweightFactor("weighted_muscle_up")!!, 0.001)
        assertEquals(1.0, defaultBodyweightFactor("assisted_muscle_up")!!, 0.001)

        // dip: 0.95
        assertEquals(0.95, defaultBodyweightFactor("dip")!!, 0.001)
        assertEquals(0.95, defaultBodyweightFactor("weighted_dip")!!, 0.001)
        assertEquals(0.95, defaultBodyweightFactor("assisted_dip")!!, 0.001)

        // push-up: 0.65
        assertEquals(0.65, defaultBodyweightFactor("push_up")!!, 0.001)
        assertEquals(0.65, defaultBodyweightFactor("weighted_push_up")!!, 0.001)
        assertEquals(0.65, defaultBodyweightFactor("assisted_push_up")!!, 0.001)

        // inverted row: 0.5
        assertEquals(0.5, defaultBodyweightFactor("inverted_row")!!, 0.001)
        assertEquals(0.5, defaultBodyweightFactor("weighted_inverted_row")!!, 0.001)
        assertEquals(0.5, defaultBodyweightFactor("assisted_inverted_row")!!, 0.001)

        // pistol squat: 0.8
        assertEquals(0.8, defaultBodyweightFactor("pistol_squat")!!, 0.001)
        assertEquals(0.8, defaultBodyweightFactor("weighted_pistol_squat")!!, 0.001)
        assertEquals(0.8, defaultBodyweightFactor("assisted_pistol_squat")!!, 0.001)

        // Non-calisthenics exercises return null
        assertNull(defaultBodyweightFactor("bench"))
        assertNull(defaultBodyweightFactor("squat"))
        assertNull(defaultBodyweightFactor("deadlift"))
    }

    @Test
    fun bodyweightFactorCanBeOverriddenByExerciseMeta() {
        // Default push-up factor is 0.65
        assertEquals(0.65, bodyweightFactor("push_up")!!, 0.001)

        // Override via ExerciseMeta event to 0.70
        val meta = ExerciseMeta(exercise = "push_up", bodyweightFactor = 0.70)
        assertEquals(0.70, bodyweightFactor("push_up", meta)!!, 0.001)

        // Meta with parent inherits parent factor
        val childMeta = ExerciseMeta(exercise = "custom_pull", parent = "pull_up")
        assertEquals(1.0, bodyweightFactor("custom_pull", childMeta)!!, 0.001)
    }

    @Test
    fun effectiveLoadCalculationWithAddedAndAssistedWeights() {
        val bodyweight = 180.0

        // Bodyweight pull-up (factor 1.0, added weight 0): load = 1.0 * 180 + 0 = 180 lb
        val bwPullUp = effectiveLoad("pull_up", weightLb = 0.0, addedLb = 0.0, bodyweightLb = bodyweight)
        assertEquals(180.0, bwPullUp, 0.001)

        // Weighted pull-up (+25 lb): load = 1.0 * 180 + 25 = 205 lb
        val weightedPullUp = effectiveLoad("pull_up", weightLb = 25.0, addedLb = 25.0, bodyweightLb = bodyweight)
        assertEquals(205.0, weightedPullUp, 0.001)

        // Band-assisted pull-up (-30 lb band): load = 1.0 * 180 - 30 = 150 lb
        val assistedPullUp = effectiveLoad("pull_up", weightLb = 0.0, addedLb = -30.0, bodyweightLb = bodyweight)
        assertEquals(150.0, assistedPullUp, 0.001)

        // Dip (factor 0.95): load = 0.95 * 180 = 171 lb
        val dipLoad = effectiveLoad("dip", weightLb = 0.0, addedLb = 0.0, bodyweightLb = bodyweight)
        assertEquals(171.0, dipLoad, 0.001)

        // Push-up (factor 0.65): load = 0.65 * 180 = 117 lb
        val pushUpLoad = effectiveLoad("push_up", weightLb = 0.0, addedLb = 0.0, bodyweightLb = bodyweight)
        assertEquals(117.0, pushUpLoad, 0.001)

        // Non-calisthenics lift ignores bodyweight factor and returns weightLb
        val benchLoad = effectiveLoad("bench", weightLb = 225.0, addedLb = null, bodyweightLb = bodyweight)
        assertEquals(225.0, benchLoad, 0.001)
    }

    @Test
    fun requiredScoringEquivalenceTest() {
        // Spec 4 required test:
        // 100 lb body weight + 100 lb added and 200 lb body weight + 0 added,
        // same reps, same factor 1.0, give equal scores; 100 lb body weight + 0 added scores lower.
        val reps = 5

        // Case A: 100 lb body weight + 100 lb added (effective load = 1.0 * 100 + 100 = 200 lb)
        val scoreA = scoreSet("pull_up", actualReps = reps, weightLb = 100.0, addedLb = 100.0, bodyweightLb = 100.0)

        // Case B: 200 lb body weight + 0 added (effective load = 1.0 * 200 + 0 = 200 lb)
        val scoreB = scoreSet("pull_up", actualReps = reps, weightLb = 0.0, addedLb = 0.0, bodyweightLb = 200.0)

        // Case C: 100 lb body weight + 0 added (effective load = 1.0 * 100 + 0 = 100 lb)
        val scoreC = scoreSet("pull_up", actualReps = reps, weightLb = 0.0, addedLb = 0.0, bodyweightLb = 100.0)

        // Verify effective loads
        val loadA = effectiveLoad("pull_up", weightLb = 100.0, addedLb = 100.0, bodyweightLb = 100.0)
        val loadB = effectiveLoad("pull_up", weightLb = 0.0, addedLb = 0.0, bodyweightLb = 200.0)
        val loadC = effectiveLoad("pull_up", weightLb = 0.0, addedLb = 0.0, bodyweightLb = 100.0)
        assertEquals(200.0, loadA, 0.001)
        assertEquals(200.0, loadB, 0.001)
        assertEquals(100.0, loadC, 0.001)

        // Scores A and B must be exactly equal
        assertEquals(scoreA, scoreB, 0.001)

        // Score C must score lower
        assertTrue("Score C ($scoreC) should be lower than Score A ($scoreA)", scoreC < scoreA)

        // Verify against exact e1rm calculation
        // e1rm(200, 5, rir=2.0) = 200 * (1 + 7/30) = 246.6667
        val expectedScore = e1rm(200.0, 5, 2.0)
        assertEquals(expectedScore, scoreA, 0.001)
    }

    @Test
    fun snapshotBodyweightPreventsHistoryRewrite() {
        // Spec: Snapshot bodyweightLb on the set event so later weigh-ins don't rewrite history.
        val sessionDate = now.toLocalDate()
        val entry1 = Entry(1, now, BodyWeightLogged("bw1", LogCodec.TS.format(now), 190.0))
        val state1 = AppState.fold(listOf(entry1))

        // Set is logged on day 1 with snapshotted bodyweight 190 lb
        val snapshotBw = state1.snapshotBodyweight(now)
        assertEquals(190.0, snapshotBw!!, 0.001)

        val set = SetLogged(
            sessionId = "s1",
            exercise = "pull_up",
            tier = "cycle",
            stage = "3x10",
            setIndex = 1,
            targetReps = 10,
            actualReps = 10,
            targetWeight = 0,
            weight = 0,
            amrap = false,
            addedLb = 0.0,
            bodyweightLb = snapshotBw,
        )

        // Later, user logs a new weigh-in (e.g. 175 lb)
        val later = now.plusDays(7)
        val entry2 = Entry(2, later, BodyWeightLogged("bw2", LogCodec.TS.format(later), 175.0))
        val state2 = state1.apply(entry2)
        assertEquals(175.0, state2.snapshotBodyweight(later)!!, 0.001)

        // Evaluating effective load from the snapshotted set preserves original 190 lb
        val load = effectiveLoad(set.exercise, set.weight.toDouble(), set.addedLb, set.bodyweightLb)
        assertEquals(190.0, load, 0.001)
    }

    @Test
    fun calisthenicsProgressionSuggestsRepsThenLoadOrHarderVariation() {
        // Under 3x12: user did 10, 9, 8 reps -> suggest adding reps up to 3x12
        val setsUnder12 = listOf(
            SetLogged("s1", "push_up", "cycle", "3x10", 1, 10, 10, 0, 0, false),
            SetLogged("s1", "push_up", "cycle", "3x10", 2, 10, 9, 0, 0, false),
            SetLogged("s1", "push_up", "cycle", "3x10", 3, 10, 8, 0, 0, false),
        )
        val progUnder = calisthenicsProgression("push_up", setsUnder12)
        assertTrue(progUnder is CalisthenicsProgression.AddReps)
        assertEquals("Add reps up to 3×12", progUnder.suggestion)

        // Achieved 3x12: user did 12, 12, 12 reps -> suggest +5 lb or harder variation
        val setsHit12 = listOf(
            SetLogged("s1", "push_up", "cycle", "3x10", 1, 10, 12, 0, 0, false),
            SetLogged("s1", "push_up", "cycle", "3x10", 2, 10, 12, 0, 0, false),
            SetLogged("s1", "push_up", "cycle", "3x10", 3, 10, 12, 0, 0, false),
        )
        val progHit = calisthenicsProgression("push_up", setsHit12)
        assertTrue(progHit is CalisthenicsProgression.NextVariation)
        assertEquals("Suggest +5 lb or weighted push-up or dip", progHit.suggestion)

        // Harder variation mappings
        assertEquals("weighted pull-up or muscle-up", harderVariation("pull_up"))
        assertEquals("weighted chin-up or pull-up", harderVariation("chin_up"))
        assertEquals("weighted dip or muscle-up", harderVariation("dip"))
        assertEquals("pull-up or weighted inverted row", harderVariation("inverted_row"))
        assertEquals("weighted pistol squat", harderVariation("pistol_squat"))
        assertEquals("weighted muscle-up", harderVariation("muscle_up"))

        // Assisted variants suggest moving to base bodyweight movement
        assertEquals("push-up", harderVariation("assisted_push_up"))
        assertEquals("pull-up", harderVariation("assisted_pull_up"))
        assertEquals("chin-up", harderVariation("assisted_chin_up"))
        assertEquals("dip", harderVariation("assisted_dip"))
    }

    @Test
    fun muscleUpIsolatedAndExcludedFromCrossExerciseComparisons() {
        // Spec 6: Muscle-up keeps its own chart and is excluded from cross-exercise comparisons.
        assertTrue(isMuscleUp("muscle_up"))
        assertTrue(isMuscleUp("weighted_muscle_up"))
        assertTrue(isMuscleUp("assisted_muscle_up"))
        assertFalse(isMuscleUp("pull_up"))
        assertFalse(isMuscleUp("dip"))

        assertTrue(isExcludedFromCrossExercise("muscle_up"))
        assertFalse(isExcludedFromCrossExercise("pull_up"))

        val exercisePool = listOf("pull_up", "dip", "push_up", "muscle_up", "pistol_squat")
        val filtered = filterCrossExerciseComparison(exercisePool)

        assertEquals(listOf("pull_up", "dip", "push_up", "pistol_squat"), filtered)
        assertFalse("muscle_up" in filtered)
    }

    @Test
    fun calisthenicsBestsComputesAbsoluteAndRelativeE1rm() {
        // Setup AppState with bodyweight and pull-up session
        val entries = listOf(
            Entry(1, now, BodyWeightLogged("bw1", LogCodec.TS.format(now), 180.0)),
            Entry(2, now, SessionStart("s1", "ppl", "pull_a")),
            Entry(3, now, SetLogged("s1", "pull_up", "cycle", "3x10", 1, 10, 10, 0, 0, false, addedLb = 0.0, bodyweightLb = 180.0)),
            Entry(4, now, SetLogged("s1", "pull_up", "cycle", "3x10", 2, 10, 8, 0, 0, false, addedLb = 0.0, bodyweightLb = 180.0)),
            Entry(5, now, SessionEnd("s1", "ppl", "pull_a", LogCodec.TS.format(now), 1800)),
        )
        val state = AppState.fold(entries)
        val logs = state.exerciseLogs()
        val pullUpLog = logs.first { it.id == "pull_up" }

        val bests = calisthenicsBests(state, pullUpLog)
        assertFalse(bests.isEmpty())
        val best = bests.first()

        // Effective load is 180 lb. Top set 10 reps @ RIR 2: e1rm = 180 * (1 + 12/30) = 252 lb.
        assertEquals(180, best.topWeight)
        assertEquals(10, best.topReps)
        assertEquals(252, best.e1rm)

        // Relative ratio = e1rm / bodyweight = 252 / 180 = 1.40x
        val relativeRatio = best.e1rm / 180.0
        assertEquals(1.40, relativeRatio, 0.01)
    }
}
