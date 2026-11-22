package com.gracefultools.astrid.ui.board

import com.gracefultools.astrid.ui.theme.DragDistance
import androidx.compose.foundation.shape.CircleShape
import com.gracefultools.astrid.ui.theme.BorderWidth
import com.gracefultools.astrid.ui.theme.ControlHeight
import com.gracefultools.astrid.ui.theme.TouchLayout
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.gracefultools.astrid.ui.theme.Radius
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.gracefultools.astrid.ui.theme.FrameSize
import com.gracefultools.astrid.ui.theme.Gap
import com.gracefultools.astrid.ui.theme.AstridShadows
import com.gracefultools.astrid.ui.theme.shadow

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
    onAddCard: suspend (String, String) -> Boolean = { _, _ -> false },
    onDismissError: () -> Unit = {},
    selectedTaskId: String? = null,
) {
    val columns = state.board?.columns.orEmpty()
    val row = rememberLazyListState()
    val columnBounds = remember { mutableStateMapOf<String, Rect>() }
    val cardBounds = remember { mutableStateMapOf<String, Pair<String, Rect>>() }
    var root by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<Drag?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val edge = with(LocalDensity.current) { DragDistance.edgeScroll.toPx() }

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

    // Where a drop would land, for iOS's accent ring on the column and line at the slot.
    val hover = drag?.takeIf { it.travelled >= edge / 2 }?.let { lifted ->
        val cards = cardBounds.entries.filter { it.key != lifted.card.id }
            .groupBy({ it.value.first }, { it.value.second }).mapValues { (_, list) -> list.sortedBy { it.top } }
        BoardDrag.target(lifted.pointer, columnBounds, cards)
    }
    fun finish() {
        val done = drag ?: return
        drag = null
        if (done.travelled < edge / 2) {
            menuFor = done.card.id
        } else {
            val cards = cardBounds.entries.filter { it.key != done.card.id }
                .groupBy({ it.value.first }, { it.value.second })
                .mapValues { (_, list) -> list.sortedBy { it.top } }
            BoardDrag.target(done.pointer, columnBounds, cards)?.let { onDrop(done.card.id, it.columnId, it.index) }
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInWindow() }
            // The board follows a lifted card's finger itself, before any child: the card that
            // started the drag may scroll out of composition while it is carried to another column.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val lifted = drag ?: continue
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            val at = root.topLeft + change.position
                            drag = lifted.copy(pointer = at, travelled = lifted.travelled + (at - lifted.pointer).getDistance())
                            change.consume()
                        } else {
                            finish()
                        }
                    }
                }
            },
    ) {
        // iOS's boardColumnsVisible: floor(width / board-column-min-width), 1...5, columns to a
        // screen — one on a phone. As iOS insets each column by half the gap, the screen edges
        // get half a gap and the gutters between columns a whole one.
        val visible = (maxWidth / TouchLayout.boardColumnMinWidth).toInt().coerceIn(1, 5)
        val gap = TouchLayout.boardColumnGap
        val columnWidth = (maxWidth - gap - gap * (visible - 1)) / visible
        // Open on the inbox column once the columns arrive (ADEV-135); a reload leaves the row be.
        var opened by remember(state.board?.listId) { mutableStateOf(false) }
        LaunchedEffect(columns.isNotEmpty(), visible) {
            if (columns.isNotEmpty() && !opened) {
                opened = true
                row.scrollToItem(BoardInitialColumn.index(columns, multiColumn = visible > 1))
            }
        }
        LazyRow(
            state = row,
            contentPadding = PaddingValues(horizontal = gap / 2, vertical = Spacing.s8),
            horizontalArrangement = Arrangement.spacedBy(gap),
            flingBehavior = com.gracefultools.astrid.ui.theme.LocalReducedMotion.current.let { still -> remember(row, still) { OneColumnFling(row, still) } },
            modifier = Modifier.fillMaxSize().testTag("board"),
        ) {
            items(columns, key = { it.id }) { column ->
                ColumnView(
                    column, columns, drag?.card?.id, menuFor, columnWidth,
                    hoverIndex = hover?.takeIf { it.columnId == column.id }?.index,
                    selectedTaskId = selectedTaskId,
                    onAddCard = onAddCard,
                    onBounds = { columnBounds[column.id] = it },
                    onCardBounds = { id, bounds -> cardBounds[id] = column.id to bounds },
                    onOpenTask = onOpenTask, onMove = onMove, onComplete = onComplete,
                    onMenu = { menuFor = it },
                    onDragStart = { card, at ->
                        val bounds = cardBounds[card.id]?.second ?: return@ColumnView
                        drag = Drag(card, column.id, bounds.topLeft + at, at, 0f)
                    },
                    onDrag = {},
                    // Moves and the release are followed by the board itself (below): on a phone the
                    // card's column scrolls away under the finger, and with it the card's own gesture.
                    onDragEnd = {},
                    onDragCancel = {},
                )
            }
        }
        // The lifted card follows the finger above everything, so no column clips it.
        drag?.let { lifted ->
            val topLeft = lifted.pointer - lifted.grab - root.topLeft
            Box(
                Modifier.zIndex(1f).offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                    .width(FrameSize.liftedCard).shadow(AstridShadows.liftedCard, RoundedCornerShape(Radius.medium)),
            ) {
                val fromDone = columns.firstOrNull { it.id == lifted.from }?.kind == "done"
                val completedLabel = if (fromDone) completedOnLabel(lifted.card.completedOn) else null
                TaskRowView(lifted.card, onLeading = {}, onOpen = {}, underTitle = completedLabel?.let { label -> { CompletedPill(label) } })
            }
        }
        state.error?.let { message ->
            com.gracefultools.astrid.ui.common.AppErrorBanner(message, onDismissError, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s16))
            LaunchedEffect(message) { delay(6_000); onDismissError() }
        }
    }
}

@Composable
private fun ColumnView(
    column: BoardColumn,
    all: List<BoardColumn>,
    draggingId: String?,
    menuFor: String?,
    width: androidx.compose.ui.unit.Dp,
    hoverIndex: Int?,
    selectedTaskId: String?,
    onAddCard: suspend (String, String) -> Boolean,
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
    // iOS's BoardColumnView: a frame (white 0.8 on Ocean) around a recessed well (the theme's
    // background), board-column-wall walls, board-column-radius; an accent ring while a card hovers.
    val frame = when { colors.isDark -> colors.headerBg; colors == com.gracefultools.astrid.ui.theme.OceanColors -> colors.tokens.surface.copy(alpha = 0.8f); else -> colors.tokens.surface }
    val well = when { colors.isDark -> colors.bgPrimary; colors == com.gracefultools.astrid.ui.theme.OceanColors -> colors.bgPrimary; else -> colors.bgTertiary }
    val shape = RoundedCornerShape(TouchLayout.boardColumnRadius)
    Column(
        Modifier.width(width).fillMaxHeight().padding(bottom = Spacing.s8).clip(shape).background(frame)
            .border(BorderWidth.heavy, if (hoverIndex != null) colors.accent else Color.Transparent, shape)
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }.testTag("board.column.${column.id}"),
    ) {
        Column(Modifier.fillMaxWidth().padding(
                start = TouchLayout.boardColumnHeaderPaddingX, end = TouchLayout.boardColumnHeaderPaddingX,
                top = TouchLayout.boardColumnHeaderPaddingTop, bottom = TouchLayout.boardColumnHeaderPaddingBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(column.name, style = com.gracefultools.astrid.ui.theme.TextStyles.headline, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                Text(
                    column.total.toString(), style = com.gracefultools.astrid.ui.theme.TextStyles.caption, color = colors.textSecondary,
                    modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (colors.isDark) colors.bgTertiary else colors.neutral.copy(alpha = 0.1f)).padding(horizontal = Spacing.s8, vertical = Spacing.s4),
                )
            }
            column.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = com.gracefultools.astrid.ui.theme.TextStyles.caption, color = colors.textSecondary, maxLines = 2)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = TouchLayout.boardColumnWall).padding(bottom = if (column.kind == "done") TouchLayout.boardColumnWall else 0.dp).background(well)) {
        if (column.cards.isEmpty() && hoverIndex == null) {
            com.gracefultools.astrid.ui.common.AstridSpeechBubble(
                stringResource(R.string.empty_state_default), Modifier.padding(Spacing.s8).heightIn(min = TouchLayout.emptyColumnMinHeight).testTag("board.column.${column.id}.empty"),
                characterSize = TouchLayout.emptyArt,
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(TouchLayout.boardCardGap),
            contentPadding = PaddingValues(horizontal = TouchLayout.boardColumnBodyPaddingX, vertical = TouchLayout.boardColumnBodyPaddingY),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(column.cards, key = { _, it -> it.id }) { index, card ->
                if (hoverIndex == index) DropLine()
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
                    val completedLabel = if (column.kind == "done") completedOnLabel(card.completedOn) else null
                    TaskRowView(
                        card, onLeading = { onComplete(card) }, onOpen = { onOpenTask(card.id) }, modifier = Modifier.fillMaxWidth(), isSelected = card.id == selectedTaskId,
                        underTitle = completedLabel?.let { label -> { CompletedPill(label) } },
                    )
                    DropdownMenu(menuFor == card.id, onDismissRequest = { onMenu(null) }) {
                        others.forEach { target ->
                            DropdownMenuItem(text = { Text("$moveLabel ${target.name}") }, onClick = { onMenu(null); onMove(card.id, target.id) })
                        }
                    }
                }
            }
            if (hoverIndex != null && hoverIndex >= column.cards.size) item { DropLine() }
        }
        }
        if (column.kind != "done") ColumnQuickAdd(column.id, onAddCard)
    }
}

/** The Done column's completed date, in the reader's locale (see [completedPillLabel]); the pill words it. */
@Composable
private fun completedOnLabel(completedOn: String?): String? {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return completedPillLabel(completedOn, java.time.LocalDate.now(), locale)
}

/**
 * A Done card's completed day under its title, as on every platform: "✓ on Nov 19" in a small
 * tonal capsule, secondary text — the same treatment as the column's count badge.
 */
@Composable
private fun CompletedPill(label: String) {
    val colors = AstridTheme.colors
    // "✓ on Nov 19": the check is part of the copy and takes the pill's colour; TalkBack reads
    // "Completed Nov 19" instead of the glyph (iOS board.card.completed_pill / completed_on).
    val spoken = stringResource(R.string.board_card_completed_on, label)
    Text(
        stringResource(R.string.board_card_completed_pill, label), style = com.gracefultools.astrid.ui.theme.TextStyles.caption, color = colors.textSecondary, maxLines = 1,
        modifier = Modifier.testTag("completedPill").clearAndSetSemantics { contentDescription = spoken }
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (colors.isDark) colors.bgTertiary else colors.neutral.copy(alpha = 0.1f))
            .padding(horizontal = Spacing.s8, vertical = Spacing.s2),
    )
}

/**
 * The core's `completedOn` day (`YYYY-MM-DD`) as a pill label: the locale's short month and day
 * ("Nov 19"), with the year only when it is not this year ("Nov 19, 2025"). Null without a day.
 */
internal fun completedPillLabel(completedOn: String?, today: java.time.LocalDate, locale: java.util.Locale): String? {
    val day = completedOn?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return null
    val skeleton = if (day.year == today.year) "MMMd" else "yMMMd"
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    return day.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
}

/** iOS's insertion line: 3 accent where the card would land. */
@Composable
private fun DropLine() {
    Box(Modifier.fillMaxWidth().height(ControlHeight.dropLine).clip(CircleShape).background(AstridTheme.colors.accent))
}

/** iOS's board-footer quick add: a bordered field and the plus, flush at the foot of the column. */
@Composable
private fun ColumnQuickAdd(columnId: String, onAdd: suspend (String, String) -> Boolean) {
    val colors = AstridTheme.colors
    var text by androidx.compose.runtime.saveable.rememberSaveable(columnId) { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val placeholder = stringResource(R.string.tasks_add_task_placeholder)
    fun submit() {
        val title = text
        if (title.isBlank()) return
        text = ""
        scope.launch { if (!onAdd(columnId, title)) text = title }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gap.s10, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(com.gracefultools.astrid.ui.theme.Radius.medium)).background(colors.field)
                .border(BorderWidth.thin, colors.fieldBorder, RoundedCornerShape(com.gracefultools.astrid.ui.theme.Radius.medium)).heightIn(min = ControlHeight.field)
                .padding(horizontal = Gap.s10, vertical = Gap.s9),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Text(placeholder, style = com.gracefultools.astrid.ui.theme.TextStyles.body, color = colors.inputPlaceholder)
            androidx.compose.foundation.text.BasicTextField(
                value = text, onValueChange = { v -> if (v.contains('\n')) { text = v.replace("\n", ""); submit() } else text = v },
                textStyle = com.gracefultools.astrid.ui.theme.TextStyles.body.copy(color = colors.textPrimary), singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Filled.AddCircle, stringResource(R.string.tasks_add_task),
            tint = if (text.isBlank()) colors.textMuted else colors.accentInk,
            modifier = Modifier.size(com.gracefultools.astrid.ui.theme.CircleSize.compact).clip(androidx.compose.foundation.shape.CircleShape).clickable(enabled = text.isNotBlank()) { submit() },
        )
    }
}


/**
 * iOS's board pages one column per swipe (ProjectStatusBoardView): whatever the speed, a fling
 * settles on the next column in its direction, or back on this one when it was barely a nudge.
 */
private class OneColumnFling(private val row: androidx.compose.foundation.lazy.LazyListState, private val still: Boolean = false) : androidx.compose.foundation.gestures.FlingBehavior {
    override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
        val first = row.firstVisibleItemIndex
        val offset = row.firstVisibleItemScrollOffset
        val size = row.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: return initialVelocity
        val target = when {
            initialVelocity > 400f -> first + 1
            initialVelocity < -400f -> first
            offset > size / 2 -> first + 1
            else -> first
        }.coerceIn(0, maxOf(0, row.layoutInfo.totalItemsCount - 1))
        val info = row.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        val distance = (info?.offset?.toFloat() ?: ((target - first) * size - offset).toFloat()) - row.layoutInfo.beforeContentPadding
        // Under reduced motion (DS17) the board lands on its column without springing.
        if (still) { scrollBy(distance); return 0f }
        var travelled = 0f
        androidx.compose.animation.core.animate(0f, distance, animationSpec = androidx.compose.animation.core.spring(stiffness = 400f)) { value, _ ->
            travelled += scrollBy(value - travelled)
        }
        return 0f
    }
}
