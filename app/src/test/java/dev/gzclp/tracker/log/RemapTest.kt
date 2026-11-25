package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

/** Merging custom exercises typed in place of built-in ones, over synthetic lines shaped like a real log. */
class RemapTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t1 = OffsetDateTime.parse("2026-11-24T09:00:00-08:00")

    private val fixture = listOf(
        """{"v":2,"seq":1,"ts":"2026-11-01T09:00:00-08:00","type":"program_start","five_rm":{"squat":200,"bench":150,"deadlift":250,"ohp":100},"program":"gzclp"}""",
        """{"v":2,"seq":2,"ts":"2026-11-01T09:00:00-08:00","type":"exercise_define","exercise":"lat_pulldown","name":"Lat pulldown","lower":false}""",
        """{"v":2,"seq":3,"ts":"2026-11-01T09:00:00-08:00","type":"exercise_define","exercise":"incline_db_press","name":"Incline dumbbell press","lower":false}""",
        """{"v":2,"seq":4,"ts":"2026-11-01T09:00:00-08:00","type":"progression","exercise":"lat_pulldown","tier":"t3","kind":"initial","to_weight":100,"to_stage":"3x15+"}""",
        """{"v":2,"seq":5,"ts":"2026-11-01T09:00:00-08:00","type":"progression","exercise":"deadlift","tier":"t2","kind":"initial","to_weight":160,"to_stage":"3x10"}""",
        """{"v":2,"seq":6,"ts":"2026-11-01T09:00:00-08:00","type":"progression","exercise":"bench","tier":"t1","kind":"initial","to_weight":125,"to_stage":"5x3+"}""",
        """{"v":2,"seq":7,"ts":"2026-11-02T07:00:00-08:00","type":"body_weight","entry_id":"w1","at":"2026-11-02T07:00:00-08:00","weight":170.0}""",
        """{"v":2,"seq":8,"ts":"2026-11-02T18:00:00-08:00","type":"session_start","session_id":"s1","program":"gzclp","day":"B1"}""",
        """{"v":2,"seq":9,"ts":"2026-11-02T18:00:10-08:00","type":"exercise_define","exercise":"incl","name":"Incl","lower":false}""",
        """{"v":2,"seq":10,"ts":"2026-11-02T18:00:10-08:00","type":"exercise_swap","session_id":"s1","exercise":"deadlift","tier":"t2","substitute":"incl"}""",
        """{"v":2,"seq":11,"ts":"2026-11-02T18:00:20-08:00","type":"session_end","session_id":"s1","program":"gzclp","day":"B1","started_at":"2026-11-02T18:00:00-08:00","duration_sec":20}""",
        """{"v":2,"seq":12,"ts":"2026-11-03T18:00:00-08:00","type":"session_start","session_id":"s2","program":"gzclp","day":"A2"}""",
        """{"v":2,"seq":13,"ts":"2026-11-03T18:00:05-08:00","type":"exercise_swap","session_id":"s2","exercise":"bench","tier":"t1","substitute":"incline_db_press"}""",
        """{"v":2,"seq":14,"ts":"2026-11-03T18:05:00-08:00","type":"set","session_id":"s2","exercise":"incline_db_press","tier":"t1","stage":"5x3+","set_index":1,"target_reps":3,"actual_reps":5,"target_weight":0,"weight":50,"amrap":false}""",
        """{"v":2,"seq":15,"ts":"2026-11-03T18:20:00-08:00","type":"exercise_define","exercise":"pull_ups","name":"Pull ups","lower":false}""",
        """{"v":2,"seq":16,"ts":"2026-11-03T18:20:00-08:00","type":"exercise_swap","session_id":"s2","exercise":"lat_pulldown","tier":"t3","substitute":"pull_ups"}""",
        """{"v":2,"seq":17,"ts":"2026-11-03T18:21:00-08:00","type":"set","session_id":"s2","exercise":"pull_ups","tier":"t3","stage":"3x15+","set_index":1,"target_reps":15,"actual_reps":8,"target_weight":0,"weight":170,"amrap":false,"rest_sec":960,"rest_target_sec":180,"rpe":8.0}""",
        """{"v":2,"seq":18,"ts":"2026-11-03T18:23:00-08:00","type":"set","session_id":"s2","exercise":"pull_ups","tier":"t3","stage":"3x15+","set_index":2,"target_reps":15,"actual_reps":6,"target_weight":0,"weight":170,"amrap":false,"rest_sec":120,"rest_target_sec":60}""",
        """{"v":2,"seq":19,"ts":"2026-11-03T18:25:00-08:00","type":"set","session_id":"s2","exercise":"pull_ups","tier":"t3","stage":"3x15+","set_index":3,"target_reps":15,"actual_reps":4,"target_weight":0,"weight":170,"amrap":true,"rest_sec":120,"rest_target_sec":60}""",
        """{"v":2,"seq":20,"ts":"2026-11-03T18:25:01-08:00","type":"session_end","session_id":"s2","program":"gzclp","day":"A2","started_at":"2026-11-03T18:00:00-08:00","duration_sec":1501}""",
    ).joinToString("") { it + "\n" }

    private fun log(): File = File(tmp.root, "gzclp-log.jsonl").apply { writeText(fixture) }

    private fun AppState.setsOf(id: String) = sessions.flatMap { it.sets }.filter { it.exercise == id }

    @Test fun beforeTheMergeTheCustomExercisesStandAlone() {
        val s = Store(log()).state.value
        assertTrue("pull_ups" in s.moves && "incl" in s.moves)
        assertEquals(3, s.setsOf("pull_ups").size)
        assertTrue(s.exerciseLogs().any { it.id == "pull_ups" })
    }

    @Test fun mergeReadsTheCustomExercisesAsBuiltIns() {
        val f = log()
        val store = Store(f)
        store.dispatch(t1, Commands::merge)
        val s = store.state.value

        assertFalse("pull_ups" in s.moves || "incl" in s.moves)
        assertEquals(setOf("pull_up", "incline_db_press"), s.exerciseLogs().map { it.id }.toSet())
        val sets = s.setsOf("pull_up")
        assertEquals(listOf(8, 6, 4), sets.map { it.actualReps })
        // The 170 typed as the weight was the body weight: nothing added.
        assertTrue(sets.all { it.weight == 0 && it.addedLb == 0.0 && it.bodyweightLb == 170.0 })
        assertEquals(8.0, sets[0].rpe)
        val best = calisthenicsBests(s, s.exerciseLogs().first { it.id == "pull_up" }).single()
        assertEquals("load is the body weight, not counted twice", 170, best.topWeight)

        val entries = remapped(f.readLines().mapNotNull { LogCodec.decode(it) })
        val swaps = entries.map { it.event }.filterIsInstance<ExerciseSwap>()
        assertEquals(listOf("incline_db_press", "incline_db_press", "pull_up"), swaps.map { it.substitute })
        assertEquals("lat_pulldown", swaps.last().exercise)
        // A swap never touches the slot's own progression.
        assertEquals(100, s.gz["lat_pulldown" to Tier.T3]?.weight)
        assertEquals(160, s.gz["deadlift" to Tier.T2]?.weight)
    }

    @Test fun mergeIsAppendOnlyAndIdempotent() {
        val f = log()
        val store = Store(f)
        store.dispatch(t1, Commands::merge)
        val once = f.readText()
        assertTrue("earlier lines are never rewritten", once.startsWith(fixture))
        assertEquals(2, once.removePrefix(fixture).lines().count { it.isNotBlank() })

        assertTrue(Commands.merge(store.state.value).isEmpty())
        store.dispatch(t1.plusMinutes(1), Commands::merge)
        assertEquals(once, f.readText())
        assertEquals("a fresh read folds the same state", store.state.value, Store(f).state.value)

        val entries = f.readLines().mapNotNull { LogCodec.decode(it) }
        assertEquals(remapped(entries), remapped(remapped(entries)))
    }

    @Test fun anExerciseAddedAfterTheMergeKeepsItsOwnId() {
        val f = log()
        val store = Store(f)
        store.dispatch(t1, Commands::merge)
        store.dispatch(t1.plusDays(1)) { listOf(ExerciseDefine("pull_ups", "Pull ups", false)) }
        assertTrue("pull_ups" in store.state.value.moves)
        assertTrue("merged once, never again", Commands.merge(store.state.value).isEmpty())
    }
}
