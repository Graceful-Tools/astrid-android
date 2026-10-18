package com.gracefultools.astrid.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Something moved in the core's cache — a colleague's edit on the live stream, a sync pass, a
 * reminder coming due. [kind] is the core's own word (`task`, `list`, `synced`, `reminder`, …) and
 * [raw] the whole notice (`astrid_core::realtime::Change::to_json`).
 */
data class CoreChange(val kind: String, val raw: JsonObject)

/**
 * The client door, as the app sees it. An interface so a view model is tested over a fake: the real
 * one is [CoreSession]; the fake records the commands and answers from a script.
 */
interface Core {
    /** Run one command on the core's own threads. Throws [CoreFailure] on a refusal. */
    suspend fun run(command: JsonObject): JsonElement

    /** Every change to the cache from now on. One stream for the whole app. */
    val changes: SharedFlow<CoreChange>
}

/** Run one command and decode its answer. */
suspend fun <T> Core.ask(command: JsonObject, as_: DeserializationStrategy<T>): T =
    CoreJson.decodeFromJsonElement(as_, run(command))
