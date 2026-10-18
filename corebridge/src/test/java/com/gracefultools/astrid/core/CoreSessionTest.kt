package com.gracefultools.astrid.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uniffi.astrid_android.CredentialStore

/**
 * AATD-4. The bridge against the REAL core: the host build of core/astrid-android, loaded through
 * JNA (`jna.library.path`, set in corebridge/build.gradle.kts). No stand-in decides anything here.
 */
class CoreSessionTest {
    private class Memory : CredentialStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun set(key: String, value: String): Boolean { map[key] = value; return true }
        override fun delete(key: String): Boolean { map.remove(key); return true }
    }

    private fun session() = CoreSession.start(":memory:", Memory(), background = false)

    @Test
    fun `AATD-4 the client door answers a cache read`() = runBlocking {
        val answer = session().run(command("lists"))
        assertTrue("lists is an array: $answer", answer is JsonArray)
    }

    @Test
    fun `AATD-4 the rules door renders markdown synchronously`() {
        val blocks = CoreRules.ask(command("renderMarkdown", "text" to "**hi**")) as JsonArray
        assertEquals("paragraph", (blocks.first().jsonObject["kind"] as JsonPrimitive).content)
    }

    @Test
    fun `AATD-4 a refusal comes back as a typed failure, not a crash`() = runBlocking {
        try {
            session().run(command("taskDetail", "taskId" to "no-such-task"))
            fail("expected a failure")
        } catch (failure: CoreFailure) {
            assertEquals(FailureKind.NOT_FOUND, failure.kind)
        }
    }

    @Test
    fun `AATD-4 nobody is signed in on a fresh cache`() = runBlocking {
        val answer = session().run(command("isSignedIn")) as JsonObject
        assertEquals(JsonPrimitive(false), answer["signedIn"])
    }

    @Test
    fun `AATD-4 the hand-off URL names the Android client`() = runBlocking {
        val answer = session().run(command("beginSignIn", "provider" to "google")) as JsonObject
        val url = (answer["authorizeUrl"] as JsonPrimitive).content
        assertTrue(url, url.contains("client=android"))
        assertTrue(url, url.contains("provider=google"))
    }

    @Test
    fun `AATD-4 seeded rows come back in the shape the screens decode`() = runBlocking {
        val core = session()
        core.run(
            CoreJson.parseToJsonElement(
                """{"kind":"seedCache",
                  "lists":[{"id":"l1","name":"Home","color":"#22c55e","privacy":"PRIVATE"}],
                  "tasks":[{"id":"t1","title":"Water plants","priority":2,"listIds":["l1"],"completed":false,
                            "dueDateTime":"2030-01-02T00:00:00.000Z","isAllDay":true,
                            "createdAt":"2026-10-01T00:00:00.000Z","updatedAt":"2026-10-01T00:00:00.000Z"}]}""",
            ).jsonObject,
        )
        val window = core.run(command("rowsForList", "listId" to "l1"))
        println("ROWS " + window)
        val rows = (window.jsonObject["rows"] as JsonArray)
        assertEquals(1, rows.size)
        val row = rows.first().jsonObject
        assertEquals(JsonPrimitive("Water plants"), row["title"])
        assertEquals("on", (row["due"]!!.jsonObject["key"] as JsonPrimitive).content)
        val detail = core.run(command("taskDetail", "taskId" to "t1"))
        println("DETAIL " + detail)
        val lists = core.run(command("lists"))
        println("LISTS " + lists)
        println("MYTASKS " + core.run(command("myTasksList")))
    }
}
