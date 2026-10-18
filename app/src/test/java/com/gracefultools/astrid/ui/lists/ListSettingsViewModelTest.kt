package com.gracefultools.astrid.ui.lists

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListSettingsViewModelTest {
    private val settings = """{"listId":"l1","name":"Home","color":"#22c55e","colorChoices":["#22c55e","#3b82f6"],
        "privacy":"PRIVATE","canManageMembers":true,"canManageList":true,"canDeleteList":true,"canLeave":false,
        "members":[{"userId":"u-bo","role":"member","user":{"id":"u-bo","name":"Bo"}}]}"""

    private fun vm(core: FakeCore) = ListSettingsViewModel(core, "l1", UnconfinedTestDispatcher())

    @Test
    fun `AATD-15 the screen opens from the cache and refreshes from the server`() = runTest {
        val core = FakeCore().apply { answer("listMembers", settings) }
        val vm = vm(core)
        assertEquals("Home", vm.state.value.settings?.name)
        assertEquals("Bo", vm.state.value.settings?.members?.first()?.display)
        assertTrue(core.kinds().contains("refreshListMembers"))
    }

    @Test
    fun `AATD-15 a rename is sent trimmed, an unchanged one is not`() = runTest {
        val core = FakeCore().apply { answer("listMembers", settings) }
        val vm = vm(core)
        vm.rename("Home")
        assertFalse(core.kinds().contains("updateList"))
        vm.rename("  House ")
        assertEquals(JsonPrimitive("House"), (core.last("updateList")!!["changes"] as JsonObject)["name"])
    }

    @Test
    fun `AATD-15 a refused invite says why — membership is online-only (D31)`() = runTest {
        val core = FakeCore().apply {
            answer("listMembers", settings)
            answer("inviteToList") { throw CoreFailure(FailureKind.REFUSED, "already a member") }
        }
        val vm = vm(core)
        vm.invite("not an email")
        assertFalse(core.kinds().contains("inviteToList"))
        vm.invite("bo@example.com")
        assertEquals("already a member", vm.state.value.error)
    }

    @Test
    fun `AATD-15 deleting closes the screen`() = runTest {
        val core = FakeCore().apply { answer("listMembers", settings) }
        val vm = vm(core)
        vm.delete()
        assertTrue(vm.state.value.gone)
        assertEquals(JsonPrimitive("l1"), core.last("deleteList")!!["listId"])
    }
}
