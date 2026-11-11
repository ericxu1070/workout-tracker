package dev.gzclp.tracker.log

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** One line of the log. docs/log-format.md is the contract; keep the two in step. */
@Serializable
sealed interface Event

@Serializable @SerialName("program_start")
data class ProgramStart(val fiveRm: Map<String, Int>, val program: String) : Event

@Serializable @SerialName("program")
data class ProgramChosen(val program: String) : Event

@Serializable @SerialName("increments")
data class IncrementsSet(val upperStep: Int, val lowerStep: Int, val cycleBump: Int) : Event

@Serializable @SerialName("exercise_define")
data class ExerciseDefine(val exercise: String, val name: String, val lower: Boolean) : Event

@Serializable @SerialName("day_exercises")
data class DayExercises(val program: String, val day: String, val exercises: List<String>) : Event

@Serializable @SerialName("progression")
data class ProgressionEvent(
    val exercise: String,
    val tier: String,
    val kind: String,
    val fromWeight: Int? = null,
    val toWeight: Int,
    val fromStage: String? = null,
    val toStage: String,
    val sessionId: String? = null,
    val fiveRm: Int? = null,
    val cycleWeek: Int? = null,
    val cycleBase: Int? = null,
) : Event

@Serializable @SerialName("session_start")
data class SessionStart(val sessionId: String, val program: String, val day: String) : Event

@Serializable @SerialName("set")
data class SetLogged(
    val sessionId: String,
    val exercise: String,
    val tier: String,
    val stage: String,
    val setIndex: Int,
    val targetReps: Int,
    val actualReps: Int,
    val targetWeight: Int,
    val weight: Int,
    val amrap: Boolean,
    val restSec: Int? = null,
    val restTargetSec: Int? = null,
    /** Rate of perceived exertion, 1 to 10 in half steps; null when not given. Never affects progression. */
    val rpe: Double? = null,
) : Event

/** In [sessionId], the slot ([exercise], [tier]) is done as [substitute]; a null [substitute] puts the original back. */
@Serializable @SerialName("exercise_swap")
data class ExerciseSwap(val sessionId: String, val exercise: String, val tier: String, val substitute: String? = null) : Event

@Serializable @SerialName("set_clear")
data class SetCleared(val sessionId: String, val exercise: String, val tier: String, val setIndex: Int) : Event

@Serializable @SerialName("session_end")
data class SessionEnd(val sessionId: String, val program: String, val day: String, val startedAt: String, val durationSec: Long) : Event

/** One weigh-in. [entryId] is its identity: a later line with the same id is an edit. [at] is when it was weighed. */
@Serializable @SerialName("body_weight")
data class BodyWeightLogged(val entryId: String, val at: String, val weight: Double) : Event

@Serializable @SerialName("body_weight_clear")
data class BodyWeightCleared(val entryId: String) : Event

/** A cardio session started now ([Entry.ts] is its start); open until a [CardioLogged] or [CardioCleared] with its [entryId]. */
@Serializable @SerialName("cardio_start")
data class CardioStart(val entryId: String, val activity: String, val name: String) : Event

/** A finished cardio session, or an edit of one: the newest line per [entryId] wins. [durationSec] is active time. */
@Serializable @SerialName("cardio")
data class CardioLogged(
    val entryId: String,
    val activity: String,
    val name: String,
    val startedAt: String,
    val durationSec: Long,
    val distanceM: Int? = null,
    /** 1 to 10 in half steps, as on [SetLogged]. */
    val rpe: Double? = null,
    val notes: String? = null,
) : Event

/** Deletes a finished cardio session, or discards the one in progress. */
@Serializable @SerialName("cardio_clear")
data class CardioCleared(val entryId: String) : Event

data class Entry(val seq: Long, val ts: OffsetDateTime, val event: Event)

object LogCodec {
    const val VERSION = 2
    val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        classDiscriminator = "type"
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(entry: Entry): String {
        val body = json.encodeToJsonElement(Event.serializer(), entry.event).jsonObject
        val head = mapOf(
            "v" to JsonPrimitive(VERSION),
            "seq" to JsonPrimitive(entry.seq),
            "ts" to JsonPrimitive(TS.format(entry.ts)),
        )
        return JsonObject(head + body).toString()
    }

    /** Null for a line this version cannot read (unknown type, other schema version, garbage). */
    fun decode(line: String): Entry? = runCatching {
        val obj = json.parseToJsonElement(line).jsonObject
        if (obj.getValue("v").jsonPrimitive.int != VERSION) return null
        val event = json.decodeFromJsonElement(Event.serializer(), JsonObject(obj - setOf("v", "seq", "ts")))
        Entry(obj.getValue("seq").jsonPrimitive.long, OffsetDateTime.parse(obj.getValue("ts").jsonPrimitive.content, TS), event)
    }.getOrNull()
}
