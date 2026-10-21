package com.gracefultools.astrid.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.platform.DeviceContacts
import com.gracefultools.astrid.ui.theme.AstridTheme

/** iOS's system `.orange`, the "access required" glyph. */
private val Orange = Color(0xFFFF9500)

/**
 * iOS's SettingsContactsSection (AATD-68). Before access: Add Collaborators, which explains first
 * and only then asks for READ_CONTACTS. With access: Synced Contacts, Last Sync, Sync Contacts Now —
 * and, like iOS, a first sync by itself when the server has none. Refused for good: a row to the
 * app's system settings. Android has no "limited" access, so iOS's Grant Full Access has no twin.
 */
@Composable
internal fun ContactsSection(vm: ContactsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val context = LocalContext.current
    var granted by rememberSaveable { mutableStateOf(DeviceContacts.granted(context)) }
    var refused by rememberSaveable { mutableStateOf(false) }
    var explaining by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        // Android stops showing its prompt after a second refusal; then only Settings can grant it.
        val activity = context as? Activity
        refused = !ok && activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) != true
        if (ok) vm.sync()
    }
    LifecycleResumeEffect(Unit) {
        granted = DeviceContacts.granted(context)
        if (granted) refused = false
        onPauseOrDispose {}
    }
    LaunchedEffect(granted) { if (granted) vm.loadCount() }
    LaunchedEffect(granted, state.count) { if (granted && state.count == 0 && state.lastSync == null) vm.sync() }

    FormSection(stringResource(R.string.contacts), footer = stringResource(R.string.contacts_footer_text)) {
        when {
            granted -> {
                ValueRow(stringResource(R.string.contacts_synced_contacts), state.count?.toString() ?: "–", icon = Icons.Filled.AccountCircle)
                state.lastSync?.let { at ->
                    FormDivider()
                    ValueRow(stringResource(R.string.settings_last_sync), DateUtils.getRelativeTimeSpanString(at.toEpochMilli()).toString(), icon = Icons.Filled.Schedule)
                }
                FormDivider()
                ButtonRow(
                    stringResource(R.string.contacts_sync_contacts_now), icon = Icons.Filled.Sync, enabled = !state.syncing,
                    trailing = { if (state.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent) },
                    onClick = vm::sync,
                )
            }
            refused -> NavRow(
                stringResource(R.string.contacts_access_required), Icons.Filled.Warning, iconTint = Orange,
                subtitle = stringResource(R.string.contacts_open_settings_prompt),
            ) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }
            else -> NavRow(
                stringResource(R.string.contacts_add_collaborators), Icons.Filled.PersonAdd,
                subtitle = stringResource(R.string.contacts_upload_for_collaboration),
            ) { explaining = true }
        }
    }

    // Explain first: what is uploaded and why, before Android's own prompt (AATD-68).
    if (explaining) AlertDialog(
        onDismissRequest = { explaining = false },
        title = { Text(stringResource(R.string.contacts_add_collaborators)) },
        text = { Text(stringResource(R.string.contacts_footer_text)) },
        confirmButton = {
            TextButton(onClick = { explaining = false; ask.launch(Manifest.permission.READ_CONTACTS) }) { Text(stringResource(R.string.contacts_add_collaborators)) }
        },
        dismissButton = { TextButton(onClick = { explaining = false }) { Text(stringResource(R.string.notifications_not_now)) } },
    )
}
