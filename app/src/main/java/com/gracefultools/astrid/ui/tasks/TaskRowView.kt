package com.gracefultools.astrid.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.DueLabel
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.PriorityColors
import com.gracefultools.astrid.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One row, drawn as iOS's TaskRowView: a rounded card, the leading control in a 34dp square with a
 * priority-coloured border, a 19sp title and a 15sp line of metadata. Every value is the core's
 * projection; nothing here decides what a row says.
 */
@Composable
fun TaskRowView(
    row: TaskRow,
    onLeading: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    avatarInitial: (String) -> String = { "?" },
) {
    val colors = AstridTheme.colors
    val priority = PriorityColors.getOrElse(row.priority) { PriorityColors[0] }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = (row.depth * 24).dp)
            .clip(shape)
            .background(if (colors.isDark) colors.bgSecondary else Color.White)
            .border(0.5.dp, colors.border, shape)
            .clickable(onClick = onOpen)
            .heightIn(min = 76.dp)
            .padding(horizontal = Spacing.s16, vertical = 14.dp)
            .alpha(if (row.isPending) 0.7f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        LeadingControl(row, priority, onLeading, avatarInitial)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                row.title,
                fontSize = 19.sp, fontWeight = FontWeight.Medium,
                color = if (row.completed) colors.textMuted else colors.textPrimary,
                textDecoration = if (row.completed) TextDecoration.LineThrough else null,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Metadata(row)
        }
    }
}

@Composable
private fun LeadingControl(row: TaskRow, priority: Color, onTap: () -> Unit, initialOf: (String) -> String) {
    val colors = AstridTheme.colors
    val square = RoundedCornerShape(8.dp)
    val label = stringResource(if (row.completed) R.string.tasks_reopen_a11y else R.string.tasks_complete_a11y, row.title)
    Box(
        Modifier
            .size(34.dp)
            .clip(square)
            .then(
                when (row.leading.kind) {
                    "checkbox" -> if (row.completed) Modifier.background(priority) else Modifier.border(2.dp, priority, square)
                    "avatar" -> Modifier.background(colors.accent).border(2.dp, priority, square)
                    else -> Modifier.border(2.dp, if (row.leading.kind == "copy") Color.Gray.copy(alpha = 0.4f) else priority, square)
                },
            )
            .clickable(onClick = onTap)
            .semantics { contentDescription = row.checkboxLabel ?: label; role = Role.Checkbox },
        contentAlignment = Alignment.Center,
    ) {
        when (row.leading.kind) {
            "checkbox" -> if (row.completed) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
            "avatar" -> Text(initialOf(row.leading.userId.orEmpty()), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            "copy" -> Icon(Icons.Outlined.ContentCopy, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            else -> Text(
                stringResource(R.string.tasks_unassigned_glyph),
                color = priority, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = if (row.completed) TextDecoration.LineThrough else null,
                modifier = Modifier.alpha(if (row.completed) 0.5f else 1f),
            )
        }
    }
}

@Composable
private fun Metadata(row: TaskRow) {
    val colors = AstridTheme.colors
    val due = dueText(row.due)
    val hasAny = due != null || row.listChips.isNotEmpty() || row.isRepeating || row.isPrivate ||
        row.commentCount > 0 || (row.showsIdentifier && row.identifier != null) || row.isPending
    if (!hasAny) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
        val muted = colors.textSecondary
        if (row.showsIdentifier && row.identifier != null) {
            Text(row.identifier, fontSize = 12.sp, color = colors.textMuted, maxLines = 1)
        }
        if (due != null) {
            Icon(Icons.Outlined.Schedule, null, tint = if (row.isOverdue) Error else muted, modifier = Modifier.size(13.dp))
            Text(due, fontSize = 15.sp, color = if (row.isOverdue) Error else muted, maxLines = 1)
        }
        if (row.isRepeating) Icon(Icons.Outlined.Repeat, null, tint = muted, modifier = Modifier.size(13.dp))
        if (row.isPrivate) Icon(Icons.Outlined.Lock, null, tint = muted, modifier = Modifier.size(13.dp))
        if (row.commentCount > 0) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = muted, modifier = Modifier.size(13.dp))
            Text(row.commentCount.toString(), fontSize = 13.sp, color = muted)
        }
        for (chip in row.listChips.take(2)) {
            val tint = parseColor(chip.color)
            Text(
                chip.name, fontSize = 13.sp, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        if (row.isPending) Text(stringResource(R.string.tasks_waiting_to_send), fontSize = 12.sp, color = colors.textMuted)
    }
}

/** The core's due label in words. The core chose the label; this only formats its date for the reader. */
@Composable
fun dueText(due: DueLabel): String? = when (due.key) {
    "today" -> stringResource(R.string.time_today)
    "tomorrow" -> stringResource(R.string.time_tomorrow)
    "yesterday" -> stringResource(R.string.time_yesterday)
    "on" -> {
        val day = runCatching { LocalDate.parse(due.date) }.getOrNull()
        val time = due.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        listOfNotNull(
            day?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
        ).joinToString(" ").ifEmpty { null }
    }
    else -> null
}

fun parseColor(hex: String?): Color = runCatching {
    Color(android.graphics.Color.parseColor(if (hex.isNullOrBlank()) "#3b82f6" else hex))
}.getOrDefault(Color(0xFF3B82F6))
