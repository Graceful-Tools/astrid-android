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
    val account: com.gracefultools.astrid.model.AccountSettings? = null,
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

    init {
        load()
        // The cache opens the screen; the server corrects it.
        work { runCatching { core.run(command("refreshSettings")) }; load() }
    }

    fun load() = work {
        val user = runCatching { CoreJson.decodeFromJsonElement(UserSummary.serializer(), core.run(command("currentUser"))) }.getOrNull()
        val account = runCatching {
            CoreJson.decodeFromJsonElement(com.gracefultools.astrid.model.AccountSettings.serializer(), core.run(command("settings")))
        }.getOrNull()
        _state.update { it.copy(account = account) }
        val stats = runCatching { core.run(command("outboxStats")) as? JsonObject }.getOrNull()
        val pending = (stats?.get("pending") as? JsonPrimitive)?.intOrNull ?: 0
        _state.update { it.copy(user = user, waitingToSend = pending) }
    }

    fun setTheme(mode: ThemeMode) {
        preferences.setTheme(mode)
        _state.update { it.copy(theme = mode) }
    }

    /** Reminder settings — UserDefaults-first on iOS; here the core journals them (offline-safe). */
    fun setReminder(field: String, value: Any?) = work {
        runCatching { core.run(command("updateReminderSettings", "changes" to mapOf(field to value))) }
        load()
    }

    fun setSmartTasks(field: String, value: Any?) = work {
        runCatching { core.run(command("updateSmartTaskSettings", "changes" to mapOf(field to value))) }
        load()
    }

    fun signOut() = work {
        runCatching { core.run(command("signOut")) }
        _state.update { it.copy(signedOut = true) }
    }

    val coreVersion: String get() = runCatching { CoreSession.coreVersion }.getOrDefault("?")
}
