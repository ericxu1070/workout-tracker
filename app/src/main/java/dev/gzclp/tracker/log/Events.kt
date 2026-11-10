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
data class ProgramStart(val fiveRm: Map<String, Int>) : Event

@Serializable @SerialName("progression")
data class ProgressionEvent(
    val lift: String,
    val tier: String,
    val kind: String,
    val fromWeight: Int? = null,
    val toWeight: Int,
    val fromStage: String? = null,
    val toStage: String,
    val sessionId: String? = null,
    val fiveRm: Int? = null,
) : Event

@Serializable @SerialName("t3_define")
data class T3Define(val lift: String, val name: String) : Event

@Serializable @SerialName("t3_day")
data class T3Day(val day: String, val lifts: List<String>) : Event

@Serializable @SerialName("session_start")
data class SessionStart(val sessionId: String, val day: String) : Event

@Serializable @SerialName("set")
data class SetLogged(
    val sessionId: String,
    val lift: String,
    val tier: String,
    val stage: String,
    val setIndex: Int,
    val targetReps: Int,
    val actualReps: Int,
    val weight: Int,
    val amrap: Boolean,
    val restSec: Int? = null,
    val restTargetSec: Int? = null,
) : Event

@Serializable @SerialName("set_clear")
data class SetCleared(val sessionId: String, val lift: String, val tier: String, val setIndex: Int) : Event

@Serializable @SerialName("session_end")
data class SessionEnd(val sessionId: String, val day: String, val startedAt: String, val durationSec: Long) : Event

data class Entry(val seq: Long, val ts: OffsetDateTime, val event: Event)

object LogCodec {
    const val VERSION = 1
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

    /** Null for a line this version cannot read (unknown type, newer schema, garbage). */
    fun decode(line: String): Entry? = runCatching {
        val obj = json.parseToJsonElement(line).jsonObject
        if (obj.getValue("v").jsonPrimitive.int > VERSION) return null
        val event = json.decodeFromJsonElement(Event.serializer(), JsonObject(obj - setOf("v", "seq", "ts")))
        Entry(obj.getValue("seq").jsonPrimitive.long, OffsetDateTime.parse(obj.getValue("ts").jsonPrimitive.content, TS), event)
    }.getOrNull()
}
