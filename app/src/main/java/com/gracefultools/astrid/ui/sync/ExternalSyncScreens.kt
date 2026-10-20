package com.gracefultools.astrid.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.gracefultools.astrid.ui.agents.InlineButton
import com.gracefultools.astrid.ui.agents.LoadingRow
import com.gracefultools.astrid.ui.agents.NavRow
import com.gracefultools.astrid.ui.agents.SmallSpinner
import com.gracefultools.astrid.ui.agents.openInCustomTab
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles
import com.gracefultools.astrid.ui.theme.Warning

/** Routes the two account screens live at. */
object SyncRoutes {
    const val GITHUB = "github-sync"
    const val GOOGLE = "google-sync"
}

/**
 * The Settings rows for GitHub Issues and Google Tasks (iOS SettingsView 100-120), drawn only when
 * the server offers each — and Google only when the account's rollout flag is on. Rows only, no
 * section: the Settings screen places them in its integrations group.
 */
@Composable
fun ExternalSyncRows(core: Core, onOpen: (String) -> Unit) {
    val vm: SyncAvailabilityViewModel = viewModel(key = "sync-availability", factory = factory { SyncAvailabilityViewModel(core) })
    val available by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    if (available.github) {
        NavRow(
            stringResource(R.string.sync_github_issues_title),
            leading = { Icon(Icons.Filled.Code, null, tint = Color(0xFFAF52DE), modifier = Modifier.size(20.dp)) },
        ) { onOpen(SyncRoutes.GITHUB) }
    }
    if (available.google) {
        NavRow(
            stringResource(R.string.sync_google_tasks_title),
            leading = { Icon(Icons.Filled.TaskAlt, null, tint = Success, modifier = Modifier.size(20.dp)) },
        ) { onOpen(SyncRoutes.GOOGLE) }
    }
}

/** iOS's menu-style Picker: the label, the current choice, and a menu of the rest. */
@Composable
internal fun MenuPicker(label: String, value: String, options: List<Pair<String?, String>>, selected: String?, enabled: Boolean = true, onSelect: (String?) -> Unit) {
    val colors = AstridTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled, role = Role.DropdownList) { open = true },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        ) {
            Text(label, style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(value, style = TextStyles.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
            Icon(Icons.Filled.UnfoldMore, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    trailingIcon = { if (id == selected) Icon(Icons.Filled.Check, null) },
                    onClick = { open = false; onSelect(id) },
                )
            }
        }
    }
}

/** iOS `GitHubSyncSettingsView` and `GoogleTasksSettingsView`: one screen, the provider decides the extras. */
@Composable
fun ExternalSyncScreen(vm: ExternalSyncViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = AstridTheme.colors
    val app = stringResource(R.string.app_name)
    val google = vm.provider == SyncProvider.GOOGLE
    LaunchedEffect(Unit) { vm.load() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resumed() }
    LaunchedEffect(state.openUrl) { state.openUrl?.let { openInCustomTab(context, it); vm.urlOpened() } }

    HubPage(stringResource(if (google) R.string.sync_google_tasks_title else R.string.sync_github_issues_title), onBack) {
        if (state.isLoading) {
            FormSection { LoadingRow(stringResource(R.string.settings_agents_loading)) }
            return@HubPage
        }
        FormSection(
            header = stringResource(R.string.sync_account),
            footer = stringResource(if (google) R.string.sync_google_footer else R.string.sync_github_footer, app),
        ) {
            if (state.connected) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    Icon(Icons.Filled.Verified, null, tint = Success, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.sync_connected), style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    InlineButton(stringResource(R.string.sync_disconnect), color = Error) { vm.disconnect() }
                }
            } else {
                ActionRow(stringResource(if (google) R.string.sync_connect_google else R.string.sync_connect_github), Icons.Filled.Link) { vm.connect() }
                state.connectError?.let { Caption2(it, Warning) }
            }
        }
        if (!state.connected) return@HubPage

        if (google) {
            FormSection(
                header = stringResource(R.string.sync_sync_mode),
                footer = when (state.mode) {
                    GoogleSyncMode.MANUAL -> stringResource(R.string.sync_mode_footer_manual)
                    GoogleSyncMode.GOOGLE_TO_ASTRID -> stringResource(R.string.sync_mode_footer_google_to_astrid, app)
                    GoogleSyncMode.ASTRID_TO_GOOGLE -> stringResource(R.string.sync_mode_footer_astrid_to_google, app)
                    GoogleSyncMode.BIDIRECTIONAL -> stringResource(R.string.sync_mode_footer_bidirectional, app)
                },
            ) {
                val labels = mapOf(
                    GoogleSyncMode.MANUAL to stringResource(R.string.sync_mode_manual),
                    GoogleSyncMode.GOOGLE_TO_ASTRID to stringResource(R.string.sync_mode_google_to_astrid, app),
                    GoogleSyncMode.ASTRID_TO_GOOGLE to stringResource(R.string.sync_mode_astrid_to_google, app),
                    GoogleSyncMode.BIDIRECTIONAL to stringResource(R.string.sync_mode_bidirectional),
                )
                MenuPicker(
                    stringResource(R.string.sync_mode), labels.getValue(state.mode),
                    options = GoogleSyncMode.entries.map { it.wire to labels.getValue(it) }, selected = state.mode.wire,
                ) { wire -> vm.setMode(GoogleSyncMode.of(wire)) }
                if (state.mode.usesSuffix) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                        FormTextField(state.suffixDraft, vm::setSuffixDraft, stringResource(R.string.sync_suffix_placeholder), modifier = Modifier.weight(1f))
                        if (state.suffixDraft != state.suffix) InlineButton(stringResource(R.string.agents_actions_save)) { vm.saveSuffix() }
                    }
                }
            }
        }

        if (state.linkedHere.isNotEmpty()) {
            FormSection(header = stringResource(R.string.sync_linked_lists)) {
                state.linkedHere.forEachIndexed { index, (list, container) ->
                    if (index > 0) FormDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(list, style = TextStyles.body, color = colors.textPrimary)
                        Caption2(container)
                    }
                }
            }
        }

        FormSection(header = stringResource(if (google) R.string.sync_link_list_tasklist else R.string.sync_link_list_repo)) {
            val chooseList = stringResource(R.string.sync_choose_list)
            MenuPicker(
                stringResource(R.string.sync_list),
                state.lists.firstOrNull { it.id == state.selectedListId }?.name ?: chooseList,
                options = listOf<Pair<String?, String>>(null to chooseList) + state.lists.map { it.id to it.name },
                selected = state.selectedListId,
            ) { vm.selectList(it) }
            val chooseContainer = stringResource(if (google) R.string.sync_choose_google_list else R.string.sync_choose_repo)
            MenuPicker(
                stringResource(if (google) R.string.sync_google_list else R.string.sync_repository),
                state.containers.firstOrNull { it.id == state.selectedContainerId }?.name ?: chooseContainer,
                options = listOf<Pair<String?, String>>(null to chooseContainer) + state.containers.map { it.id to it.name },
                selected = state.selectedContainerId,
            ) { vm.selectContainer(it) }
            ActionRow(
                stringResource(if (state.isLinking) R.string.sync_linking else R.string.sync_link),
                enabled = state.canLink,
            ) { vm.link() }
            if (!google) state.error?.let { Caption2(it, Warning) }
        }

        // GitHub has no client pass: a cron on the server syncs every linked repository.
        if (google) {
            FormSection {
                ActionRow(stringResource(R.string.sync_sync_now), Icons.Filled.Sync, enabled = !state.isSyncing, busy = state.isSyncing) { vm.syncNow() }
                state.lastSyncedAt?.let { at ->
                    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))
                    Caption2(stringResource(R.string.sync_last_synced, time))
                }
                state.error?.let { Caption2(it, Warning) }
            }
        }
    }
}

// ── One list's integrations ───────────────────────────────────────────────────────────────────

/**
 * iOS ListAdminTab 223-397, for the list settings screen to place: the coding agent's repository
 * (when the account has agents to run) and this list's own GitHub Issues / Google Tasks link.
 */
@Composable
fun ListIntegrationsSections(core: Core, listId: String) {
    val vm: ListExternalSyncViewModel = viewModel(key = "list-sync-$listId", factory = factory { ListExternalSyncViewModel(core, listId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    LaunchedEffect(listId) { vm.load() }
    val none = stringResource(R.string.agents_model_none)

    val options = state.options
    if ((options != null && options.agents.isNotEmpty()) || state.repositoriesError != null) {
        FormSection(header = stringResource(R.string.lists_ai_coding_agent)) {
            Caption(stringResource(R.string.lists_link_repo_description))
            val repositoriesError = state.repositoriesError
            when {
                state.loadingRepositories -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                    SmallSpinner()
                    Caption2(stringResource(R.string.lists_loading_repositories), colors.textMuted)
                }
                repositoriesError != null -> {
                    Caption2(repositoriesError, Error)
                    InlineButton(stringResource(R.string.agents_actions_retry)) { vm.refreshRepositories() }
                }
                options != null -> {
                    MenuPicker(
                        stringResource(R.string.lists_github_repository),
                        options.repositories.firstOrNull { it.fullName == options.githubRepositoryId }?.name ?: options.githubRepositoryId ?: none,
                        options = listOf<Pair<String?, String>>(null to none) + options.repositories.map { it.fullName to it.name },
                        selected = options.githubRepositoryId,
                    ) { vm.setRepository(it) }
                    if (options.repositories.isEmpty()) Caption2(stringResource(R.string.lists_no_repositories_found), colors.textMuted)
                    ActionRow(stringResource(R.string.user_menu_refresh_data), Icons.Filled.Sync, small = true, enabled = !state.loadingRepositories) {
                        vm.refreshRepositories()
                    }
                }
            }
        }
    }

    FormSection(
        header = stringResource(R.string.sync_external_sync),
        footer = stringResource(R.string.sync_external_footer),
    ) {
        ProviderLinkRow(state.sync.of(SyncProvider.GITHUB), stringResource(R.string.sync_github_issues_title), stringResource(R.string.sync_connect_github_hint), vm, SyncProvider.GITHUB)
        if (state.googleOffered) {
            FormDivider()
            ProviderLinkRow(state.sync.of(SyncProvider.GOOGLE), stringResource(R.string.sync_google_tasks_title), stringResource(R.string.sync_connect_google_hint), vm, SyncProvider.GOOGLE)
        }
        state.error?.let { Caption2(it, Warning) }
    }
}

@Composable
private fun ProviderLinkRow(sync: ProviderSync, title: String, connectHint: String, vm: ListExternalSyncViewModel, provider: SyncProvider) {
    val colors = AstridTheme.colors
    val link = sync.link
    when {
        !sync.connected -> Caption(connectHint)
        link != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            Text(title, style = TextStyles.body, color = colors.textPrimary)
            Text(
                sync.containers.firstOrNull { it.id == link.remoteContainerId }?.name?.takeIf { provider == SyncProvider.GOOGLE } ?: link.remoteContainerId,
                style = TextStyles.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            InlineButton(stringResource(R.string.sync_unlink), color = Error) { vm.unlink(provider, link.id) }
        }
        else -> {
            val notLinked = stringResource(R.string.sync_not_linked)
            MenuPicker(
                title, notLinked,
                options = listOf<Pair<String?, String>>(null to notLinked) + sync.containers.map { it.id to it.name },
                selected = null,
            ) { id -> if (id != null) vm.link(provider, id) }
        }
    }
}
