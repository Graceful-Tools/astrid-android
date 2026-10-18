package com.gracefultools.astrid.ui.lists

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.ListSettings
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ListSettingsState(val settings: ListSettings? = null, val gone: Boolean = false, val error: String? = null)

/**
 * A list's settings and members (iOS's ListSettingsView). What this account may change is the
 * core's answer (`canManageList`, `canManageMembers`, `canDeleteList`, `canLeave`) — never a role
 * comparison here (ASTRID.md rule 9). Membership is online-only on purpose (D31): a refusal shows.
 */
class ListSettingsViewModel(
    private val core: Core,
    private val listId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ListSettingsState())
    val state: StateFlow<ListSettingsState> = _state.asStateFlow()

    init {
        load()
        // The cache opens the screen; the network corrects it.
        work { runCatching { core.run(command("refreshListMembers", "listId" to listId)) }; load() }
    }

    fun load() = work {
        runCatching { CoreJson.decodeFromJsonElement(ListSettings.serializer(), core.run(command("listMembers", "listId" to listId))) }
            .onSuccess { settings -> _state.update { it.copy(settings = settings) } }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == _state.value.settings?.name) return
        change("name" to trimmed)
    }

    fun setColor(color: String) = change("color" to color)
    fun setPrivacy(privacy: String) = change("privacy" to privacy)

    fun setFavorite(favorite: Boolean) = act { core.run(command("setListFavorite", "listId" to listId, "favorite" to favorite)) }

    fun invite(email: String) {
        val trimmed = email.trim()
        if (!trimmed.contains('@')) return
        act { core.run(command("inviteToList", "listId" to listId, "email" to trimmed, "role" to "member")) }
    }

    fun setRole(userId: String, role: String) = act { core.run(command("setMemberRole", "listId" to listId, "userId" to userId, "role" to role)) }
    fun remove(userId: String) = act { core.run(command("removeMember", "listId" to listId, "userId" to userId)) }

    fun leave() = act(goneAfter = true) { core.run(command("leaveList", "listId" to listId)) }
    fun delete() = act(goneAfter = true) { core.run(command("deleteList", "listId" to listId)) }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun change(vararg changes: Pair<String, Any?>) = act { core.run(command("updateList", "listId" to listId, "changes" to changes.toMap())) }

    private fun act(goneAfter: Boolean = false, block: suspend () -> Unit) = work {
        try {
            block()
            if (goneAfter) _state.update { it.copy(gone = true) } else load()
        } catch (failure: CoreFailure) {
            if (failure.isStillPending) {
                if (goneAfter) _state.update { it.copy(gone = true) } else load()
            } else {
                _state.update { it.copy(error = failure.message) }
            }
        }
    }
}
