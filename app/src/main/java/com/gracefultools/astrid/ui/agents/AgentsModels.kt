package com.gracefultools.astrid.ui.agents

import kotlinx.serialization.Serializable

/** One agent row of the core's `agents` answer (`rows::agent`): its mailbox, mode, and the modes it may take. */
@Serializable
data class HubAgent(
    val id: String = "",
    val name: String = "",
    val email: String? = null,
    val mode: String = "off",
    val locked: Boolean = false,
    val modes: List<String> = listOf("api", "polling", "webhook", "off"),
)

/** `rows::credential`: whether a provider key is stored. The key itself never crosses. */
@Serializable
data class HubCredential(val serviceId: String = "", val name: String = "", val configured: Boolean = false)

@Serializable
data class CopilotStatus(val connected: Boolean = false)

/** The core's `agents` command: the hub in one answer. */
@Serializable
data class AgentHubAnswer(
    val agents: List<HubAgent> = emptyList(),
    val credentials: List<HubCredential> = emptyList(),
    val copilot: CopilotStatus = CopilotStatus(),
)

/** `webhookSettings` — every key optional but `configured`, as iOS's `WebhookSettings`. */
@Serializable
data class WebhookSettings(
    val configured: Boolean = false,
    val enabled: Boolean? = null,
    val webhookUrl: String? = null,
    val hasSecret: Boolean? = null,
    val events: List<String>? = null,
    val agents: List<String>? = null,
    val failureCount: Int? = null,
    val lastFiredAt: String? = null,
    val availableEvents: List<String>? = null,
    val availableAgents: List<String>? = null,
)

/** `saveWebhook`'s answer: the secret is present exactly once. */
@Serializable
data class WebhookSaveAnswer(val webhookSecret: String? = null)

/** `testWebhook`'s answer. */
@Serializable
data class WebhookTestResult(
    val success: Boolean = false,
    val message: String? = null,
    val responseTime: Int? = null,
    val statusCode: Int? = null,
    val error: String? = null,
)

/** One of `customAgents`. */
@Serializable
data class CustomAgent(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val image: String? = null,
    val agentName: String = "",
    val status: String = "idle",
    val registeredAt: String = "",
    val lastActiveAt: String? = null,
)

@Serializable
data class RegisteredAgent(val id: String = "", val email: String = "", val name: String = "")

@Serializable
data class RegisteredOAuth(val clientId: String = "", val clientSecret: String = "")

@Serializable
data class RegisteredConfig(val sseEndpoint: String = "", val apiBase: String = "", val tokenEndpoint: String = "")

/** `registerCustomAgent`'s answer: the credentials the server shows only this once. */
@Serializable
data class CustomAgentRegistration(
    val agent: RegisteredAgent = RegisteredAgent(),
    val oauth: RegisteredOAuth = RegisteredOAuth(),
    val config: RegisteredConfig = RegisteredConfig(),
)

/** A client the server has just made (`mintTransportCredentials`, `createOAuthClient`): the secret, once. */
@Serializable
data class MintedClient(val clientId: String = "", val clientSecret: String = "", val name: String = "")

/** One row of `astridModel`. */
@Serializable
data class AstridModelOption(
    val id: String = "",
    val name: String = "",
    val service: String = "",
    val serviceLabel: String = "",
    val isSelected: Boolean = false,
)

/** `astridModel` / `setAstridModel`: the agents that could power the assistant, and which one does. */
@Serializable
data class AstridModel(val options: List<AstridModelOption> = emptyList(), val selected: String? = null)
