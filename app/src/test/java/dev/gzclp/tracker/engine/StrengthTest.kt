package dev.gzclp.tracker.engine

import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.ExerciseMeta
import dev.gzclp.tracker.log.ProgressionEvent
import dev.gzclp.tracker.log.SessionEnd
import dev.gzclp.tracker.log.SessionStart
import dev.gzclp.tracker.log.SetLogged
import dev.gzclp.tracker.log.StrengthState
import dev.gzclp.tracker.log.adaptSessionStartEvents
import dev.gzclp.tracker.log.shouldSuggest
import dev.gzclp.tracker.log.strengthModel
import dev.gzclp.tracker.log.strengthSuggestion
import dev.gzclp.tracker.log.suggestionChipText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class StrengthTest {

    private val now = OffsetDateTime.parse("2026-11-24T10:00:00-08:00")

    @Test
    fun ohpScenarioYieldsT1Suggestion110Or115() {
        // Spec: The user overrode T2 OHP to 95 lb 3x10, and the next cycle still prescribed T1 OHP 105 lb 5x3.
        // 95x10 at RIR 2 gives e1RM = 95 * (1 + 12/30) = 133 lb.
        // For fresh model: 133 / (1 + 5/30) = 114 -> round5 is 115.
        // For model initialized with prior T1 105 (e1RM = 122.5):
        // α = 0.6 update: 122.5 + 0.6 * (133 - 122.5) = 128.8 -> 128.8 / (7/6) = 110.4 -> round5 is 110.
        // Both cases must yield 110 or 115.

        // Case A: Fresh manual edit
        val freshState = StrengthState().observeManualEdit("ohp", 95.0, 10)
        val freshSuggestion = freshState.suggest("ohp", reps = 3, targetRir = 2.0)
        assertEquals(115, freshSuggestion)

        // Case B: Manual edit on top of existing baseline (105 lb 5x3)
        val baseline = e1rm(105.0, 3, 2.0) // 122.5
        val updatedState = StrengthState().observeSession("ohp", baseline)
            .observeManualEdit("ohp", 95.0, 10)
        val updatedSuggestion = updatedState.suggest("ohp", reps = 3, targetRir = 2.0)
        assertEquals(110, updatedSuggestion)

        // Case C: log replay
        val entries = listOf(
            Entry(1, now.minusDays(2), ProgressionEvent("ohp", "t1", "initial", toWeight = 105, toStage = "5x3+")),
            Entry(2, now.minusDays(1), ProgressionEvent("ohp", "t2", "manual_edit", toWeight = 95, toStage = "3x10")),
        )
        val replayed = dev.gzclp.tracker.log.AppState.fold(entries).strengthModel()
        val replayedSuggestion = replayed.suggest("ohp", reps = 3, targetRir = 2.0)
        assertNotNull(replayedSuggestion)
        assertTrue("Expected 110 or 115 but was $replayedSuggestion", replayedSuggestion in listOf(110, 115))
    }

    @Test
    fun asymmetricEmaRisesFastAndDropsSlower() {
        // Spec: α_up = 0.4, α_down = 0.15
        val baseline = 100.0

        // Step up by 50 lb (100 -> 150)
        val up = updateEma(baseline, 150.0, alphaUp = 0.4, alphaDown = 0.15)
        assertEquals(120.0, up, 0.001) // 100 + 0.4 * 50 = 120.0

        // Step down by 50 lb from 120 (120 -> 70)
        val down = updateEma(up, 70.0, alphaUp = 0.4, alphaDown = 0.15)
        assertEquals(112.5, down, 0.001) // 120 + 0.15 * (-50) = 112.5

        // Verify across sessions with StrengthState
        var state = StrengthState().observeSession("squat", 100.0)
        state = state.observeSession("squat", 150.0)
        assertEquals(120.0, state.estimate("squat")!!, 0.001)
        state = state.observeSession("squat", 70.0)
        assertEquals(112.5, state.estimate("squat")!!, 0.001)

        // The upward delta was +20, downward delta was only -7.5 for the same 50 lb observation deviation
        assertTrue(Math.abs(up - baseline) > Math.abs(down - up))
    }

    @Test
    fun rpeHandlingRirEqualsTenMinusRpe() {
        // Spec: Per-set e1RM uses RIR = 10 − RPE when RPE is logged;
        // otherwise 0 for an AMRAP last set, 2 for fixed sets.

        assertEquals(2.0, rirForSet(8.0, false), 0.001)
        assertEquals(1.5, rirForSet(8.5, false), 0.001)
        assertEquals(1.0, rirForSet(9.0, true), 0.001) // RPE overrides AMRAP
        assertEquals(0.0, rirForSet(10.0, false), 0.001)

        // Without RPE
        assertEquals(0.0, rirForSet(null, amrap = true), 0.001)
        assertEquals(2.0, rirForSet(null, amrap = false), 0.001)

        // e1RM calculation with RPE
        val load = 200.0
        val reps = 5
        // RPE 8 -> RIR 2: 200 * (1 + (5 + 2)/30) = 200 * (1 + 7/30) = 246.6667
        assertEquals(200.0 * (1.0 + 7.0 / 30.0), e1rm(load, reps, rirForSet(8.0, false)), 0.001)

        // RPE 10 -> RIR 0: 200 * (1 + 5/30) = 233.3333
        assertEquals(200.0 * (1.0 + 5.0 / 30.0), e1rm(load, reps, rirForSet(10.0, false)), 0.001)
    }

    @Test
    fun setsOverTwelveRepsIgnored() {
        // Spec: Sets over 12 reps are ignored.
        assertEquals(0.0, e1rm(100.0, 13, 0.0), 0.001)
        assertEquals(0.0, e1rm(100.0, 15, 2.0), 0.001)
        assertEquals(0.0, e1rm(100.0, 20, 0.0), 0.001)

        // Exactly 12 reps is not ignored
        assertTrue(e1rm(100.0, 12, 0.0) > 0.0)

        // Session with only sets over 12 reps must not feed the model
        val entries = listOf(
            Entry(1, now, SessionStart("s1", "gzclp", "A1")),
            Entry(2, now, SetLogged("s1", "lat_pulldown", "t3", "3x15+", 1, 15, 15, 100, 100, false)),
            Entry(3, now, SetLogged("s1", "lat_pulldown", "t3", "3x15+", 2, 15, 13, 100, 100, false)),
            Entry(4, now, SessionEnd("s1", "gzclp", "A1", now.toString(), 3600)),
        )
        val model = dev.gzclp.tracker.log.AppState.fold(entries).strengthModel()
        assertNull(model.estimate("lat_pulldown"))
    }

    @Test
    fun manualEditPropagatesToSiblingTier() {
        // Spec: A manual weight edit is a strong observation: treat the edited weight
        // and that slot's prescribed reps as a set at RIR 2 and update the model with α = 0.6.
        // Propagation: after any update, compute the model's suggested weight for every other tier/slot
        // of the same exercise (suggest(reps, targetRir) where targetRir is 2 for T1 and T2, 3 for T3 and cycle weeks).

        val entries = listOf(
            Entry(1, now, ProgressionEvent("bench", "t2", "manual_edit", toWeight = 155, toStage = "3x8")),
        )
        val model = dev.gzclp.tracker.log.AppState.fold(entries).strengthModel()

        // T1 sibling (reps = 3, targetRir = 2.0)
        val t1Suggest = model.suggest("bench", reps = 3, targetRir = 2.0)
        assertNotNull(t1Suggest)
        assertTrue(t1Suggest!! > 155)

        // Cycle week sibling (reps = 10, targetRir = 3.0)
        val cycleSuggest = model.suggest("bench", reps = 10, targetRir = 3.0)
        assertNotNull(cycleSuggest)

        // T3 sibling (reps = 15, targetRir = 3.0)
        val t3Suggest = model.suggest("bench", reps = 15, targetRir = 3.0)
        assertNotNull(t3Suggest)
    }

    @Test
    fun neverSuggestDropOfMoreThanTenPercent() {
        // Spec: Never suggest a drop of more than 10% at once.
        val model = StrengthState().observeSession("bench", 100.0) // weak session: e1rm = 100

        // Current weight is 200 lb. 10% drop limit means min weight = round5(200 * 0.90) = 180.
        // Raw suggest for e1rm 100 with reps = 3, rir = 2 is 100 / (7/6) = 85.7 -> 85 lb.
        val suggestedWithCurrent = model.suggest("bench", reps = 3, targetRir = 2.0, currentWeight = 200)
        assertEquals(180, suggestedWithCurrent)
    }

    @Test
    fun chipThresholdExceedsByAtLeastOneIncrement() {
        // Spec: When the suggestion exceeds the engine's weight by at least one increment
        // (e.g., 5 lb for bench/ohp/upper or 10 lb for lower), show a chip on the exercise:
        // "Plan 105 · recent sets say 115 · Use".

        val fiveRm = mapOf(Lift.BENCH to 165, Lift.SQUAT to 225)
        val setupEvents = Commands.setup(fiveRm, Programs.GZCLP)
        var seq = 0L
        val entries = setupEvents.map { Entry(++seq, now, it) }.toMutableList()

        // Upper body: bench (step = 5)
        // Planned T1 bench from 165 5RM is 140 lb (e1RM = 163.33).
        // User overrides T2 bench to 185 lb 3x8 (e1RM = 185 * (1 + 10/30) = 246.67).
        // With α = 0.6: EMA = 163.33 + 0.6 * (246.67 - 163.33) = 213.33.
        // Suggestion for T1 5x3 (targetRir = 2): 213.33 / (7/6) = 182.85 -> 185 lb.
        entries.add(Entry(++seq, now, ProgressionEvent("bench", "t2", "manual_edit", toWeight = 185, toStage = "3x8")))

        val appState = dev.gzclp.tracker.log.AppState.fold(entries)
        // Day A2 has T1 Bench (plan 140 lb)
        val dayA2 = appState.program.day("A2")!!
        val benchEx = appState.exercises(dayA2).first { it.id == "bench" }
        val suggestion = appState.strengthSuggestion(benchEx)
        assertNotNull(suggestion)
        assertEquals(185, suggestion)
        assertTrue(appState.shouldSuggest(benchEx, suggestion!!))
        val chipText = appState.suggestionChipText(benchEx)
        assertNotNull(chipText)
        assertEquals("Plan 140 · recent sets say 185 · Use", chipText)
    }

    @Test
    fun autoAdaptWeightsAtSessionStart() {
        // Spec: A Settings toggle "Auto-adapt weights" applies it automatically at session start.
        // 5RM 125 yields initial T1 OHP of 105 lb (Progression.t1FromFiveRm(125) = round5(125 * 0.85) = 105).
        val fiveRm = mapOf(Lift.OHP to 125, Lift.SQUAT to 225)
        val setupEvents = Commands.setup(fiveRm, Programs.GZCLP)
        var seq = 0L
        val entries = setupEvents.map { Entry(++seq, now, it) }.toMutableList()

        // T2 OHP edited to 95 lb 3x10 (e1RM = 133 lb)
        // T1 OHP 105 has e1RM = 122.5 lb.
        // Blended with α = 0.6: EMA = 128.8 lb -> suggestion for 5x3 is 110 lb.
        entries.add(Entry(++seq, now, ProgressionEvent("ohp", "t2", "manual_edit", toWeight = 95, toStage = "3x10")))
        val appState = dev.gzclp.tracker.log.AppState.fold(entries)

        // Day B1 has T1 OHP (plan 105 lb)
        val dayB1 = appState.program.day("B1")!!
        val adaptEvents = appState.adaptSessionStartEvents(dayB1)
        assertFalse(adaptEvents.isEmpty())
        val ohpAdapt = adaptEvents.filterIsInstance<ProgressionEvent>().firstOrNull { it.exercise == "ohp" }
        assertNotNull(ohpAdapt)
        assertEquals(ChangeKind.MANUAL_EDIT.id, ohpAdapt!!.kind)
        assertEquals(105, ohpAdapt.fromWeight)
        assertEquals(110, ohpAdapt.toWeight)
    }

    @Test
    fun manualEditDownAfterAStrongSessionLowersTheModel() {
        val fiveRm = mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115)
        var state = AppState.fold(Commands.setup(fiveRm, Programs.GZCLP).mapIndexed { i, e -> Entry(i.toLong(), now, e) })
        val day = state.nextDay
        state = Commands.startOpen(state, now).fold(state) { s, e -> s.apply(Entry(50, now, e)) }
        val squat = state.exercises(day).first { it.id == "squat" && it.tier == Tier.T1 }
        state = Commands.logSet(state, squat, 1, 5, 300, now, emptyMap()).fold(state) { s, e -> s.apply(Entry(51, now, e)) }
        state = Commands.end(state, now.plusMinutes(30), null).fold(state) { s, e -> s.apply(Entry(52, now, e)) }
        val strong = state.strengthModel().estimate("squat")!!
        val t1 = state.exercises(day).first { it.id == "squat" && it.tier == Tier.T1 }
        state = Commands.editWeight(state, t1, 150).fold(state) { s, e -> s.apply(Entry(53, now, e)) }
        val after = state.strengthModel().estimate("squat")!!
        assertTrue("a lower manual edit lowers the estimate ($after < $strong)", after < strong)
    }
}
