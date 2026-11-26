package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class MuscleTest {
    private val gz = Programs.of(Programs.GZCLP)
    private fun set(ex: String, reps: Int = 5) = SetLogged("s", ex, Tier.T1.id, "5x3+", 1, 5, reps, 100, 100, false)
    private fun session(at: String, vararg sets: SetLogged) = SessionRecord("s$at", gz, gz.days[0], OffsetDateTime.parse(at), 3600, sets.toList())

    @Test fun everyCatalogMoveWorksAMuscle() {
        for (m in Catalog.all) assertTrue(m.id, Muscles.of(m.id)?.primary?.isNotEmpty() == true)
    }

    @Test fun customMovesMatchByWholeWords() {
        assertEquals(listOf(Muscle.HAMSTRINGS), Muscles.of("seated_leg_curl")!!.primary)
        assertEquals(listOf(Muscle.BICEPS), Muscles.of("preacher_curl")!!.primary)
        assertEquals(listOf(Muscle.GLUTES), Muscles.of("hip_thrust")!!.primary)
        assertNull(Muscles.of("farmers_walk"))
    }

    @Test fun primarySetsCountWholeAndSecondaryHalf() {
        val s = AppState(sessions = listOf(
            session("2026-11-10T18:00:00-08:00", set("bench"), set("bench"), set("bench", reps = 0), set("weighted_dip")),
            session("2026-11-08T18:00:00-08:00", set("bench")), // the Sunday before
        ))
        val (from, to) = muscleWindow(LocalDate.parse("2026-11-12"), month = false)
        val w = s.muscleWork(from, to)
        assertEquals(MuscleWork(3, 3.0), w[Muscle.CHEST]) // two bench, one dip; the failed set is left out
        assertEquals(MuscleWork(3, 2.0), w[Muscle.TRICEPS]) // bench helps twice, dip trains once
        assertEquals(MuscleWork(3, 1.5), w[Muscle.SHOULDERS])
        assertNull(w[Muscle.QUADS])
        assertEquals(4.0, s.muscleWork(muscleWindow(LocalDate.parse("2026-11-12"), month = true).first, to)[Muscle.CHEST]!!.load, 0.0)
    }

    @Test fun theRunningSessionCounts() {
        val start = OffsetDateTime.parse("2026-11-12T18:00:00-08:00")
        val a = ActiveSession("a", gz, gz.days[0], start, mapOf(SetKey("squat", Tier.T1, 1) to LoggedSet(set("squat"), start)))
        val w = AppState(active = a).muscleWork(LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-12"))
        assertNotNull(w[Muscle.QUADS])
    }

    @Test fun levelsScaleToTheMostWorkedOrTenSetsAWeek() {
        assertEquals(mapOf(Muscle.CHEST to 0.5f), Muscles.levels(mapOf(Muscle.CHEST to MuscleWork(5, 5.0)), 3))
        assertEquals(mapOf(Muscle.CHEST to 1f, Muscle.BICEPS to 0.25f), Muscles.levels(mapOf(Muscle.CHEST to MuscleWork(20, 20.0), Muscle.BICEPS to MuscleWork(5, 5.0)), 7))
        assertEquals(mapOf(Muscle.CHEST to 0.5f), Muscles.levels(mapOf(Muscle.CHEST to MuscleWork(20, 20.0)), 28)) // four weeks want 40
        assertEquals(emptyMap<Muscle, Float>(), Muscles.levels(emptyMap(), 7))
    }
}
