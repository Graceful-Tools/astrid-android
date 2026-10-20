package com.gracefultools.astrid.ui.lists

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.UserSummary
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Somebody to invite once the list exists. */
data class Invitee(val email: String, val role: String)

data class NewListState(
    val name: String = "",
    val description: String = "",
    val invitees: List<Invitee> = emptyList(),
    val busy: Boolean = false,
    /** The list, once made — a retry only re-sends the invitations that failed. */
    val createdId: String? = null,
    /** iOS's "List created, but failed to add: …": the addresses that were refused. */
    val failedEmails: List<String> = emptyList(),
    val error: String? = null,
    /** Set when everything went: the screen closes and opens the list. */
    val done: Boolean = false,
    val suggestions: List<UserSummary> = emptyList(),
) {
    val canCreate: Boolean get() = name.isNotBlank() && !busy
}

/**
 * iOS's ListEditView for a new list: a name, a description and people to invite with their roles.
 * The list is made with `createList`, then each person is invited with `inviteToList`; an
 * invitation that fails leaves the list made and says who was not added.
 */
class NewListViewModel(
    private val core: Core,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(NewListState())
    val state: StateFlow<NewListState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setDescription(description: String) = _state.update { it.copy(description = description) }

    /** iOS AddMemberSheet's onAdd: an address already added is not added twice. */
    fun addInvitee(email: String, role: String) {
        val trimmed = email.trim()
        if (!trimmed.contains('@')) return
        _state.update { s ->
            if (s.invitees.any { it.email.equals(trimmed, ignoreCase = true) }) s
            else s.copy(invitees = s.invitees + Invitee(trimmed, role))
        }
    }

    fun removeInvitee(email: String) = _state.update { s -> s.copy(invitees = s.invitees.filterNot { it.email == email }) }

    fun create() {
        val s = _state.value
        if (!s.canCreate) return
        _state.update { it.copy(busy = true, error = null) }
        work {
            var listId = s.createdId
            if (listId == null) {
                try {
                    val made = core.run(
                        command(
                            "createList", "name" to s.name.trim(),
                            "description" to s.description.takeIf { it.isNotBlank() },
                            "privacy" to "PRIVATE",
                        ),
                    )
                    listId = ((made as? JsonObject)?.get("id") as? JsonPrimitive)?.content
                } catch (failure: CoreFailure) {
                    _state.update { it.copy(busy = false, error = failure.message) }
                    return@work
                }
            }
            if (listId == null) {
                _state.update { it.copy(busy = false) }
                return@work
            }
            val toInvite = if (s.createdId != null) s.invitees.filter { it.email in s.failedEmails } else s.invitees
            val failed = toInvite.filter { invitee ->
                try {
                    core.run(command("inviteToList", "listId" to listId, "email" to invitee.email, "role" to invitee.role))
                    false
                } catch (failure: CoreFailure) {
                    // Offline means it is queued and will go: not a failure.
                    !failure.isStillPending
                }
            }.map { it.email }
            _state.update {
                it.copy(busy = false, createdId = listId, failedEmails = failed, done = failed.isEmpty())
            }
        }
    }

    /** Suggestions for the Add Member sheet, from 2 characters. */
    fun search(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        searchJob = work {
            delay(ListSettingsViewModel.SEARCH_DEBOUNCE_MS)
            val found = runCatching {
                CoreJson.decodeFromJsonElement(
                    ListSerializer(UserSummary.serializer()),
                    core.run(command("searchUsers", "query" to trimmed)) as? JsonArray ?: JsonArray(emptyList()),
                )
            }.getOrDefault(emptyList())
            val taken = _state.value.invitees.map { it.email.lowercase() }.toSet()
            _state.update { it.copy(suggestions = found.filter { u -> !u.email.isNullOrBlank() && u.email.lowercase() !in taken }) }
        }
    }

    fun clearSuggestions() {
        searchJob?.cancel()
        _state.update { it.copy(suggestions = emptyList()) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
