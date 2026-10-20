package com.gracefultools.astrid.ui.lists

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.common.FloatingTextHeader
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Spacing

/**
 * iOS ListSettingsModal: "List Settings" with a segmented Filters / Members / Admin control (Admin
 * only for those who may manage the list). Android pushes it as a screen with a back chevron where
 * iOS presents a sheet with Done.
 */
@Composable
fun ListSettingsScreen(
    vm: ListSettingsViewModel,
    onBack: () -> Unit,
    onGone: () -> Unit,
    /** The Filters tab's sort and filter sections. */
    filters: @Composable () -> Unit = {},
    /** The Admin tab's AI Coding Agent and External sync sections (ui/sync). */
    integrations: @Composable () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    var tabIndex by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    DisposableEffect(Unit) { onDispose { vm.flushEdits() } }
    LaunchedEffect(state.gone) { if (state.gone) onGone() }
    val tabs = state.tabs
    val tab = tabs.getOrElse(tabIndex) { ListSettingsTab.FILTERS }

    Box(Modifier.fillMaxSize().background(colors.pageBg)) {
        Column(Modifier.fillMaxSize()) {
            FloatingTextHeader(stringResource(R.string.lists_list_settings), onBack = onBack)
            SegmentedTabs(
                tabs.map { t ->
                    when (t) {
                        ListSettingsTab.FILTERS -> Icons.Outlined.FilterList to stringResource(R.string.lists_filters)
                        ListSettingsTab.MEMBERS -> Icons.Outlined.People to stringResource(R.string.lists_members)
                        ListSettingsTab.ADMIN -> Icons.Outlined.Settings to stringResource(R.string.lists_admin)
                    }
                },
                selected = tabs.indexOf(tab).coerceAtLeast(0),
                onSelect = { tabIndex = it },
                modifier = Modifier.padding(Spacing.s16),
            )
            val settings = state.settings
            if (settings != null) Box(Modifier.weight(1f)) {
                when (tab) {
                    ListSettingsTab.FILTERS -> ListFiltersTab(settings.listId, settings.isFavorite, vm::setFavorite, filters = filters)
                    ListSettingsTab.MEMBERS -> ListMembersTab(state, vm)
                    ListSettingsTab.ADMIN -> ListAdminTab(state, vm, integrations)
                }
            }
        }
        // iOS's AppErrorBanner, as every screen shows a refusal (AATD-33).
        state.error?.let { message ->
            com.gracefultools.astrid.ui.common.AppErrorBanner(message, vm::dismissError, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = com.gracefultools.astrid.ui.theme.Spacing.s16))
            LaunchedEffect(message) { kotlinx.coroutines.delay(6_000); vm.dismissError() }
        }
    }
}

/** A Form-styled scrolling column of sections, 24 apart, as iOS's grouped Form spaces them. */
@Composable
internal fun FormColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Spacing.s8, bottom = Spacing.s32).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s24),
    ) { content() }
}

/** iOS's destructive `.alert`: a title, a message, Cancel and a red confirming button. */
@Composable
internal fun ConfirmAlert(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = Error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
internal fun rememberImagePicker(onPicked: (String) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { com.gracefultools.astrid.ui.detail.copyIntoApp(context, it)?.first?.let(onPicked) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
internal fun BottomSpace() = Spacer(Modifier.fillMaxWidth().height(Spacing.s16))
