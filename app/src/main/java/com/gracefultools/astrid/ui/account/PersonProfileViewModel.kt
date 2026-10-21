package com.gracefultools.astrid.ui.account

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.FailureKind
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.PersonProfile
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PersonProfileState(
    val person: PersonProfile? = null,
    val loading: Boolean = true,
    /** Why it did not load; the screen words it as iOS does ("User not found", "Failed to load…"). */
    val error: FailureKind? = null,
)

/**
 * iOS's UserProfileView opened on somebody else — from the member list or a `users/<id>` link
 * (AATD-69). One `profileTasks` answers who they are, their three numbers and the tasks they share,
 * so the numbers and the rows beneath them come from the same moment. An online answer: the server
 * holds other people's profiles, and nothing of theirs is cached here.
 */
class PersonProfileViewModel(
    private val core: Core,
    private val userId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(PersonProfileState())
    val state: StateFlow<PersonProfileState> = _state.asStateFlow()

    init { load() }

    fun load() = work {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val person = CoreJson.decodeFromJsonElement(PersonProfile.serializer(), core.run(command("profileTasks", "userId" to userId)))
            _state.update { it.copy(person = person, loading = false) }
        } catch (failure: CoreFailure) {
            _state.update { it.copy(loading = false, error = failure.kind) }
        } catch (_: IllegalArgumentException) {
            _state.update { it.copy(loading = false, error = FailureKind.UNKNOWN) }
        }
    }
}
