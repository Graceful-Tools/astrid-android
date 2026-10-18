package com.gracefultools.astrid.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.BoardColumn
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.tasks.TaskRowView
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing

/**
 * iOS's BoardView: columns side by side, each a scroll of cards. A long press offers "Move to…",
 * which is also how TalkBack and a keyboard move a card (as on the Mac and Windows).
 */
@Composable
fun BoardContent(state: BoardState, onOpenTask: (String) -> Unit, onMove: (String, String) -> Unit, onComplete: (TaskRow) -> Unit) {
    val columns = state.board?.columns.orEmpty()
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.s12, vertical = Spacing.s8),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(columns, key = { it.id }) { column -> ColumnView(column, columns, onOpenTask, onMove, onComplete) }
    }
}

@Composable
private fun ColumnView(
    column: BoardColumn,
    all: List<BoardColumn>,
    onOpenTask: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onComplete: (TaskRow) -> Unit,
) {
    val colors = AstridTheme.colors
    Column(
        Modifier.width(300.dp).fillMaxHeight().padding(bottom = Spacing.s8).clip(RoundedCornerShape(12.dp))
            .background(colors.bgSecondary.copy(alpha = 0.6f)).padding(Spacing.s8),
    ) {
        Row(Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s8)) {
            Text(column.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.weight(1f, fill = false))
            Text("  ${column.total}", fontSize = 15.sp, color = colors.textMuted)
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.s8),
            modifier = Modifier.padding(bottom = Spacing.s4),
        ) {
            items(column.cards, key = { it.id }) { card -> Card(card, column, all, onOpenTask, onMove, onComplete) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Card(
    card: TaskRow,
    column: BoardColumn,
    all: List<BoardColumn>,
    onOpenTask: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onComplete: (TaskRow) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val others = all.filter { it.id != column.id }
    val moveLabel = stringResource(R.string.board_move_to)
    Box(
        Modifier.padding(0.dp).semantics {
            customActions = others.map { target -> CustomAccessibilityAction("$moveLabel ${target.name}") { onMove(card.id, target.id); true } }
        },
    ) {
        Box(Modifier.combinedClickable(onClick = { onOpenTask(card.id) }, onLongClick = { menu = true })) {
            TaskRowView(card, onLeading = { onComplete(card) }, onOpen = { onOpenTask(card.id) }, modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            others.forEach { target ->
                DropdownMenuItem(text = { Text("$moveLabel ${target.name}") }, onClick = { menu = false; onMove(card.id, target.id) })
            }
        }
    }
}
