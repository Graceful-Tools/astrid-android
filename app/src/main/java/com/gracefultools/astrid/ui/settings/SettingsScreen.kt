package com.gracefultools.astrid.ui.settings

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.BuildConfig
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.SettingChoice
import com.gracefultools.astrid.ui.common.AppErrorBanner
import com.gracefultools.astrid.ui.common.coreLabel
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles
import com.gracefultools.astrid.ui.theme.Warning

/** Purple and green, iOS's tints on the GitHub Issues and Google Tasks rows. */
private val GitHubPurple = Color(0xFFAF52DE)
private val GoogleGreen = Color(0xFF34C759)

/**
 * iOS's SettingsView, in its order: Profile; Sync; Features; Exploratory Features; Preferences; Sub
 * tasks; Outbox; Account; About; Sign Out. Reminders, Appearance, Language and Account & Access are
 * their own pages ([onOpen] takes the route). [canOpen] says whether a route exists in this build, so
 * the GitHub Issues and Google Tasks rows appear once their screens do.
 */
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onOpen: (String) -> Unit = {},
    canOpen: (String) -> Boolean = { true },
    /** Settings → Contacts (AATD-68); null hides the section. */
    contacts: ContactsViewModel? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    SettingsPage(
        stringResource(R.string.settings_title), Icons.Filled.Settings, onBack,
        overlay = {
            state.error?.let { message ->
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s24)) { AppErrorBanner(message, vm::dismissError) }
            }
        },
    ) {
        state.user?.let { user ->
            FormSection {
                NavRow(user.display, Icons.Outlined.AccountCircle, subtitle = stringResource(R.string.settings_profile_row)) { onOpen("profile") }
            }
        }

        FormSection(stringResource(R.string.settings_sync)) {
            // The core keeps the last pass's time but does not answer it yet; the row waits for it.
            state.outbox?.lastSync?.let { iso ->
                val millis = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
                ValueRow(
                    stringResource(R.string.settings_last_sync),
                    millis?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: stringResource(R.string.settings_never),
                    icon = Icons.Filled.Sync,
                )
                FormDivider()
            }
            ButtonRow(
                stringResource(R.string.settings_sync_now), icon = Icons.Filled.Refresh, color = colors.textPrimary, enabled = !state.syncing,
                trailing = { if (state.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent) },
                onClick = vm::syncNow,
            )
        }

        FormSection(stringResource(R.string.settings_features)) {
            NavRow(stringResource(R.string.settings_reminders), Icons.Filled.Notifications) { onOpen("settings/reminders") }
        }

        FormSection(stringResource(R.string.settings_exploratory_features)) {
            NavRow(stringResource(R.string.settings_agents_title), Icons.Filled.Memory) { onOpen("agents") }
            FormDivider()
            NavRow(stringResource(R.string.settings_connections_title), Icons.Filled.Link) { onOpen("connections") }
            if (state.serverOffersGithub && canOpen(ROUTE_GITHUB)) {
                FormDivider()
                NavRow(stringResource(R.string.settings_github_issues), Icons.Filled.Code, iconTint = GitHubPurple) { onOpen(ROUTE_GITHUB) }
            }
            if (state.showsGoogleTasks && canOpen(ROUTE_GOOGLE_TASKS)) {
                FormDivider()
                NavRow(stringResource(R.string.settings_google_tasks), Icons.Outlined.TaskAlt, iconTint = GoogleGreen) { onOpen(ROUTE_GOOGLE_TASKS) }
            }
        }

        FormSection(stringResource(R.string.settings_preferences)) {
            NavRow(stringResource(R.string.settings_appearance), Icons.Filled.Brush) { onOpen("settings/appearance") }
            if (Build.VERSION.SDK_INT >= 33) {
                FormDivider()
                NavRow(stringResource(R.string.settings_language), Icons.Filled.Language, value = currentLanguageName()) { onOpen("settings/language") }
            }
            FormDivider()
            ToggleRow(
                stringResource(R.string.settings_smart_task_creation),
                state.account?.smartTasks?.smartTaskCreationEnabled ?: true,
                subtitle = stringResource(R.string.settings_smart_task_creation_description),
            ) { vm.setSmartTasks("smartTaskCreationEnabled", it) }
        }

        state.account?.let { account ->
            FormSection(stringResource(R.string.settings_subtasks_title)) {
                MenuPickerRow(
                    stringResource(R.string.settings_subtasks_display),
                    account.subtaskChoices, account.smartTasks.subtaskDisplay,
                ) { vm.setSmartTasks("subtaskDisplay", it) }
            }
        }

        // After Preferences, as iOS orders it.
        contacts?.let { ContactsSection(it) }

        FormSection(stringResource(R.string.settings_outbox)) { OutboxRows(state, vm) }

        FormSection(stringResource(R.string.settings_account)) {
            NavRow(
                stringResource(R.string.settings_account_access), Icons.Filled.AccountCircle,
                subtitle = stringResource(R.string.settings_account_access_desc),
            ) { onOpen("account") }
        }

        FormSection(stringResource(R.string.settings_about)) {
            ValueRow(stringResource(R.string.settings_version_label), BuildConfig.VERSION_NAME)
            FormDivider(inset = false)
            ValueRow(stringResource(R.string.settings_build_label), BuildConfig.VERSION_CODE.toString())
            FormDivider(inset = false)
            ValueRow(stringResource(R.string.settings_core_label), vm.coreVersion)
            FormDivider(inset = false)
            // iOS's "Send Feedback" route (ReviewPromptManager.openSupportEmail), reachable here too (AATD-63).
            val context = androidx.compose.ui.platform.LocalContext.current
            ButtonRow(stringResource(R.string.review_send_feedback), icon = Icons.Filled.Email) {
                com.gracefultools.astrid.platform.sendFeedback(context)
            }
        }

        FormSection {
            ButtonRow(stringResource(R.string.settings_sign_out), icon = Icons.AutoMirrored.Filled.Logout, color = Error, centered = true, onClick = vm::askSignOut)
        }
    }

    if (state.confirmingSignOut) {
        AlertDialog(
            onDismissRequest = vm::cancelSignOut,
            title = { Text(stringResource(R.string.settings_sign_out)) },
            text = { Text(stringResource(R.string.settings_are_you_sure_sign_out)) },
            dismissButton = { TextButton(onClick = vm::cancelSignOut) { Text(stringResource(R.string.common_cancel)) } },
            confirmButton = {
                TextButton(onClick = vm::signOut) { Text(stringResource(R.string.settings_sign_out), color = Error, fontWeight = FontWeight.SemiBold) }
            },
        )
    }
}

/** Where the GitHub Issues and Google Tasks pages will live (item 38's screens). */
const val ROUTE_GITHUB = com.gracefultools.astrid.ui.sync.SyncRoutes.GITHUB
const val ROUTE_GOOGLE_TASKS = com.gracefultools.astrid.ui.sync.SyncRoutes.GOOGLE

/** iOS's Outbox section: the journal's health, why each refused write was refused, and a retry. */
@Composable
private fun OutboxRows(state: SettingsState, vm: SettingsViewModel) {
    val colors = AstridTheme.colors
    val outbox = state.outbox
    if (outbox == null) {
        ButtonRow(stringResource(R.string.settings_outbox_load), onClick = vm::refreshOutbox)
        return
    }
    FormRowFrame {
        Icon(
            if (outbox.isHealthy) Icons.Filled.Verified else Icons.Filled.Warning, null,
            tint = if (outbox.isHealthy) Success else Warning, modifier = Modifier.size(20.dp),
        )
        Text(
            stringResource(R.string.settings_outbox_summary, outbox.completed, outbox.pending + outbox.running, outbox.failed),
            style = TextStyles.caption2, color = colors.textSecondary, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = vm::refreshOutbox) { Text(stringResource(R.string.settings_outbox_refresh), style = TextStyles.caption2, color = colors.accent) }
    }
    outbox.deadLetters.forEach { letter ->
        FormDivider(inset = false)
        Text(
            "${letter.kind}: ${letter.error ?: stringResource(R.string.settings_outbox_unknown_error)}",
            style = TextStyles.caption2, color = Warning, maxLines = 3,
            modifier = Modifier.padding(horizontal = Spacing.s16, vertical = Spacing.s8),
        )
    }
    if (outbox.failed > 0) {
        FormDivider(inset = false)
        ButtonRow(stringResource(R.string.settings_outbox_retry), onClick = vm::retryDropped)
    }
}

/** iOS's Picker in a Form: the title, the chosen value, and a menu of the core's choices. */
@Composable
internal fun MenuPickerRow(title: String, choices: List<SettingChoice>, selected: String, onPick: (String) -> Unit) {
    val colors = AstridTheme.colors
    var open by remember { mutableStateOf(false) }
    val current = choices.firstOrNull { it.value == selected }?.let { coreLabel(it.titleKey) } ?: selected
    Box {
        NavRow(title, value = current, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.padding(horizontal = Spacing.s4)) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(coreLabel(choice.titleKey), color = colors.textPrimary) },
                    trailingIcon = if (choice.value == selected) ({ Icon(Icons.Filled.CheckCircle, null, tint = colors.accent) }) else null,
                    onClick = { open = false; onPick(choice.value) },
                )
            }
        }
    }
}

@Composable
private fun currentLanguageName(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val chosen = remember { appLanguageTag(context) }
    return chosen?.let { tag -> AppLanguages.firstOrNull { it.tag == tag }?.let { stringResource(it.name) } } ?: stringResource(R.string.settings_automatic)
}
