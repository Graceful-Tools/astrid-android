package com.gracefultools.astrid.ui.settings

import com.gracefultools.astrid.Preferences
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.CoreSession
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.UserSummary
import com.gracefultools.astrid.ui.common.CoreViewModel
import com.gracefultools.astrid.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

data class SettingsState(
    val user: UserSummary? = null,
    val theme: ThemeMode = ThemeMode.OCEAN,
    val waitingToSend: Int = 0,
    val signedOut: Boolean = false,
)

class SettingsViewModel(
    private val core: Core,
    private val preferences: Preferences,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(SettingsState(theme = preferences.theme.value))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init { load() }

    fun load() = work {
        val user = runCatching { CoreJson.decodeFromJsonElement(UserSummary.serializer(), core.run(command("currentUser"))) }.getOrNull()
        val stats = runCatching { core.run(command("outboxStats")) as? JsonObject }.getOrNull()
        val pending = (stats?.get("pending") as? JsonPrimitive)?.intOrNull ?: 0
        _state.update { it.copy(user = user, waitingToSend = pending) }
    }

    fun setTheme(mode: ThemeMode) {
        preferences.setTheme(mode)
        _state.update { it.copy(theme = mode) }
    }

    fun signOut() = work {
        runCatching { core.run(command("signOut")) }
        _state.update { it.copy(signedOut = true) }
    }

    val coreVersion: String get() = runCatching { CoreSession.coreVersion }.getOrDefault("?")
}
