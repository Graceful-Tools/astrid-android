package com.gracefultools.astrid.ui.agents

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.FailureKind
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** iOS `AgentOwnership`: who runs an agent. Astrid on the provider's key, the user's own runtime, or nobody. */
enum class Ownership { ASTRID, USER, OFF }

/** iOS `AgentSelfTransport`: how a user-run agent hears about work. SSE is a hand-over to Custom Agents, not a mode. */
enum class Transport(val mode: String?) { POLLING("polling"), WEBHOOK("webhook"), SSE(null) }

/** The mode the core stored, as an ownership (iOS `AgentOwnership.init(mode:)`). */
fun ownershipOf(mode: String): Ownership = when (mode) {
    "api" -> Ownership.ASTRID
    "polling", "webhook" -> Ownership.USER
    else -> Ownership.OFF
}

/** What went wrong, in the server's words when it sent some. */
internal fun Throwable.reason(): String = message ?: toString()

/**
 * Some agent-hub writes are session-only on the server (webhook configuration): a 401/403 there means
 * "do this from a signed-in web session", not "try again" (iOS `AgentHubErrors.requiresWebSession`).
 */
internal fun Throwable.requiresWebSession(): Boolean =
    this is CoreFailure && (status == 401 || status == 403 || kind == FailureKind.UNAUTHORIZED || kind == FailureKind.REFUSED)

internal fun <T> decode(serializer: KSerializer<T>, value: JsonElement): T = CoreJson.decodeFromJsonElement(serializer, value)

data class AgentsState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    /** A mode change the server refused, rolled back (iOS `settings.agents.save_failed`). */
    val saveError: String? = null,
    /** A key or Copilot action that failed. */
    val actionError: String? = null,
    val agents: List<HubAgent> = emptyList(),
    val credentials: Map<String, HubCredential> = emptyMap(),
    val copilotConnected: Boolean = false,
    val savingAgentId: String? = null,
    val keyBusy: String? = null,
    /** The last Test answer per service. */
    val keyWorks: Map<String, Boolean> = emptyMap(),
    /** Services whose key editor is open although a key is stored (Update). */
    val editingKeys: Set<String> = emptySet(),
    val awaitingCopilot: Boolean = false,
    val pollingCopilot: Boolean = false,
    /** A URL for the screen to open in a Custom Tab, once. */
    val openUrl: String? = null,
    /** Server capabilities: Custom Agents offered, and the MCP guide/skill links. Absent means offered, as iOS reads them. */
    val customAgentsOffered: Boolean = true,
    val mcpOffered: Boolean = true,
)

/**
 * The Agent Hub (iOS `AgentHubModel`): who runs each agent, how a user-run one hears about work,
 * the provider keys Astrid runs it on, and Copilot's GitHub grant. Everything comes from the core's
 * `agents` answer; every write is a core command. The rows are the core's (`rows::agent`), so which
 * modes an agent may take — a locked harness-only agent may not be "Astrid runs it" — is the core's.
 */
class AgentsViewModel(
    private val core: Core,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val pollInterval: Long = 2_000,
    private val pollAttempts: Int = 10,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(AgentsState())
    val state: StateFlow<AgentsState> = _state.asStateFlow()

    fun load() = work {
        _state.update { it.copy(isLoading = true, loadError = null, saveError = null, actionError = null) }
        try {
            adopt(decode(AgentHubAnswer.serializer(), core.run(command("agents"))))
        } catch (failure: Throwable) {
            _state.update { it.copy(isLoading = false, loadError = failure.reason()) }
            return@work
        }
        _state.update { it.copy(isLoading = false) }
        readCapabilities()
    }

    private fun adopt(answer: AgentHubAnswer) = _state.update {
        it.copy(
            agents = answer.agents,
            credentials = answer.credentials.associateBy { credential -> credential.serviceId },
            copilotConnected = answer.copilot.connected,
        )
    }

    /** Which hub sections the deployment ships. Absent means offered, as iOS's ServerCapabilities decodes it. */
    private suspend fun readCapabilities() {
        val capabilities = runCatching { core.run(command("refreshCapabilities")) as? JsonObject }.getOrNull() ?: return
        val integrations = capabilities["integrations"] as? JsonObject
        fun flag(name: String) = (integrations?.get(name) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: true
        _state.update { it.copy(customAgentsOffered = flag("customAgents"), mcpOffered = flag("mcp")) }
    }

    // ── Derived, from the core's row ──────────────────────────────────────────────────────────

    fun ownership(agent: HubAgent): Ownership = ownershipOf(agent.mode)

    /** The ownerships the core's `modes` allow: a locked agent has nobody but the user to run it. */
    fun offeredOwnerships(agent: HubAgent): List<Ownership> = buildList {
        if ("api" in agent.modes) add(Ownership.ASTRID)
        if ("polling" in agent.modes || "webhook" in agent.modes) add(Ownership.USER)
        if ("off" in agent.modes) add(Ownership.OFF)
    }

    fun transport(agent: HubAgent): Transport? = when (agent.mode) {
        "polling" -> Transport.POLLING
        "webhook" -> Transport.WEBHOOK
        else -> null
    }

    /** A harness-only agent has exactly one transport, so the picker would be a choice between one thing. */
    fun showsTransport(agent: HubAgent): Boolean = ownership(agent) == Ownership.USER && offeredTransports(agent).size > 1

    fun offeredTransports(agent: HubAgent): List<Transport> = buildList {
        if ("polling" in agent.modes) add(Transport.POLLING)
        if ("webhook" in agent.modes) add(Transport.WEBHOOK)
        if (!agent.locked) add(Transport.SSE)
    }

    /** Copilot runs on the GitHub grant rather than a pasted key. */
    fun usesOAuth(agent: HubAgent): Boolean = agent.id == COPILOT

    /** "Needs setup": only "Astrid runs it" needs a credential — the key, or Copilot's grant. */
    fun isConfigured(agent: HubAgent): Boolean {
        if (agent.mode != "api") return true
        if (usesOAuth(agent)) return _state.value.copilotConnected
        return _state.value.credentials[agent.id]?.configured == true
    }

    // ── Ownership and transport ───────────────────────────────────────────────────────────────

    /** iOS `AgentOwnership.modeToWrite`: re-choosing "I run it" keeps a webhook; entering it writes polling. */
    fun selectOwnership(agentId: String, ownership: Ownership) {
        val agent = agentOf(agentId) ?: return
        if (ownership !in offeredOwnerships(agent)) return
        val mode = when (ownership) {
            Ownership.ASTRID -> "api".takeIf { agent.mode != "api" }
            Ownership.OFF -> "off".takeIf { agent.mode != "off" }
            Ownership.USER -> (if ("polling" in agent.modes) "polling" else "webhook").takeIf { ownershipOf(agent.mode) != Ownership.USER }
        } ?: return
        setMode(agent, mode)
    }

    /** Returns true when the choice is the Custom Agent hand-over, which stores nothing. */
    fun selectTransport(agentId: String, transport: Transport): Boolean {
        val agent = agentOf(agentId) ?: return false
        val mode = transport.mode ?: return true
        if (mode != agent.mode && mode in agent.modes) setMode(agent, mode)
        return false
    }

    /** Optimistic, with rollback, exactly like the web and iOS. */
    private fun setMode(agent: HubAgent, mode: String) = work {
        val previous = agent.mode
        _state.update { it.copy(agents = it.agents.withMode(agent.id, mode), savingAgentId = agent.id, saveError = null) }
        try {
            val answer = core.run(command("setAgentMode", "agent" to agent.id, "mode" to mode)) as? JsonObject
            val stored = ((answer?.get("modes") as? JsonObject)?.get(agent.id) as? JsonPrimitive)?.contentOrNull
            if (stored != null) _state.update { it.copy(agents = it.agents.withMode(agent.id, stored)) }
        } catch (failure: Throwable) {
            _state.update { it.copy(agents = it.agents.withMode(agent.id, previous), saveError = failure.reason()) }
        } finally {
            _state.update { it.copy(savingAgentId = null) }
        }
    }

    // ── Provider keys ─────────────────────────────────────────────────────────────────────────

    fun editKey(serviceId: String, editing: Boolean) = _state.update {
        it.copy(editingKeys = if (editing) it.editingKeys + serviceId else it.editingKeys - serviceId)
    }

    /** Straight to the server; the hub re-reads which services have one. Returns through [state]. */
    fun saveKey(serviceId: String, key: String) = work {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return@work
        keyAction(serviceId) {
            core.run(command("saveAgentCredential", "serviceId" to serviceId, "key" to trimmed))
            _state.update { it.copy(editingKeys = it.editingKeys - serviceId, keyWorks = it.keyWorks - serviceId) }
            refresh()
        }
    }

    fun testKey(serviceId: String) = work {
        keyAction(serviceId) {
            val works = ((core.run(command("testAgentCredential", "serviceId" to serviceId)) as? JsonObject)?.get("works") as? JsonPrimitive)
                ?.contentOrNull?.toBooleanStrictOrNull() ?: false
            _state.update { it.copy(keyWorks = it.keyWorks + (serviceId to works)) }
        }
    }

    fun deleteKey(serviceId: String) = work {
        keyAction(serviceId) {
            core.run(command("deleteAgentCredential", "serviceId" to serviceId))
            _state.update {
                val credential = it.credentials[serviceId] ?: HubCredential(serviceId = serviceId)
                it.copy(
                    credentials = it.credentials + (serviceId to credential.copy(configured = false)),
                    keyWorks = it.keyWorks - serviceId,
                    editingKeys = it.editingKeys + serviceId,
                )
            }
        }
    }

    private suspend fun keyAction(serviceId: String, block: suspend () -> Unit) {
        _state.update { it.copy(keyBusy = serviceId, actionError = null) }
        try {
            block()
        } catch (failure: Throwable) {
            _state.update { it.copy(actionError = failure.reason()) }
        } finally {
            _state.update { it.copy(keyBusy = null) }
        }
    }

    /** Re-read the hub without the loading state — the rows stay on screen. */
    private suspend fun refresh() {
        runCatching { adopt(decode(AgentHubAnswer.serializer(), core.run(command("agents")))) }
    }

    // ── Copilot (GitHub OAuth) ────────────────────────────────────────────────────────────────

    /** The core's GitHub URL, for the screen to open in a Custom Tab; the status is polled on return. */
    fun connectCopilot() = work {
        _state.update { it.copy(actionError = null) }
        try {
            val url = ((core.run(command("connectCopilot")) as? JsonObject)?.get("authorizeUrl") as? JsonPrimitive)?.contentOrNull
            if (url.isNullOrEmpty()) return@work
            _state.update { it.copy(openUrl = url, awaitingCopilot = true) }
        } catch (failure: Throwable) {
            _state.update { it.copy(actionError = failure.reason()) }
        }
    }

    fun urlOpened() = _state.update { it.copy(openUrl = null) }

    /**
     * Back from the browser. The server-side callback ends on a "return to the app" page rather than
     * an app-scheme redirect, so the grant is read again until it shows or the attempts run out.
     */
    fun resumed() = work {
        if (!_state.value.awaitingCopilot) return@work
        _state.update { it.copy(awaitingCopilot = false, pollingCopilot = true) }
        try {
            var attempt = 0
            while (true) {
                refresh()
                if (_state.value.copilotConnected || attempt >= pollAttempts) break
                attempt++
                delay(pollInterval)
            }
        } finally {
            _state.update { it.copy(pollingCopilot = false) }
        }
    }

    fun disconnectCopilot() = work {
        try {
            core.run(command("disconnectCopilot"))
            _state.update { it.copy(copilotConnected = false) }
        } catch (failure: Throwable) {
            _state.update { it.copy(actionError = failure.reason()) }
        }
    }

    private fun agentOf(id: String) = _state.value.agents.firstOrNull { it.id == id }

    private fun List<HubAgent>.withMode(id: String, mode: String) = map { if (it.id == id) it.copy(mode = mode) else it }

    private companion object {
        const val COPILOT = "copilot"
    }
}

// ── The webhook transport ─────────────────────────────────────────────────────────────────────

data class WebhookState(
    val isLoading: Boolean = true,
    val settings: WebhookSettings = WebhookSettings(),
    val url: String = "",
    val enabled: Boolean = true,
    val selectedAgents: List<String> = emptyList(),
    val regenerateSecret: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val error: String? = null,
    val requiresWebSession: Boolean = false,
    /** Shown once, right after the save that minted it. */
    val newSecret: String? = null,
    val testResult: WebhookTestResult? = null,
    val confirmingRemove: Boolean = false,
    val isMinting: Boolean = false,
    val mintError: String? = null,
    val minted: MintedClient? = null,
) {
    val availableAgents: List<String> get() = settings.availableAgents ?: DEFAULT_AGENTS

    /** iOS `WebhookSettingsModel.canSave`: an http(s) URL and nothing already saving. */
    val canSave: Boolean get() = !isSaving && url.trim().let { it.startsWith("http://") || it.startsWith("https://") }

    companion object {
        val DEFAULT_EVENTS = listOf("task.assigned", "comment.created")
        val DEFAULT_AGENTS = listOf("claude", "openai", "gemini", "copilot")
    }
}

/** iOS `WebhookSettingsModel` + `TransportCredentialsModel`, over `webhookSettings` / `saveWebhook` / `testWebhook`. */
class WebhookViewModel(private val core: Core, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(WebhookState())
    val state: StateFlow<WebhookState> = _state.asStateFlow()

    fun load() = work {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            adopt(decode(WebhookSettings.serializer(), core.run(command("webhookSettings"))))
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun adopt(settings: WebhookSettings) = _state.update {
        it.copy(
            settings = settings,
            url = settings.webhookUrl ?: it.url,
            enabled = settings.enabled ?: true,
            selectedAgents = settings.agents ?: it.selectedAgents,
        )
    }

    fun setUrl(url: String) = _state.update { it.copy(url = url) }
    fun setEnabled(enabled: Boolean) = _state.update { it.copy(enabled = enabled) }
    fun setRegenerate(regenerate: Boolean) = _state.update { it.copy(regenerateSecret = regenerate) }
    fun toggleAgent(agent: String) = _state.update {
        it.copy(selectedAgents = if (agent in it.selectedAgents) it.selectedAgents - agent else it.selectedAgents + agent)
    }

    fun save() = work {
        val draft = _state.value
        if (!draft.canSave) return@work
        _state.update { it.copy(isSaving = true, error = null, newSecret = null) }
        try {
            val answer = core.run(
                command(
                    "saveWebhook",
                    "url" to draft.url.trim(),
                    "enabled" to draft.enabled,
                    "events" to (draft.settings.events ?: WebhookState.DEFAULT_EVENTS),
                    "agents" to draft.selectedAgents,
                    "regenerateSecret" to draft.regenerateSecret,
                ),
            )
            val secret = runCatching { decode(WebhookSaveAnswer.serializer(), answer).webhookSecret }.getOrNull()
            _state.update { it.copy(newSecret = secret, regenerateSecret = false, requiresWebSession = false) }
            runCatching { adopt(decode(WebhookSettings.serializer(), core.run(command("webhookSettings")))) }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason(), requiresWebSession = failure.requiresWebSession()) }
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    /** Removing stops the user's server receiving work, so it is asked first. */
    fun requestRemove() = _state.update { it.copy(confirmingRemove = it.settings.configured) }
    fun cancelRemove() = _state.update { it.copy(confirmingRemove = false) }

    fun confirmRemove() = work {
        if (!_state.value.confirmingRemove) return@work
        _state.update { it.copy(confirmingRemove = false, isSaving = true, error = null) }
        try {
            core.run(command("deleteWebhook"))
            _state.update {
                it.copy(settings = WebhookSettings(availableAgents = it.settings.availableAgents), url = "", enabled = true,
                    selectedAgents = emptyList(), newSecret = null, testResult = null)
            }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason(), requiresWebSession = failure.requiresWebSession()) }
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun test() = work {
        _state.update { it.copy(isTesting = true, testResult = null) }
        try {
            val result = decode(WebhookTestResult.serializer(), core.run(command("testWebhook")))
            _state.update { it.copy(testResult = result) }
        } catch (failure: Throwable) {
            _state.update { it.copy(testResult = WebhookTestResult(success = false, message = failure.reason()), requiresWebSession = failure.requiresWebSession()) }
        } finally {
            _state.update { it.copy(isTesting = false) }
        }
    }

    /** The server needs a client_credentials pair as well as the secret; minted from the webhook preset. */
    fun mintCredentials() = work {
        _state.update { it.copy(isMinting = true, mintError = null) }
        try {
            val agent = _state.value.selectedAgents.firstOrNull() ?: "claude"
            val minted = decode(MintedClient.serializer(), core.run(command("mintTransportCredentials", "preset" to "webhookServer", "agent" to agent)))
            _state.update { it.copy(minted = minted) }
        } catch (failure: Throwable) {
            _state.update { it.copy(mintError = failure.reason(), requiresWebSession = failure.requiresWebSession()) }
        } finally {
            _state.update { it.copy(isMinting = false) }
        }
    }

    fun dismissMinted() = _state.update { it.copy(minted = null) }
}

// ── Custom Agents ─────────────────────────────────────────────────────────────────────────────

data class CustomAgentsState(
    val isLoading: Boolean = true,
    val agents: List<CustomAgent> = emptyList(),
    val error: String? = null,
    val isRegistering: Boolean = false,
    val registerError: String? = null,
    /** The one-time credentials of the agent just registered. */
    val registration: CustomAgentRegistration? = null,
    val pendingDelete: CustomAgent? = null,
    val deletingId: String? = null,
    /** iOS flashes "Agent created" / "Agent removed"; the screen words it. */
    val flash: Flash? = null,
) {
    enum class Flash { CREATED, DELETED }
}

/** iOS `CustomAgentsModel` over `customAgents` / `registerCustomAgent` / `deleteCustomAgent`. */
class CustomAgentsViewModel(private val core: Core, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(CustomAgentsState())
    val state: StateFlow<CustomAgentsState> = _state.asStateFlow()

    fun load() = work {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            _state.update { it.copy(agents = agents()) }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun agents(): List<CustomAgent> =
        (core.run(command("customAgents")) as? JsonArray)?.map { decode(CustomAgent.serializer(), it) } ?: emptyList()

    fun clearRegisterError() = _state.update { it.copy(registerError = null) }

    /** Lower-cased as iOS's field types it; the server says whether the name is acceptable. Returns through [state]. */
    fun register(name: String) = work {
        val cleaned = name.trim().lowercase()
        if (cleaned.isEmpty()) return@work
        _state.update { it.copy(isRegistering = true, registerError = null) }
        try {
            val result = decode(CustomAgentRegistration.serializer(), core.run(command("registerCustomAgent", "name" to cleaned)))
            _state.update { it.copy(registration = result, flash = CustomAgentsState.Flash.CREATED) }
            runCatching { agents() }.onSuccess { list -> _state.update { it.copy(agents = list) } }
        } catch (failure: Throwable) {
            _state.update { it.copy(registerError = failure.reason()) }
        } finally {
            _state.update { it.copy(isRegistering = false) }
        }
    }

    fun dismissRegistration() = _state.update { it.copy(registration = null) }
    fun dismissFlash() = _state.update { it.copy(flash = null) }

    fun requestDelete(agent: CustomAgent) = _state.update { it.copy(pendingDelete = agent) }
    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    fun confirmDelete() = work {
        val agent = _state.value.pendingDelete ?: return@work
        _state.update { it.copy(pendingDelete = null, deletingId = agent.id, error = null) }
        try {
            core.run(command("deleteCustomAgent", "agentId" to agent.id))
            _state.update { state -> state.copy(agents = state.agents.filterNot { it.id == agent.id }, flash = CustomAgentsState.Flash.DELETED) }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        } finally {
            _state.update { it.copy(deletingId = null) }
        }
    }
}

// ── The model behind @astrid ──────────────────────────────────────────────────────────────────

data class AstridModelState(
    val isLoading: Boolean = true,
    val model: AstridModel = AstridModel(),
    val error: String? = null,
    val isSaving: Boolean = false,
)

/** iOS `DefaultAgentPickerView` over `astridModel` / `setAstridModel`. */
class AstridModelViewModel(private val core: Core, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(AstridModelState())
    val state: StateFlow<AstridModelState> = _state.asStateFlow()

    fun load() = work {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            _state.update { it.copy(model = decode(AstridModel.serializer(), core.run(command("astridModel")))) }
        } catch (failure: Throwable) {
            _state.update { it.copy(error = failure.reason()) }
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    /** Null is "None": the server picks. A refusal puts the old choice back, as iOS does. */
    fun select(agentId: String?) = work {
        val previous = _state.value.model
        _state.update { it.copy(model = it.model.copy(selected = agentId), isSaving = true) }
        try {
            _state.update { it.copy(model = decode(AstridModel.serializer(), core.run(command("setAstridModel", "agentId" to agentId)))) }
        } catch (failure: Throwable) {
            _state.update { it.copy(model = previous) }
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }
}
