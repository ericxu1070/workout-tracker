package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.Activity
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

class CardioTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-22T08:00:00-08:00")
    private val running = Activities.of("running")!!
    private val file get() = File(tmp.root, "log.jsonl")

    private fun store() = Store(file).apply {
        dispatch(t0.minusDays(30)) { Commands.setup(mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105), Programs.GZCLP) }
    }

    private fun Store.log(a: Activity, at: OffsetDateTime, sec: Long = 1800, id: String? = null, km: Int? = null, rpe: Double? = null, notes: String? = null) =
        dispatch(at.plusSeconds(sec)) { Commands.logCardio(it, id, a, at, sec, km, rpe, notes, at.plusSeconds(sec)) }

    @Test fun startThenStopFinishesTheRunAndSurvivesARebuild() {
        val s = store()
        s.dispatch(t0) { Commands.startCardio(it, running, t0) }
        val run = s.state.value.cardioActive!!
        assertEquals(CardioRun("c1795363200", "running", "Running", t0), run)
        val end = t0.plusSeconds(2490)
        s.dispatch(end) { Commands.stopCardio(it, end, 2490, 8047, 7.2, "  ") }
        val want = Cardio("c1795363200", "running", "Running", t0, 2490, 8047, 7.0, null)
        assertNull(s.state.value.cardioActive)
        assertEquals(mapOf(want.id to want), s.state.value.cardio)
        assertEquals(s.state.value, Store(file).state.value)
        assertTrue(s.dispatch(end) { Commands.stopCardio(it, end, 2490, null, null, null) }.isEmpty())
    }

    @Test fun manualEntryThenEditNewestWins() {
        val s = store()
        val at = t0.minusDays(1).withHour(18)
        val id = s.log(Activities.of("basketball")!!, at, 4500, notes = "Pickup at the Y").single().let { (it.event as CardioLogged).entryId }
        assertEquals("c1795312800", id)
        assertNull(s.state.value.cardioActive)
        s.log(Activities.of("basketball")!!, at, 3900, id, notes = "Pickup at the Y")
        assertEquals(3900L, s.state.value.cardio.getValue(id).durationSec)
        assertEquals(1, s.state.value.cardio.size)
        assertTrue("unchanged edit writes nothing", s.log(Activities.of("basketball")!!, at, 3900, id, notes = "Pickup at the Y").isEmpty())
        assertTrue("unknown id is not an edit", s.log(running, at, 3900, "c1").isEmpty())
        s.log(running, at, 3900, id, notes = "Pickup at the Y")
        assertEquals("running", s.state.value.cardio.getValue(id).activity)
    }

    @Test fun clearDeletesFinishedAndDiscardsRunning() {
        val s = store()
        val id = (s.log(running, t0).single().event as CardioLogged).entryId
        s.dispatch(t0) { Commands.clearCardio(it, id) }
        assertTrue(s.state.value.cardio.isEmpty())
        val later = t0.plusHours(3)
        s.dispatch(later) { Commands.startCardio(it, running, later) }
        val run = s.state.value.cardioActive!!.id
        s.dispatch(later) { Commands.clearCardio(it, run) }
        assertNull(s.state.value.cardioActive)
        assertTrue(s.state.value.cardio.isEmpty())
        assertTrue(s.dispatch(later) { Commands.clearCardio(it, run) }.isEmpty())
    }

    @Test fun liftingAndCardioAreOneAtATime() {
        val s = store()
        while (true) {
            val ex = Commands.missingWeight(s.state.value) ?: break
            s.dispatch(t0) { Commands.startingWeight(it, ex, 50) }
        }
        s.dispatch(t0) { Commands.startCardio(it, running, t0) }
        assertTrue(s.dispatch(t0) { Commands.start(it, t0) }.isEmpty())
        assertTrue("one cardio session at a time", s.dispatch(t0) { Commands.startCardio(it, running, t0) }.isEmpty())
        s.dispatch(t0) { Commands.stopCardio(it, t0.plusMinutes(5), 300, null, null, null) }
        s.dispatch(t0) { Commands.start(it, t0) }
        assertNotNull(s.state.value.active)
        assertTrue(s.dispatch(t0) { Commands.startCardio(it, running, t0) }.isEmpty())
        // A past session can still be entered by hand during a workout.
        assertEquals(1, s.log(running, t0.minusDays(1)).size)
    }

    @Test fun validationBounds() {
        val s = AppState()
        fun log(sec: Long = 1800, start: OffsetDateTime = t0, m: Int? = null, rpe: Double? = null, notes: String? = null, a: Activity = running) =
            Commands.logCardio(s, null, a, start, sec, m, rpe, notes, t0).singleOrNull() as CardioLogged?
        assertNull(log(sec = 59))
        assertNotNull(log(sec = 60))
        assertNotNull(log(sec = 86_400))
        assertNull(log(sec = 86_401))
        assertNull("start in the future", log(start = t0.plusSeconds(1)))
        assertNull("blank name", log(a = Activity("x", " ")))
        assertEquals(null, log(m = 0)!!.distanceM)
        assertEquals(1, log(m = 1)!!.distanceM)
        assertEquals(1_000_000, log(m = 1_000_000)!!.distanceM)
        assertEquals(null, log(m = 1_000_001)!!.distanceM)
        assertEquals(8.5, log(rpe = 8.4)!!.rpe)
        assertEquals(10.0, log(rpe = 10.0)!!.rpe)
        assertEquals(null, log(rpe = 0.5)!!.rpe)
        assertEquals(null, log(rpe = 10.5)!!.rpe)
        assertEquals("Hill repeats", log(notes = "  Hill repeats ")!!.notes)
        assertEquals(null, log(notes = "   ")!!.notes)
        assertEquals(200, log(notes = "x".repeat(300))!!.notes!!.length)
        assertEquals(40, log(a = Activity("x", "y".repeat(50)))!!.name.length)
    }

    @Test fun idsGetSuffixesOnCollision() {
        val s = store()
        val ids = List(3) { (s.log(running, t0).single().event as CardioLogged).entryId }
        assertEquals(listOf("c1795363200", "c1795363200_1", "c1795363200_2"), ids)
        val start = t0.minusSeconds(1800)
        s.dispatch(t0) { Commands.startCardio(it, running, start) }
        s.dispatch(t0) { Commands.clearCardio(it, s.state.value.cardioActive!!.id) }
        s.dispatch(t0) { Commands.startCardio(it, running, start) }
        assertEquals("a running start counts as taken", "c1795361400", s.state.value.cardioActive!!.id)
        assertEquals("c1795361400_1", (s.log(running, start).single().event as CardioLogged).entryId)
    }

    @Test fun recentsAreDistinctNewestFirst() {
        val s = store()
        val spike = Activities.typed("Spikeball")!!
        s.log(running, t0.minusDays(3))
        s.log(Activities.of("yoga")!!, t0.minusDays(2))
        s.log(spike, t0.minusDays(4))
        s.log(running, t0.minusDays(1))
        // Entered later but done earliest: order is by when it was done.
        s.log(Activities.of("rowing")!!, t0.minusDays(5))
        assertEquals(listOf("running", "yoga", "spikeball", "rowing"), Commands.recentActivities(s.state.value, 5).map { it.id })
        assertEquals(listOf("running", "yoga"), Commands.recentActivities(s.state.value, 2).map { it.id })
        assertEquals(spike, Commands.recentActivities(s.state.value, 5)[2])
    }

    @Test fun catalogue() {
        assertEquals(59, Activities.all.size)
        assertEquals(59, Activities.all.map { it.id }.toSet().size)
        assertEquals(59, Activities.all.map { it.name.lowercase() }.toSet().size)
        assertTrue(Activities.all.all { it.name.length <= Activities.NAME_MAX })
        assertEquals(listOf("Running"), Activities.search("JOG").map { it.name })
        assertEquals(listOf("Martial arts"), Activities.search("bjj").map { it.name })
        assertEquals(listOf("Skiing", "Cross-country skiing"), Activities.search("ski").map { it.name }.filter { "ski" in it.lowercase() })
        assertEquals(Activities.all.map { it.name }.sortedBy { it.lowercase() }, Activities.search(" ").map { it.name })
        assertTrue(Activities.search("spikeball").isEmpty())
    }

    @Test fun typedNamesBecomeCustomActivities() {
        assertEquals(Activity("spikeball", "Spikeball"), Activities.typed("  Spikeball "))
        assertEquals(Activity("spike_ball_2v2", "Spike ball 2v2"), Activities.typed("Spike   ball 2v2"))
        assertEquals("a built-in name resolves to it", Activities.of("running"), Activities.typed("running"))
        assertEquals("collides with a built-in id", Activity("stairs_2", "Stairs"), Activities.typed("Stairs"))
        assertEquals(Activity("activity", "!!!"), Activities.typed("!!!"))
        assertNull(Activities.typed("  "))
        assertEquals(Activities.NAME_MAX, Activities.typed("z".repeat(60))!!.name.length)
    }

    @Test fun docExampleResolves() {
        val doc = File("../docs/log-format.md").readText()
        val lines = doc.substringAfter("```jsonl\n").substringBefore("```").lines().filter { it.isNotBlank() }
        val s = AppState.fold(lines.map { LogCodec.decode(it)!! })
        assertNull(s.cardioActive)
        assertEquals(setOf("c1795363200", "c1795312800"), s.cardio.keys)
        assertEquals(3900L, s.cardio.getValue("c1795312800").durationSec)
        assertEquals(8047, s.cardio.getValue("c1795363200").distanceM)
    }
}
