package dev.gzclp.tracker.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.DistanceUnit
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.ActiveSession
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.BodyWeightCleared
import dev.gzclp.tracker.log.BodyWeightLogged
import dev.gzclp.tracker.log.CardioCleared
import dev.gzclp.tracker.log.CardioLogged
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.SessionEnd
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Locale

/** What one dispatch writes to Health Connect. [deletedWeights] are weigh-in entry ids, [deletedSessions] cardio entry ids. */
data class HealthWrites(val upserts: List<Record>, val deletedWeights: List<String>, val deletedSessions: List<String> = emptyList()) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletedWeights.isEmpty() && deletedSessions.isEmpty()
}

/**
 * Turns appended log entries into Health Connect records. Pure. Each record's client id is its log id
 * (session id, cardio or weigh-in entry id) and its version is the entry's seq, so an edit replaces the earlier record.
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
        val deletedSessions = mutableListOf<String>()
        var s = before
        for (entry in entries) {
            when (val e = entry.event) {
                is BodyWeightLogged -> upserts += weight(e, entry.seq)
                is BodyWeightCleared -> deletes += e.entryId
                is CardioLogged -> upserts += cardio(e, entry.seq)
                // A discarded session in progress was never written.
                is CardioCleared -> if (e.entryId in s.cardio) deletedSessions += e.entryId
                is SessionEnd -> s.active?.takeIf { it.id == e.sessionId && it.sets.isNotEmpty() }?.let { upserts += session(s, it, e, entry.seq) }
                else -> {}
            }
            s = s.apply(entry)
        }
        return HealthWrites(upserts, deletes, deletedSessions)
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

    /** One cardio session; distance, pace or speed, and RPE lead the notes, since writing a distance record needs a new permission. */
    fun cardio(e: CardioLogged, seq: Long): ExerciseSessionRecord {
        val at = OffsetDateTime.parse(e.startedAt, LogCodec.TS)
        val unit = Activities.of(e.activity)?.unit ?: DistanceUnit.MI
        val stats = listOfNotNull(
            e.distanceM?.let { distance(it, unit) },
            e.distanceM?.let { pace(e.activity, unit, it, e.durationSec) },
            e.rpe?.let { "RPE " + if (it % 1 == 0.0) "${it.toInt()}" else "$it" },
        ).joinToString(" · ")
        return ExerciseSessionRecord(
            startTime = at.toInstant(), startZoneOffset = at.offset,
            endTime = at.toInstant().plusSeconds(e.durationSec), endZoneOffset = at.offset,
            metadata = Metadata.manualEntry(e.entryId, seq),
            exerciseType = cardioType(e.activity),
            title = e.name,
            notes = listOf(stats, e.notes.orEmpty()).filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { null }?.take(1000),
        )
    }

    /** 5.00 mi, 1500 yd, 2000 m. */
    fun distance(meters: Int, unit: DistanceUnit): String =
        if (unit == DistanceUnit.MI) "%.2f mi".format(Locale.US, meters / unit.meters) else "${Math.round(meters / unit.meters)} ${unit.label}"

    private val PACED = setOf("running", "treadmill", "walking", "hiking", "snowshoeing")

    /** 8:18 /mi on foot, 2:05 /100 yd swimming, 2:01 /500 m rowing, else 14.2 mph. */
    fun pace(activity: String, unit: DistanceUnit, meters: Int, durationSec: Long): String {
        val per = when (unit) {
            DistanceUnit.YD -> 100 to "/100 yd"
            DistanceUnit.M -> 500 to "/500 m"
            DistanceUnit.MI -> if (activity in PACED) 1 to "/mi" else
                return "%.1f mph".format(Locale.US, meters / unit.meters / (durationSec / 3600.0))
        }
        val sec = Math.round(durationSec * per.first * unit.meters / meters)
        val clock = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
        return "$clock ${per.second}"
    }

    /** Health Connect's exercise type for an activity; ones it has no type for (and custom ones) are other workouts, titled by name. */
    fun cardioType(activity: String): Int = when (activity) {
        "running" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        "treadmill" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
        "walking" -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        "hiking" -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
        "cycling" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        "stationary_bike" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
        "swimming" -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
        "open_water_swim" -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER
        "rowing_machine" -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
        "rowing" -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING
        "paddling" -> ExerciseSessionRecord.EXERCISE_TYPE_PADDLING
        "elliptical" -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
        "inline_skating" -> ExerciseSessionRecord.EXERCISE_TYPE_SKATING
        "cross_country_skiing", "skiing" -> ExerciseSessionRecord.EXERCISE_TYPE_SKIING
        "snowshoeing" -> ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING
        "wheelchair" -> ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR
        "stair_climber" -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE
        "stairs" -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING
        "hiit" -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        "boot_camp" -> ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP
        "fitness_class" -> ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS
        "calisthenics" -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
        "yoga" -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
        "pilates" -> ExerciseSessionRecord.EXERCISE_TYPE_PILATES
        "stretching" -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
        "dancing" -> ExerciseSessionRecord.EXERCISE_TYPE_DANCING
        "boxing" -> ExerciseSessionRecord.EXERCISE_TYPE_BOXING
        "kickboxing", "martial_arts" -> ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS
        "climbing" -> ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING
        "gymnastics" -> ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS
        "basketball" -> ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL
        "soccer" -> ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
        "american_football" -> ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN
        "volleyball" -> ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL
        "baseball" -> ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL
        "softball" -> ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL
        "ice_hockey" -> ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY
        "roller_hockey" -> ExerciseSessionRecord.EXERCISE_TYPE_ROLLER_HOCKEY
        "rugby" -> ExerciseSessionRecord.EXERCISE_TYPE_RUGBY
        "ultimate" -> ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC
        "cricket" -> ExerciseSessionRecord.EXERCISE_TYPE_CRICKET
        "handball" -> ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL
        "water_polo" -> ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO
        "tennis" -> ExerciseSessionRecord.EXERCISE_TYPE_TENNIS
        "badminton" -> ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON
        "squash" -> ExerciseSessionRecord.EXERCISE_TYPE_SQUASH
        "racquetball" -> ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL
        "table_tennis" -> ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS
        "golf" -> ExerciseSessionRecord.EXERCISE_TYPE_GOLF
        "fencing" -> ExerciseSessionRecord.EXERCISE_TYPE_FENCING
        "snowboarding" -> ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING
        "ice_skating" -> ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING
        "surfing" -> ExerciseSessionRecord.EXERCISE_TYPE_SURFING
        else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
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
