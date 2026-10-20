package com.gracefultools.astrid.ui.connections

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.ui.agents.MintedClient
import com.gracefultools.astrid.ui.agents.decode
import com.gracefultools.astrid.ui.agents.reason
import com.gracefultools.astrid.ui.agents.requiresWebSession
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** One thing that can act as the account — `services::connections::Connection` as the core sends it. */
@Serializable
data class Connection(
    val id: String = "",
    val kind: String = "unknown",
    val category: String? = null,
    val owner: String? = null,
    val name: String = "",
    val actsAs: String? = null,
    val scopes: List<String> = emptyList(),
    val createdAt: String = "",
    val lastUsedAt: String? = null,
    val expiresAt: String? = null,
    val status: String = "unknown",
    val revocable: Boolean = false,
    val manageIn: String? = null,
    /** The client this screen may edit, when it is one — the core's rule. */
    val editableClientId: String? = null,
) {
    val managedOnAgentsPage: Boolean get() = manageIn == "agents"
}

/** A heading and the rows under it, in the core's order. `titleKey` is `connections.category.app` and the like. */
@Serializable
data class ConnectionSection(
    val titleKey: String = "",
    val showsOwnerBadges: Boolean = false,
    val rows: List<Connection> = emptyList(),
)

/** The core's `connections` answer. */
@Serializable
data class ConnectionsAnswer(
    val connections: List<Connection> = emptyList(),
    val sections: List<ConnectionSection> = emptyList(),
    val reviewCount: Int = 0,
)

data class ConnectionsState(
    val isLoading: Boolean = true,
    val panel: ConnectionsAnswer = ConnectionsAnswer(),
    val error: String? = null,
    val requiresWebSession: Boolean = false,
    /** The row a Revoke is waiting to be confirmed for. Nothing is revoked without it. */
    val pendingRevoke: Connection? = null,
    val revoking: Set<String> = emptySet(),
)

/**
 * iOS `ConnectionsModel`: everything that can act as the account, grouped by the core, and a Revoke
 * for each that asks first — anything using a connection stops working the moment it goes.
 */
class ConnectionsViewModel(private val core: Core, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ConnectionsState())
    val state: StateFlow<ConnectionsState> = _state.asStateFlow()

    fun load() = work {
        _state.update { it.copy(isLoading = it.panel.connections.isEmpty(), error = null, requiresWebSession = false) }
        try {
            _state.update { it.copy(panel = decode(ConnectionsAnswer.serializer(), core.run(command("connections")))) }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason(), requiresWebSession = failure.requiresWebSession()) }
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun requestRevoke(row: Connection) {
        if (!row.revocable) return
        _state.update { it.copy(pendingRevoke = row) }
    }

    fun cancelRevoke() = _state.update { it.copy(pendingRevoke = null) }

    fun confirmRevoke() = work {
        val row = _state.value.pendingRevoke ?: return@work
        _state.update { it.copy(pendingRevoke = null, revoking = it.revoking + row.key(), error = null) }
        try {
            core.run(command("revokeConnection", "connectionKind" to row.kind, "id" to row.id))
            _state.update { state ->
                state.copy(panel = decode(ConnectionsAnswer.serializer(), core.run(command("connections"))))
            }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason(), requiresWebSession = failure.requiresWebSession()) }
        } finally {
            _state.update { it.copy(revoking = it.revoking - row.key()) }
        }
    }

    fun isRevoking(row: Connection) = row.key() in _state.value.revoking

    private fun Connection.key() = "$kind:$id"
}

// ── The OAuth client editor ───────────────────────────────────────────────────────────────────

/** One client, for editing (`loadOAuthClient`). */
@Serializable
data class OAuthClientSummary(
    val clientId: String = "",
    val name: String = "",
    val description: String? = null,
    val redirectUris: List<String> = emptyList(),
    val grantTypes: List<String> = emptyList(),
    val scopes: List<String> = emptyList(),
)

/** The first thing to fix, as the core names it: `nameMissing`, `grantRequired`, `redirectRequired`, `redirectInvalid`. */
@Serializable
data class DraftProblem(val key: String = "", val uri: String? = null)

/** `checkOAuthClientDraft`'s answer. */
@Serializable
data class DraftCheck(
    val problem: DraftProblem? = null,
    val canSend: Boolean = false,
    val grantTypes: List<String> = emptyList(),
    val redirectUris: List<String> = emptyList(),
    val scopes: List<String> = emptyList(),
)

data class OAuthClientDraft(
    val name: String = "",
    val description: String = "",
    val scopes: Set<String> = emptySet(),
    val grantTypes: List<String> = listOf("client_credentials"),
    val redirectUriText: String = "",
)

data class OAuthClientEditorState(
    val clientId: String? = null,
    val isLoading: Boolean = false,
    val draft: OAuthClientDraft = OAuthClientDraft(),
    val check: DraftCheck = DraftCheck(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val requiresWebSession: Boolean = false,
    /** Set once, on a successful create: the secret the server will never show again. */
    val minted: MintedClient? = null,
    /** The sheet may close and the list catch up. */
    val finished: Boolean = false,
) {
    val isEditing: Boolean get() = clientId != null
}

/**
 * iOS `OAuthClientEditorModel`: make a connection, or change one's redirect URIs. The rules — which
 * grants travel together, what the first problem is, which scopes may be offered — are the core's
 * `checkOAuthClientDraft`, asked on every edit.
 */
class OAuthClientEditorViewModel(
    private val core: Core,
    clientId: String?,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(OAuthClientEditorState(clientId = clientId))
    val state: StateFlow<OAuthClientEditorState> = _state.asStateFlow()

    fun load() = work {
        val clientId = _state.value.clientId
        if (clientId != null) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val client = decode(OAuthClientSummary.serializer(), core.run(command("loadOAuthClient", "clientId" to clientId)))
                _state.update {
                    it.copy(draft = OAuthClientDraft(client.name, client.description.orEmpty(), client.scopes.toSet(), client.grantTypes, client.redirectUris.joinToString("\n")))
                }
            } catch (failure: Throwable) {
                _state.update { it.copy(error = failure.reason(), requiresWebSession = failure.requiresWebSession()) }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
        check(null)
    }

    fun setName(name: String) = edit { it.copy(name = name) }
    fun setDescription(description: String) = edit { it.copy(description = description) }
    fun setRedirectUris(text: String) = edit { it.copy(redirectUriText = text) }
    fun toggleScope(scope: String) = edit { it.copy(scopes = if (scope in it.scopes) it.scopes - scope else it.scopes + scope) }

    /** The pairing rule (authorization code ⇄ refresh token, never none) is the core's. */
    fun toggleGrant(grant: String) = work { check(grant) }

    private fun edit(change: (OAuthClientDraft) -> OAuthClientDraft) {
        _state.update { it.copy(draft = change(it.draft)) }
        work { check(null) }
    }

    private suspend fun check(toggleGrant: String?) {
        val draft = _state.value.draft
        val fields = fields(draft).toMutableList()
        if (toggleGrant != null) fields += "toggleGrant" to toggleGrant
        val answer = runCatching { decode(DraftCheck.serializer(), core.run(command("checkOAuthClientDraft", *fields.toTypedArray()))) }.getOrNull() ?: return
        _state.update { it.copy(check = answer, draft = if (toggleGrant != null) it.draft.copy(grantTypes = answer.grantTypes) else it.draft) }
    }

    fun save() = work {
        val current = _state.value
        if (!current.check.canSend || current.isSaving) return@work
        _state.update { it.copy(isSaving = true, error = null, requiresWebSession = false) }
        try {
            val clientId = current.clientId
            if (clientId == null) {
                // Held, not dismissed: the secret is shown once and closing on top of it would lose it.
                val minted = decode(MintedClient.serializer(), core.run(command("createOAuthClient", *fields(current.draft).toTypedArray())))
                _state.update { it.copy(minted = minted) }
            } else {
                core.run(command("updateOAuthClient", "clientId" to clientId, *fields(current.draft).toTypedArray()))
                _state.update { it.copy(finished = true) }
            }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason(), requiresWebSession = failure.requiresWebSession()) }
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun dismissMinted() = _state.update { it.copy(minted = null, finished = true) }

    private fun fields(draft: OAuthClientDraft): List<Pair<String, Any?>> = listOf(
        "name" to draft.name,
        "description" to draft.description,
        "scopes" to draft.scopes.sorted(),
        "grantTypes" to draft.grantTypes,
        "redirectUriText" to draft.redirectUriText,
    )
}
