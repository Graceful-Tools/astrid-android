package com.gracefultools.astrid.ui.signin

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

sealed interface SignInState {
    data object Checking : SignInState
    data class SignedOut(val error: String?) : SignInState
    data object WaitingForBrowser : SignInState
    data object Completing : SignInState
    data object SignedIn : SignInState
}

/**
 * Sign-in through the browser hand-off — the Android form of iOS's ASWebAuthenticationSession and
 * Windows' browser hand-off (ASTRID.md §2). The core builds the URL, holds the PKCE flow, checks the
 * callback and stores the credential; this only opens the page and hands back what came home.
 */
class SignInViewModel(
    private val core: Core,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {

    private val _state = MutableStateFlow<SignInState>(SignInState.Checking)
    val state: StateFlow<SignInState> = _state.asStateFlow()

    /** A URL for the screen to open in a Custom Tab, once. */
    private val _openBrowser = MutableStateFlow<String?>(null)
    val openBrowser: StateFlow<String?> = _openBrowser.asStateFlow()

    fun load() = work {
        val answer = runCatching { core.run(command("isSignedIn")) as? JsonObject }.getOrNull()
        val signedIn = (answer?.get("signedIn") as? JsonPrimitive)?.booleanOrNull == true
        _state.value = if (signedIn) SignInState.SignedIn else SignInState.SignedOut(null)
    }

    /** `provider` is `google`, `github` or `sso`; null shows every way to sign in on the page. */
    fun begin(provider: String?) = work {
        try {
            val answer = core.run(command("beginSignIn", "provider" to provider)) as JsonObject
            _openBrowser.value = (answer["authorizeUrl"] as JsonPrimitive).contentOrNull
            _state.value = SignInState.WaitingForBrowser
        } catch (failure: CoreFailure) {
            _state.value = SignInState.SignedOut(failure.message)
        }
    }

    fun browserOpened() { _openBrowser.value = null }

    fun callback(url: String) = work {
        _state.value = SignInState.Completing
        _openBrowser.value = null
        _state.value = try {
            core.run(command("completeSignIn", "callbackUrl" to url))
            SignInState.SignedIn
        } catch (failure: CoreFailure) {
            SignInState.SignedOut(failure.message)
        }
    }

    fun cancel() = work {
        runCatching { core.run(command("cancelSignIn")) }
        _openBrowser.value = null
        _state.value = SignInState.SignedOut(null)
    }

    /** After the core forgets everything (`signOut`), the app is back at the start. */
    fun signedOut() { _state.value = SignInState.SignedOut(null) }

    companion object {
        /** The fixed callback the server delivers to (astrid-web `desktop-handoff.ts`). */
        fun isCallback(url: String): Boolean = url.startsWith("astrid://auth/callback")
    }
}
