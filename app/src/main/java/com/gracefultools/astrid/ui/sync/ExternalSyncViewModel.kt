package com.gracefultools.astrid.ui.sync

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.ListSummary
import com.gracefultools.astrid.ui.agents.decode
import com.gracefultools.astrid.ui.agents.reason
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Which system, as the core's `Provider` travels (`snake_case`). */
enum class SyncProvider(val wire: String) { GITHUB("git_hub"), GOOGLE("google_tasks") }

/** How Google lists get linked — the core's `SyncMode`, as it travels. */
enum class GoogleSyncMode(val wire: String) {
    MANUAL("manual"),
    GOOGLE_TO_ASTRID("all_google_to_astrid"),
    ASTRID_TO_GOOGLE("all_astrid_to_google"),
    BIDIRECTIONAL("all_bidirectional");

    /** The modes that make Astrid lists for Google ones, and so name them with the suffix. */
    val usesSuffix: Boolean get() = this == GOOGLE_TO_ASTRID || this == BIDIRECTIONAL

    companion object {
        fun of(wire: String?) = entries.firstOrNull { it.wire == wire } ?: MANUAL
    }
}

@Serializable
data class SyncContainer(val id: String = "", val name: String = "")

@Serializable
data class SyncLink(val id: String = "", val astridListId: String = "", val remoteContainerId: String = "")

/** One provider of the core's `externalSync` answer. */
@Serializable
data class ProviderSync(
    val provider: String = "",
    val connected: Boolean = false,
    val containers: List<SyncContainer> = emptyList(),
    val link: SyncLink? = null,
)

@Serializable
data class ExternalSyncAnswer(val listId: String = "", val providers: List<ProviderSync> = emptyList()) {
    fun of(provider: SyncProvider) = providers.firstOrNull { it.provider == provider.wire } ?: ProviderSync(provider.wire)
}

@Serializable
data class GoogleSyncModeAnswer(val mode: String = "manual", val suffix: String = "")

internal suspend fun Core.externalSync(listId: String): ExternalSyncAnswer =
    decode(ExternalSyncAnswer.serializer(), run(command("externalSync", "listId" to listId)))

internal fun JsonObject?.flag(group: String, name: String, absent: Boolean): Boolean =
    ((this?.get(group) as? JsonObject)?.get(name) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: absent

data class ExternalSyncState(
    val isLoading: Boolean = true,
    val connected: Boolean = false,
    val containers: List<SyncContainer> = emptyList(),
    val lists: List<ListSummary> = emptyList(),
    val selectedListId: String? = null,
    val selectedContainerId: String? = null,
    val isLinking: Boolean = false,
    /** The pairs linked from this screen, so a Link visibly did something. */
    val linkedHere: List<Pair<String, String>> = emptyList(),
    val mode: GoogleSyncMode = GoogleSyncMode.MANUAL,
    val suffix: String = "",
    val suffixDraft: String = "",
    val isSyncing: Boolean = false,
    val lastSyncedAt: Long? = null,
    val error: String? = null,
    val connectError: String? = null,
    val openUrl: String? = null,
    val awaitingConnect: Boolean = false,
) {
    val canLink: Boolean get() = selectedListId != null && selectedContainerId != null && !isLinking
}

/**
 * iOS `GitHubSyncSettingsView` / `GoogleTasksSettingsView`: connect the account, link a list to a
 * repository or a Google list, and (Google) choose the sync mode and run a pass. Every part is a core
 * command; the connect hand-off opens the core's URL in a Custom Tab.
 */
class ExternalSyncViewModel(
    private val core: Core,
    val provider: SyncProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val now: () -> Long = System::currentTimeMillis,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ExternalSyncState())
    val state: StateFlow<ExternalSyncState> = _state.asStateFlow()

    fun load() = work {
        _state.update { it.copy(isLoading = !it.connected && it.containers.isEmpty()) }
        try {
            // The panel is per list; asked for no list it still answers what is connected and what
            // could be linked, which is all the account screen needs.
            val answer = core.externalSync("").of(provider)
            _state.update { it.copy(connected = answer.connected, containers = answer.containers) }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        }
        val lists = runCatching {
            (core.run(command("lists")) as? JsonArray)?.map { decode(ListSummary.serializer(), it) }.orEmpty().filter { it.isVirtual != true }
        }.getOrDefault(emptyList())
        _state.update { it.copy(lists = lists) }
        if (provider == SyncProvider.GOOGLE && _state.value.connected) {
            runCatching { decode(GoogleSyncModeAnswer.serializer(), core.run(command("googleSyncMode"))) }.getOrNull()?.let { answer ->
                _state.update { it.copy(mode = GoogleSyncMode.of(answer.mode), suffix = answer.suffix, suffixDraft = answer.suffix) }
            }
        }
        _state.update { it.copy(isLoading = false) }
    }

    fun connect() = work {
        _state.update { it.copy(connectError = null) }
        try {
            val url = ((core.run(command("connectProvider", "provider" to provider.wire)) as? JsonObject)?.get("authorizeUrl") as? JsonPrimitive)?.contentOrNull
            if (!url.isNullOrEmpty()) _state.update { it.copy(openUrl = url, awaitingConnect = true) }
        } catch (failure: Throwable) {
            _state.update { it.copy(connectError = failure.reason()) }
        }
    }

    fun urlOpened() = _state.update { it.copy(openUrl = null) }

    /** Back from the browser: read the connection again. */
    fun resumed() {
        if (!_state.value.awaitingConnect) return
        _state.update { it.copy(awaitingConnect = false) }
        load()
    }

    fun disconnect() = work {
        try {
            core.run(command("disconnectProvider", "provider" to provider.wire))
            _state.update { it.copy(connected = false, containers = emptyList(), linkedHere = emptyList()) }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        }
    }

    fun selectList(id: String?) = _state.update { it.copy(selectedListId = id) }
    fun selectContainer(id: String?) = _state.update { it.copy(selectedContainerId = id) }

    fun link() = work {
        val current = _state.value
        val listId = current.selectedListId ?: return@work
        val containerId = current.selectedContainerId ?: return@work
        if (current.isLinking) return@work
        _state.update { it.copy(isLinking = true, error = null) }
        try {
            core.run(command("linkList", "provider" to provider.wire, "listId" to listId, "containerId" to containerId))
            val listName = current.lists.firstOrNull { it.id == listId }?.name ?: listId
            val containerName = current.containers.firstOrNull { it.id == containerId }?.name ?: containerId
            _state.update { it.copy(selectedListId = null, selectedContainerId = null, linkedHere = it.linkedHere + (listName to containerName)) }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        } finally {
            _state.update { it.copy(isLinking = false) }
        }
    }

    fun setSuffixDraft(text: String) = _state.update { it.copy(suffixDraft = text) }

    /** The account's choice, so it follows the person to their other devices. */
    fun setMode(mode: GoogleSyncMode) = work {
        val previous = _state.value.mode
        _state.update { it.copy(mode = mode) }
        writeMode(mode, previous)
    }

    fun saveSuffix() = work { writeMode(_state.value.mode, _state.value.mode) }

    private suspend fun writeMode(mode: GoogleSyncMode, previous: GoogleSyncMode) {
        val suffix = _state.value.suffixDraft
        try {
            core.run(command("setGoogleSyncMode", "mode" to mode.wire, "suffix" to suffix))
            _state.update { it.copy(suffix = suffix) }
        } catch (failure: Throwable) {
            _state.update { it.copy(mode = previous, error = failure.reason()) }
        }
    }

    /** One Google pass over every linked list. A list that failed is named; the rest still ran. */
    fun syncNow() = work {
        if (_state.value.isSyncing) return@work
        _state.update { it.copy(isSyncing = true, error = null) }
        try {
            val answer = core.run(command("syncExternal")) as? JsonObject
            val failed = (answer?.get("passes") as? JsonArray)?.firstNotNullOfOrNull { pass ->
                ((pass as? JsonObject)?.get("error") as? JsonPrimitive)?.contentOrNull
            }
            _state.update { it.copy(lastSyncedAt = now(), error = failed) }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        } finally {
            _state.update { it.copy(isSyncing = false) }
        }
    }
}

// ── Whether Settings offers the two rows ──────────────────────────────────────────────────────

data class SyncAvailability(val github: Boolean = false, val google: Boolean = false)

/**
 * iOS SettingsView 100-120: GitHub Issues when the deployment offers it; Google Tasks when the
 * deployment offers it AND the account's rollout flag is on. A capability the server does not
 * mention reads as offered, as iOS's ServerCapabilities decodes it.
 */
class SyncAvailabilityViewModel(private val core: Core, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(SyncAvailability())
    val state: StateFlow<SyncAvailability> = _state.asStateFlow()

    fun load() = work {
        val capabilities = runCatching { core.run(command("refreshCapabilities")) as? JsonObject }.getOrNull()
        val googleFlag = runCatching {
            ((core.run(command("features")) as? JsonObject)?.get("googleTasks") as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
        }.getOrNull() ?: false
        _state.value = SyncAvailability(
            github = capabilities.flag("sync", "githubIssues", absent = true),
            google = capabilities.flag("sync", "googleTasks", absent = true) && googleFlag,
        )
    }
}

// ── One list's integrations (list settings → Admin) ───────────────────────────────────────────

@Serializable
data class AgentOption(val id: String = "", val name: String = "")

@Serializable
data class RepositoryOption(val fullName: String = "", val name: String = "")

/** The core's `listAgentOptions`. */
@Serializable
data class ListAgentOptions(
    val defaultAgentId: String? = null,
    val githubRepositoryId: String? = null,
    val agents: List<AgentOption> = emptyList(),
    val repositories: List<RepositoryOption> = emptyList(),
    val githubConnected: Boolean = false,
)

data class ListSyncState(
    val isLoading: Boolean = true,
    val sync: ExternalSyncAnswer = ExternalSyncAnswer(),
    val options: ListAgentOptions? = null,
    val repositoriesError: String? = null,
    val loadingRepositories: Boolean = false,
    val googleOffered: Boolean = false,
    val error: String? = null,
)

/**
 * iOS ListAdminTab 223-397: the repository a list's coding agent works in (shown when the account has
 * agents to run), and the list's own GitHub Issues / Google Tasks link. Links are per user; connecting
 * the account lives in Settings.
 */
class ListExternalSyncViewModel(
    private val core: Core,
    private val listId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ListSyncState())
    val state: StateFlow<ListSyncState> = _state.asStateFlow()

    fun load() = work {
        val googleFlag = runCatching {
            ((core.run(command("features")) as? JsonObject)?.get("googleTasks") as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
        }.getOrNull() ?: false
        _state.update { it.copy(googleOffered = googleFlag) }
        readSync()
        loadRepositories()
        _state.update { it.copy(isLoading = false) }
    }

    private suspend fun readSync() {
        try {
            _state.update { it.copy(sync = core.externalSync(listId)) }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        }
    }

    fun refreshRepositories() = work { loadRepositories() }

    private suspend fun loadRepositories() {
        _state.update { it.copy(loadingRepositories = true, repositoriesError = null) }
        try {
            _state.update { it.copy(options = decode(ListAgentOptions.serializer(), core.run(command("listAgentOptions", "listId" to listId)))) }
        } catch (failure: Throwable) {
            _state.update { it.copy(repositoriesError = failure.reason()) }
        } finally {
            _state.update { it.copy(loadingRepositories = false) }
        }
    }

    /** The repository the list's coding agent works in; null is None. */
    fun setRepository(fullName: String?) = work {
        val previous = _state.value.options
        _state.update { it.copy(options = it.options?.copy(githubRepositoryId = fullName)) }
        try {
            core.run(command("updateList", "listId" to listId, "changes" to mapOf("githubRepositoryId" to fullName)))
        } catch (failure: Throwable) {
            _state.update { it.copy(options = previous, error = failure.reason()) }
        }
    }

    fun link(provider: SyncProvider, containerId: String) = work {
        try {
            core.run(command("linkList", "provider" to provider.wire, "listId" to listId, "containerId" to containerId))
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        }
        readSync()
    }

    fun unlink(provider: SyncProvider, linkId: String) = work {
        try {
            core.run(command("unlinkList", "provider" to provider.wire, "linkId" to linkId))
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        }
        readSync()
    }
}
