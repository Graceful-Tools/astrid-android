package com.gracefultools.astrid.ui.account

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.AgentHub
import com.gracefultools.astrid.model.ConnectionsPanel
import com.gracefultools.astrid.model.ProfileStats
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

data class AccountState(
    val stats: ProfileStats? = null,
    val agents: AgentHub? = null,
    val connections: ConnectionsPanel? = null,
    val exported: String? = null,
    val error: String? = null,
)

/**
 * Profile, Agent Hub and Connections — iOS's account screens that are not Apple-only. All three are
 * online answers from the core (these are account facts the server holds); a refusal shows.
 */
class AccountViewModel(private val core: Core, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    fun loadProfile() = work {
        load { CoreJson.decodeFromJsonElement(ProfileStats.serializer(), core.run(command("profileStats"))) }?.let { s -> _state.update { it.copy(stats = s) } }
    }

    fun loadAgents() = work {
        load { CoreJson.decodeFromJsonElement(AgentHub.serializer(), core.run(command("agents"))) }?.let { a -> _state.update { it.copy(agents = a) } }
    }

    fun loadConnections() = work {
        load { CoreJson.decodeFromJsonElement(ConnectionsPanel.serializer(), core.run(command("connections"))) }?.let { c -> _state.update { it.copy(connections = c) } }
    }

    /** One of the agent's own modes — the core says which it allows; a locked agent is not changed. */
    fun setAgentMode(agentId: String, mode: String) = work {
        val agent = _state.value.agents?.agents?.firstOrNull { it.id == agentId } ?: return@work
        if (agent.locked || mode !in agent.modes) return@work
        load { core.run(command("setAgentMode", "agent" to agentId, "mode" to mode)) }
        loadAgents()
    }

    fun revoke(kind: String, id: String) = work {
        val row = _state.value.connections?.connections?.firstOrNull { it.id == id && it.kind == kind } ?: return@work
        if (!row.revocable) return@work
        load { core.run(command("revokeConnection", "connectionKind" to kind, "id" to id)) }
        loadConnections()
    }

    /** The account's data written straight to [path] by the core (JSON or CSV); then shared. */
    fun export(format: String, path: String) = work {
        load { core.run(command("exportAccount", "format" to format, "path" to path)) as? JsonObject }
            ?.let { _state.update { it.copy(exported = path) } }
    }

    fun exportShared() = _state.update { it.copy(exported = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    private suspend fun <T> load(block: suspend () -> T): T? = try {
        block()
    } catch (failure: CoreFailure) {
        _state.update { it.copy(error = failure.message) }
        null
    }
}
