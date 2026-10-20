package com.gracefultools.astrid.ui.share

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.SharedFile
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareTaskViewModelTest {
    private fun vm(core: FakeCore) = ShareTaskViewModel(core, UnconfinedTestDispatcher())

    @Test
    fun `AATD-60 a shared file becomes a task for the reader in the default list, with the file attached`() = runTest {
        val core = FakeCore().apply {
            answer("currentUser", """{"id":"u1","name":"Ada"}""")
            answer("createTask", """{"id":"t9","title":"vacation photo"}""")
        }
        val vm = vm(core)
        vm.save(" vacation photo ", "From the beach", 2, SharedFile("/data/outgoing/1-vacation_photo.jpg", "vacation_photo.jpg", "image/jpeg", 2_500_000))
        val create = core.last("createTask")!!
        assertEquals(JsonPrimitive("vacation photo"), create["title"])
        assertEquals(JsonArray(emptyList()), create["listIds"])
        assertEquals(JsonPrimitive("u1"), create["assigneeId"])
        assertEquals(JsonPrimitive(2), create["priority"])
        assertEquals(JsonPrimitive("From the beach"), create["description"])
        val attach = core.last("attachFile")!!
        assertEquals(JsonPrimitive("t9"), attach["taskId"])
        assertEquals(JsonPrimitive("/data/outgoing/1-vacation_photo.jpg"), attach["path"])
        assertEquals(JsonPrimitive("vacation_photo.jpg"), attach["name"])
        assertEquals(JsonPrimitive("image/jpeg"), attach["mimeType"])
        assertTrue(vm.state.value.done)
    }

    @Test
    fun `AATD-60 no priority and no description are left unsaid, and text needs no attachment`() = runTest {
        val core = FakeCore().apply {
            answer("currentUser", """{"id":"u1"}""")
            answer("createTask", """{"id":"t1"}""")
        }
        vm(core).save("Read this https://example.com", "  ", 0, null)
        val create = core.last("createTask")!!
        assertNull(create["priority"])
        assertNull(create["description"])
        assertFalse(core.kinds().contains("attachFile"))
    }

    @Test
    fun `AATD-60 a blank title saves nothing, a refusal is shown, offline is done`() = runTest {
        val core = FakeCore()
        val vm = vm(core)
        vm.save("   ", "", 0, null)
        assertFalse(core.kinds().contains("createTask"))

        core.answer("createTask") { throw CoreFailure(FailureKind.REFUSED, "Not allowed") }
        vm.save("Task", "", 0, null)
        assertEquals("Not allowed", vm.state.value.error)
        assertFalse(vm.state.value.done)

        core.answer("createTask") { throw CoreFailure(FailureKind.OFFLINE, "offline") }
        vm.save("Task", "", 0, null)
        assertTrue(vm.state.value.done)
    }

    @Test
    fun `AATD-60 the title is prefilled from the file name as iOS does`() {
        assertEquals("vacation photo 2026", ShareTaskViewModel.titleFromFileName("vacation_photo-2026.jpg"))
        assertEquals("notes", ShareTaskViewModel.titleFromFileName("notes"))
    }
}
