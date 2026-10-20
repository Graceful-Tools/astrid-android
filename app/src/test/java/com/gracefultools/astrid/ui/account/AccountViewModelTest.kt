package com.gracefultools.astrid.ui.account

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private fun vm(core: FakeCore) = AccountViewModel(core, UnconfinedTestDispatcher())

    @Test
    fun `AATD-27 an agent's mode changes only to one it allows, and never when locked`() = runTest {
        val core = FakeCore().apply {
            answer("agents", """{"agents":[{"id":"claude","name":"Claude","mode":"api","modes":["api","off"]},{"id":"x","name":"X","mode":"off","locked":true}]}""")
        }
        val vm = vm(core).apply { loadAgents() }
        vm.setAgentMode("claude", "webhook")
        assertFalse(core.kinds().contains("setAgentMode"))
        vm.setAgentMode("x", "api")
        assertFalse(core.kinds().contains("setAgentMode"))
        vm.setAgentMode("claude", "off")
        assertEquals(JsonPrimitive("off"), core.last("setAgentMode")!!["mode"])
    }

    @Test
    fun `AATD-27 only a revocable connection is revoked, by its kind and id`() = runTest {
        val core = FakeCore().apply {
            answer("connections", """{"connections":[{"id":"c1","kind":"accessToken","name":"CLI","revocable":true},{"id":"c2","kind":"session","name":"This phone"}]}""")
        }
        val vm = vm(core).apply { loadConnections() }
        vm.revoke("session", "c2")
        assertFalse(core.kinds().contains("revokeConnection"))
        vm.revoke("accessToken", "c1")
        assertEquals(JsonPrimitive("accessToken"), core.last("revokeConnection")!!["connectionKind"])
    }
}
