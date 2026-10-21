package com.gracefultools.astrid.ui.settings

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.platform.DeviceContact
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {
    private val book = listOf(DeviceContact("ann@x.io", "Ann Lee", "+1 555"), DeviceContact("bo@x.io", null, null))

    @Test
    fun `AATD-68 Synced Contacts is the server's count`() = runTest {
        val core = FakeCore().apply { answer("contacts", """{"contacts":[],"total":42}""") }
        val vm = ContactsViewModel(core, { book }, UnconfinedTestDispatcher())
        vm.loadCount()
        assertEquals(42, vm.state.value.count)
    }

    @Test
    fun `AATD-68 Sync Contacts Now hands the address book to the core and shows the new total`() = runTest {
        val core = FakeCore().apply { answer("uploadContacts", """{"total":2,"created":2}""") }
        val vm = ContactsViewModel(core, { book }, UnconfinedTestDispatcher())
        vm.sync()
        val sent = core.last("uploadContacts")!!["contacts"] as JsonArray
        assertEquals(2, sent.size)
        val ann = sent[0] as JsonObject
        assertEquals(JsonPrimitive("ann@x.io"), ann["email"])
        assertEquals(JsonPrimitive("Ann Lee"), ann["name"])
        assertEquals(JsonPrimitive("+1 555"), ann["phoneNumber"])
        assertEquals(2, vm.state.value.count)
        assertNotNull(vm.state.value.lastSync)
        assertFalse(vm.state.value.syncing)
    }

    @Test
    fun `AATD-68 a failed upload keeps the old count and no new sync time`() = runTest {
        val core = FakeCore().apply {
            answer("contacts", """{"contacts":[],"total":7}""")
            answer("uploadContacts") { throw com.gracefultools.astrid.core.CoreFailure(com.gracefultools.astrid.core.FailureKind.OFFLINE, "offline") }
        }
        val vm = ContactsViewModel(core, { book }, UnconfinedTestDispatcher())
        vm.loadCount()
        vm.sync()
        assertEquals(7, vm.state.value.count)
        assertNull(vm.state.value.lastSync)
        assertFalse(vm.state.value.syncing)
    }
}
