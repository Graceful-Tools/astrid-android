package com.gracefultools.astrid.ui.settings

import com.gracefultools.astrid.Preferences
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.CoreSession
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.core.movesOutbox
import com.gracefultools.astrid.core.movesSettings
import com.gracefultools.astrid.model.AccountSettings
import com.gracefultools.astrid.model.AccountUser
import com.gracefultools.astrid.model.FeatureFlags
import com.gracefultools.astrid.model.OutboxStats
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
import kotlinx.serialization.json.booleanOrNull

data class SettingsState(
    val user: AccountUser? = null,
    val account: AccountSettings? = null,
    /** True once the server's settings have arrived: a layout choice before then would overwrite them. */
    val serverLoaded: Boolean = false,
    val features: FeatureFlags = FeatureFlags(),
    /** The deployment ships GitHub Issues / Google Tasks sync (`refreshCapabilities` → `sync`). */
    val serverOffersGithub: Boolean = false,
    val serverOffersGoogleTasks: Boolean = false,
    val theme: ThemeMode = ThemeMode.OCEAN,
    val outbox: OutboxStats? = null,
    val syncing: Boolean = false,
    val refreshing: Boolean = false,
    val confirmingSignOut: Boolean = false,
    val signedOut: Boolean = false,
    val error: String? = null,
) {
    /** iOS: the capability first, then the rollout flag (null = not asked = show). */
    val showsGoogleTasks: Boolean get() = serverOffersGoogleTasks && features.googleTasks != false
    val showsTaskDisplayMode: Boolean get() = features.projectMode == true
}

/**
 * iOS's SettingsView and its pages (Reminders, Appearance): every value is the core's, every write
 * a core command. Only the theme is this device's (iOS keeps it in @AppStorage too).
 */
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
        work {
            val refreshed = runCatching { core.run(command("refreshSettings")) }.isSuccess
            if (refreshed) _state.update { it.copy(serverLoaded = true) }
            load()
        }
        work { loadCapabilities() }
        work { core.changes.collect { if (it.movesOutbox || it.movesSettings) load() } }
    }

    /** Pull to refresh: the server's settings again. */
    fun refresh() = work {
        _state.update { it.copy(refreshing = true) }
        val refreshed = runCatching { core.run(command("refreshSettings")) }.isSuccess
        _state.update { it.copy(refreshing = false, serverLoaded = it.serverLoaded || refreshed) }
        load()
    }

    fun load() = work {
        val user = runCatching { CoreJson.decodeFromJsonElement(AccountUser.serializer(), core.run(command("currentUser"))) }.getOrNull()
        val account = runCatching { CoreJson.decodeFromJsonElement(AccountSettings.serializer(), core.run(command("settings"))) }.getOrNull()
        val features = runCatching { CoreJson.decodeFromJsonElement(FeatureFlags.serializer(), core.run(command("features"))) }.getOrNull()
        _state.update { it.copy(user = user ?: it.user, account = account ?: it.account, features = features ?: it.features) }
        loadOutbox()
    }

    private suspend fun loadOutbox() {
        val stats = runCatching { CoreJson.decodeFromJsonElement(OutboxStats.serializer(), core.run(command("outboxStats"))) }.getOrNull()
        _state.update { it.copy(outbox = stats ?: it.outbox) }
    }

    /** What the deployment ships — online, best effort: unknown hides the two sync rows, as iOS's default does. */
    private suspend fun loadCapabilities() {
        val caps = runCatching { core.run(command("refreshCapabilities")) as? JsonObject }.getOrNull() ?: return
        val sync = caps["sync"] as? JsonObject
        fun flag(name: String) = (sync?.get(name) as? JsonPrimitive)?.booleanOrNull == true
        _state.update { it.copy(serverOffersGithub = flag("githubIssues"), serverOffersGoogleTasks = flag("googleTasks")) }
    }

    // ── Sync and the Outbox ─────────────────────────────────────────────────────────────────

    /** Sync Now: one core pass, with the spinner up while it runs. Offline is not an error. */
    fun syncNow() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        work {
            try {
                core.run(command("sync"))
            } catch (failure: CoreFailure) {
                if (!failure.isStillPending) _state.update { it.copy(error = failure.message) }
            } finally {
                _state.update { it.copy(syncing = false) }
            }
            load()
        }
    }

    fun refreshOutbox() = work { loadOutbox() }

    /** "Retry dropped writes": give every refused write another go. */
    fun retryDropped() = work {
        runCatching { core.run(command("retryDeadLetters")) }
        loadOutbox()
    }

    // ── Preferences ─────────────────────────────────────────────────────────────────────────

    fun setTheme(mode: ThemeMode) {
        preferences.setTheme(mode)
        _state.update { it.copy(theme = mode) }
    }

    /** Reminder settings — the server's field names, journaled by the core (offline-safe). */
    fun setReminder(field: String, value: Any?) = setReminders(mapOf(field to value))

    fun setReminders(changes: Map<String, Any?>) = work {
        runCatching { core.run(command("updateReminderSettings", "changes" to changes)) }
        load()
    }

    /** The digest goes with the zone it is scheduled in, as iOS sends it; the device's when none is stored. */
    fun setDailyDigest(enabled: Boolean, deviceZone: String = java.time.ZoneId.systemDefault().id) {
        val changes = mutableMapOf<String, Any?>("enableDailyDigest" to enabled)
        if (enabled && _state.value.account?.reminderSettings?.dailyDigestTimezone.isNullOrBlank()) changes["dailyDigestTimezone"] = deviceZone
        setReminders(changes)
    }

    /** Quiet hours on start at iOS's 22:00–08:00; off clears both, because the server merges. */
    fun setQuietHours(enabled: Boolean) = setReminders(
        if (enabled) mapOf("quietHoursStart" to "22:00", "quietHoursEnd" to "08:00")
        else mapOf("quietHoursStart" to null, "quietHoursEnd" to null),
    )

    fun setSmartTasks(field: String, value: Any?) = work {
        try {
            core.run(command("updateSmartTaskSettings", "changes" to mapOf(field to value)))
        } catch (failure: CoreFailure) {
            _state.update { it.copy(error = failure.message) }
        }
        load()
    }

    /** iOS's TaskDisplayMode.mayPersistSelection: not before the server's choice has arrived. */
    fun setTaskDisplayMode(mode: String) {
        if (!_state.value.serverLoaded) return
        setSmartTasks("taskDisplayMode", mode)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    // ── Sign out ────────────────────────────────────────────────────────────────────────────

    fun askSignOut() = _state.update { it.copy(confirmingSignOut = true) }
    fun cancelSignOut() = _state.update { it.copy(confirmingSignOut = false) }

    /** Only from the confirmation: "Are you sure you want to sign out?" */
    fun signOut() {
        if (!_state.value.confirmingSignOut) return
        _state.update { it.copy(confirmingSignOut = false) }
        work {
            runCatching { core.run(command("signOut")) }
            _state.update { it.copy(signedOut = true) }
        }
    }

    val coreVersion: String get() = runCatching { CoreSession.coreVersion }.getOrDefault("?")
}
