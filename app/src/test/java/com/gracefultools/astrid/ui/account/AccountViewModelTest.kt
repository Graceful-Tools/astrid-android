package com.gracefultools.astrid.ui.account

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private fun vm(core: FakeCore) = AccountViewModel(core, UnconfinedTestDispatcher())

    @Test
    fun `AATD-27 the profile shows the server's three numbers`() = runTest {
        val core = FakeCore().apply { answer("profileStats", """{"completed":42,"inspired":3,"supported":7}""") }
        val vm = vm(core).apply { loadProfile() }
        assertEquals(42, vm.state.value.stats?.completed)
    }
}
