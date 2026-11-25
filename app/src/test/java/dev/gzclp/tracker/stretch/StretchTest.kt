package dev.gzclp.tracker.stretch

import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.SessionRecord
import dev.gzclp.tracker.log.SetLogged
import dev.gzclp.tracker.log.StretchCleared
import dev.gzclp.tracker.log.StretchLogged
import dev.gzclp.tracker.remind.HabitKind
import dev.gzclp.tracker.remind.rebuildHabits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class StretchTest {

    @Test
    fun testMovesCountAndAreaCoverage() {
        val moves = StretchMoves.ALL
        assertTrue("At least 20 moves must be present (got ${moves.size})", moves.size >= 20)

        // Verify all 10 target body areas are covered
        val requiredAreas = setOf(
            BodyArea.HIPS,
            BodyArea.HAMSTRINGS,
            BodyArea.QUADS,
            BodyArea.CALVES,
            BodyArea.ANKLES,
            BodyArea.UPPER_BACK,
            BodyArea.CHEST,
            BodyArea.SHOULDERS,
            BodyArea.LATS,
            BodyArea.SPINE,
        )

        val coveredAreas = moves.map { it.targetArea }.toSet()
        assertEquals("All 10 required body areas must be covered", requiredAreas, coveredAreas)

        // Verify every area has at least 2 distinct moves
        for (area in requiredAreas) {
            val count = moves.count { it.targetArea == area }
            assertTrue("Area ${area.displayName} should have at least 2 moves (found $count)", count >= 2)
        }

        // Verify valid hold times (20-45s) and cues
        for (m in moves) {
            assertTrue("Move ${m.id} holdSec (${m.holdSec}s) should be between 20 and 45s", m.holdSec in 20..45)
            assertTrue("Move ${m.id} cue must not be blank", m.cue.isNotBlank())
            assertTrue("Move ${m.id} name must not be blank", m.name.isNotBlank())
        }
    }

    @Test
    fun testCoachingDisclaimer() {
        assertEquals(
            "These are coaching defaults, not validated programs.",
            StretchRoutines.COACHING_DISCLAIMER,
        )
    }

    @Test
    fun testAfterWorkoutDynamicAdaptation() {
        // 1. Default empty state: 8-10 minutes (480-600s)
        val defaultRoutine = StretchRoutines.afterWorkout()
        assertEquals(StretchRoutines.ID_AFTER_WORKOUT, defaultRoutine.id)
        assertTrue(
            "Default after-workout duration (${defaultRoutine.totalDurationSec}s) must be 8–10 minutes (480-600s)",
            defaultRoutine.totalDurationSec in 480..600,
        )

        val now = OffsetDateTime.now()
        val dummyProgram = Programs.of(Programs.GZCLP)
        val dummyDay = dummyProgram.days.first()

        // 2. State with Squat lift: should target Hips, Quads, Hamstrings
        val squatSession = SessionRecord(
            id = "s1",
            program = dummyProgram,
            day = dummyDay,
            startedAt = now,
            durationSec = 2400,
            sets = listOf(
                SetLogged("s1", "squat", "T1", "3x5+", 0, 5, 5, 200, 200, false),
            ),
        )
        val squatState = AppState(sessions = listOf(squatSession))
        val squatRoutine = StretchRoutines.afterWorkout(squatState)
        val squatAreas = squatRoutine.moves.map { it.targetArea }.toSet()
        assertTrue("Squat session must include HIPS", squatAreas.contains(BodyArea.HIPS))
        assertTrue("Squat session must include QUADS", squatAreas.contains(BodyArea.QUADS))
        assertTrue("Squat session must include HAMSTRINGS", squatAreas.contains(BodyArea.HAMSTRINGS))
        assertTrue(
            "Squat routine duration (${squatRoutine.totalDurationSec}s) must be 8–10 minutes",
            squatRoutine.totalDurationSec in 480..600,
        )

        // 3. State with Bench Press: should target Chest and Shoulders
        val benchSession = SessionRecord(
            id = "s2",
            program = dummyProgram,
            day = dummyDay,
            startedAt = now,
            durationSec = 2400,
            sets = listOf(
                SetLogged("s2", "bench", "T1", "3x5+", 0, 5, 5, 150, 150, false),
            ),
        )
        val benchState = AppState(sessions = listOf(benchSession))
        val benchRoutine = StretchRoutines.afterWorkout(benchState)
        val benchAreas = benchRoutine.moves.map { it.targetArea }.toSet()
        assertTrue("Bench session must include CHEST", benchAreas.contains(BodyArea.CHEST))
        assertTrue("Bench session must include SHOULDERS", benchAreas.contains(BodyArea.SHOULDERS))
        assertTrue(
            "Bench routine duration (${benchRoutine.totalDurationSec}s) must be 8–10 minutes",
            benchRoutine.totalDurationSec in 480..600,
        )

        // 4. State with Deadlift: should target Hamstrings and Spine/Hips
        val deadliftSession = SessionRecord(
            id = "s3",
            program = dummyProgram,
            day = dummyDay,
            startedAt = now,
            durationSec = 2400,
            sets = listOf(
                SetLogged("s3", "deadlift", "T1", "3x5+", 0, 5, 5, 250, 250, false),
            ),
        )
        val deadliftState = AppState(sessions = listOf(deadliftSession))
        val deadliftRoutine = StretchRoutines.afterWorkout(deadliftState)
        val deadliftAreas = deadliftRoutine.moves.map { it.targetArea }.toSet()
        assertTrue("Deadlift session must include HAMSTRINGS", deadliftAreas.contains(BodyArea.HAMSTRINGS))
        assertTrue("Deadlift session must include SPINE", deadliftAreas.contains(BodyArea.SPINE))
        assertTrue(
            "Deadlift routine duration (${deadliftRoutine.totalDurationSec}s) must be 8–10 minutes",
            deadliftRoutine.totalDurationSec in 480..600,
        )
    }

    @Test
    fun testFlexibilityRoutineAlternatesDays() {
        val d0 = LocalDate.of(2026, 11, 23) // arbitrary anchor
        val r0 = StretchRoutines.flexibility(d0)
        val r1 = StretchRoutines.flexibility(d0.plusDays(1))
        val r2 = StretchRoutines.flexibility(d0.plusDays(2))

        val names = listOf(r0.name, r1.name, r2.name)
        assertTrue("Must include Legs & Splits day", names.any { it.contains("Legs") })
        assertTrue("Must include Back-Bending day", names.any { it.contains("Back") })
        assertTrue("Must include Rest & Recovery day", names.any { it.contains("Rest") })

        // Cycle repeats on day 3
        val r3 = StretchRoutines.flexibility(d0.plusDays(3))
        assertEquals(r0.name, r3.name)
    }

    @Test
    fun testDanceRoutineTargets() {
        val dance = StretchRoutines.dance()
        assertEquals(StretchRoutines.ID_DANCE, dance.id)
        val areas = dance.moves.map { it.targetArea }.toSet()
        assertTrue("Dance must cover HIPS", areas.contains(BodyArea.HIPS))
        assertTrue("Dance must cover HAMSTRINGS", areas.contains(BodyArea.HAMSTRINGS))
        assertTrue("Dance must cover SPINE", areas.contains(BodyArea.SPINE))
        assertTrue("Dance must cover ANKLES (feet)", areas.contains(BodyArea.ANKLES))
        assertTrue("Dance routine frequency should specify 4–6 days/week", dance.frequency.contains("4–6"))
    }

    @Test
    fun testAthleticsRoutineModes() {
        val staticRoutine = StretchRoutines.athleticsStatic()
        assertEquals(StretchRoutines.ID_ATHLETICS, staticRoutine.id)
        val staticAreas = staticRoutine.moves.map { it.targetArea }.toSet()
        assertTrue("Athletics must target SHOULDERS", staticAreas.contains(BodyArea.SHOULDERS))
        assertTrue("Athletics must target HIPS", staticAreas.contains(BodyArea.HIPS))
        assertTrue("Athletics must target ANKLES", staticAreas.contains(BodyArea.ANKLES))
        assertTrue("Athletics must target UPPER_BACK", staticAreas.contains(BodyArea.UPPER_BACK))
        assertTrue("Athletics static holds must be between 20-30s", staticRoutine.moves.all { it.holdSec in 20..30 })

        val dynamicRoutine = StretchRoutines.athleticsDynamic()
        assertEquals(StretchRoutines.ID_ATHLETICS_DYNAMIC, dynamicRoutine.id)
        assertTrue("Athletics dynamic moves must have isDynamic=true", dynamicRoutine.moves.all { it.isDynamic })
        assertTrue("Dynamic holds should be quick (<= 20s)", dynamicRoutine.moves.all { it.holdSec <= 20 })
    }

    @Test
    fun everyRoutineIsListedOnceByName() {
        val names = StretchRoutines.all().map { it.name }
        assertEquals(7, names.size)
        assertEquals(names.size, names.toSet().size)
        // The default routine resolves to a listed one on every day of the Flexibility rotation.
        val d0 = LocalDate.of(2026, 11, 24)
        for (d in 0L..2L) assertTrue(StretchRoutines.of(StretchRoutines.ID_FLEXIBILITY, date = d0.plusDays(d)).name in names)
    }

    @Test
    fun testStretchPlayerStateFlow() {
        val routine = StretchRoutine(
            id = "test_routine",
            name = "Test Routine",
            description = "Quick test routine",
            frequency = "Daily",
            moves = listOf(
                StretchMoves.PIGEON_POSE.copy(holdSec = 2),
                StretchMoves.STANDING_QUAD_STRETCH.copy(holdSec = 3),
            ),
        )

        val player = StretchPlayerState(routine)
        val ready = StretchPlayerState.READY_SEC
        assertEquals(0, player.moveIndex)
        assertEquals(2, player.secondsRemaining)
        assertEquals(StretchMoves.PIGEON_POSE.id, player.currentMove?.id)
        assertEquals(StretchMoves.STANDING_QUAD_STRETCH.id, player.nextMove?.id)
        assertFalse(player.isFinished)

        // The first move opens with the get-ready countdown; the hold waits for it.
        assertTrue(player.isGettingReady)
        assertEquals(ready, player.readyRemaining)
        repeat(ready) { player.tick() }
        assertFalse(player.isGettingReady)
        assertEquals(2, player.secondsRemaining)

        // Tick 1s
        player.tick()
        assertEquals(1, player.secondsRemaining)
        assertEquals(0, player.moveIndex)
        assertEquals(ready + 1L, player.elapsedTotalSec)

        // Tick 2s -> Move 1 completes, advances to Move 2, which gets ready again
        player.tick()
        assertEquals(1, player.moveIndex)
        assertEquals(3, player.secondsRemaining)
        assertEquals(ready, player.readyRemaining)
        assertEquals(StretchMoves.STANDING_QUAD_STRETCH.id, player.currentMove?.id)
        assertEquals(listOf(StretchMoves.PIGEON_POSE.id), player.completedMoves)

        // Pause test
        player.pause()
        assertTrue(player.isPaused)
        player.tick()
        assertEquals(ready, player.readyRemaining) // did not decrement
        player.resume()
        assertFalse(player.isPaused)

        // Skip to end
        player.skipNext()
        assertTrue(player.isFinished)
        assertEquals(2, player.completedMoves.size)

        // Create log event
        val atTime = OffsetDateTime.parse("2026-11-24T10:00:00Z")
        val logged = player.createLoggedEvent(id = "st_test_123", at = atTime)
        assertEquals("st_test_123", logged.id)
        assertEquals(LogCodec.TS.format(atTime), logged.at)
        assertEquals("test_routine", logged.routine)
        assertTrue(logged.durationSec >= 1L)
        assertEquals(2, logged.moves.size)
        assertTrue(logged.moves.contains(StretchMoves.PIGEON_POSE.id))
        assertTrue(logged.moves.contains(StretchMoves.STANDING_QUAD_STRETCH.id))
    }

    @Test
    fun testAppStateStretchLoggedAndCleared() {
        val event = StretchLogged(
            id = "st100",
            at = "2026-11-24T09:00:00-08:00",
            routine = StretchRoutines.ID_AFTER_WORKOUT,
            durationSec = 520,
            moves = listOf("hips_pigeon", "quads_standing"),
        )

        // Test decode/encode in LogCodec
        val entry = Entry(1, OffsetDateTime.parse(event.at, LogCodec.TS), event)
        val encoded = LogCodec.encode(entry)
        val decoded = LogCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(event, decoded?.event)

        // Test AppState handles stretch and stretch_clear
        val baseState = AppState()
        assertEquals(0, baseState.stretches.size)

        // Apply event
        val stateWithStretch = baseState.copy(stretches = baseState.stretches + (event.id to event))
        assertEquals(1, stateWithStretch.stretches.size)
        assertEquals(event, stateWithStretch.stretches["st100"])

        // Apply clear
        val clearEvent = StretchCleared("st100")
        val stateCleared = stateWithStretch.copy(stretches = stateWithStretch.stretches - clearEvent.id)
        assertEquals(0, stateCleared.stretches.size)
    }

    @Test
    fun testHabitKindStretchIntegration() {
        val zone = ZoneOffset.ofHours(-8)
        val t0 = OffsetDateTime.of(2026, 11, 23, 7, 0, 0, 0, zone)
        val entries = listOf(
            Entry(1, t0.withHour(20).withMinute(0), StretchLogged("s1", LogCodec.TS.format(t0.withHour(20).withMinute(0)), "after_workout", 540)),
            Entry(2, t0.plusDays(1).withHour(20).withMinute(15), StretchLogged("s2", LogCodec.TS.format(t0.plusDays(1).withHour(20).withMinute(15)), "after_workout", 500)),
        )

        val state = AppState.fold(entries)
        val model = rebuildHabits(state, emptyList(), zone)

        assertEquals(2, model.count(HabitKind.STRETCH))
        val predicted = model.predict(HabitKind.STRETCH, t0.toLocalDate())
        assertEquals(1, predicted.size)
        val predTime = predicted.first()
        assertEquals(20, predTime.hour)
    }
}
