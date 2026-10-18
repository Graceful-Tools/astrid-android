package com.gracefultools.astrid.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.BoardColumn
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.tasks.TaskRowView
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The card being dragged: which, where the finger is (window), and where on the card it took hold. */
private data class Drag(val card: TaskRow, val from: String, val pointer: Offset, val grab: Offset, val travelled: Float)

/**
 * iOS's BoardView: columns side by side, each a scroll of cards. Press and drag a card to another
 * column or position (dropBoardCard); a press that does not move offers "Move to…", which is also
 * how TalkBack moves a card.
 */
@Composable
fun BoardContent(
    state: BoardState,
    onOpenTask: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onComplete: (TaskRow) -> Unit,
    onDrop: (String, String, Int) -> Unit = { _, _, _ -> },
) {
    val columns = state.board?.columns.orEmpty()
    val row = rememberLazyListState()
    val columnBounds = remember { mutableStateMapOf<String, Rect>() }
    val cardBounds = remember { mutableStateMapOf<String, Pair<String, Rect>>() }
    var root by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<Drag?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val edge = with(LocalDensity.current) { 48.dp.toPx() }

    // Near an edge while dragging, the columns scroll — a phone shows one and a bit of them.
    LaunchedEffect(drag != null) {
        while (drag != null) {
            val x = drag!!.pointer.x
            when {
                x > root.right - edge -> row.scrollBy(24f)
                x < root.left + edge -> row.scrollBy(-24f)
            }
            delay(16)
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInWindow() }) {
        LazyRow(
            state = row,
            contentPadding = PaddingValues(horizontal = Spacing.s12, vertical = Spacing.s8),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            modifier = Modifier.fillMaxSize().testTag("board"),
        ) {
            items(columns, key = { it.id }) { column ->
                ColumnView(
                    column, columns, drag?.card?.id, menuFor,
                    onBounds = { columnBounds[column.id] = it },
                    onCardBounds = { id, bounds -> cardBounds[id] = column.id to bounds },
                    onOpenTask = onOpenTask, onMove = onMove, onComplete = onComplete,
                    onMenu = { menuFor = it },
                    onDragStart = { card, at ->
                        val bounds = cardBounds[card.id]?.second ?: return@ColumnView
                        drag = Drag(card, column.id, bounds.topLeft + at, at, 0f)
                    },
                    onDrag = { amount -> drag = drag?.let { it.copy(pointer = it.pointer + amount, travelled = it.travelled + amount.getDistance()) } },
                    onDragEnd = {
                        val done = drag ?: return@ColumnView
                        drag = null
                        if (done.travelled < edge / 2) {
                            menuFor = done.card.id
                        } else {
                            val cards = cardBounds.entries.filter { it.key != done.card.id }
                                .groupBy({ it.value.first }, { it.value.second })
                                .mapValues { (_, list) -> list.sortedBy { it.top } }
                            BoardDrag.target(done.pointer, columnBounds, cards)?.let { onDrop(done.card.id, it.columnId, it.index) }
                        }
                    },
                    onDragCancel = { drag = null },
                )
            }
        }
        // The lifted card follows the finger above everything, so no column clips it.
        drag?.let { lifted ->
            val topLeft = lifted.pointer - lifted.grab - root.topLeft
            Box(
                Modifier.zIndex(1f).offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                    .width(284.dp).shadow(8.dp, RoundedCornerShape(8.dp)),
            ) { TaskRowView(lifted.card, onLeading = {}, onOpen = {}) }
        }
    }
}

@Composable
private fun ColumnView(
    column: BoardColumn,
    all: List<BoardColumn>,
    draggingId: String?,
    menuFor: String?,
    onBounds: (Rect) -> Unit,
    onCardBounds: (String, Rect) -> Unit,
    onOpenTask: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onComplete: (TaskRow) -> Unit,
    onMenu: (String?) -> Unit,
    onDragStart: (TaskRow, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val colors = AstridTheme.colors
    Column(
        Modifier.width(300.dp).fillMaxHeight().padding(bottom = Spacing.s8).clip(RoundedCornerShape(12.dp))
            .background(colors.bgSecondary.copy(alpha = 0.6f))
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .padding(Spacing.s8),
    ) {
        Row(Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s8)) {
            Text(column.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.weight(1f, fill = false))
            Text("  ${column.total}", fontSize = 15.sp, color = colors.textMuted)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s8), modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s4)) {
            items(column.cards, key = { it.id }) { card ->
                val others = all.filter { it.id != column.id }
                val moveLabel = stringResource(R.string.board_move_to)
                Box(
                    Modifier
                        .onGloballyPositioned { onCardBounds(card.id, it.boundsInWindow()) }
                        .alpha(if (card.id == draggingId) 0.3f else 1f)
                        .pointerInput(card.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { at -> onDragStart(card, at) },
                                onDrag = { change, amount -> change.consume(); onDrag(amount) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel,
                            )
                        }
                        .semantics {
                            customActions = others.map { target -> CustomAccessibilityAction("$moveLabel ${target.name}") { onMove(card.id, target.id); true } }
                        },
                ) {
                    TaskRowView(card, onLeading = { onComplete(card) }, onOpen = { onOpenTask(card.id) }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(menuFor == card.id, onDismissRequest = { onMenu(null) }) {
                        others.forEach { target ->
                            DropdownMenuItem(text = { Text("$moveLabel ${target.name}") }, onClick = { onMenu(null); onMove(card.id, target.id) })
                        }
                    }
                }
            }
        }
    }
}
