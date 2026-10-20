package com.gracefultools.astrid.ui.account

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val me = """{"id":"u1","name":"Ada Lovelace","email":"ada@example.com","createdAt":"2024-03-02T10:00:00Z",
        "verified":false,"hasPendingChange":true,"pendingEmail":"new@example.com"}"""

    private fun vm(core: FakeCore) = ProfileViewModel(core, UnconfinedTestDispatcher())
    private fun core() = FakeCore().apply { answer("currentUser", me) }

    @Test
    fun `AATD-56 the profile shows the signed-in user and the server's three numbers`() = runTest {
        val core = core().apply { answer("profileStats", """{"completed":13,"inspired":3,"supported":7}""") }
        val vm = vm(core).apply { loadProfile() }
        assertEquals("Ada Lovelace", vm.state.value.user?.name)
        assertEquals("2024-03-02T10:00:00Z", vm.state.value.user?.createdAt)
        assertEquals("new@example.com", vm.state.value.user?.pendingEmail)
        assertEquals(13, vm.state.value.stats?.completed)
        assertFalse(vm.state.value.loadingStats)
    }

    @Test
    fun `AATD-56 Edit Profile sends the trimmed new name and the picked photo in one updateProfile`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.saveProfile("  Ada King ", "/cache/photo.jpg")
        val sent = core.last("updateProfile")!!
        assertEquals(JsonPrimitive("Ada King"), sent["name"])
        assertEquals(JsonPrimitive("/cache/photo.jpg"), sent["photoPath"])
    }

    @Test
    fun `AATD-56 an unchanged name and no photo send nothing, a photo alone sends no name`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.saveProfile("Ada Lovelace")
        vm.saveProfile("   ")
        assertFalse(core.kinds().contains("updateProfile"))
        vm.saveProfile("Ada Lovelace", "/cache/p.png")
        assertEquals(JsonNull, core.last("updateProfile")!!["name"])
    }

    @Test
    fun `AATD-56 resend verification shows the server's message`() = runTest {
        val core = core().apply { answer("resendVerification", """{"message":"Verification email sent"}""") }
        val vm = vm(core)
        vm.resendVerification()
        assertEquals("Verification email sent", vm.state.value.success)
    }

    @Test
    fun `AATD-56 passkeys list, rename by id with the trimmed name, and revoke by id`() = runTest {
        val core = core().apply {
            answer("passkeys", """{"passkeys":[{"id":"pk1","name":"Pixel","createdAt":"2025-01-01T00:00:00Z","isSynced":true}]}""")
        }
        val vm = vm(core).apply { loadPasskeys() }
        assertEquals("Pixel", vm.state.value.passkeys?.single()?.name)
        assertTrue(vm.state.value.passkeys!!.single().isSynced)
        vm.renamePasskey("pk1", "  ")
        assertFalse(core.kinds().contains("renamePasskey"))
        vm.renamePasskey("pk1", " Work phone ")
        assertEquals(JsonPrimitive("pk1"), core.last("renamePasskey")!!["id"])
        assertEquals(JsonPrimitive("Work phone"), core.last("renamePasskey")!!["name"])
        vm.revokePasskey("pk1")
        assertEquals(JsonPrimitive("pk1"), core.last("revokePasskey")!!["id"])
    }

    @Test
    fun `AATD-56 export asks the core for the format and the file, then offers it to share`() = runTest {
        val core = core().apply { answer("exportAccount", """{"path":"/files/astrid-export.csv","bytes":10}""") }
        val vm = vm(core)
        vm.export("csv", "/files/astrid-export.csv")
        assertEquals(JsonPrimitive("csv"), core.last("exportAccount")!!["format"])
        assertEquals(JsonPrimitive("/files/astrid-export.csv"), core.last("exportAccount")!!["path"])
        assertEquals("/files/astrid-export.csv", vm.state.value.exported)
        vm.exportShared()
        assertNull(vm.state.value.exported)
    }

    @Test
    fun `AATD-56 delete account needs the exact phrase and the final alert`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.setDeleteText("delete my account")
        vm.askDelete()
        assertFalse(vm.state.value.confirmingDelete)
        vm.deleteAccount()
        assertFalse(core.kinds().contains("deleteAccount"))

        vm.setDeleteText(DELETE_PHRASE)
        vm.deleteAccount() // no final alert yet
        assertFalse(core.kinds().contains("deleteAccount"))
        vm.askDelete()
        assertTrue(vm.state.value.confirmingDelete)
        vm.cancelDelete()
        vm.deleteAccount()
        assertFalse(core.kinds().contains("deleteAccount"))

        vm.askDelete()
        vm.deleteAccount()
        assertEquals(JsonPrimitive("DELETE MY ACCOUNT"), core.last("deleteAccount")!!["confirmation"])
        assertTrue(vm.state.value.signedOut)
    }

    @Test
    fun `AATD-56 a refused delete keeps the person signed in and says why`() = runTest {
        val core = core().apply { answer("deleteAccount") { throw CoreFailure(FailureKind.REFUSED, "nope") } }
        val vm = vm(core)
        vm.setDeleteText(DELETE_PHRASE)
        vm.askDelete()
        vm.deleteAccount()
        assertFalse(vm.state.value.signedOut)
        assertEquals("nope", vm.state.value.error)
    }
}
