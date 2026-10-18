package com.gracefultools.astrid.ui.signin

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private fun vm(core: FakeCore) = SignInViewModel(core, UnconfinedTestDispatcher())

    @Test
    fun `AATD-6 a signed-in session opens on the app, not on sign-in`() = runTest {
        val core = FakeCore().apply { answer("isSignedIn", """{"signedIn":true,"waitingForCallback":false}""") }
        val vm = vm(core)
        vm.load()
        assertEquals(SignInState.SignedIn, vm.state.value)
    }

    @Test
    fun `AATD-6 choosing a provider asks the core for the hand-off URL and opens it`() = runTest {
        val core = FakeCore().apply {
            answer("isSignedIn", """{"signedIn":false,"waitingForCallback":false}""")
            answer("beginSignIn", """{"authorizeUrl":"https://astrid.cc/auth/desktop?client=android&provider=google"}""")
        }
        val vm = vm(core)
        vm.load()
        vm.begin(provider = "google")
        assertEquals(JsonPrimitive("google"), core.last("beginSignIn")!!["provider"])
        assertEquals("https://astrid.cc/auth/desktop?client=android&provider=google", vm.openBrowser.value)
        assertEquals(SignInState.WaitingForBrowser, vm.state.value)
    }

    @Test
    fun `AATD-6 the callback completes sign-in`() = runTest {
        val core = FakeCore().apply {
            answer("beginSignIn", """{"authorizeUrl":"https://astrid.cc/auth/desktop"}""")
            answer("completeSignIn", """{"id":"u1","email":"ada@example.com"}""")
        }
        val vm = vm(core)
        vm.begin(provider = null)
        vm.browserOpened()
        vm.callback("astrid://auth/callback?code=abc&state=xyz")
        assertEquals(JsonPrimitive("astrid://auth/callback?code=abc&state=xyz"), core.last("completeSignIn")!!["callbackUrl"])
        assertEquals(SignInState.SignedIn, vm.state.value)
        assertNull(vm.openBrowser.value)
    }

    @Test
    fun `AATD-6 a refused callback says so and offers sign-in again`() = runTest {
        val core = FakeCore().apply {
            answer("beginSignIn", """{"authorizeUrl":"https://astrid.cc/auth/desktop"}""")
            answer("completeSignIn") { throw CoreFailure(FailureKind.REFUSED, "the state did not match") }
        }
        val vm = vm(core)
        vm.begin(provider = null)
        vm.callback("astrid://auth/callback?code=abc&state=wrong")
        val state = vm.state.value
        assertTrue(state is SignInState.SignedOut && state.error != null)
    }

    @Test
    fun `AATD-6 cancelling abandons the flow in the core`() = runTest {
        val core = FakeCore().apply { answer("beginSignIn", """{"authorizeUrl":"u"}""") }
        val vm = vm(core)
        vm.begin(provider = null)
        vm.cancel()
        assertTrue(core.kinds().contains("cancelSignIn"))
        assertEquals(SignInState.SignedOut(null), vm.state.value)
    }

    @Test
    fun `AATD-6 only an astrid auth callback is taken`() {
        assertTrue(SignInViewModel.isCallback("astrid://auth/callback?code=a&state=b"))
        assertTrue(!SignInViewModel.isCallback("astrid://task/AATD-1"))
        assertTrue(!SignInViewModel.isCallback("https://astrid.cc/auth/callback?code=a"))
    }
}
