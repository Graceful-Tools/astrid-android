package com.gracefultools.astrid.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import uniffi.astrid_android.ChangeListener
import uniffi.astrid_android.CoreClient
import uniffi.astrid_android.CredentialStore

/**
 * A running astrid-core client — its cache, its Outbox and, when started with [background], the
 * loops that keep them current (sync, delivery, the live stream, reminders, the Google mirror).
 *
 * The Kotlin twin of astrid-ios `CoreSession.swift`.
 */
class CoreSession private constructor(private val client: CoreClient) : Core {

    private val _changes = MutableSharedFlow<CoreChange>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val changes: SharedFlow<CoreChange> = _changes.asSharedFlow()

    init {
        client.subscribe(object : ChangeListener {
            // On one of the core's threads; tryEmit never blocks it.
            override fun onChange(changeJson: String) {
                val raw = runCatching { CoreJson.parseToJsonElement(changeJson).jsonObject }.getOrNull() ?: return
                val kind = (raw["change"] as? JsonPrimitive)?.contentOrNull ?: return
                _changes.tryEmit(CoreChange(kind, raw))
            }
        })
    }

    override suspend fun run(command: JsonObject): JsonElement = answerOf(client.run(command.toString()))

    /**
     * Run a command that stays on this device and wait for it on this thread — for the one read
     * that must land before the first frame. The core refuses anything that could reach the network.
     */
    fun runBlocking(command: JsonObject): JsonElement = answerOf(client.runBlocking(command.toString()))

    /** Ask the background loops to stop. Commands still run afterwards. */
    fun stop() = client.stop()

    @Serializable
    private data class Config(val cachePath: String, val baseUrl: String? = null, val platform: String)

    companion object {
        /**
         * @param cachePath the SQLite file the cache lives in, or `":memory:"` for a test.
         * @param baseUrl the server; the core's default (astrid.cc) when null.
         * @param background start the loops that keep the app current. Tests pass `false`.
         */
        fun start(
            cachePath: String,
            credentials: CredentialStore,
            background: Boolean,
            baseUrl: String? = null,
        ): CoreSession {
            val config = CoreJson.encodeToString(
                Config.serializer(),
                Config(cachePath, baseUrl, CorePlatform.ANDROID.wire),
            )
            return CoreSession(CoreClient.start(config, credentials, background))
        }

        /** The version of astrid-core this build links. */
        val coreVersion: String get() = uniffi.astrid_android.coreVersion()
    }
}

/** The rules door: pure, synchronous, safe while composing. */
object CoreRules {
    fun ask(command: JsonObject): JsonElement = answerOf(uniffi.astrid_android.runRule(command.toString()))
}
