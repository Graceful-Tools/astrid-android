package com.gracefultools.astrid.ui.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gracefultools.astrid.R
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.factory
import com.gracefultools.astrid.ui.agents.ActionRow
import com.gracefultools.astrid.ui.agents.Caption
import com.gracefultools.astrid.ui.agents.Caption2
import com.gracefultools.astrid.ui.agents.FormDivider
import com.gracefultools.astrid.ui.agents.FormSection
import com.gracefultools.astrid.ui.agents.FormTextField
import com.gracefultools.astrid.ui.agents.HubPage
import com.gracefultools.astrid.ui.agents.HubSheet
import com.gracefultools.astrid.ui.agents.LoadingRow
import com.gracefultools.astrid.ui.agents.ProblemLabel
import com.gracefultools.astrid.ui.agents.SmallSpinner
import com.gracefultools.astrid.ui.agents.ToggleRow
import com.gracefultools.astrid.ui.agents.TransportCredentialsSheet
import com.gracefultools.astrid.ui.agents.WEB_ORIGIN
import com.gracefultools.astrid.ui.agents.WebSessionRequiredRow
import com.gracefultools.astrid.ui.agents.displayDate
import com.gracefultools.astrid.ui.agents.openInCustomTab
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles

/** The core names a section by resource key (`connections.category.app`); these are iOS's words for them. */
@Composable
private fun sectionTitle(key: String): String = stringResource(
    when (key.substringAfter("connections.")) {
        "category.app" -> R.string.settings_connections_category_app
        "category.token" -> R.string.settings_connections_category_token
        "category.webhook" -> R.string.settings_connections_category_webhook
        "kind.oauthClient" -> R.string.settings_connections_kind_oauth_client
        "kind.authorizedApp" -> R.string.settings_connections_kind_authorized_app
        "kind.customAgent" -> R.string.settings_connections_kind_custom_agent
        "kind.accessToken" -> R.string.settings_connections_kind_access_token
        "kind.webhook" -> R.string.settings_connections_kind_webhook
        "kind.unknown" -> R.string.settings_connections_kind_unknown
        else -> R.string.settings_connections_category_unknown
    },
)

@Composable
private fun kindLabel(kind: String): String = stringResource(
    when (kind) {
        "oauthClient" -> R.string.settings_connections_kind_oauth_client
        "authorizedApp" -> R.string.settings_connections_kind_authorized_app
        "customAgent" -> R.string.settings_connections_kind_custom_agent
        "accessToken" -> R.string.settings_connections_kind_access_token
        "webhook" -> R.string.settings_connections_kind_webhook
        else -> R.string.settings_connections_kind_unknown
    },
)

@Composable
private fun ownerLabel(owner: String?): String? = when (owner) {
    "you" -> stringResource(R.string.settings_connections_owner_you)
    "thirdParty" -> stringResource(R.string.settings_connections_owner_third_party)
    "agent" -> stringResource(R.string.settings_connections_owner_agent)
    else -> null
}

@Composable
private fun statusLabel(status: String): String = stringResource(
    when (status) {
        "active" -> R.string.settings_connections_status_active
        "expired" -> R.string.settings_connections_status_expired
        "disabled" -> R.string.settings_connections_status_disabled
        else -> R.string.settings_connections_status_unknown
    },
)

/** iOS `ConnectionDates.summary`: Created · Last used (or never) · Expires, in the reader's short date. */
@Composable
private fun dateSummary(row: Connection): String {
    val never = stringResource(R.string.settings_connections_never)
    val parts = mutableListOf(
        stringResource(R.string.settings_connections_created, displayDate(row.createdAt) ?: never),
        stringResource(R.string.settings_connections_last_used, displayDate(row.lastUsedAt) ?: never),
    )
    if (row.expiresAt != null) parts += stringResource(R.string.settings_connections_expires, displayDate(row.expiresAt) ?: never)
    return parts.joinToString(" · ")
}

/**
 * iOS `ConnectionsScreen`: everything that can act as the account, in the core's sections, each with
 * Edit where the core says it may be edited and Revoke — always confirmed first.
 */
@Composable
fun ConnectionsScreen(vm: ConnectionsViewModel, core: Core, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = AstridTheme.colors
    // A fresh editor per opening: the key changes, so the last one's draft never leaks into the next.
    var editor by remember { mutableStateOf<EditorMode?>(null) }
    var openings by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.load() }

    HubPage(
        stringResource(R.string.connections_title), onBack,
        actions = {
            Icon(
                Icons.Filled.Add, stringResource(R.string.settings_connections_new), tint = colors.accent,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { openings++; editor = EditorMode(null, openings) }.padding(4.dp),
            )
        },
    ) {
        FormSection { Caption(stringResource(R.string.settings_connections_subtitle)) }

        if (state.isLoading) {
            FormSection { LoadingRow(stringResource(R.string.settings_agents_loading)) }
        } else {
            state.error?.let { error ->
                FormSection {
                    ProblemLabel(error)
                    if (state.requiresWebSession) WebSessionRequiredRow()
                }
            }
            if (state.panel.connections.isEmpty() && state.error == null) {
                FormSection { Caption(stringResource(R.string.settings_connections_empty)) }
            }
            state.panel.sections.forEach { section ->
                FormSection(header = sectionTitle(section.titleKey)) {
                    section.rows.forEachIndexed { index, row ->
                        if (index > 0) FormDivider()
                        ConnectionRow(
                            row,
                            ownerBadge = if (section.showsOwnerBadges) ownerLabel(row.owner) else null,
                            isRevoking = vm.isRevoking(row),
                            onEdit = row.editableClientId?.let { clientId -> { openings++; editor = EditorMode(clientId, openings) } },
                            onRevoke = { vm.requestRevoke(row) },
                        )
                    }
                }
            }
        }

        FormSection {
            ActionRow(stringResource(R.string.settings_connections_developer_web), Icons.Filled.Public) {
                openInCustomTab(context, "$WEB_ORIGIN/settings/connections")
            }
        }
    }

    state.pendingRevoke?.let { row ->
        AlertDialog(
            onDismissRequest = vm::cancelRevoke,
            title = { Text(stringResource(R.string.connections_revoke)) },
            text = {
                Text(
                    stringResource(
                        if (row.kind == "customAgent") R.string.settings_connections_remove_agent_confirm else R.string.settings_connections_revoke_confirm,
                        row.name,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmRevoke) { Text(stringResource(R.string.connections_revoke), color = Error) } },
            dismissButton = { TextButton(onClick = vm::cancelRevoke) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    editor?.let { mode ->
        val editorVm: OAuthClientEditorViewModel = viewModel(
            key = "oauth-client-${mode.opening}",
            factory = factory { OAuthClientEditorViewModel(core, mode.clientId) },
        )
        OAuthClientEditorSheet(
            editorVm,
            onFinished = { editor = null; vm.load() },
            onCancel = { editor = null },
        )
    }
}

private data class EditorMode(val clientId: String?, val opening: Int)

@Composable
private fun ConnectionRow(row: Connection, ownerBadge: String?, isRevoking: Boolean, onEdit: (() -> Unit)?, onRevoke: () -> Unit) {
    val colors = AstridTheme.colors
    Column(Modifier.padding(vertical = 2.dp).alpha(if (row.status == "active") 1f else 0.7f), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            Text(
                row.name.ifEmpty { kindLabel(row.kind) }, style = TextStyles.body, color = colors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            ownerBadge?.let {
                Caption2(
                    it,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.textSecondary.copy(alpha = 0.12f)).padding(horizontal = Spacing.s4, vertical = 1.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Caption2(statusLabel(row.status), if (row.status == "active") Success else colors.textSecondary)
        }
        Text(
            stringResource(R.string.settings_connections_acts_as, row.actsAs ?: stringResource(R.string.settings_connections_acts_as_you)),
            style = TextStyles.caption, fontFamily = FontFamily.Monospace, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (row.scopes.isNotEmpty()) {
            Text(row.scopes.joinToString(" · "), style = TextStyles.caption2, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Caption2(dateSummary(row))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            if (row.managedOnAgentsPage) Caption2(stringResource(R.string.settings_connections_manage_in_agents))
            Spacer(Modifier.weight(1f))
            if (isRevoking) SmallSpinner() else {
                if (onEdit != null) RowButton(stringResource(R.string.actions_edit), Icons.Filled.Edit, colors.accent, onEdit)
                if (row.revocable) RowButton(stringResource(R.string.connections_revoke), Icons.Filled.GppBad, Error, onRevoke)
            }
        }
    }
}

@Composable
private fun RowButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick).heightIn(min = 36.dp).padding(horizontal = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = TextStyles.caption, color = color)
    }
}

// ── New connection / Edit ─────────────────────────────────────────────────────────────────────

/** The grants in the core's display order, with iOS's words for each. */
private val GRANTS = listOf(
    Triple("client_credentials", R.string.settings_connections_grant_client_credentials, R.string.settings_connections_grant_client_credentials_hint),
    Triple("authorization_code", R.string.settings_connections_grant_authorization_code, R.string.settings_connections_grant_authorization_code_hint),
    Triple("refresh_token", R.string.settings_connections_grant_refresh_token, R.string.settings_connections_grant_refresh_token_hint),
)

@Composable
private fun problemMessage(problem: DraftProblem): String = when (problem.key) {
    "nameMissing" -> stringResource(R.string.settings_connections_error_name_required)
    "grantRequired" -> stringResource(R.string.settings_connections_error_grant_required)
    "redirectRequired" -> stringResource(R.string.settings_connections_error_redirect_required)
    else -> stringResource(R.string.settings_connections_error_redirect_invalid, problem.uri.orEmpty())
}

/** iOS `OAuthClientEditorScreen`: make a connection, or change one's redirect URIs (name and grants fixed). */
@Composable
private fun OAuthClientEditorSheet(vm: OAuthClientEditorViewModel, onFinished: () -> Unit, onCancel: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    HubSheet(
        stringResource(if (state.isEditing) R.string.settings_connections_edit_title else R.string.settings_connections_new_title),
        onDismiss = { if (!state.isSaving) onCancel() },
        leadingAction = { TextButton(onClick = onCancel, enabled = !state.isSaving) { Text(stringResource(R.string.common_cancel)) } },
        trailingAction = {
            if (state.isSaving) SmallSpinner() else {
                TextButton(onClick = vm::save, enabled = state.check.canSend && !state.isLoading) {
                    Text(stringResource(if (state.isEditing) R.string.agents_actions_save else R.string.settings_connections_create))
                }
            }
        },
    ) {
        val draft = state.draft
        if (state.isLoading) {
            FormSection { LoadingRow(stringResource(R.string.settings_agents_loading)) }
        } else {
            FormSection(
                header = stringResource(R.string.settings_connections_field_name),
                footer = if (state.isEditing) stringResource(R.string.settings_connections_name_fixed) else null,
            ) {
                if (state.isEditing) Text(draft.name, style = TextStyles.body, color = colors.textSecondary)
                else FormTextField(draft.name, vm::setName, stringResource(R.string.settings_connections_field_name_placeholder))
            }
            FormSection(header = stringResource(R.string.settings_connections_field_description)) {
                if (state.isEditing) {
                    Text(draft.description.ifEmpty { stringResource(R.string.settings_connections_field_description_none) }, style = TextStyles.body, color = colors.textSecondary)
                } else {
                    FormTextField(draft.description, vm::setDescription, stringResource(R.string.settings_connections_field_description_placeholder))
                }
            }
            FormSection(header = stringResource(R.string.settings_connections_field_grant_types)) {
                GRANTS.forEach { (grant, label, hint) ->
                    ToggleRow(stringResource(label), grant in draft.grantTypes, subtitle = stringResource(hint), enabled = !state.isEditing) { vm.toggleGrant(grant) }
                }
            }
            FormSection(
                header = stringResource(R.string.settings_connections_field_redirect_uris),
                footer = stringResource(R.string.settings_connections_field_redirect_uris_hint),
            ) {
                FormTextField(draft.redirectUriText, vm::setRedirectUris, "https://", monospace = true, singleLine = false, minLines = 3)
                state.check.problem?.takeIf { it.key == "redirectRequired" || it.key == "redirectInvalid" }?.let {
                    Caption2(problemMessage(it), Error)
                }
            }
            FormSection(
                header = stringResource(R.string.settings_connections_field_scopes),
                footer = stringResource(if (draft.scopes.isEmpty()) R.string.settings_connections_scopes_none else R.string.settings_connections_scopes_hint),
            ) {
                if (state.isEditing) {
                    Text(
                        if (draft.scopes.isEmpty()) stringResource(R.string.settings_connections_scopes_none) else draft.scopes.sorted().joinToString(" · "),
                        style = TextStyles.caption, fontFamily = FontFamily.Monospace, color = colors.textSecondary,
                    )
                } else {
                    state.check.scopes.forEach { scope ->
                        ToggleRow(scope, scope in draft.scopes) { vm.toggleScope(scope) }
                    }
                }
            }
        }
        val error = state.error
        if (error != null) {
            FormSection {
                ProblemLabel(error)
                if (state.requiresWebSession) WebSessionRequiredRow()
            }
        }
    }

    // The secret exists in exactly one place until this is dismissed.
    state.minted?.let { TransportCredentialsSheet(it, onDone = vm::dismissMinted) }
}
