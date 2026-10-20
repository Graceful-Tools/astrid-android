package com.gracefultools.astrid.ui.agents

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gracefultools.astrid.Brand
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles
import com.gracefultools.astrid.ui.theme.Warning
import kotlinx.coroutines.delay

/** Routes the hub pushes. */
object AgentRoutes {
    const val HUB = "agents"
    const val WEBHOOK = "agents/webhook"
    const val CUSTOM = "agents/custom"
    const val MODEL = "agents/model"
    const val CONNECTIONS = "connections"
}

/** iOS `AgentRuntimeRow.imageAsset`, by mailbox: the bundled brand marks. */
@DrawableRes
internal fun agentIcon(service: String): Int? = when (service) {
    "claude" -> R.drawable.ai_claude
    "openai", "codex" -> R.drawable.ai_openai
    "gemini" -> R.drawable.ai_gemini
    "copilot" -> R.drawable.ai_copilot
    "muse" -> R.drawable.ai_muse
    "openclaw" -> R.drawable.ai_openclaw
    else -> null
}

/** iOS `AIService.keyPlaceholder` / `documentationURL`: where a provider's key comes from. */
private fun keyPlaceholder(service: String): String? = when (service) {
    "claude" -> "sk-ant-..."
    "openai" -> "sk-..."
    "gemini" -> "AIza..."
    "copilot" -> "Copilot token"
    "muse" -> "Meta API key"
    else -> null
}

private fun keyDocs(service: String): String? = when (service) {
    "claude" -> "https://console.anthropic.com/settings/keys"
    "openai" -> "https://platform.openai.com/api-keys"
    "gemini" -> "https://aistudio.google.com/apikey"
    "copilot" -> "https://docs.github.com/en/copilot"
    "muse" -> "https://developer.meta.com/ai/"
    else -> null
}

/** iOS `WebhookAgentLabel`. */
private fun webhookAgentLabel(agent: String): String = when (agent) {
    "claude" -> "Claude"
    "openai" -> "OpenAI"
    "gemini" -> "Gemini"
    "copilot" -> "GitHub Copilot"
    else -> agent
}

@Composable
internal fun BrandIcon(@DrawableRes icon: Int?, size: androidx.compose.ui.unit.Dp = 30.dp, fallback: ImageVector = Icons.Filled.Memory, modifier: Modifier = Modifier) {
    if (icon != null) {
        Image(painterResource(icon), null, modifier.size(size).clip(CircleShape), contentScale = ContentScale.Fit)
    } else {
        Icon(fallback, null, tint = AstridTheme.colors.accent, modifier = modifier.size(size).padding(4.dp))
    }
}

// ── The hub ───────────────────────────────────────────────────────────────────────────────────

/**
 * iOS `AgentHubView`: per agent, who runs it (segmented), how a user-run one hears about work, and
 * what "Astrid runs it" needs — a provider key, or Copilot's GitHub grant. Off rows are dimmed.
 */
@Composable
fun AgentsScreen(vm: AgentsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = stringResource(R.string.app_name)
    LaunchedEffect(Unit) { vm.load() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resumed() }
    LaunchedEffect(state.openUrl) {
        state.openUrl?.let { openInCustomTab(context, it); vm.urlOpened() }
    }

    HubPage(stringResource(R.string.agents_title), onBack) {
        FormSection(header = stringResource(R.string.settings_agents_your_agents)) {
            Caption(stringResource(R.string.settings_agents_description, app))
            if (state.mcpOffered) {
                ActionRow(stringResource(R.string.settings_agents_guide_link), Icons.AutoMirrored.Filled.MenuBook) {
                    openInCustomTab(context, Brand.webUrl("docs/loops"))
                }
                ActionRow(stringResource(R.string.settings_agents_download_skill), Icons.Filled.Download) {
                    openInCustomTab(context, Brand.webUrl("api/downloads/ASTRID_WORKFLOW.md"))
                }
            }
        }

        val loadError = state.loadError
        when {
            state.isLoading && state.agents.isEmpty() -> FormSection { LoadingRow(stringResource(R.string.settings_agents_loading)) }
            loadError != null -> FormSection {
                ProblemLabel(stringResource(R.string.settings_agents_load_failed, loadError))
                ActionRow(stringResource(R.string.agents_actions_retry)) { vm.load() }
            }
            else -> {
                state.saveError?.let { FormSection { ProblemLabel(stringResource(R.string.settings_agents_save_failed, it)) } }
                state.actionError?.let { FormSection { ProblemLabel(it) } }
                state.agents.forEach { agent -> AgentSection(vm, state, agent, onOpen) }

                if (state.customAgentsOffered) {
                    FormSection {
                        NavRow(
                            stringResource(R.string.settings_agents_custom_title), stringResource(R.string.settings_agents_custom_subtitle),
                            leading = { BrandIcon(R.drawable.ai_openclaw) },
                        ) { onOpen(AgentRoutes.CUSTOM) }
                    }
                }
                FormSection {
                    NavRow(
                        stringResource(R.string.connections_title), stringResource(R.string.settings_connections_subtitle),
                        leading = { Icon(Icons.Filled.Link, null, tint = AstridTheme.colors.accent, modifier = Modifier.size(30.dp).padding(4.dp)) },
                    ) { onOpen(AgentRoutes.CONNECTIONS) }
                }
                FormSection(footer = stringResource(R.string.settings_agents_model_description, app)) {
                    NavRow(
                        stringResource(R.string.settings_agents_model_title, app),
                        leading = { Image(painterResource(R.drawable.astrid_character), null, Modifier.size(30.dp)) },
                    ) { onOpen(AgentRoutes.MODEL) }
                }
                GitHubConnectionSection()
            }
        }
    }
}

@Composable
private fun AgentSection(vm: AgentsViewModel, state: AgentsState, agent: HubAgent, onOpen: (String) -> Unit) {
    val colors = AstridTheme.colors
    val app = stringResource(R.string.app_name)
    val ownership = vm.ownership(agent)
    val ownerships = vm.offeredOwnerships(agent)
    val busy = state.savingAgentId != null
    FormSection(modifier = Modifier.alpha(if (agent.mode == "off") 0.65f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            BrandIcon(agentIcon(agent.id))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                    Text(agent.name, style = TextStyles.body, color = colors.textPrimary)
                    if (!vm.isConfigured(agent) && agent.mode != "off") Caption2(stringResource(R.string.settings_agents_needs_setup), Warning)
                }
                Caption2(agent.email ?: "${agent.id}@${Brand.agentEmailDomain}")
            }
            if (state.savingAgentId == agent.id) SmallSpinner()
        }

        SegmentedControl(
            options = ownerships.map { ownershipLabel(it, app) },
            selected = ownerships.indexOf(ownership),
            enabled = !busy,
        ) { index -> vm.selectOwnership(agent.id, ownerships[index]) }

        if (vm.showsTransport(agent)) {
            TransportPicker(vm, agent, enabled = !busy, onCustomAgents = { onOpen(AgentRoutes.CUSTOM) })
        }

        when (agent.mode) {
            "api" -> {
                Caption(stringResource(R.string.settings_agents_api_description, app))
                if (vm.usesOAuth(agent)) CopilotConnection(vm, state) else KeyEditor(vm, state, agent.id)
            }
            "polling" -> {
                Caption(stringResource(R.string.settings_agents_polling_description, agent.name))
                Caption2(stringResource(R.string.settings_agents_polling_guide_hint))
            }
            "webhook" -> {
                Caption(stringResource(R.string.settings_agents_webhook_description))
                NavRow(
                    stringResource(R.string.settings_agents_webhook_configure),
                    leading = { Icon(Icons.Filled.Hub, null, tint = colors.accent, modifier = Modifier.size(20.dp)) },
                ) { onOpen(AgentRoutes.WEBHOOK) }
            }
            else -> Caption(stringResource(R.string.settings_agents_off_description, app))
        }
    }
}

@Composable
private fun ownershipLabel(ownership: Ownership, app: String): String = when (ownership) {
    Ownership.ASTRID -> stringResource(R.string.settings_agents_ownership_astrid, app)
    Ownership.USER -> stringResource(R.string.settings_agents_ownership_user)
    Ownership.OFF -> stringResource(R.string.settings_agents_ownership_off)
}

@Composable
private fun transportLabel(transport: Transport): String = stringResource(
    when (transport) {
        Transport.POLLING -> R.string.settings_agents_transport_polling
        Transport.WEBHOOK -> R.string.settings_agents_transport_webhook
        Transport.SSE -> R.string.settings_agents_transport_sse
    },
)

private fun transportIcon(transport: Transport): ImageVector = when (transport) {
    Transport.POLLING -> Icons.Filled.Terminal
    Transport.WEBHOOK -> Icons.Filled.Hub
    Transport.SSE -> Icons.Filled.SettingsInputAntenna
}

/** iOS's menu Picker: "Transport" with the current choice; the menu lists the transports with their icons. */
@Composable
private fun TransportPicker(vm: AgentsViewModel, agent: HubAgent, enabled: Boolean, onCustomAgents: () -> Unit) {
    val colors = AstridTheme.colors
    var open by remember { mutableStateOf(false) }
    val current = vm.transport(agent) ?: Transport.POLLING
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled, role = Role.DropdownList) { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_agents_transport), style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(transportLabel(current), style = TextStyles.body, color = colors.textSecondary)
            Icon(Icons.Filled.UnfoldMore, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            vm.offeredTransports(agent).forEach { transport ->
                DropdownMenuItem(
                    text = { Text(transportLabel(transport)) },
                    leadingIcon = { Icon(transportIcon(transport), null) },
                    trailingIcon = { if (transport == current) Icon(Icons.Filled.Check, null) },
                    onClick = {
                        open = false
                        if (vm.selectTransport(agent.id, transport)) onCustomAgents()
                    },
                )
            }
        }
    }
}

@Composable
private fun CopilotConnection(vm: AgentsViewModel, state: AgentsState) {
    Caption(
        stringResource(if (state.copilotConnected) R.string.settings_agents_copilot_connected else R.string.settings_agents_copilot_disconnected),
        AstridTheme.colors.textPrimary,
    )
    if (state.pollingCopilot) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
            SmallSpinner()
            Caption(stringResource(R.string.settings_agents_copilot_waiting))
        }
    } else {
        ActionRow(
            stringResource(if (state.copilotConnected) R.string.settings_agents_copilot_disconnect else R.string.settings_agents_copilot_connect),
            Icons.Filled.Code,
        ) { if (state.copilotConnected) vm.disconnectCopilot() else vm.connectCopilot() }
    }
}

/** iOS `apiKeyEditor`: the field until a key is stored; then its status with Test / Update / Delete. */
@Composable
private fun KeyEditor(vm: AgentsViewModel, state: AgentsState, service: String) {
    val context = LocalContext.current
    val colors = AstridTheme.colors
    val inputs = remember { mutableStateMapOf<String, String>() }
    val hasKey = state.credentials[service]?.configured == true
    val editing = !hasKey || service in state.editingKeys
    val busy = state.keyBusy == service

    if (editing) {
        FormTextField(
            value = inputs[service].orEmpty(),
            onChange = { inputs[service] = it },
            placeholder = if (hasKey) stringResource(R.string.settings_agents_key_replace_placeholder)
            else keyPlaceholder(service) ?: stringResource(R.string.settings_agents_manage_key),
            secret = true,
        )
        LaunchedEffect(state.keyBusy, state.editingKeys, hasKey) {
            // A save that landed closes the editor; clear what was typed with it.
            if (state.keyBusy == null && hasKey && service !in state.editingKeys) inputs.remove(service)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            Row(
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = inputs[service].orEmpty().isNotBlank() && !busy, role = Role.Button) {
                    vm.saveKey(service, inputs[service].orEmpty())
                }.heightIn(min = 36.dp).padding(horizontal = Spacing.s4),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
            ) {
                if (busy) SmallSpinner() else {
                    val tint = if (inputs[service].orEmpty().isNotBlank()) colors.accent else colors.textMuted
                    Icon(Icons.Filled.Check, null, tint = tint, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.agents_actions_save), style = TextStyles.body, color = tint)
                }
            }
            if (hasKey) InlineButton(stringResource(R.string.common_cancel), enabled = !busy) { vm.editKey(service, false); inputs.remove(service) }
        }
    } else {
        val works = state.keyWorks[service]
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (works == false) Icons.Filled.Cancel else Icons.Filled.CheckCircle, null,
                tint = if (works == false) Error else Success, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.weight(1f))
            if (busy) SmallSpinner() else {
                InlineButton(stringResource(R.string.settings_agents_test_key)) { vm.testKey(service) }
                InlineButton(stringResource(R.string.agents_actions_update)) { vm.editKey(service, true) }
                InlineButton(stringResource(R.string.actions_delete), color = Error) { vm.deleteKey(service) }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        Caption2(stringResource(R.string.settings_agents_key_footer), modifier = Modifier.weight(1f, fill = false))
        keyDocs(service)?.let { url ->
            InlineButton(stringResource(R.string.settings_agents_get_key)) { openInCustomTab(context, url) }
        }
    }
}

/**
 * iOS `GitHubConnectionSection`: what the GitHub connection is for and where to manage it. The
 * connected / repository-count line is not drawn: the core has no command for the GitHub App status.
 */
@Composable
private fun GitHubConnectionSection() {
    val context = LocalContext.current
    val app = stringResource(R.string.app_name)
    FormSection(
        headerContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Code, null, tint = AstridTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.settings_agents_github_title).uppercase(), style = TextStyles.footnote, color = AstridTheme.colors.textSecondary)
            }
        },
        footer = stringResource(R.string.settings_agents_github_description, app),
    ) {
        ActionRow(stringResource(R.string.settings_agents_github_manage), Icons.Filled.Public) {
            openInCustomTab(context, Brand.webUrl("settings/agents"))
        }
    }
}

// ── The webhook transport ─────────────────────────────────────────────────────────────────────

/** iOS `WebhookSettingsScreen`. */
@Composable
fun WebhookSettingsScreen(vm: WebhookViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val app = stringResource(R.string.app_name)
    LaunchedEffect(Unit) { vm.load() }

    HubPage(stringResource(R.string.settings_agents_transport_webhook), onBack) {
        if (state.isLoading) {
            FormSection { LoadingRow(stringResource(R.string.settings_agents_loading)) }
            return@HubPage
        }
        state.error?.let { error ->
            FormSection {
                ProblemLabel(error)
                if (state.requiresWebSession) WebSessionRequiredRow()
            }
        }
        state.newSecret?.let { secret ->
            FormSection(header = stringResource(R.string.settings_agents_webhook_secret_title)) {
                ProblemLabel(stringResource(R.string.settings_agents_webhook_secret_warning), Warning)
                CopyableCodeBlock(secret)
                CopyableCodeBlock("ASTRID_WEBHOOK_SECRET=$secret")
            }
        }

        FormSection(
            footerContent = {
                Caption(stringResource(R.string.settings_agents_webhook_url_hint, app))
                state.mintError?.let { Caption(it, Error) }
                if (state.requiresWebSession && state.error == null) WebSessionRequiredRow()
                ActionRow(
                    stringResource(R.string.settings_agents_webhook_create_credentials), Icons.Filled.Key,
                    small = true, enabled = !state.isMinting, busy = state.isMinting,
                ) { vm.mintCredentials() }
            },
        ) {
            FormTextField(
                value = state.url, onChange = vm::setUrl, placeholder = "https://your-server.com/webhook",
                label = stringResource(R.string.settings_agents_webhook_url), keyboardType = KeyboardType.Uri,
            )
            ToggleRow(stringResource(R.string.settings_agents_webhook_enabled), state.enabled) { vm.setEnabled(it) }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Text(stringResource(R.string.settings_agents_webhook_agents), style = TextStyles.body, color = colors.textPrimary)
                Caption2(stringResource(R.string.settings_agents_webhook_agents_hint))
                state.availableAgents.forEach { agent ->
                    ToggleRow(webhookAgentLabel(agent), agent in state.selectedAgents) { vm.toggleAgent(agent) }
                }
                if (state.selectedAgents.isEmpty()) Caption2(stringResource(R.string.settings_agents_webhook_no_agents), Warning)
            }
            if (state.settings.configured && state.settings.hasSecret == true) {
                ToggleRow(stringResource(R.string.settings_agents_webhook_regenerate), state.regenerateSecret) { vm.setRegenerate(it) }
            }
            ActionRow(
                stringResource(if (state.settings.configured) R.string.settings_agents_webhook_update else R.string.settings_agents_webhook_save),
                Icons.Filled.Check, enabled = state.canSave, busy = state.isSaving,
            ) { vm.save() }
            if (state.settings.configured) {
                ActionRow(stringResource(R.string.settings_agents_webhook_remove), Icons.Filled.Delete, destructive = true, enabled = !state.isSaving) {
                    vm.requestRemove()
                }
            }
        }

        if (state.settings.configured) {
            FormSection(header = stringResource(R.string.settings_agents_webhook_status)) {
                val active = state.settings.enabled == true
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) Success else Color.Gray))
                    Text(
                        stringResource(if (active) R.string.settings_agents_webhook_active else R.string.settings_agents_webhook_disabled),
                        style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f),
                    )
                    val failures = state.settings.failureCount ?: 0
                    Caption2(
                        if (failures > 0) stringResource(R.string.settings_agents_webhook_failures, failures)
                        else stringResource(R.string.settings_agents_webhook_no_failures),
                        if (failures > 0) Error else colors.textSecondary,
                    )
                }
                state.settings.lastFiredAt?.let { fired ->
                    Caption2(stringResource(R.string.settings_agents_webhook_last_fired, displayDate(fired, withTime = true) ?: fired))
                }
                ActionRow(
                    stringResource(R.string.settings_agents_webhook_test), Icons.AutoMirrored.Filled.Send,
                    enabled = !state.isTesting && active, busy = state.isTesting,
                ) { vm.test() }
                state.testResult?.let { result ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null, tint = if (result.success) Success else Error, modifier = Modifier.size(16.dp))
                            Caption(result.message ?: if (result.success) "OK" else "Failed", if (result.success) Success else Error)
                        }
                        result.responseTime?.let { Caption2(stringResource(R.string.settings_agents_webhook_test_response, it)) }
                        result.error?.let { Caption2(it, Error) }
                    }
                }
            }
        }
    }

    if (state.confirmingRemove) {
        AlertDialog(
            onDismissRequest = vm::cancelRemove,
            text = { Text(stringResource(R.string.settings_agents_webhook_remove_confirm)) },
            confirmButton = { TextButton(onClick = vm::confirmRemove) { Text(stringResource(R.string.settings_agents_webhook_remove), color = Error) } },
            dismissButton = { TextButton(onClick = vm::cancelRemove) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    state.minted?.let { TransportCredentialsSheet(it, onDone = vm::dismissMinted) }
}

/** A sheet as iOS presents one inside the hub: a title with Cancel/Done-style actions, then the rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HubSheet(
    title: String,
    onDismiss: () -> Unit,
    leadingAction: (@Composable () -> Unit)? = null,
    trailingAction: @Composable () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = AstridTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bgPrimary,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { leadingAction?.invoke() }
            Text(title, style = TextStyles.headline, color = colors.textPrimary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailingAction() }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s16).padding(top = Spacing.s8, bottom = Spacing.s32),
            verticalArrangement = Arrangement.spacedBy(Spacing.s20),
            content = content,
        )
    }
}

/** iOS `TransportCredentialsSheet`: a transport's client credentials, shown once. */
@Composable
internal fun TransportCredentialsSheet(minted: MintedClient, onDone: () -> Unit) {
    HubSheet(
        stringResource(R.string.settings_agents_webhook_credentials_title), onDismiss = onDone,
        trailingAction = { TextButton(onClick = onDone) { Text(stringResource(R.string.actions_done)) } },
    ) {
        FormSection { ProblemLabel(stringResource(R.string.settings_agents_webhook_credentials_warning), Warning) }
        FormSection {
            CredentialCopyRow(stringResource(R.string.settings_openclaw_credential_client_id), minted.clientId)
            CredentialCopyRow(stringResource(R.string.settings_openclaw_credential_client_secret), minted.clientSecret, secret = true)
        }
        FormSection { Caption(stringResource(R.string.settings_agents_webhook_credentials_needs_both)) }
    }
}

// ── Custom Agents ─────────────────────────────────────────────────────────────────────────────

/** iOS `CustomAgentsScreen`: register (credentials shown once), list, and delete with a confirmation. */
@Composable
fun CustomAgentsScreen(vm: CustomAgentsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val app = stringResource(R.string.app_name)
    var registering by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.flash) { if (state.flash != null) { delay(3_000); vm.dismissFlash() } }
    LaunchedEffect(state.registration) { if (state.registration != null) registering = false }

    HubPage(stringResource(R.string.settings_openclaw_title), onBack) {
        FormSection {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                BrandIcon(R.drawable.ai_openclaw, size = 36.dp)
                Text(stringResource(R.string.settings_openclaw_title), style = TextStyles.subheadline, color = colors.textPrimary)
            }
            Caption(stringResource(R.string.settings_openclaw_description))
        }
        state.flash?.let { flash ->
            FormSection {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Success, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (flash == CustomAgentsState.Flash.CREATED) R.string.settings_openclaw_agent_created else R.string.settings_openclaw_agent_deleted),
                        style = TextStyles.body, color = Success,
                    )
                }
            }
        }
        state.error?.let { FormSection { ProblemLabel(it) } }

        when {
            state.isLoading -> FormSection { LoadingRow(stringResource(R.string.settings_openclaw_loading)) }
            state.agents.isEmpty() -> FormSection {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s16), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                    Icon(Icons.Filled.Memory, null, tint = colors.textSecondary, modifier = Modifier.size(36.dp))
                    Text(stringResource(R.string.settings_openclaw_no_agents), style = TextStyles.subheadline, color = colors.textPrimary)
                    Text(stringResource(R.string.settings_openclaw_no_agents_hint, app), style = TextStyles.caption, color = colors.textSecondary, textAlign = TextAlign.Center)
                }
            }
            else -> FormSection(header = stringResource(R.string.settings_openclaw_section)) {
                state.agents.forEachIndexed { index, agent ->
                    if (index > 0) FormDivider()
                    CustomAgentRow(agent, busy = state.deletingId == agent.id) { vm.requestDelete(agent) }
                }
            }
        }

        FormSection {
            ActionRow(stringResource(R.string.settings_openclaw_connect_agent), Icons.Filled.AddCircle) {
                vm.clearRegisterError(); registering = true
            }
        }
        FormSection {
            Text(stringResource(R.string.settings_openclaw_about), style = TextStyles.subheadline, color = colors.textPrimary)
            Caption(stringResource(R.string.settings_openclaw_about_description, app))
        }
        FormSection {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lightbulb, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.settings_openclaw_tip_title), style = TextStyles.subheadline, color = colors.textPrimary)
            }
            Caption(stringResource(R.string.settings_openclaw_tip_description))
        }
    }

    if (registering) RegisterAgentSheet(vm, state, onDismiss = { registering = false })
    state.registration?.let { CredentialsSheet(it, onDone = vm::dismissRegistration) }
    state.pendingDelete?.let { agent ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text(stringResource(R.string.settings_openclaw_delete_agent)) },
            text = { Text(stringResource(R.string.settings_openclaw_delete_confirm, agent.email)) },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text(stringResource(R.string.settings_openclaw_delete_agent), color = Error) } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun CustomAgentRow(agent: CustomAgent, busy: Boolean, onDelete: () -> Unit) {
    val colors = AstridTheme.colors
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s12), modifier = Modifier.padding(vertical = 4.dp)) {
        val image = agent.image?.takeIf { it.isNotBlank() }?.let { path ->
            val png = if (path.endsWith(".svg")) path.removeSuffix(".svg") + ".png" else path
            if (png.startsWith("http")) png else Brand.webUrl(png)
        }
        if (image != null) {
            AsyncImage(
                model = image, contentDescription = null, contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ai_openclaw), error = painterResource(R.drawable.ai_openclaw),
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        } else {
            BrandIcon(R.drawable.ai_openclaw, size = 40.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(agent.email, style = TextStyles.subheadline, fontFamily = FontFamily.Monospace, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                val active = agent.status == "active"
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) Success else Color.Gray))
                Caption2(stringResource(if (active) R.string.settings_openclaw_status_active else R.string.settings_openclaw_status_idle))
            }
            Caption2(stringResource(R.string.settings_openclaw_registered, displayDate(agent.registeredAt, withTime = true) ?: agent.registeredAt))
            agent.lastActiveAt?.let { Caption2(stringResource(R.string.settings_openclaw_last_active, displayDate(it, withTime = true) ?: it)) }
        }
        if (busy) SmallSpinner() else {
            Icon(
                Icons.Filled.Delete, stringResource(R.string.settings_openclaw_delete_agent), tint = Error,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDelete).padding(Spacing.s8),
            )
        }
    }
}

@Composable
private fun RegisterAgentSheet(vm: CustomAgentsViewModel, state: CustomAgentsState, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    HubSheet(
        stringResource(R.string.settings_openclaw_register_title), onDismiss = { if (!state.isRegistering) onDismiss() },
        leadingAction = { TextButton(onClick = onDismiss, enabled = !state.isRegistering) { Text(stringResource(R.string.common_cancel)) } },
        trailingAction = {
            if (state.isRegistering) SmallSpinner() else {
                TextButton(onClick = { vm.register(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.settings_openclaw_create_agent)) }
            }
        },
    ) {
        FormSection(
            header = stringResource(R.string.settings_openclaw_agent_name),
            footer = stringResource(R.string.settings_openclaw_agent_name_hint),
        ) {
            FormTextField(name, { name = it.lowercase() }, stringResource(R.string.settings_openclaw_agent_name_placeholder))
        }
        if (name.isNotBlank()) {
            FormSection {
                Text(stringResource(R.string.settings_openclaw_agent_name_preview, name.trim(), Brand.agentEmailDomain), style = TextStyles.body, fontFamily = FontFamily.Monospace, color = Success)
            }
        }
        state.registerError?.let { FormSection { ProblemLabel(it) } }
    }
}

@Composable
private fun CredentialsSheet(result: CustomAgentRegistration, onDone: () -> Unit) {
    HubSheet(
        stringResource(R.string.settings_openclaw_credentials_title), onDismiss = onDone,
        trailingAction = { TextButton(onClick = onDone) { Text(stringResource(R.string.actions_done)) } },
    ) {
        FormSection { ProblemLabel(stringResource(R.string.settings_openclaw_credentials_warning), Warning) }
        FormSection {
            CredentialCopyRow(stringResource(R.string.settings_openclaw_credential_email), result.agent.email)
            CredentialCopyRow(stringResource(R.string.settings_openclaw_credential_client_id), result.oauth.clientId)
            CredentialCopyRow(stringResource(R.string.settings_openclaw_credential_client_secret), result.oauth.clientSecret, secret = true)
            CredentialCopyRow(stringResource(R.string.settings_openclaw_credential_token_endpoint), result.config.tokenEndpoint)
            CredentialCopyRow(stringResource(R.string.settings_openclaw_credential_api_base), result.config.apiBase)
            CredentialCopyRow(stringResource(R.string.settings_openclaw_credential_sse_endpoint), result.config.sseEndpoint)
        }
    }
}

// ── The model behind @astrid ──────────────────────────────────────────────────────────────────

/** iOS `BuiltInModel`: the providers listed even before a key makes them runnable. */
private data class BuiltInModel(val service: String, val displayName: String, val subtitle: String)

private val BUILT_IN_MODELS = listOf(
    BuiltInModel("claude", "Claude", "Anthropic"),
    BuiltInModel("openai", "OpenAI", "OpenAI"),
    BuiltInModel("gemini", "Gemini", "Google"),
    BuiltInModel("muse", "Muse", "Meta"),
)

/** iOS `DefaultAgentPickerView`: the model that powers the assistant; providers without a key lead to their key. */
@Composable
fun AstridModelScreen(vm: AstridModelViewModel, onBack: () -> Unit, onAddKey: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val app = stringResource(R.string.app_name)
    LaunchedEffect(Unit) { vm.load() }

    HubPage(stringResource(R.string.agents_model_screen_title, app), onBack) {
        val error = state.error
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SmallSpinner() }
            error != null -> FormSection { Text(error, style = TextStyles.body, color = Error) }
            else -> FormSection(footer = stringResource(R.string.settings_agents_model_footer, app, app)) {
                val options = state.model.options
                options.forEach { option ->
                    ModelRow(
                        title = option.name,
                        subtitle = option.serviceLabel.ifBlank { option.service }.replaceFirstChar { it.uppercase() },
                        subtitleColor = Color(0xFFAF52DE),
                        selected = state.model.selected == option.id,
                        leading = { BrandIcon(agentIcon(option.service), size = 36.dp, fallback = Icons.Filled.Cloud) },
                    ) { vm.select(option.id) }
                }
                val configured = options.map { it.service }.toSet()
                BUILT_IN_MODELS.filterNot { it.service in configured }.forEach { model ->
                    ModelRow(
                        title = model.displayName,
                        subtitle = stringResource(R.string.agents_model_key_required, model.subtitle),
                        subtitleColor = Warning,
                        selected = false,
                        leading = { BrandIcon(agentIcon(model.service), size = 36.dp, modifier = Modifier.alpha(0.5f)) },
                        trailing = { Icon(Icons.Filled.Key, null, tint = Warning, modifier = Modifier.size(14.dp)) },
                        onClick = onAddKey,
                    )
                }
                ModelRow(
                    title = stringResource(R.string.agents_model_none),
                    subtitle = stringResource(R.string.agents_model_none_subtitle, app),
                    subtitleColor = colors.textMuted,
                    selected = state.model.selected == null,
                    leading = {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    },
                ) { vm.select(null) }
            }
        }
    }
}

@Composable
private fun ModelRow(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    selected: Boolean,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(6.dp))
            .then(
                if (trailing == null) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                else Modifier.clickable(role = Role.Button, onClick = onClick),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TextStyles.body, color = colors.textPrimary)
            Caption2(subtitle, subtitleColor)
        }
        when {
            trailing != null -> trailing()
            selected -> Icon(Icons.Filled.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            else -> Spacer(Modifier.width(18.dp))
        }
    }
}
