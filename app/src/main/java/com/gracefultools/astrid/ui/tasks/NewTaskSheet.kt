package com.gracefultools.astrid.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.SharedFile
import com.gracefultools.astrid.ui.share.ShareTaskViewModel
import com.gracefultools.astrid.ui.theme.Accent
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS's share extension form (TaskQuickCreateView): the attachment, title and description,
 * priority, and where the task will go. Also what the "New task" launcher shortcut opens, and what
 * shared text opens — both Android extras (AATD-60).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskSheet(
    initial: String,
    file: SharedFile?,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, priority: Int) -> Unit,
) {
    val colors = AstridTheme.colors
    val prefill = remember(initial, file) { file?.let { ShareTaskViewModel.titleFromFileName(it.name) } ?: initial }
    var title by rememberSaveable(prefill) { mutableStateOf(prefill) }
    var description by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(500); runCatching { focus.requestFocus() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (colors.isDark) colors.bgSecondary else Color.White,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(bottom = Spacing.s24)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Text(
                    stringResource(R.string.share_create_task), style = TextStyles.headline, color = colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() }, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(onClick = { onSave(title, description, priority) }, enabled = title.isNotBlank() && !saving) {
                    Text(stringResource(R.string.share_save), fontWeight = FontWeight.SemiBold)
                }
            }
            if (file != null) {
                Section(stringResource(R.string.share_attachment)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12), verticalAlignment = Alignment.CenterVertically) {
                        Icon(fileIcon(file.mimeType.orEmpty()), null, tint = Accent, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, style = TextStyles.subheadline, color = colors.textPrimary, maxLines = 2)
                            Text(
                                android.text.format.Formatter.formatShortFileSize(LocalContext.current, file.size),
                                style = TextStyles.caption, color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
            Section(stringResource(R.string.share_task_details)) {
                OutlinedTextField(
                    title, { title = it }, Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text(stringResource(R.string.share_task_title)) }, singleLine = true,
                )
                Spacer(Modifier.size(Spacing.s8))
                OutlinedTextField(
                    description, { description = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.share_description_optional)) }, minLines = 3, maxLines = 5,
                )
            }
            Section(stringResource(R.string.share_priority)) {
                val names = listOf(R.string.share_priority_none, R.string.share_priority_low, R.string.share_priority_medium, R.string.share_priority_high)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    names.forEachIndexed { value, name ->
                        SegmentedButton(
                            selected = priority == value,
                            onClick = { priority = value },
                            shape = SegmentedButtonDefaults.itemShape(value, names.size),
                            icon = { Icon(if (value == 0) Icons.Outlined.Circle else Icons.Filled.Flag, null, Modifier.size(14.dp)) },
                        ) { Text(stringResource(name), maxLines = 1) }
                    }
                }
            }
            Column(Modifier.padding(horizontal = Spacing.s16, vertical = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Note(Icons.Filled.Info, stringResource(R.string.share_default_list_note))
                if (file != null) Note(Icons.Filled.ArrowCircleUp, stringResource(R.string.share_upload_note))
                error?.let { Text(it, style = TextStyles.caption, color = com.gracefultools.astrid.ui.theme.Error) }
            }
        }
    }
}

@Composable
private fun Section(header: String, content: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    Text(
        header.uppercase(), style = TextStyles.footnote, color = colors.textSecondary,
        modifier = Modifier.padding(start = Spacing.s24, end = Spacing.s16, top = Spacing.s16, bottom = Spacing.s8).semantics { heading() },
    )
    Column(
        Modifier.padding(horizontal = Spacing.s16).fillMaxWidth()
            .background(if (colors.isDark) colors.bgTertiary else colors.bgSecondary, RoundedCornerShape(Radius.large))
            .padding(Spacing.s12),
    ) { content() }
}

@Composable
private fun Note(icon: ImageVector, text: String) {
    val colors = AstridTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        Text(text, style = TextStyles.caption, color = colors.textSecondary)
    }
}

/** iOS fileIcon(for:), as Material symbols. */
private fun fileIcon(mimeType: String): ImageVector = when {
    mimeType.startsWith("image/") -> Icons.Filled.Photo
    mimeType.startsWith("video/") -> Icons.Filled.Videocam
    "pdf" in mimeType -> Icons.Filled.Description
    "text" in mimeType -> Icons.AutoMirrored.Filled.Article
    "zip" in mimeType || "archive" in mimeType -> Icons.Filled.Archive
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}
