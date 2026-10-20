package com.gracefultools.astrid.ui.account

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.AccountUser
import com.gracefultools.astrid.model.Passkey
import com.gracefultools.astrid.model.Passkeys
import com.gracefultools.astrid.model.ProfileStats
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ProfileState(
    val user: AccountUser? = null,
    val stats: ProfileStats? = null,
    val loadingStats: Boolean = false,
    val saving: Boolean = false,
    val passkeys: List<Passkey>? = null,
    val loadingPasskeys: Boolean = false,
    val exporting: Boolean = false,
    /** A file the core wrote, waiting for the share sheet. */
    val exported: String? = null,
    val deleteText: String = "",
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    val signedOut: Boolean = false,
    /** A success alert's message (iOS "Success!"), and an error alert's. */
    val success: String? = null,
    val error: String? = null,
) {
    /** iOS DeleteAccountView: the button wakes only on the exact phrase. */
    val deleteMatches: Boolean get() = deleteText == DELETE_PHRASE
}

/**
 * The phrase the core and the server require, typed exactly (astrid-core
 * `services::account::DELETE_CONFIRMATION`). The core refuses anything else without a request; this
 * copy only drives the live match/mismatch line. Not translated, like iOS's.
 */
const val DELETE_PHRASE = "DELETE MY ACCOUNT"

/**
 * iOS's UserProfileView, EditProfileView, AccountSettingsView and DeleteAccountView — the signed-in
 * account. Account facts are the server's, so these are online answers; a refusal shows.
 */
class ProfileViewModel(private val core: Core, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init { loadUser() }

    fun loadUser() = work {
        runCatching { CoreJson.decodeFromJsonElement(AccountUser.serializer(), core.run(command("currentUser"))) }.getOrNull()
            ?.let { user -> _state.update { it.copy(user = user) } }
    }

    fun loadProfile() = work {
        loadUser()
        _state.update { it.copy(loadingStats = true) }
        attempt { CoreJson.decodeFromJsonElement(ProfileStats.serializer(), core.run(command("profileStats"))) }
            ?.let { s -> _state.update { it.copy(stats = s) } }
        _state.update { it.copy(loadingStats = false) }
    }

    /**
     * Save Changes: the name if it changed, the photo if one was picked — one `updateProfile`. The core
     * uploads the photo first and answers with the account redrawn.
     */
    fun saveProfile(name: String?, photoPath: String? = null) {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() && it != _state.value.user?.name }
        if (trimmed == null && photoPath == null) return
        _state.update { it.copy(saving = true) }
        work {
            val done = attempt { core.run(command("updateProfile", "name" to trimmed, "photoPath" to photoPath)) } != null
            _state.update { it.copy(saving = false) }
            if (done) loadUser()
        }
    }

    /** Send / Resend Verification: the server's own message comes back as the success line. */
    fun resendVerification() = work {
        val answer = attempt { core.run(command("resendVerification")) } ?: return@work
        val message = ((answer as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
        _state.update { it.copy(success = message ?: "") }
    }

    // ── Passkeys ────────────────────────────────────────────────────────────────────────────

    fun loadPasskeys() = work {
        _state.update { it.copy(loadingPasskeys = true) }
        attempt { CoreJson.decodeFromJsonElement(Passkeys.serializer(), core.run(command("passkeys"))) }
            ?.let { p -> _state.update { it.copy(passkeys = p.passkeys) } }
        _state.update { it.copy(loadingPasskeys = false) }
    }

    fun renamePasskey(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        work {
            attempt { core.run(command("renamePasskey", "id" to id, "name" to trimmed)) }
            loadPasskeys()
        }
    }

    fun revokePasskey(id: String) = work {
        attempt { core.run(command("revokePasskey", "id" to id)) }
        loadPasskeys()
    }

    // ── Your data ───────────────────────────────────────────────────────────────────────────

    /** The account's data written straight to [path] by the core (JSON or CSV); then shared. */
    fun export(format: String, path: String) {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true) }
        work {
            val done = attempt { core.run(command("exportAccount", "format" to format, "path" to path)) } != null
            _state.update { it.copy(exporting = false, exported = if (done) path else it.exported) }
        }
    }

    fun exportShared() = _state.update { it.copy(exported = null) }

    // ── Delete account ──────────────────────────────────────────────────────────────────────

    fun setDeleteText(text: String) = _state.update { it.copy(deleteText = text) }

    /** "Delete My Account": opens the final alert, and only on the exact phrase. */
    fun askDelete() {
        if (!_state.value.deleteMatches) return
        _state.update { it.copy(confirmingDelete = true) }
    }

    fun cancelDelete() = _state.update { it.copy(confirmingDelete = false) }

    /** "Delete Forever": the core deletes, then signs this device out. */
    fun deleteAccount() {
        val current = _state.value
        if (!current.confirmingDelete || !current.deleteMatches || current.deleting) return
        _state.update { it.copy(confirmingDelete = false, deleting = true) }
        work {
            val done = attempt { core.run(command("deleteAccount", "confirmation" to current.deleteText)) } != null
            _state.update { it.copy(deleting = false, signedOut = done) }
        }
    }

    fun resetDelete() = _state.update { it.copy(deleteText = "", confirmingDelete = false) }

    fun dismissSuccess() = _state.update { it.copy(success = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (failure: CoreFailure) {
        _state.update { it.copy(error = failure.message) }
        null
    } catch (_: IllegalArgumentException) {
        // An answer of a shape this build cannot read: nothing to show rather than a crash.
        null
    }
}
