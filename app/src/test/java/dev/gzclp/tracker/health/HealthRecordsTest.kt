package dev.gzclp.tracker.health

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.WeightRecord
import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.DistanceUnit
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Event
import dev.gzclp.tracker.log.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

class HealthRecordsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = OffsetDateTime.parse("2026-11-02T18:00:00-08:00")
    private val rest = dev.gzclp.tracker.engine.Tier.entries.associateWith { it.defaultRestSec }

    private fun store() = Store(File(tmp.root, "log.jsonl")).apply {
        dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 225, Lift.BENCH to 165, Lift.DEADLIFT to 275, Lift.OHP to 105), Programs.GZCLP) }
        while (true) {
            val ex = Commands.missingWeight(state.value) ?: break
            dispatch(t0) { Commands.startingWeight(it, ex, 50) }
        }
    }

    /** Dispatches like AppModel does and returns what it would write. */
    private fun Store.write(at: OffsetDateTime, build: (AppState) -> List<Event>): HealthWrites {
        val before = state.value
        return HealthRecords.of(before, dispatch(at, build))
    }

    @Test fun finishedWorkoutBecomesOneStrengthSessionWithASegmentPerSet() {
        val s = store()
        s.dispatch(t0) { Commands.start(it, t0) }
        val exercises = s.state.value.exercises(s.state.value.active!!.day)
        var t = t0
        for (ex in exercises) for (i in 1..ex.scheme.sets) {
            t = t.plusSeconds(90)
            val at = t
            s.dispatch(at) { Commands.logSet(it, ex, i, ex.scheme.reps, ex.weight!!, at, rest) }
        }
        val sets = exercises.sumOf { it.scheme.sets }
        val end = t.plusMinutes(2)
        val w = s.write(end) { Commands.end(it, end, null) }

        val rec = w.upserts.single() as ExerciseSessionRecord
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, rec.exerciseType)
        assertEquals(t0.toInstant(), rec.startTime)
        assertEquals(end.toInstant(), rec.endTime)
        assertEquals("s" + t0.toEpochSecond(), rec.metadata.clientRecordId)
        assertEquals(sets, rec.segments.size)
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, rec.segments.first().segmentType)
        assertEquals(exercises.first().scheme.reps, rec.segments.first().repetitions)
        assertEquals(60L, rec.segments.first().let { it.endTime.epochSecond - it.startTime.epochSecond })
        assertEquals("Workout A1 · GZCLP", rec.title)
        assertTrue(rec.notes!!, rec.notes!!.startsWith("Squat T1: ${exercises.first().weight}×3, "))
    }

    @Test fun setsLoggedInTheSameSecondStillMakeAValidSession() {
        val s = store()
        s.dispatch(t0) { Commands.start(it, t0) }
        val ex = s.state.value.exercises(s.state.value.active!!.day).first()
        for (i in 1..3) s.dispatch(t0) { Commands.logSet(it, ex, i, 3, ex.weight!!, t0, rest) }
        val w = s.write(t0) { Commands.end(it, t0, null) }
        val rec = w.upserts.single() as ExerciseSessionRecord
        assertTrue(rec.endTime.isAfter(rec.startTime))
        assertEquals(0, rec.segments.size)
    }

    @Test fun emptyWorkoutWritesNothing() {
        val s = store()
        s.dispatch(t0) { Commands.start(it, t0) }
        assertTrue(s.write(t0.plusMinutes(5)) { Commands.end(it, t0.plusMinutes(5), null) }.isEmpty)
    }

    @Test fun weighInsUpsertByEntryIdAndClearsDelete() {
        val s = store()
        val at = t0.withHour(7)
        val logged = s.write(at) { Commands.logBodyWeight(it, null, at, 181.4) }
        val rec = logged.upserts.single() as WeightRecord
        val id = s.state.value.bodyWeights.keys.single()
        assertEquals(id, rec.metadata.clientRecordId)
        assertEquals(181.4, rec.weight.inPounds, 1e-9)
        assertEquals(at.toInstant(), rec.time)

        val edited = s.write(at) { Commands.logBodyWeight(it, id, at, 180.0) }.upserts.single() as WeightRecord
        assertEquals(id, edited.metadata.clientRecordId)
        assertTrue(edited.metadata.clientRecordVersion > rec.metadata.clientRecordVersion)

        assertEquals(listOf(id), s.write(at) { Commands.clearBodyWeight(it, id) }.deletedWeights)
    }

    @Test fun everyMappedSegmentFitsAStrengthSession() {
        val ids = dev.gzclp.tracker.engine.Catalog.all.map { it.id } + "custom"
        val start = t0.toInstant()
        val segments = ids.mapIndexed { i, id ->
            ExerciseSegment(start.plusSeconds(i * 10L), start.plusSeconds(i * 10L + 5), HealthRecords.segmentType(id), 1)
        }
        // The constructor rejects a segment type the session type does not allow.
        ExerciseSessionRecord(
            start, t0.offset, start.plusSeconds(ids.size * 10L), t0.offset,
            androidx.health.connect.client.records.metadata.Metadata.manualEntry(),
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, segments = segments,
        )
    }

    @Test fun finishedCardioBecomesASessionOfItsTypeWithStatsInTheNotes() {
        val s = store()
        val run = Activities.of("running")!!
        s.dispatch(t0) { Commands.startCardio(it, run, t0) }
        val end = t0.plusSeconds(2490)
        val w = s.write(end) { Commands.stopCardio(it, end, 2490, 8047, 7.0, "Easy loop") }
        val rec = w.upserts.single() as ExerciseSessionRecord
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, rec.exerciseType)
        assertEquals(t0.toInstant(), rec.startTime)
        assertEquals(end.toInstant(), rec.endTime)
        assertEquals(t0.offset, rec.startZoneOffset)
        assertEquals("c" + t0.toEpochSecond(), rec.metadata.clientRecordId)
        assertEquals("Running", rec.title)
        assertEquals("5.00 mi · 8:18 /mi · RPE 7\nEasy loop", rec.notes)
    }

    @Test fun startingCardioWritesNothing() {
        assertTrue(store().write(t0) { Commands.startCardio(it, Activities.of("running")!!, t0) }.isEmpty)
    }

    @Test fun cardioEditKeepsTheClientIdWithAHigherVersion() {
        val s = store()
        val ball = Activities.of("basketball")!!
        val first = s.write(t0) { Commands.logCardio(it, null, ball, t0, 4500, null, null, "Pickup", t0) }.upserts.single() as ExerciseSessionRecord
        val id = s.state.value.cardio.keys.single()
        val edited = s.write(t0) { Commands.logCardio(it, id, ball, t0, 3900, null, 6.5, "Pickup", t0) }.upserts.single() as ExerciseSessionRecord
        assertEquals(id, first.metadata.clientRecordId)
        assertEquals(id, edited.metadata.clientRecordId)
        assertTrue(edited.metadata.clientRecordVersion > first.metadata.clientRecordVersion)
        assertEquals(t0.toInstant().plusSeconds(3900), edited.endTime)
        assertEquals("RPE 6.5\nPickup", edited.notes)
    }

    @Test fun deletingAFinishedCardioSessionDeletesItAndDiscardingARunningOneWritesNothing() {
        val s = store()
        s.dispatch(t0) { Commands.logCardio(it, null, Activities.of("yoga")!!, t0, 1800, null, null, null, t0) }
        val id = s.state.value.cardio.keys.single()
        val del = s.write(t0) { Commands.clearCardio(it, id) }
        assertEquals(listOf(id), del.deletedSessions)
        assertTrue(del.upserts.isEmpty() && del.deletedWeights.isEmpty())

        val later = t0.plusHours(2)
        s.dispatch(later) { Commands.startCardio(it, Activities.of("cycling")!!, later) }
        val running = s.state.value.cardioActive!!.id
        assertTrue(s.write(later) { Commands.clearCardio(it, running) }.isEmpty)
    }

    @Test fun cardioTypesMapAndCustomActivitiesAreOtherWorkouts() {
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, HealthRecords.cardioType("swimming"))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS, HealthRecords.cardioType("kickboxing"))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, HealthRecords.cardioType("pickleball"))
        val spike = Activities.typed("Spikeball")!!
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, HealthRecords.cardioType(spike.id))
        // Every built-in other than these few has a named type.
        val other = Activities.all.filter { HealthRecords.cardioType(it.id) == ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT }.map { it.id }
        assertEquals(setOf("jump_rope", "lacrosse", "pickleball", "other"), other.toSet())
    }

    @Test fun paceOrSpeedFollowsTheActivity() {
        assertEquals("1500 yd", HealthRecords.distance(1372, DistanceUnit.YD))
        assertEquals("2:00 /100 yd", HealthRecords.pace("swimming", DistanceUnit.YD, 1372, 1800))
        assertEquals("2:00 /500 m", HealthRecords.pace("rowing_machine", DistanceUnit.M, 2000, 480))
        assertEquals("15.0 mph", HealthRecords.pace("cycling", DistanceUnit.MI, 24140, 3600))
        assertEquals("1:00:00 /mi", HealthRecords.pace("hiking", DistanceUnit.MI, 3219, 7200))
    }
}
