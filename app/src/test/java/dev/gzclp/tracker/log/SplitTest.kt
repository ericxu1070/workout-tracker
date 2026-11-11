package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.CycleState
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Scheme
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.Wave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

class SplitTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-10T18:00:00-08:00")
    private val rest = Tier.entries.associateWith { it.defaultRestSec }
    private val fiveRm = mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105)
    private val five = Wave(5, listOf(8, 6, 5))
    private val file get() = File(tmp.root, "log.jsonl")

    private fun store(program: String = "ppl") = Store(file).apply { dispatch(t0) { Commands.setup(fiveRm, program) } }

    /** Copies [from] as a split named [name] and returns its id. */
    private fun Store.copy(from: String, name: String): String =
        (dispatch(t0) { Commands.copySplit(it, from, name) }.single().event as ProgramDefine).program

    /** A split copied from [from] and in use. */
    private fun Store.split(from: String, name: String = "Mine"): String = copy(from, name).also { id -> dispatch(t0) { Commands.chooseProgram(it, id) } }

    private val Store.st get() = state.value
    private fun Store.days(id: String) = st.customPrograms.getValue(id).days

    private fun Store.fillWeights() {
        while (true) {
            val ex = Commands.missingWeight(st) ?: return
            dispatch(t0) { Commands.startingWeight(it, ex, 50) }
        }
    }

    private fun Store.begin(at: OffsetDateTime) {
        fillWeights()
        dispatch(at) { Commands.start(it, at) }
    }

    /** Logs every set of the active workout, [reps] per set or the target, and ends it. */
    private fun Store.finish(at: OffsetDateTime, reps: (Exercise, Int) -> Int = { ex, _ -> ex.scheme.reps }) {
        val a = st.active!!
        for (ex in st.exercises(a.program, a.day)) for (i in 1..ex.scheme.sets) dispatch(at) { Commands.logSet(it, ex, i, reps(ex, i), ex.weight!!, at, rest) }
        dispatch(at) { Commands.end(it, at.plusMinutes(40), null) }
    }

    private fun Store.workout(at: OffsetDateTime, reps: (Exercise, Int) -> Int = { ex, _ -> ex.scheme.reps }) {
        begin(at)
        finish(at, reps)
    }

    private fun Store.progressions() = bytes().decodeToString().lines().mapNotNull { LogCodec.decode(it)?.event as? ProgressionEvent }.filter { it.sessionId != null }

    @Test fun copyingAPresetMakesANamedSplitAtTheDefaultWave() {
        val s = store()
        val ppl = Programs.of("ppl")
        s.dispatch(t0) { Commands.removeExercise(it, ppl, ppl.days[0], "lateral_raise") }
        val id = s.copy("ppl", "  My   PPL ")
        assertEquals("my_ppl", id)
        val p = s.st.customPrograms.getValue(id)
        assertEquals(listOf("My PPL", "ppl"), listOf(p.name, p.from))
        assertEquals(ppl.days.map { it.id to it.title }, p.days.map { it.id to it.title })
        assertEquals("the copy is the preset as the user has it", listOf("bench", "ohp", "tricep_pushdown"), p.days[0].slots.map { it.ex })
        assertTrue(p.days.all { d -> d.slots.all { it.tier == Tier.CUSTOM && it.wave == Wave.DEFAULT } })
        assertEquals("ppl", s.st.programId)

        assertEquals("a second copy gets its own id", "my_ppl_2", s.copy("ppl", "My PPL"))
        s.dispatch(t0) { Commands.setWave(it, id, "push_a", "bench", five) }
        val againId = s.copy(id, "Again")
        val again = s.st.customPrograms.getValue(againId)
        assertEquals("a copy of a split keeps its waves and its preset", listOf(five, "ppl"), listOf(again.days[0].slots[0].wave, again.from))

        for ((from, name) in listOf(Programs.GZCLP to "Mine", "nope" to "Mine", "ppl" to "   ")) assertTrue(Commands.copySplit(s.st, from, name).isEmpty())
        assertEquals(s.st, Store(file).state.value)
    }

    @Test fun eachEditWritesOneLineAndBadOrEmptyEditsWriteNothing() {
        val s = store()
        val id = s.copy("full_body", "Mine")
        fun one(build: (AppState) -> List<Event>) = assertEquals(1, s.dispatch(t0, build).size)

        one { Commands.setWave(it, id, "a", "bench", five) }
        assertEquals(five, s.days(id)[0].slots[1].wave)
        assertTrue("unchanged", Commands.setWave(s.st, id, "a", "bench", five).isEmpty())
        for (bad in listOf(Wave(0, listOf(8, 6, 5)), Wave(11, listOf(8, 6, 5)), Wave(5, listOf(8, 6)), Wave(5, listOf(8, 0, 5)))) {
            assertTrue("invalid $bad", Commands.setWave(s.st, id, "a", "bench", bad).isEmpty())
        }
        one { Commands.setAll(it, id, Wave(4, listOf(10, 8, 6))) }
        assertTrue(s.days(id).all { d -> d.slots.all { it.wave == Wave(4, listOf(10, 8, 6)) } })

        one { Commands.renameSplit(it, id, "Full") }
        one { Commands.addDay(it, id, "Arms day") }
        one { Commands.renameDay(it, id, "arms_day", "Arms") }
        assertEquals("Full", s.st.customPrograms.getValue(id).name)
        assertEquals(listOf("arms_day", "Arms", 0), s.days(id).last().let { listOf(it.id, it.title, it.slots.size) })

        one { Commands.addSplitExercise(it, id, "arms_day", "barbell curl") }
        assertEquals("a new exercise is defined in the same action", 2, s.dispatch(t0) { Commands.addSplitExercise(it, id, "arms_day", "Spider curl") }.size)
        assertTrue("no exercise twice in a day", Commands.addSplitExercise(s.st, id, "arms_day", "Barbell curl").isEmpty())
        one { Commands.moveSplitExercise(it, id, "arms_day", 1, 0) }
        assertEquals(listOf("spider_curl", "barbell_curl"), s.days(id).last().slots.map { it.ex })
        assertEquals(Wave.DEFAULT, s.days(id).last().slots[0].wave)
        one { Commands.removeSplitExercise(it, id, "arms_day", "barbell_curl") }
        assertTrue(Commands.moveSplitExercise(s.st, id, "arms_day", 0, 5).isEmpty())

        one { Commands.moveDay(it, id, 3, 0) }
        one { Commands.removeDay(it, id, "b") }
        assertEquals(listOf("arms_day", "a", "c"), s.days(id).map { it.id })
        one { Commands.removeDay(it, id, "a") }
        one { Commands.removeDay(it, id, "c") }
        assertTrue("never the last day", Commands.removeDay(s.st, id, "arms_day").isEmpty())
        assertTrue("blank names", (Commands.renameSplit(s.st, id, " ") + Commands.addDay(s.st, id, "") + Commands.renameDay(s.st, id, "arms_day", " ")).isEmpty())
        assertTrue("built-ins are not edited here", Commands.setAll(s.st, "ppl", five).isEmpty())

        assertEquals("rebuild from the log equals live state", s.st, Store(file).state.value)
    }

    @Test fun aCustomWaveRunsTheCycleWithItsOwnRepsAndItsOwnState() {
        val s = store()
        val id = s.split("full_body")
        s.dispatch(t0) { Commands.setWave(it, id, "a", "bench", five) }
        s.dispatch(t0) { Commands.addSplitExercise(it, id, "c", "Bench press") } // the same lift at 3 x 12·10·8
        val bench = { day: Int -> s.st.exercises(s.days(id)[day]).first { it.id == "bench" } }
        assertNull("a new wave asks for a starting weight", bench(0).weight)

        s.workout(t0.plusDays(1))
        assertEquals(listOf(6, 5, 55, 1), bench(0).let { listOf(it.scheme.reps, it.scheme.sets, it.weight, it.week) })
        val line = s.progressions().single { it.exercise == "bench" }
        assertEquals(
            ProgressionEvent("bench", "custom", "week_advance", 50, 55, "5x8", "5x6", line.sessionId, cycleWeek = 2, cycleBase = 50, scheme = "5x8,6,5"),
            line,
        )
        s.workout(t0.plusDays(2))
        s.workout(t0.plusDays(3))
        assertEquals("the same lift at another wave is separate", CycleState(50, 1), s.st.custom["bench" to Wave.DEFAULT.key])
        assertEquals("and so is its cycle program state", CycleState(105), s.st.cycle["bench"])
        s.workout(t0.plusDays(4))
        assertEquals(listOf(5, 60, 2), bench(0).let { listOf(it.scheme.reps, it.weight, it.week) })

        // Heavy week, every set at its 5: the next cycle starts at base + bump.
        s.workout(t0.plusDays(5))
        s.workout(t0.plusDays(6))
        s.workout(t0.plusDays(7))
        assertEquals(CycleState(55, 0), s.st.custom["bench" to five.key])
        assertEquals(listOf(8, 55), bench(0).let { listOf(it.scheme.reps, it.weight) })
        repeat(8) { s.workout(t0.plusDays(8L + it)) } // b c a b c a b c
        // Heavy week again, one set short: the cycle repeats at the same base.
        s.workout(t0.plusDays(16)) { ex, i -> if (ex.id == "bench" && i == 5) 4 else ex.scheme.reps }
        assertEquals(CycleState(55, 0), s.st.custom["bench" to five.key])
        val kinds = s.progressions().filter { it.exercise == "bench" && it.scheme == five.key }.map { it.kind }
        assertEquals(listOf("week_advance", "week_advance", "cycle_up", "week_advance", "week_advance", "cycle_repeat"), kinds)
        assertEquals(s.st, Store(file).state.value)
    }

    @Test fun upcomingAndEditWeightFollowTheWave() {
        val s = store()
        val id = s.split("full_body")
        s.dispatch(t0) { Commands.setWave(it, id, "a", "bench", five) }
        s.workout(t0.plusDays(1))
        val up = Commands.upcoming(s.st, 4)
        assertEquals(listOf("b", "c", "a", "b"), up.map { it.first.id })
        assertEquals(listOf("5x6", 55), up[2].second.first { it.id == "bench" }.let { listOf(it.scheme.label, it.weight) })

        val bench = s.st.exercises(s.days(id)[0]).first { it.id == "bench" }
        val edit = Commands.editWeight(s.st, bench, 77).single() as ProgressionEvent
        assertEquals(listOf("custom", "manual_edit", "5x8,6,5", "5x6", 75, 70), listOf(edit.tier, edit.kind, edit.scheme, edit.toStage, edit.toWeight, edit.cycleBase))
        s.dispatch(t0) { listOf(edit) }
        assertEquals(CycleState(70, 1), s.st.custom["bench" to five.key])
    }

    @Test fun theRotationFollowsDayIdsThroughEdits() {
        val s = store()
        val id = s.split("ppl")
        s.workout(t0.plusDays(1))
        assertEquals("pull_a", s.st.nextDay.id)
        s.dispatch(t0) { Commands.renameDay(it, id, "pull_a", "Back") }
        s.dispatch(t0) { Commands.moveDay(it, id, 1, 5) }
        assertEquals("reordered, the due day stays due", "pull_a", s.st.nextDay.id)
        s.dispatch(t0) { Commands.moveDay(it, id, 5, 1) }
        s.dispatch(t0) { Commands.removeDay(it, id, "pull_a") }
        assertEquals("deleted, the day after it is due", "legs_a", s.st.nextDay.id)
        s.dispatch(t0) { Commands.addDay(it, id, "Pull C") }
        s.dispatch(t0) { Commands.moveDay(it, id, 1, 0) }
        assertEquals(listOf("legs_a", "push_a", "push_b", "pull_b", "legs_b", "pull_c"), s.days(id).map { it.id })
        s.workout(t0.plusDays(2))
        assertEquals("push_a", s.st.nextDay.id)
        repeat(4) { s.workout(t0.plusDays(3L + it)) }
        assertEquals("pull_c", s.st.next[id])
        assertEquals("an empty day is skipped", "legs_a", s.st.nextDay.id)
        assertEquals(listOf("legs_a", "push_a"), Commands.upcoming(s.st, 2).map { it.first.id })
        assertEquals(s.st, Store(file).state.value)
    }

    @Test fun aWorkoutInProgressKeepsItsPlanThroughEdits() {
        val s = store()
        val id = s.split("ppl")
        s.begin(t0)
        val plan = s.st.sessionExercises()
        s.dispatch(t0) { Commands.setWave(it, id, "push_a", "bench", five) }
        s.dispatch(t0) { Commands.removeSplitExercise(it, id, "push_a", "ohp") }
        s.dispatch(t0) { Commands.removeDay(it, id, "push_a") }
        assertEquals(plan, s.st.sessionExercises())
        s.finish(t0)
        assertEquals("progression keys the wave the workout used", Wave.DEFAULT.key, s.progressions().first { it.exercise == "bench" }.scheme)
        assertEquals(CycleState(50, 1), s.st.custom["bench" to Wave.DEFAULT.key])
        assertNull(s.st.custom["bench" to five.key])
        assertEquals("the day it did was deleted: the next one is due", "pull_a", s.st.nextDay.id)
        assertEquals(listOf("bench", "ohp"), s.st.sessions.last().day.slots.take(2).map { it.ex })
    }

    @Test fun deletingTheSplitInUseFallsBackToItsPreset() {
        val s = store()
        val other = s.copy("bro", "Other")
        val id = s.split("upper_lower", "Mine")
        s.begin(t0)
        s.dispatch(t0) { Commands.deleteSplit(it, other) }
        assertEquals(id, s.st.programId)
        s.dispatch(t0) { Commands.deleteSplit(it, id) }
        assertEquals("upper_lower", s.st.programId)
        assertEquals(emptyMap<String, Any>(), s.st.customPrograms)
        assertTrue(Commands.deleteSplit(s.st, id).isEmpty())
        s.finish(t0)
        assertEquals("history keeps the split's name", "Mine", s.st.sessions.single().program.name)
        assertEquals("upper_a", s.st.nextDay.id)
        assertEquals("a new split never reuses a done split's id", "mine_2", s.copy("ppl", "Mine"))
        assertEquals(s.st, Store(file).state.value)
    }

    @Test fun startingWeightEstimatesComeFromTheLatestSessionRoundedToPlates() {
        val day = Programs.of("ppl").days[0]
        fun set(ex: String, w: Int, reps: Int) = SetLogged("s", ex, "cycle", "3x10", 1, 10, reps, w, w, false)
        fun session(at: String, vararg sets: SetLogged) = SessionRecord("s$at", Programs.of("ppl"), day, OffsetDateTime.parse(at), 3000, sets.toList())
        val st = AppState(sessions = listOf(
            session("2026-11-01T18:00:00-08:00", set("bench", 300, 5), set("lateral_raise", 20, 12)),
            session("2026-11-03T18:00:00-08:00", set("bench", 200, 5), set("bench", 210, 3)),
            session("2026-11-05T18:00:00-08:00", set("bench", 400, 0)),
        ))
        fun ex(id: String, reps: Int) = Exercise(id, id, Tier.CUSTOM, Scheme(5, reps, false), null, 0, wave = Wave(5, listOf(reps, reps, reps)))
        // 200 x 5 is a 233 lb 1RM; at 8 reps that is 184, and the plates make 185.
        assertEquals(185, Commands.estimate(st, ex("bench", 8), Plates()))
        assertEquals("a lift off the bar rounds to 5 lb", 20, Commands.estimate(st, ex("lateral_raise", 12), Plates()))
        assertNull(Commands.estimate(st, ex("squat", 5), Plates()))
    }

    @Test fun theLogIgnoresDefinitionsItCannotRun() {
        val s = store()
        val id = s.copy("ppl", "Mine")
        val good = s.st
        val bad = good.customPrograms.getValue(id).toDefine()
        s.dispatch(t0) {
            listOf(
                bad.copy(program = "ppl"),
                bad.copy(days = emptyList()),
                bad.copy(days = listOf(SplitDay("x", "X", listOf(SplitSlot("bench", 3, emptyList()))))),
                bad.copy(days = listOf(SplitDay("x", "X", listOf(SplitSlot("bench", 3, listOf(5, 5, 5)), SplitSlot("bench", 5, listOf(5, 5, 5)))))),
                ProgramRemove("ppl"),
            )
        }
        assertEquals(good.customPrograms, s.st.customPrograms)
        assertEquals(Programs.of("ppl"), s.st.programOf("ppl"))
    }

    @Test fun anOldLogFoldsAsBefore() {
        val old = """
            {"v":2,"seq":1,"ts":"2026-11-01T09:00:00-08:00","type":"program_start","five_rm":{"bench":175},"program":"ppl"}
            {"v":2,"seq":2,"ts":"2026-11-01T09:00:00-08:00","type":"exercise_define","exercise":"bench","name":"Bench press","lower":false}
            {"v":2,"seq":3,"ts":"2026-11-01T09:00:00-08:00","type":"progression","exercise":"bench","tier":"cycle","kind":"initial","to_weight":115,"to_stage":"3x12","cycle_week":1,"cycle_base":115}
            {"v":2,"seq":4,"ts":"2026-11-02T18:00:00-08:00","type":"session_start","session_id":"s1","program":"ppl","day":"legs_b"}
            {"v":2,"seq":5,"ts":"2026-11-02T18:05:00-08:00","type":"set","session_id":"s1","exercise":"bench","tier":"cycle","stage":"3x12","set_index":1,"target_reps":12,"actual_reps":12,"target_weight":115,"weight":115,"amrap":false}
            {"v":2,"seq":6,"ts":"2026-11-02T18:40:00-08:00","type":"progression","exercise":"bench","tier":"cycle","kind":"week_advance","from_weight":115,"to_weight":120,"from_stage":"3x12","to_stage":"3x10","session_id":"s1","cycle_week":2,"cycle_base":115}
            {"v":2,"seq":7,"ts":"2026-11-02T18:40:00-08:00","type":"session_end","session_id":"s1","program":"ppl","day":"legs_b","started_at":"2026-11-02T18:00:00-08:00","duration_sec":2400}
        """.trimIndent() + "\n"
        val st = AppState.fold(old.lines().mapNotNull { LogCodec.decode(it) })
        assertEquals("ppl", st.programId)
        assertEquals("the rotation wraps", "push_a", st.nextDay.id)
        assertEquals(mapOf("bench" to CycleState(115, 1)), st.cycle)
        assertEquals(1, st.sessions.size)
        assertTrue(st.custom.isEmpty() && st.customPrograms.isEmpty())
        assertEquals(listOf("push_a", "pull_a"), Commands.upcoming(st, 2).map { it.first.id })
    }

    @Test fun theWeekComparesACustomLiftWithTheSameWeekOfItsLastCycle() {
        val p = Programs.of("ppl")
        fun set(stage: String, w: Int) = SetLogged("s", "bench", "custom", stage, 1, 5, 5, w, w, false)
        fun session(at: String, s: SetLogged) = SessionRecord("s$at", p, p.days[0], OffsetDateTime.parse(at), 3000, listOf(s))
        val st = AppState(sessions = listOf(
            session("2026-10-27T18:00:00-08:00", set("5x8", 100)),
            session("2026-11-03T18:00:00-08:00", set("5x6", 105)),
            session("2026-11-10T18:00:00-08:00", set("5x8", 110)),
        ))
        val lift = st.week(LocalDate.parse("2026-11-09")).lifts.single()
        assertEquals(listOf(Tier.CUSTOM, "5x8", 110, 100), listOf(lift.tier, lift.stage, lift.now.topWeight, lift.prev?.topWeight))
    }

    @Test fun theNewPresetHasItsDays() {
        val p = Programs.of("chest_back_arms_legs")
        assertEquals("Chest and back, arms, legs", p.name)
        assertEquals(
            listOf(
                "chest_back" to listOf("bench", "lat_pulldown", "chest_fly", "barbell_row"),
                "arms" to listOf("ohp", "lateral_raise", "barbell_curl", "tricep_pushdown", "hammer_curl", "overhead_tricep_extension"),
                "legs" to listOf("squat", "romanian_deadlift", "leg_press", "leg_curl", "calf_raise"),
            ),
            p.days.map { d -> d.id to d.slots.map { it.ex } },
        )
        assertTrue(p.days.all { d -> d.slots.all { it.tier == Tier.CYCLE } })
    }

    @Test fun aSplitRestsByTheWeeksRepsAndEveryOtherTierBySettings() {
        assertEquals(listOf(180, 180, 120, 120, 90, 90), listOf(1, 5, 6, 10, 11, 50).map { Commands.restSec(Tier.CUSTOM, it, rest) })
        assertEquals(rest[Tier.CYCLE], Commands.restSec(Tier.CYCLE, 3, rest))
        val restAfter = listOf(Wave.DEFAULT, Wave(3, listOf(5, 5, 5))).map { wave ->
            val s = Store(File(tmp.newFolder(), "log.jsonl")).apply { dispatch(t0) { Commands.setup(fiveRm, "ppl") } }
            val id = s.split("ppl")
            s.dispatch(t0) { Commands.setAll(it, id, wave) }
            s.begin(t0)
            val ex = s.st.sessionExercises().first()
            s.dispatch(t0) { Commands.logSet(it, ex, 1, ex.scheme.reps, ex.weight!!, t0, rest) }
            s.dispatch(t0.plusMinutes(2)) { Commands.logSet(it, ex, 2, ex.scheme.reps, ex.weight!!, t0.plusMinutes(2), rest) }
            s.st.active!!.sets.getValue(SetKey(ex.id, Tier.CUSTOM, 2)).event.restTargetSec
        }
        assertEquals(listOf(90, 180), restAfter)
    }

    @Test fun aWaveReadsBackFromItsLogKey() {
        assertEquals(five, Wave.of(five.key))
        assertEquals("5 × 8·6·5", five.display)
        listOf("", "5", "5x", "x8,6,5", "5x8,6", "0x8,6,5", "5x8,6,a").forEach { assertNull(it, Wave.of(it)) }
    }
}
