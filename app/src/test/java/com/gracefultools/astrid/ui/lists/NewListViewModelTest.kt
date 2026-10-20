package com.gracefultools.astrid.ui.lists

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewListViewModelTest {
    private fun vm(core: FakeCore) = NewListViewModel(core, UnconfinedTestDispatcher())
    private fun core() = FakeCore().apply { answer("createList", """{"id":"new1","name":"Trip"}""") }

    @Test
    fun `AATD-40 Create needs a name`() = runTest {
        val core = core()
        val vm = vm(core)
        assertFalse(vm.state.value.canCreate)
        vm.create()
        assertFalse(core.kinds().contains("createList"))
    }

    @Test
    fun `AATD-40 the list is made with its name and description, then each person invited with their role`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.setName("  Trip ")
        vm.setDescription("Packing")
        vm.addInvitee("ann@example.com", "admin")
        vm.addInvitee("ANN@example.com", "member")
        vm.addInvitee("ben@example.com", "member")
        assertEquals(2, vm.state.value.invitees.size)
        vm.create()
        val made = core.last("createList")!!
        assertEquals(JsonPrimitive("Trip"), made["name"])
        assertEquals(JsonPrimitive("Packing"), made["description"])
        val invites = core.sent.filter { it["kind"]!!.jsonPrimitive.content == "inviteToList" }
        assertEquals(listOf("ann@example.com" to "admin", "ben@example.com" to "member"), invites.map { it["email"]!!.jsonPrimitive.content to it["role"]!!.jsonPrimitive.content })
        assertTrue(invites.all { it["listId"]!!.jsonPrimitive.content == "new1" })
        assertTrue(vm.state.value.done)
        assertEquals("new1", vm.state.value.createdId)
    }

    @Test
    fun `AATD-40 an empty description is not sent`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.setName("Trip")
        vm.create()
        assertEquals(JsonNull, core.last("createList")!!["description"])
    }

    @Test
    fun `AATD-40 a refused invitation leaves the list made and names who was not added, and a retry re-sends only them`() = runTest {
        val core = core().apply {
            answer("inviteToList") { cmd ->
                if (cmd["email"]!!.jsonPrimitive.contentOrNull == "bad@example.com") throw CoreFailure(FailureKind.REFUSED, "no") else JsonNull
            }
        }
        val vm = vm(core)
        vm.setName("Trip")
        vm.addInvitee("ok@example.com", "member")
        vm.addInvitee("bad@example.com", "member")
        vm.create()
        assertFalse(vm.state.value.done)
        assertEquals(listOf("bad@example.com"), vm.state.value.failedEmails)
        vm.create()
        assertEquals(1, core.kinds().count { it == "createList" })
        assertEquals(JsonPrimitive("bad@example.com"), core.last("inviteToList")!!["email"])
    }

    @Test
    fun `AATD-40 an invitation queued offline is not a failure`() = runTest {
        val core = core().apply { answer("inviteToList") { throw CoreFailure(FailureKind.OFFLINE, "offline") } }
        val vm = vm(core)
        vm.setName("Trip")
        vm.addInvitee("ann@example.com", "member")
        vm.create()
        assertTrue(vm.state.value.done)
    }

    @Test
    fun `AATD-40 a refused create says why and makes nothing`() = runTest {
        val core = FakeCore().apply { answer("createList") { throw CoreFailure(FailureKind.REFUSED, "too long") } }
        val vm = vm(core)
        vm.setName("Trip")
        vm.create()
        assertEquals("too long", vm.state.value.error)
        assertNull(vm.state.value.createdId)
        assertFalse(core.kinds().contains("inviteToList"))
    }
}
