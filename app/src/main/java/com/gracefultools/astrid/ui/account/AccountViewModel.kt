package com.gracefultools.astrid.ui.account

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AccountState(
    val error: String? = null,
)

/**
 * Agent Hub and Connections — iOS's account screens that are not Apple-only (the profile is
 * [ProfileViewModel]'s). Both are
 * online answers from the core (these are account facts the server holds); a refusal shows.
 */
class AccountViewModel(private val core: Core, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    fun dismissError() = _state.update { it.copy(error = null) }

    private suspend fun <T> load(block: suspend () -> T): T? = try {
        block()
    } catch (failure: CoreFailure) {
        _state.update { it.copy(error = failure.message) }
        null
    }
}
