package com.gracefultools.astrid.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.StatusOptions
import com.gracefultools.astrid.ui.theme.AstridTheme

/** iOS's status quick picker: the board's columns, the current one checked. */
@Composable
fun StatusPicker(options: StatusOptions, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_move_to)) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        text = {
            Column {
                options.columns.forEach { column ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(column.id) }.padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(column.name, fontSize = 17.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        if (column.isCurrent) Icon(Icons.Filled.Check, null, tint = colors.accent)
                    }
                }
            }
        },
    )
}
