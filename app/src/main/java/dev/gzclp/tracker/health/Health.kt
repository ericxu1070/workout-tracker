package dev.gzclp.tracker.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.ActiveSession
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.BodyWeightCleared
import dev.gzclp.tracker.log.BodyWeightLogged
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.SessionEnd
import java.time.Duration
import java.time.OffsetDateTime

/** What one dispatch writes to Health Connect. [deletedWeights] are weigh-in entry ids. */
data class HealthWrites(val upserts: List<Record>, val deletedWeights: List<String>) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletedWeights.isEmpty()
}

/**
 * Turns appended log entries into Health Connect records. Pure. Each record's client id is its log id
 * (session id or weigh-in entry id) and its version is the entry's seq, so an edit replaces the earlier record.
 */
object HealthRecords {
    val PERMISSIONS = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
    )

    /** The log only stamps when a set was logged; a segment covers up to this long before it. */
    private val SET_TIME: Duration = Duration.ofSeconds(60)

    fun of(before: AppState, entries: List<Entry>): HealthWrites {
        val upserts = mutableListOf<Record>()
        val deletes = mutableListOf<String>()
        var s = before
        for (entry in entries) {
            when (val e = entry.event) {
                is BodyWeightLogged -> upserts += weight(e, entry.seq)
                is BodyWeightCleared -> deletes += e.entryId
                is SessionEnd -> s.active?.takeIf { it.id == e.sessionId && it.sets.isNotEmpty() }?.let { upserts += session(s, it, e, entry.seq) }
                else -> {}
            }
            s = s.apply(entry)
        }
        return HealthWrites(upserts, deletes)
    }

    fun weight(e: BodyWeightLogged, seq: Long): WeightRecord {
        val at = OffsetDateTime.parse(e.at, LogCodec.TS)
        return WeightRecord(at.toInstant(), at.offset, Mass.pounds(e.weight), Metadata.manualEntry(e.entryId, seq))
    }

    /** One strength session; each logged set becomes a segment with its reps, and the notes list every set's weight. */
    fun session(state: AppState, a: ActiveSession, end: SessionEnd, seq: Long): ExerciseSessionRecord {
        val start = a.startedAt.toInstant()
        val stop = maxOf(start.plusSeconds(end.durationSec), start.plusSeconds(1))
        val sets = a.sets.values.sortedBy { it.ts }
        var prev = start
        val segments = sets.mapNotNull { set ->
            val to = minOf(set.ts.toInstant(), stop)
            val from = maxOf(prev, to - SET_TIME)
            if (!from.isBefore(to)) return@mapNotNull null
            prev = to
            ExerciseSegment(from, to, segmentType(set.event.exercise), set.event.actualReps)
        }
        val notes = sets.groupBy { it.event.exercise to it.event.tier }.entries.joinToString("\n") { (key, logged) ->
            val (id, tier) = key
            val label = Tier.of(tier)?.takeIf { it != Tier.CYCLE }?.let { " ${it.label}" } ?: ""
            "${state.name(id)}$label: " + logged.joinToString(", ") { "${it.event.weight}×${it.event.actualReps}" } + " lb"
        }
        return ExerciseSessionRecord(
            startTime = start, startZoneOffset = a.startedAt.offset, endTime = stop, endZoneOffset = a.startedAt.offset,
            metadata = Metadata.manualEntry(a.id, seq),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = "${a.day.title} · ${a.program.name}",
            notes = notes.take(1000),
            segments = segments,
        )
    }

    /** Health Connect's closest named movement, or plain weightlifting. */
    fun segmentType(exercise: String): Int = when (exercise) {
        "squat" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT
        "bench" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS
        "deadlift", "romanian_deadlift" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT
        "ohp" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS
        "dumbbell_row" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW
        "lat_pulldown" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN
        "lateral_raise" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE
        "barbell_curl", "hammer_curl" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL
        "tricep_pushdown", "overhead_tricep_extension" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION
        "leg_press" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS
        "leg_curl" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL
        "leg_extension" -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION
        else -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
    }
}
