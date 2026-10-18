package com.gracefultools.astrid.ui.tasks

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.gracefultools.astrid.R

/** iOS's share-extension sheet: what was shared, editable, added to the open list with smart parsing. */
@Composable
fun NewTaskSheet(initial: String, listName: String?, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(listName?.let { stringResource(R.string.share_add_to, it) } ?: stringResource(R.string.tasks_add_task)) },
        text = { OutlinedTextField(text, { text = it }, placeholder = { Text(stringResource(R.string.tasks_add_task_placeholder)) }, minLines = 2) },
        confirmButton = { TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.tasks_add_task)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
