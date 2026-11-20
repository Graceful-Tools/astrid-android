package com.gracefultools.astrid.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.StatusOptions
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TypeSize

/**
 * The quick changer's completion row (ADEV-146/148, iOS MarkDoneQuickSetRow): the task row's own
 * checkbox, then "Mark as done", or checked beside "Mark as to-do" once it is done. No Compose here,
 * so a test can ask.
 */
object MarkDoneQuickSet {
    @StringRes
    fun label(completed: Boolean): Int = if (completed) R.string.tasks_mark_as_todo else R.string.tasks_mark_as_done

    /**
     * Completing a task that is not yours (someone else's or nobody's) asks first; your own and
     * reopening never do (iOS `completionNeedsConfirmation`, ADEV-79). Whose it is comes from the
     * core's row: an open task of yours is the one that wears the checkbox.
     */
    fun needsConfirmation(row: TaskRow): Boolean = !row.completed && row.leading.kind != "checkbox"
}

/**
 * iOS's quick changer as Android draws it: the board's columns, the current one checked, then the
 * completion row — or, for a task that is not yours, the question in its place (ADEV-150).
 */
@Composable
fun StatusPicker(
    options: StatusOptions,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    row: TaskRow? = null,
    onComplete: () -> Unit = {},
) {
    val colors = AstridTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_move_to)) },
        confirmButton = {},
        dismissButton = { AstridTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        text = {
            Column {
                options.columns.forEach { column ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(column.id) }.padding(vertical = Spacing.s12, horizontal = Spacing.s8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(column.name, fontSize = TypeSize.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        if (column.isCurrent) Icon(Icons.Filled.Check, null, tint = colors.accentInk)
                    }
                }
                if (row != null) {
                    HorizontalDivider(Modifier.padding(vertical = Spacing.s8), color = colors.divider)
                    if (MarkDoneQuickSet.needsConfirmation(row)) CompletionQuestion(row, onDismiss, onComplete)
                    else MarkDoneRow(row, onComplete)
                }
            }
        },
    )
}

/** The task's own checkbox art at menu size, then what a tap does. */
@Composable
private fun MarkDoneRow(row: TaskRow, onComplete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onComplete).semantics { role = Role.Button }
            .padding(vertical = Spacing.s8, horizontal = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        CheckboxArt(row.priority, row.isRepeating, row.completed, size = Spacing.s24)
        Text(stringResource(MarkDoneQuickSet.label(row.completed)), fontSize = TypeSize.body, color = AstridTheme.colors.textPrimary)
    }
}

/** iOS CompletionConfirmationPrompt: "Complete this task?", whose it is, then Cancel / Complete. */
@Composable
private fun CompletionQuestion(row: TaskRow, onCancel: () -> Unit, onComplete: () -> Unit) {
    val colors = AstridTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            CheckboxArt(row.priority, row.isRepeating, checked = false, size = Spacing.s24)
            Text(stringResource(R.string.tasks_confirm_complete_title), fontSize = TypeSize.body, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        }
        val who = row.assignee?.display
        Text(
            if (who.isNullOrBlank()) stringResource(R.string.tasks_confirm_complete_unassigned) else stringResource(R.string.mac_assigned_to, who),
            fontSize = TypeSize.caption1, color = colors.textSecondary,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AstridTextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
            AstridTextButton(onClick = onComplete) { Text(stringResource(R.string.reminders_complete), color = colors.accentInk) }
        }
    }
}
