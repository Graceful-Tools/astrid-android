package com.gracefultools.astrid.ui.signin

import com.gracefultools.astrid.Brand
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

/**
 * What the sign-in screen draws from the deployment's `/api/v1/capabilities` (the core's
 * `refreshCapabilities`, unauthenticated so it works before sign-in): the buttons in the server's
 * order, and the brand lockup. Null brand fields mean this build's own wordmark and slogan.
 */
data class SignInLook(
    val providers: List<String> = SignInMethods.offered(null),
    val wordmark: String? = null,
    val slogan: String? = null,
)

/**
 * iOS `SignInMethods.offered(by:)`: the providers the deployment lists, in its order; from a server
 * older than the list, its booleans in the order this app drew before (Google and Passkey on unless
 * said otherwise, GitHub and SSO off unless said). Apple is left out — its native flow is Apple's
 * own and the browser hand-off cannot start it.
 */
object SignInMethods {
    val LEGACY_ORDER = listOf("google", "passkey", "github", "sso")
    private val KNOWN = LEGACY_ORDER.toSet()

    fun offered(capabilities: JsonObject?): List<String> {
        val auth = capabilities?.get("auth") as? JsonObject ?: return LEGACY_ORDER.filter { it == "google" || it == "passkey" }
        val listed = auth["providers"] as? kotlinx.serialization.json.JsonArray
        if (listed != null) {
            return listed.mapNotNull { entry ->
                val id = (entry as? JsonObject)?.get("id") ?: entry
                (id as? JsonPrimitive)?.contentOrNull
            }.filter { it in KNOWN }.distinct()
        }
        fun flag(name: String, default: Boolean) = (auth[name] as? JsonPrimitive)?.booleanOrNull ?: default
        return LEGACY_ORDER.filter { flag(it, default = it == "google" || it == "passkey") }
    }

    /** iOS `Brand.resolvedWordmark` / `resolvedSlogan`: the server's, or null for this build's own. */
    fun look(capabilities: JsonObject?): SignInLook {
        val brand = capabilities?.get("brand") as? JsonObject
        fun text(name: String) = (brand?.get(name) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        return SignInLook(
            providers = offered(capabilities),
            wordmark = text("wordmark") ?: text("appName")?.lowercase(),
            slogan = text("slogan"),
        )
    }
}

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

    private val _look = MutableStateFlow(SignInLook())
    val look: StateFlow<SignInLook> = _look.asStateFlow()

    /**
     * Which buttons and which brand, asked when the sign-in screen is about to show (iOS
     * LoginView's .task) — never in front of a signed-in launch. A failure keeps this build's own.
     */
    private fun loadLook() = work {
        runCatching { core.run(command("refreshCapabilities")) as? JsonObject }.getOrNull()
            ?.let { _look.value = SignInMethods.look(it) }
    }

    fun load() = work {
        val answer = runCatching { core.run(command("isSignedIn")) as? JsonObject }.getOrNull()
        val signedIn = (answer?.get("signedIn") as? JsonPrimitive)?.booleanOrNull == true
        _state.value = if (signedIn) SignInState.SignedIn else SignInState.SignedOut(null)
        if (!signedIn) loadLook()
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
    fun signedOut() {
        _state.value = SignInState.SignedOut(null)
        loadLook()
    }

    companion object {
        /** The callback the server delivers to (astrid-web `desktop-handoff.ts`), on the brand's scheme. */
        fun isCallback(url: String, scheme: String = Brand.urlScheme): Boolean = url.startsWith("$scheme://auth/callback")
    }
}
