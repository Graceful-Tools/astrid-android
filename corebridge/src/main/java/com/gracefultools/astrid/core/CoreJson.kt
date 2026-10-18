package com.gracefultools.astrid.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The one JSON configuration both doors are read with. The server is permissive; so is this. */
val CoreJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** What kind of failure it was — `astrid_rules::envelope::FailureKind`. The part to branch on. */
enum class FailureKind {
    BAD_REQUEST, UNAUTHORIZED, REFUSED, OFFLINE, NOT_FOUND, CACHE, UNKNOWN;

    companion object {
        fun of(wire: String?): FailureKind = when (wire) {
            "badRequest" -> BAD_REQUEST
            "unauthorized" -> UNAUTHORIZED
            "refused" -> REFUSED
            "offline" -> OFFLINE
            "notFound" -> NOT_FOUND
            "cache" -> CACHE
            else -> UNKNOWN
        }
    }
}

/**
 * Why a command did not work. [message] is for a person and a log, never for branching.
 *
 * [isStillPending] is the one to respect: an offline write is in the Outbox and will go when the
 * network does, so a screen shows it as "waiting to send", never as an error (ASTRID.md rule 6).
 */
class CoreFailure(
    val kind: FailureKind,
    override val message: String,
    val status: Int? = null,
    val id: String? = null,
) : Exception(message) {
    val needsSignIn: Boolean get() = kind == FailureKind.UNAUTHORIZED
    val isStillPending: Boolean get() = kind == FailureKind.OFFLINE
}

/** Read one envelope: the value on `ok`, or the failure thrown. */
fun answerOf(envelope: String): JsonElement {
    val root = try {
        CoreJson.parseToJsonElement(envelope).jsonObject
    } catch (error: IllegalArgumentException) {
        throw CoreFailure(FailureKind.BAD_REQUEST, "the core answered something that is not an envelope")
    }
    if ((root["ok"] as? JsonPrimitive)?.contentOrNull == "true") return root["value"] ?: JsonNull
    val error = root["error"] as? JsonObject
    throw CoreFailure(
        kind = FailureKind.of(error?.get("kind")?.jsonPrimitive?.contentOrNull),
        message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "the core refused without saying why",
        status = error?.get("status")?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
        id = error?.get("id")?.jsonPrimitive?.contentOrNull,
    )
}

/**
 * A command, as the core reads it: `{"kind": …, field: value, …}`. Values go through
 * kotlinx.serialization's JSON tree so a string, a number, a list and `null` all say what they are.
 */
fun command(kind: String, vararg fields: Pair<String, Any?>): JsonObject = buildJsonObject {
    put("kind", JsonPrimitive(kind))
    for ((name, value) in fields) put(name, toJson(value))
}

internal fun toJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Enum<*> -> JsonPrimitive(value.name)
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
    is Iterable<*> -> kotlinx.serialization.json.JsonArray(value.map(::toJson))
    is Array<*> -> kotlinx.serialization.json.JsonArray(value.map(::toJson))
    else -> throw IllegalArgumentException("cannot send ${value::class.simpleName} to the core")
}
