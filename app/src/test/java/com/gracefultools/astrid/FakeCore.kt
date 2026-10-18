package com.gracefultools.astrid

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreChange
import com.gracefultools.astrid.core.CoreJson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * A core that answers from a script and records every command — astrid-windows' FakeCore.cs.
 * Answers are keyed by command `kind`; an answer may throw a CoreFailure to script a refusal.
 */
class FakeCore : Core {
    val sent = mutableListOf<JsonObject>()
    private val answers = mutableMapOf<String, (JsonObject) -> JsonElement>()
    private val _changes = MutableSharedFlow<CoreChange>(extraBufferCapacity = 16)
    override val changes: SharedFlow<CoreChange> = _changes

    fun answer(kind: String, json: String) { answers[kind] = { CoreJson.parseToJsonElement(json) } }
    fun answer(kind: String, block: (JsonObject) -> JsonElement) { answers[kind] = block }

    fun emit(kind: String, json: String = "{\"change\":\"$kind\"}") {
        _changes.tryEmit(CoreChange(kind, CoreJson.parseToJsonElement(json).jsonObject))
    }

    fun kinds(): List<String> = sent.map { (it["kind"] as JsonPrimitive).content }
    fun last(kind: String): JsonObject? = sent.lastOrNull { (it["kind"] as? JsonPrimitive)?.contentOrNull == kind }

    override suspend fun run(command: JsonObject): JsonElement {
        sent += command
        val kind = (command["kind"] as JsonPrimitive).content
        return answers[kind]?.invoke(command) ?: JsonNull
    }
}
