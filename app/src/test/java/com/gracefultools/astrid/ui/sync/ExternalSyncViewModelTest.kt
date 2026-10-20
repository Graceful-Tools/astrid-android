package com.gracefultools.astrid.ui.sync

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExternalSyncViewModelTest {
    private val sync = """{"listId":"","providers":[
        {"provider":"google_tasks","connected":true,"containers":[{"id":"gl1","name":"Groceries"}],"link":null},
        {"provider":"git_hub","connected":false,"containers":[],"link":null}
    ]}"""
    private val lists = """[{"id":"l1","name":"Work"},{"id":"today","name":"Today","isVirtual":true}]"""

    private fun core() = FakeCore().apply {
        answer("externalSync", sync)
        answer("lists", lists)
        answer("googleSyncMode", """{"mode":"all_google_to_astrid","suffix":"[GT]"}""")
    }

    private fun vm(core: FakeCore, provider: SyncProvider) =
        ExternalSyncViewModel(core, provider, UnconfinedTestDispatcher(), now = { 42L }).apply { load() }

    @Test
    fun `AATD-57 the account screen reads the provider's connection, its containers, the real lists and Google's mode`() = runTest {
        val vm = vm(core(), SyncProvider.GOOGLE)
        val state = vm.state.value
        assertTrue(state.connected)
        assertEquals(listOf("gl1"), state.containers.map { it.id })
        assertEquals(listOf("l1"), state.lists.map { it.id })
        assertEquals(GoogleSyncMode.GOOGLE_TO_ASTRID, state.mode)
        assertEquals("[GT]", state.suffixDraft)
        assertFalse(state.isLoading)
    }

    @Test
    fun `AATD-57 Connect opens the core's URL for that provider and re-reads on return`() = runTest {
        val core = core().apply { answer("connectProvider", """{"authorizeUrl":"https://github.com/login"}""") }
        val vm = vm(core, SyncProvider.GITHUB)
        assertFalse(vm.state.value.connected)
        vm.connect()
        assertEquals(JsonPrimitive("git_hub"), core.last("connectProvider")!!["provider"])
        assertEquals("https://github.com/login", vm.state.value.openUrl)
        vm.urlOpened()
        val reads = core.kinds().count { it == "externalSync" }
        vm.resumed()
        assertEquals(reads + 1, core.kinds().count { it == "externalSync" })
    }

    @Test
    fun `AATD-57 Disconnect sends the provider`() = runTest {
        val core = core()
        val vm = vm(core, SyncProvider.GOOGLE)
        vm.disconnect()
        assertEquals(JsonPrimitive("google_tasks"), core.last("disconnectProvider")!!["provider"])
        assertFalse(vm.state.value.connected)
    }

    @Test
    fun `AATD-57 Link needs both a list and a container and sends them with the provider`() = runTest {
        val core = core()
        val vm = vm(core, SyncProvider.GOOGLE)
        vm.selectList("l1")
        assertFalse(vm.state.value.canLink)
        vm.link()
        assertFalse(core.kinds().contains("linkList"))
        vm.selectContainer("gl1")
        vm.link()
        val sent = core.last("linkList")!!
        assertEquals(JsonPrimitive("google_tasks"), sent["provider"])
        assertEquals(JsonPrimitive("l1"), sent["listId"])
        assertEquals(JsonPrimitive("gl1"), sent["containerId"])
        assertEquals(listOf("Work" to "Groceries"), vm.state.value.linkedHere)
        assertNull(vm.state.value.selectedListId)
    }

    @Test
    fun `AATD-57 the Google mode is written with the suffix, and a refusal puts it back`() = runTest {
        val core = core()
        val vm = vm(core, SyncProvider.GOOGLE)
        vm.setSuffixDraft("[G]")
        vm.setMode(GoogleSyncMode.BIDIRECTIONAL)
        val sent = core.last("setGoogleSyncMode")!!
        assertEquals(JsonPrimitive("all_bidirectional"), sent["mode"])
        assertEquals(JsonPrimitive("[G]"), sent["suffix"])
        core.answer("setGoogleSyncMode") { throw com.gracefultools.astrid.core.CoreFailure(com.gracefultools.astrid.core.FailureKind.REFUSED, "no") }
        vm.setMode(GoogleSyncMode.MANUAL)
        assertEquals(GoogleSyncMode.BIDIRECTIONAL, vm.state.value.mode)
    }

    @Test
    fun `AATD-57 Sync now runs one pass and names a list that failed`() = runTest {
        val core = core().apply { answer("syncExternal", """{"passes":[{"linkId":"k1","report":{}},{"linkId":"k2","error":"tasklist gone"}]}""") }
        val vm = vm(core, SyncProvider.GOOGLE)
        vm.syncNow()
        assertTrue(core.kinds().contains("syncExternal"))
        assertEquals(42L, vm.state.value.lastSyncedAt)
        assertEquals("tasklist gone", vm.state.value.error)
    }

    @Test
    fun `AATD-57 Settings offers GitHub Issues unless the server says no, and Google Tasks only with the flag`() = runTest {
        val core = FakeCore().apply {
            answer("refreshCapabilities", """{"sync":{"googleTasks":true}}""")
            answer("features", """{"googleTasks":false}""")
        }
        val vm = SyncAvailabilityViewModel(core, UnconfinedTestDispatcher()).apply { load() }
        assertEquals(SyncAvailability(github = true, google = false), vm.state.value)
        core.answer("refreshCapabilities", """{"sync":{"githubIssues":false,"googleTasks":true}}""")
        core.answer("features", """{"googleTasks":true}""")
        vm.load()
        assertEquals(SyncAvailability(github = false, google = true), vm.state.value)
    }

    @Test
    fun `AATD-57 a list links, unlinks and picks its coding repository through the core`() = runTest {
        val core = FakeCore().apply {
            answer("features", """{"googleTasks":true}""")
            answer("externalSync", """{"listId":"l1","providers":[{"provider":"git_hub","connected":true,"containers":[{"id":"me/app","name":"app"}],"link":{"id":"k9","astridListId":"l1","remoteContainerId":"me/app"}}]}""")
            answer("listAgentOptions", """{"agents":[{"id":"claude","name":"Claude"}],"repositories":[{"fullName":"me/app","name":"app"}],"githubConnected":true}""")
        }
        val vm = ListExternalSyncViewModel(core, "l1", UnconfinedTestDispatcher()).apply { load() }
        assertEquals(JsonPrimitive("l1"), core.last("externalSync")!!["listId"])
        assertEquals("k9", vm.state.value.sync.of(SyncProvider.GITHUB).link?.id)
        vm.unlink(SyncProvider.GITHUB, "k9")
        assertEquals(JsonPrimitive("k9"), core.last("unlinkList")!!["linkId"])
        assertEquals(JsonPrimitive("git_hub"), core.last("unlinkList")!!["provider"])
        vm.link(SyncProvider.GITHUB, "me/app")
        assertEquals(JsonPrimitive("me/app"), core.last("linkList")!!["containerId"])
        vm.setRepository("me/app")
        assertEquals(JsonPrimitive("me/app"), (core.last("updateList")!!["changes"] as JsonObject)["githubRepositoryId"])
        vm.setRepository(null)
        assertEquals(JsonNull, (core.last("updateList")!!["changes"] as JsonObject)["githubRepositoryId"])
    }
}
