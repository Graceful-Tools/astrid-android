package com.gracefultools.astrid.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.withContext
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

    /**
     * Awaited on [Dispatchers.IO], never on the caller's dispatcher. UniFFI resumes the waiting
     * coroutine from a core thread; resumed inline there (an unconfined or immediate dispatcher),
     * the coroutine calls back into Rust, a nested callback returns, JNA detaches the thread while
     * the outer Kotlin frame is still running, and ART aborts the process ("attempting to detach
     * while still running code"). On IO every resumption is dispatched off the core's thread.
     */
    override suspend fun run(command: JsonObject): JsonElement =
        withContext(Dispatchers.IO) { answerOf(client.run(command.toString())) }

    /**
     * Run a command that stays on this device and wait for it on this thread — for the one read
     * that must land before the first frame. The core refuses anything that could reach the network.
     */
    fun runBlocking(command: JsonObject): JsonElement = answerOf(client.runBlocking(command.toString()))

    /** Ask the background loops to stop. Commands still run afterwards. */
    fun stop() = client.stop()

    /** The core's `Config`; a null field is left out, so the core takes its own default (CoreJson). */
    @Serializable
    private data class Config(
        val cachePath: String,
        val baseUrl: String? = null,
        val platform: String,
        val urlScheme: String? = null,
        val handoffClientId: String? = null,
    )

    companion object {
        /**
         * @param cachePath the SQLite file the cache lives in, or `":memory:"` for a test.
         * @param baseUrl the server; the core's default (astrid.cc) when null.
         * @param background start the loops that keep the app current. Tests pass `false`.
         * @param urlScheme the scheme the sign-in callback arrives on; the core's (`astrid`) when null.
         * @param handoffClientId the server's id for this app; the core's (`android`) when null.
         */
        fun start(
            cachePath: String,
            credentials: CredentialStore,
            background: Boolean,
            baseUrl: String? = null,
            urlScheme: String? = null,
            handoffClientId: String? = null,
        ): CoreSession =
            CoreSession(CoreClient.start(configJson(cachePath, baseUrl, urlScheme, handoffClientId), credentials, background))

        /** What [start] hands the core (astrid-core `Config`, AWTD2-95's `urlScheme` / `handoffClientId`). */
        internal fun configJson(
            cachePath: String,
            baseUrl: String? = null,
            urlScheme: String? = null,
            handoffClientId: String? = null,
        ): String = CoreJson.encodeToString(
            Config.serializer(),
            Config(cachePath, baseUrl, CorePlatform.ANDROID.wire, urlScheme, handoffClientId),
        )

        /** The version of astrid-core this build links. */
        val coreVersion: String get() = uniffi.astrid_android.coreVersion()
    }
}

/** The rules door: pure, synchronous, safe while composing. */
object CoreRules {
    fun ask(command: JsonObject): JsonElement = answerOf(uniffi.astrid_android.runRule(command.toString()))
}
