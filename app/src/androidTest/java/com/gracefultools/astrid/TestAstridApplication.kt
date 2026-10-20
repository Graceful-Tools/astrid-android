package com.gracefultools.astrid

import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.CoreSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import uniffi.astrid_android.CredentialStore

/**
 * The REAL core on the device, with the network loops off and an in-memory cache seeded with a list
 * and three tasks. A stand-in session cookie makes it signed in; the loops are off, so nothing ever
 * sends it anywhere. A UI test can therefore never reach the real account.
 *
 * **Live mode.** Given the `astridLiveCookie` instrumentation argument — a session for the dedicated
 * `uitest@astrid.cc` account, minted by astrid-web `scripts/uitest-account.ts --cookie`, the same one
 * the iOS UI suite uses — the core instead talks to astrid.cc with that cookie and nothing else. The
 * credential store is in memory, so the user's own sign-in on the device is never read or replaced.
 * Only [LiveAccountTest] runs in that mode; it skips itself without the argument.
 */
class TestAstridApplication : AstridApplication() {
    override val schedulesBackgroundWork = false

    override fun makeGraph(): AppGraph = liveCookie()?.let(::liveGraph) ?: seededGraph()

    override fun onCreate() {
        super.onCreate()
        if (liveCookie() != null) graph.scope.launch { fetchTheUser(); liveReady.complete(Unit) }
    }

    /**
     * A real sign-in stores the user with the cookie; a handed cookie arrives alone, and with no user
     * the core syncs nothing. `refreshSettings` fetches the user first. Retried: a process's first
     * request can fail while it is still starting (seen on API 35), and one miss would leave the run
     * empty for good.
     */
    private suspend fun fetchTheUser() {
        repeat(20) { attempt ->
            val answer = runCatching { graph.core.run(CoreJson.parseToJsonElement("""{"kind":"refreshSettings"}""").jsonObject) }
            android.util.Log.i("AstridLive", "refreshSettings #$attempt -> ${answer.fold({ "ok" }, { "failed: $it" })}")
            if (answer.isSuccess) {
                runCatching { graph.core.run(CoreJson.parseToJsonElement("""{"kind":"sync"}""").jsonObject) }
                    .also { android.util.Log.i("AstridLive", "sync -> ${it.getOrNull() ?: it.exceptionOrNull()}") }
                return
            }
            kotlinx.coroutines.delay(1_000)
        }
    }

    private fun liveGraph(cookie: String): AppGraph {
        val credentials = MemoryCredentials(hashMapOf("astrid.session-cookie" to cookie))
        val core = CoreSession.start(":memory:", credentials, background = true, baseUrl = LIVE_BASE_URL)
        return AppGraph(core, CoroutineScope(SupervisorJob() + Dispatchers.Default), Preferences(this))
    }

    private class MemoryCredentials(val map: HashMap<String, String>) : CredentialStore {
        override fun get(key: String) = map[key]
        override fun set(key: String, value: String): Boolean { map[key] = value; return true }
        override fun delete(key: String): Boolean { map.remove(key); return true }
    }

    private fun seededGraph(): AppGraph {
        val credentials = object : CredentialStore {
            val map = hashMapOf(
                "astrid.session-cookie" to "next-auth.session-token=ui-test",
                "astrid.current-user-id" to "u-me",
            )
            override fun get(key: String) = map[key]
            override fun set(key: String, value: String): Boolean { map[key] = value; return true }
            override fun delete(key: String): Boolean { map.remove(key); return true }
        }
        val core = CoreSession.start(":memory:", credentials, background = false)
        runBlocking { core.run(CoreJson.parseToJsonElement(SEED).jsonObject) }
        return AppGraph(core, CoroutineScope(SupervisorJob() + Dispatchers.Default), Preferences(this))
    }

    companion object {
        const val LIVE_BASE_URL = "https://astrid.cc"

        /** Completes once the live account's user and first sync are in the cache. */
        val liveReady = kotlinx.coroutines.CompletableDeferred<Unit>()

        /** The test account's session cookie, when this run was handed one. */
        fun liveCookie(): String? = runCatching {
            androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("astridLiveCookie")
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val SEED = """{"kind":"seedCache",
          "currentUser":{"id":"u-me","name":"Ada Lovelace","email":"ada@example.com"},
          "users":[{"id":"u-me","name":"Ada Lovelace","email":"ada@example.com"}],
          "projects":[{"id":"p-launch","name":"Launch","ownerId":"u-me","key":"LCH"}],
          "lists":[{"id":"l-launch","name":"Launch","color":"#a855f7","privacy":"PRIVATE","ownerId":"u-me","projectId":"p-launch"},{"id":"l-home","name":"Home","color":"#22c55e","privacy":"PRIVATE","ownerId":"u-me","isFavorite":true,"favoriteOrder":1},
                   {"id":"l-work","name":"Work","color":"#3b82f6","privacy":"PRIVATE","ownerId":"u-me"}],
          "tasks":[
            {"id":"t-plan","title":"Plan launch","listIds":["l-launch"],"statusRole":"ready","creatorId":"u-me","assigneeId":"u-me",
             "createdAt":"2026-10-05T00:00:00.000Z","updatedAt":"2026-10-05T00:00:00.000Z"},
            {"id":"t-copy","title":"Write copy","listIds":["l-launch"],"statusRole":"ready","creatorId":"u-me","assigneeId":"u-me",
             "createdAt":"2026-10-04T00:00:00.000Z","updatedAt":"2026-10-04T00:00:00.000Z"},
            {"id":"t-plants","title":"Water plants","priority":2,"listIds":["l-home"],"completed":false,"creatorId":"u-me","assigneeId":"u-me",
             "description":"Use the **blue** can.","createdAt":"2026-10-01T00:00:00.000Z","updatedAt":"2026-10-01T00:00:00.000Z"},
            {"id":"t-mum","title":"Call mum","priority":3,"listIds":["l-home"],"completed":false,"creatorId":"u-me","assigneeId":"u-me",
             "dueDateTime":"2030-01-02T00:00:00.000Z","isAllDay":true,"createdAt":"2026-10-02T00:00:00.000Z","updatedAt":"2026-10-02T00:00:00.000Z"},
            {"id":"t-report","title":"Send the report","priority":1,"listIds":["l-work"],"completed":false,"creatorId":"u-me","assigneeId":"u-me",
             "createdAt":"2026-10-03T00:00:00.000Z","updatedAt":"2026-10-03T00:00:00.000Z"}]}"""
    }
}
