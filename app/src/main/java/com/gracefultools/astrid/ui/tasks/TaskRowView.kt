package com.gracefultools.astrid.ui.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.ui.platform.testTag
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.common.CheckboxArt
import com.gracefultools.astrid.ui.common.RowFaceShape
import com.gracefultools.astrid.ui.common.initialsOf
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles
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
import androidx.compose.material.icons.outlined.ContentCopy
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
import com.gracefultools.astrid.ui.theme.PriorityColors
import com.gracefultools.astrid.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One row, drawn as iOS's TaskRowView (AATD-36): a rounded-8 card (white 0.8 on Ocean, 1.0 and a
 * 2.5 accent border when it is the open task in two panes), the leading control in a 34 square — the
 * checkbox artwork, a face, the unassigned mark or the copy control — a 19sp title, and one line of
 * metadata: the board identifier, the due date, up to two list chips and "+N". Every value is the
 * core's projection; nothing here decides what a row says.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TaskRowView(
    row: TaskRow,
    onLeading: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** The task open beside the list (two panes): accent border, full white. */
    isSelected: Boolean = false,
    /** One-line title while the detail pane is open, as iOS's compact mode. */
    compact: Boolean = false,
    /** A board card's host draws the card; the row draws only its content. */
    embedded: Boolean = false,
) {
    val colors = AstridTheme.colors
    val shape = RoundedCornerShape(8.dp)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier.fillMaxWidth().padding(start = (minOf(row.depth, 4) * 16).dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (embedded) Modifier else Modifier.clip(shape)
                        .background(if (isSelected) colors.cardSelected else colors.card)
                        .border(if (isSelected) 2.5.dp else 0.5.dp, if (isSelected) colors.accent else colors.cardBorder, shape),
                )
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = if (row.offersCopyIdentifier && row.identifier != null) ({
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        menu = true
                    }) else null,
                )
                .heightIn(min = 76.dp)
                .padding(horizontal = if (embedded) 10.dp else Spacing.s16, vertical = if (embedded) 12.dp else 14.dp)
                .alpha(if (row.isPending) 0.7f else 1f)
                .testTag("taskRow"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            LeadingControl(row) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onLeading()
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.title,
                        style = TextStyles.rowTitle,
                        color = if (row.completed) colors.textMuted else colors.textPrimary,
                        textDecoration = if (row.completed) TextDecoration.LineThrough else null,
                        maxLines = if (compact) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TitleChips(row)
                }
                Metadata(row)
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.tasks_task_id_copy)) },
                leadingIcon = { Icon(Icons.Filled.Tag, null) },
                onClick = { row.identifier?.let { clipboard.setText(androidx.compose.ui.text.AnnotatedString(it)) }; menu = false },
            )
        }
    }
}

/** iOS's title chips: outlined, radius 4, caption — Won't do, Syncing / Sync failed, and `repo#N`. */
@Composable
private fun TitleChips(row: TaskRow) {
    val colors = AstridTheme.colors
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    @Composable
    fun chip(text: String, onClick: (() -> Unit)? = null) = Text(
        text, style = TextStyles.caption, color = colors.textSecondary, maxLines = 1,
        modifier = Modifier.border(1.dp, colors.border, RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
    if (row.isCanceled) chip(stringResource(R.string.tasks_wont_do_chip))
    when (row.syncMark) {
        "pending" -> chip(stringResource(R.string.tasks_sync_pending_chip))
        "failed" -> chip(stringResource(R.string.tasks_sync_failed_chip))
    }
    row.remote?.let { remote -> chip(remote.label, remote.url?.let { url -> { runCatching { uri.openUri(url) } } }) }
}

@Composable
private fun LeadingControl(row: TaskRow, onTap: () -> Unit) {
    val colors = AstridTheme.colors
    val priority = PriorityColors.getOrElse(row.priority) { PriorityColors[0] }
    val square = RoundedCornerShape(8.dp)
    val label = stringResource(if (row.completed) R.string.tasks_reopen_a11y else R.string.tasks_complete_a11y, row.title)
    val tap = Modifier.size(34.dp).clip(square).clickable(onClick = onTap)
        .semantics { contentDescription = row.checkboxLabel ?: label; role = Role.Checkbox }
    when (row.leading.kind) {
        "copy" -> Box(tap.border(2.dp, Color.Gray.copy(alpha = 0.4f), square), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ContentCopy, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
        "avatar" -> Avatar(
            image = row.assignee?.image,
            initials = row.leading.initial ?: initialsOf(row.assignee?.name, row.assignee?.email),
            size = 34.dp, shape = RowFaceShape, border = priority, modifier = tap,
        )
        "unassigned" -> Box(tap.border(2.dp, priority, square), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.tasks_unassigned_glyph),
                color = priority, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = if (row.completed) TextDecoration.LineThrough else null,
                modifier = Modifier.alpha(if (row.completed) 0.5f else 1f),
            )
        }
        else -> CheckboxArt(row.priority, row.isRepeating, row.completed, tap)
    }
}

@Composable
private fun Metadata(row: TaskRow) {
    val colors = AstridTheme.colors
    val due = rowDueText(row)
    val identifier = row.identifier.takeIf { row.showsIdentifier }
    if (due == null && row.listChips.isEmpty() && identifier == null && !row.isPending) return
    Row(
        Modifier.heightIn(min = 27.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (identifier != null) Text(identifier, style = TextStyles.subheadline, color = colors.textMuted, maxLines = 1)
        if (due != null) Text(due, style = TextStyles.subheadline, color = colors.textMuted, maxLines = 1)
        if (row.listChips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                for (chip in row.listChips.take(2)) ListChipView(chip, Modifier.weight(1f, fill = false))
                if (row.listChips.size > 2) Text("+${row.listChips.size - 2}", style = TextStyles.subheadline, color = colors.textMuted)
            }
        }
        // Android only: the Outbox is visible per row (iOS marks only GitHub-board syncing). PARITY.md.
        if (row.isPending) Text(stringResource(R.string.tasks_waiting_to_send), style = TextStyles.caption, color = colors.textMuted, maxLines = 1)
    }
}

/** iOS's list chip: glyph + name in subheadline, grey fill, 1pt border, radius 12, padding 10x4. */
@Composable
fun ListChipView(chip: com.gracefultools.astrid.model.ListChip, modifier: Modifier = Modifier) {
    val colors = AstridTheme.colors
    val tint = parseColor(chip.color)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.clip(shape).background(colors.chipBg).border(1.dp, colors.chipBorder, shape).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, iconTint) = when (chip.glyph) {
            "tag" -> Icons.Outlined.Sell to tint
            "globe" -> Icons.Outlined.Public to Success
            "people" -> Icons.Outlined.People to colors.accent
            else -> Icons.Filled.Tag to tint
        }
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(13.dp))
        Text(chip.name, style = TextStyles.subheadline, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A row's date as iOS draws it: a timed task as "Oct 19 6:00 PM" (its own date, even today), an
 * all-day one as Today / Tomorrow / Yesterday or a medium date.
 */
@Composable
fun rowDueText(row: TaskRow): String? {
    val at = row.dueAt
    if (at != null) {
        val day = runCatching { LocalDate.parse(at.date) }.getOrNull() ?: return dueText(row.due)
        val time = runCatching { LocalTime.parse(at.time) }.getOrNull()
        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd")
        return listOfNotNull(
            day.format(DateTimeFormatter.ofPattern(pattern, locale)),
            time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)),
        ).joinToString(" ")
    }
    return dueText(row.due)
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
