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
    fun `an error clears when dismissed`() = runTest {
        val vm = vm(FakeCore())
        vm.dismissError()
        assertEquals(null, vm.state.value.error)
    }
}
