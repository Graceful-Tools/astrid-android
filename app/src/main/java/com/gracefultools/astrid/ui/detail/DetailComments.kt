package com.gracefultools.astrid.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.CommentRow
import com.gracefultools.astrid.model.FileRow
import com.gracefultools.astrid.model.TaskDetail
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.markdown.MarkdownView
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.OceanColors
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS's CommentSectionViewEnhanced (AATD-53): "Comments (n)" with Show/Hide system, the empty line,
 * then a chat thread — others on the left with their face, mine on the right with mine, "name · time"
 * under each bubble, replies nested, the server's notes centred. Long press: Copy, Reply, and for
 * mine Edit and Delete.
 */
@Composable
internal fun CommentsSection(
    detail: TaskDetail,
    onOpenFile: (FileRow) -> Unit,
    fetch: (String, (String) -> Unit) -> Unit,
    onReply: (CommentRow) -> Unit,
    onEdit: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenList: (String) -> Unit,
    typingAgent: TypingAgent? = null,
    offline: Boolean = false,
) {
    val colors = AstridTheme.colors
    var showSystem by remember { mutableStateOf(false) }
    val people = detail.comments.filterNot { it.isSystem }
    val shown = if (showSystem) detail.comments else people
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s16, vertical = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.comments_count, people.size), style = TextStyles.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (detail.comments.any { it.isSystem }) {
                Text(
                    stringResource(if (showSystem) R.string.comments_hide_system else R.string.comments_show_system),
                    style = TextStyles.caption, color = colors.accent,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { showSystem = !showSystem }.padding(6.dp),
                )
            }
            // iOS's offline mark in the header: the thread is what this device last heard (AATD-62).
            if (offline) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                Icon(Icons.Outlined.WifiOff, null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
                Text(stringResource(R.string.comments_offline), style = TextStyles.caption2, color = colors.textMuted)
            }
        }
        if (people.isEmpty()) Text(stringResource(R.string.comments_empty), style = TextStyles.caption, color = colors.textMuted)
        shown.forEach { comment ->
            if (comment.isSystem) SystemNote(comment)
            else CommentBubble(comment, onOpenFile, fetch, onReply, onEdit, onDelete, onOpenTask, onOpenList)
        }
        // iOS's AgentTypingIndicator under the thread, while an agent works on this task (AATD-53).
        typingAgent?.let { com.gracefultools.astrid.ui.common.AgentTypingIndicator(it.name) }
    }
}

@Composable
private fun SystemNote(comment: CommentRow) {
    val colors = AstridTheme.colors
    val day = comment.createdAt?.let { formatCommentDay(it) }
    Text(
        if (day != null) stringResource(R.string.comments_system_on, day, comment.content) else comment.content,
        style = TextStyles.caption, color = colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentBubble(
    comment: CommentRow,
    onOpenFile: (FileRow) -> Unit,
    fetch: (String, (String) -> Unit) -> Unit,
    onReply: (CommentRow) -> Unit,
    onEdit: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenList: (String) -> Unit,
) {
    val colors = AstridTheme.colors
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val mine = comment.isMine
    val bubble = when {
        colors.isDark -> colors.bgSecondary
        mine -> if (colors == OceanColors) Color.White else colors.bgSecondary
        else -> Color.White.copy(alpha = 0.5f)
    }
    val indent = if (comment.isReply) 24.dp else 0.dp
    Row(
        Modifier.fillMaxWidth().padding(start = if (!mine) indent else 0.dp, end = if (mine) indent else 0.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) { Avatar(comment.authorImage, comment.authorInitials ?: "", 32.dp); Spacer(Modifier.size(Spacing.s8)) }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.weight(1f, fill = false)) {
            Box {
                Column(
                    Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(8.dp)).background(bubble)
                        .combinedClickable(onClick = {}, onLongClick = { menu = true }).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (comment.showsText) {
                        if (comment.blocks.isNotEmpty()) MarkdownView(comment.blocks, onReference = { kind, id -> if (kind == "list") onOpenList(id) else onOpenTask(id) })
                        else Text(comment.content, style = TextStyles.body, color = colors.textPrimary)
                    }
                    comment.files.filter { it.rendersInline }.forEach { file -> InlineImage(file, fetch) { onOpenFile(file) } }
                    comment.files.filterNot { it.rendersInline }.forEach { file ->
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(colors.bgTertiary).clickable { onOpenFile(file) }.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.AttachFile, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                            Text(file.name, style = TextStyles.subheadline, color = colors.textPrimary)
                        }
                    }
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.actions_copy)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = { clipboard.setText(AnnotatedString(comment.content)); menu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.comments_reply)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                        onClick = { onReply(comment); menu = false })
                    if (mine) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.comments_edit)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { editing = true; menu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.actions_delete), color = Error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Error) },
                            onClick = { confirmingDelete = true; menu = false })
                    }
                }
            }
            val who = if (mine) stringResource(R.string.assignee_you) else comment.authorName.orEmpty()
            val time = comment.createdAt?.let { formatCommentTime(it) }
            Text(
                listOfNotNull(who.takeIf { it.isNotEmpty() }, time).joinToString(" · ") +
                    if (comment.isPending) "  " + stringResource(R.string.tasks_waiting_to_send) else "",
                style = TextStyles.caption2, color = colors.textMuted, modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
            )
        }
        if (mine) { Spacer(Modifier.size(Spacing.s8)); Avatar(comment.authorImage, comment.authorInitials ?: "", 32.dp) }
    }
    if (editing) {
        var text by remember { mutableStateOf(comment.content) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.comments_edit)) },
            text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { editing = false; onEdit(comment.id, text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.detail_save)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.comments_delete_comment)) },
            text = { Text(stringResource(R.string.comments_delete_confirm)) },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; onDelete(comment.id) }) { Text(stringResource(R.string.actions_delete), color = Error) } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun formatCommentTime(iso: String): String? {
    val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return null
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val local = instant.atZone(java.time.ZoneId.systemDefault())
    val today = java.time.LocalDate.now()
    val time = local.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale))
    if (local.toLocalDate() == today) return time
    val day = local.format(java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"), locale))
    return "$day $time"
}

@Composable
private fun formatCommentDay(iso: String): String? {
    val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return null
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return instant.atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale))
}
