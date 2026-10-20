package com.gracefultools.astrid.ui.tasks

import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.common.CheckboxArt
import com.gracefultools.astrid.ui.common.EmptyState
import com.gracefultools.astrid.ui.common.HamburgerIcon
import com.gracefultools.astrid.ui.common.RowFaceShape
import com.gracefultools.astrid.ui.common.AppErrorBanner
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.PriorityColors
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import kotlinx.coroutines.launch

/** iOS's HeaderToggleSegment: what the main pane shows for a list. */
enum class ViewMode { LIST, MESSAGES, BOARD }

/** iOS's nextRotatorSegment: list → messages → board → list, skipping what the list cannot show. */
fun nextViewMode(current: ViewMode, hasBoard: Boolean, includesMessages: Boolean): ViewMode {
    val order = listOfNotNull(ViewMode.LIST, ViewMode.MESSAGES.takeIf { includesMessages }, ViewMode.BOARD.takeIf { hasBoard })
    val at = order.indexOf(current).takeIf { it >= 0 } ?: 0
    return order[(at + 1) % order.size]
}

/**
 * A list, as iOS's TaskListView on iPhone (AATD-35/37/38): the floating header (menu, title, view
 * rotator, filters or settings), the rows or the board or the messages, and the floating quick-add
 * bar. Pull to refresh; swipe left to delete, as iOS's only row swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    title: String,
    state: TaskListState,
    /** Null when the sidebar is always on screen (a wide window). */
    onMenu: (() -> Unit)?,
    onOpenTask: (String) -> Unit,
    onComplete: (TaskRow) -> Unit,
    onDelete: (TaskRow) -> Unit,
    /** Adds a task; answers its id (empty while it is only queued), null when nothing was made. */
    onAdd: suspend (String) -> String?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDismissError: () -> Unit,
    /** What the empty list says (iOS's getEmptyStateMessage). */
    emptyMessage: String,
    viewMode: ViewMode = ViewMode.LIST,
    hasBoard: Boolean = false,
    includesMessages: Boolean = false,
    onViewMode: (ViewMode) -> Unit = {},
    board: @Composable () -> Unit = {},
    messages: @Composable () -> Unit = {},
    /** My Tasks: iOS's filter button. */
    onFilter: (() -> Unit)? = null,
    /** A list the reader is a member of: iOS's gear. */
    onListSettings: (() -> Unit)? = null,
    /** Quick add is hidden where the reader cannot add (iOS ListPermissions.canAddTasks). */
    canAdd: Boolean = true,
    onPickPriority: (Int) -> Unit = {},
    onPickAssignee: (String?) -> Unit = {},
    selectedTaskId: String? = null,
    /** My Tasks' active filters after the title (iOS: "My Tasks - Today !! !"). */
    titleSuffix: androidx.compose.ui.text.AnnotatedString? = null,
    /** A row dropped (AATD-42): task, zone (`row`/`line`/`outdent`/`indent`), target row, insertion index. */
    onDrop: (String, String, String?, Int?) -> Unit = { _, _, _, _ -> },
) {
    val colors = AstridTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            ListHeader(title, titleSuffix, onMenu, viewMode, hasBoard, includesMessages, onViewMode, onFilter, onListSettings)
            when (viewMode) {
                ViewMode.BOARD -> Box(Modifier.weight(1f)) { board() }
                ViewMode.MESSAGES -> Box(Modifier.weight(1f)) { messages() }
                ViewMode.LIST -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(listState, state.rows.size) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .collect { last -> if (last >= state.rows.size - 20) onLoadMore() }
                    }
                    when {
                        state.rows.isEmpty() && state.loading -> EmptyState(stringResource(R.string.tasks_loading))
                        state.rows.isEmpty() -> EmptyState(emptyMessage)
                    }
                    val focus = LocalFocusManager.current
                    RowDragArea(state, listState, onDrop, onDelete, onComplete, onOpenTask, selectedTaskId, focus)
                }
            }
            if (viewMode == ViewMode.LIST && canAdd) {
                QuickAddBar(state.quickAdd, onAdd, onOpenTask, onPickPriority, onPickAssignee)
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
        state.error?.let { message ->
            AppErrorBanner(message, onDismissError, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
            LaunchedEffect(message) { kotlinx.coroutines.delay(6_000); onDismissError() }
        }
    }
}

/** Dragging the list down far enough puts the keyboard away, as iOS's quick add does. */
private fun dismissKeyboardOnDrag(dismiss: () -> Unit) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 10f) dismiss()
        return Offset.Zero
    }
}

/**
 * iOS's standardHeader: the two-line menu icon (whose tap target takes in the title), the title in
 * headline, then the view rotator and the filter (My Tasks) or the gear (a list).
 */
@Composable
private fun ListHeader(
    title: String,
    titleSuffix: androidx.compose.ui.text.AnnotatedString?,
    onMenu: (() -> Unit)?,
    mode: ViewMode,
    hasBoard: Boolean,
    includesMessages: Boolean,
    onViewMode: (ViewMode) -> Unit,
    onFilter: (() -> Unit)?,
    onListSettings: (() -> Unit)?,
) {
    val colors = AstridTheme.colors
    val openSidebar = stringResource(R.string.navigation_open_sidebar)
    com.gracefultools.astrid.ui.common.FloatingHeader {
        Row(
            Modifier.weight(1f).heightIn(min = 32.dp).testTag("listTitle")
                .then(if (onMenu != null) Modifier.clip(RoundedCornerShape(Radius.small)).clickable(onClick = onMenu).semantics { contentDescription = openSidebar } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onMenu != null) {
                HamburgerIcon(Modifier.padding(start = 6.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (titleSuffix == null) androidx.compose.ui.text.AnnotatedString(title) else androidx.compose.ui.text.AnnotatedString(title) + titleSuffix,
                style = TextStyles.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        val next = nextViewMode(mode, hasBoard, includesMessages)
        if (next != mode) {
            HeaderButton(
                when (next) {
                    ViewMode.LIST -> Icons.AutoMirrored.Filled.FormatListBulleted
                    ViewMode.MESSAGES -> Icons.Outlined.Forum
                    ViewMode.BOARD -> Icons.Outlined.ViewWeek
                },
                stringResource(
                    when (next) {
                        ViewMode.LIST -> R.string.board_show_list
                        ViewMode.MESSAGES -> R.string.chat_show_messages
                        ViewMode.BOARD -> R.string.board_show_board
                    },
                ),
            ) { onViewMode(next) }
        }
        if (onFilter != null) HeaderButton(Icons.Outlined.FilterList, stringResource(R.string.my_tasks_filters_title), onFilter)
        else if (onListSettings != null) HeaderButton(Icons.Outlined.Settings, stringResource(R.string.lists_settings), onListSettings)
    }
}

@Composable
private fun HeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Icon(
        icon, label, tint = AstridTheme.colors.textPrimary,
        modifier = Modifier.padding(start = Spacing.s8).size(32.dp).clip(CircleShape).clickable(onClick = onClick).padding(5.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value -> if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Only while a swipe is under way: the cards are translucent (white 0.8 on Ocean), so a
            // background drawn at rest would tint every row red.
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) Box(
                Modifier.fillMaxSize().background(Error, RoundedCornerShape(8.dp)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, stringResource(R.string.tasks_delete), tint = Color.White) }
        },
    ) { content() }
}

/**
 * iOS's QuickAddTaskView: a floating bar (white 0.8 on Ocean, radius 12, shadow) holding the
 * leading control — the checkbox in the next task's priority, or the chosen assignee's face — a
 * white bordered field that grows, and the plus. Return adds and keeps the keyboard; the plus adds
 * and opens the task; Return on an empty field puts the keyboard away. The text clears at once and
 * comes back if the core refuses (AATD-37).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddBar(
    defaults: QuickAddDefaults,
    onAdd: suspend (String) -> String?,
    onOpen: (String) -> Unit,
    onPickPriority: (Int) -> Unit,
    onPickAssignee: (String?) -> Unit,
) {
    val colors = AstridTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val field = remember { FocusRequester() }
    fun submit(open: Boolean) {
        val title = text.replace("\n", "")
        if (title.isBlank()) { focus.clearFocus(); return }
        if (sending) return
        sending = true
        text = ""
        scope.launch {
            val made = onAdd(title)
            sending = false
            if (made == null) text = title
            else {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (open && made.isNotEmpty()) { focus.clearFocus(); onOpen(made) }
            }
        }
    }
    val shape = RoundedCornerShape(Radius.large)
    Row(
        Modifier.navigationBarsPadding().imePadding().padding(start = Spacing.s8, end = Spacing.s8, bottom = Spacing.s8)
            .fillMaxWidth().shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
            .clip(shape).background(colors.floatingBar).padding(horizontal = Spacing.s16, vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        val pickLabel = stringResource(R.string.quick_add_picker_a11y)
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).clickable { focus.clearFocus(); picker = true }
                .semantics { contentDescription = pickLabel },
        ) {
            val chosen = defaults.options.firstOrNull { it.userId != null && it.userId == defaults.assigneeId }
            if (defaults.leading.kind == "avatar" && chosen != null) {
                Avatar(chosen.image, chosen.initials, 34.dp, shape = RowFaceShape, border = PriorityColors.getOrElse(defaults.priority) { PriorityColors[0] })
            } else {
                CheckboxArt(defaults.priority, repeating = false, checked = false)
            }
        }
        BasicTextField(
            value = text,
            onValueChange = { value -> if (value.contains('\n')) { text = value.replace("\n", ""); submit(open = false) } else text = value },
            textStyle = TextStyles.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit(open = false) }),
            maxLines = 6,
            modifier = Modifier.weight(1f).focusRequester(field).testTag("quickAdd"),
            decorationBox = { inner ->
                Box(
                    Modifier.clip(RoundedCornerShape(Radius.medium)).background(colors.field)
                        .border(1.dp, colors.fieldBorder, RoundedCornerShape(Radius.medium))
                        .heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) Text(stringResource(R.string.tasks_add_task_placeholder), style = TextStyles.body, color = colors.inputPlaceholder)
                    inner()
                }
            },
        )
        Icon(
            Icons.Filled.AddCircle, stringResource(R.string.tasks_add_task),
            tint = if (text.isBlank()) colors.textMuted else colors.accent,
            modifier = Modifier.size(32.dp).clip(CircleShape).clickable(enabled = text.isNotBlank()) { submit(open = true) },
        )
    }
    if (picker) {
        ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = if (colors.isDark) colors.bgSecondary else Color.White) {
            QuickAddPicker(
                defaults,
                onPriority = { onPickPriority(it); picker = false },
                onAssignee = { onPickAssignee(it); picker = false },
            )
        }
    }
}

/** iOS's QuickAddPickerSheet: Priority (○ ! !! !!!), then Assignee (nobody first, then people). */
@Composable
private fun QuickAddPicker(defaults: QuickAddDefaults, onPriority: (Int) -> Unit, onAssignee: (String?) -> Unit) {
    val colors = AstridTheme.colors
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s16).padding(bottom = Spacing.s24), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        Text(stringResource(R.string.tasks_priority), style = TextStyles.headline, color = colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            listOf("○", "!", "!!", "!!!").forEachIndexed { p, glyph ->
                val tint = PriorityColors[p]
                val selected = defaults.priority == p
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(Radius.small))
                        .background(if (selected) tint else Color.Transparent)
                        .border(1.5.dp, tint, RoundedCornerShape(Radius.small))
                        .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPriority(p) },
                    contentAlignment = Alignment.Center,
                ) { Text(glyph, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else tint) }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.s12), color = colors.border)
        Text(stringResource(R.string.tasks_assignee), style = TextStyles.headline, color = colors.textPrimary)
        val people = defaults.options.filter { it.userId != null }
            .sortedWith(compareBy({ !it.isAgent }, { !it.isCurrentUser }, { (it.name ?: "").lowercase() }))
        AssigneeChoice(null, null, "", stringResource(R.string.assignee_unassigned), null, defaults.assigneeId == null) { onAssignee(null) }
        for (person in people) {
            AssigneeChoice(
                person.userId, person.image, person.initials, person.name ?: person.userId.orEmpty(),
                when {
                    person.isCurrentUser -> stringResource(R.string.assignee_you)
                    person.isAgent -> stringResource(R.string.profile_ai_agent)
                    else -> null
                },
                defaults.assigneeId == person.userId,
            ) { onAssignee(person.userId) }
        }
    }
}

@Composable
private fun AssigneeChoice(id: String?, image: String?, initials: String, name: String, caption: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    val shape = RoundedCornerShape(Radius.medium)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) colors.bgSecondary else Color.Transparent)
            .border(1.dp, if (selected) colors.accent else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(horizontal = Spacing.s12, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        if (id == null) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(colors.bgSecondary), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PersonOff, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
        } else Avatar(image, initials, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = TextStyles.body, color = colors.textPrimary)
            if (caption != null) Text(caption, style = TextStyles.caption2, color = colors.textMuted)
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
    }
}


/** A row being carried: which, where the finger is (in the list), how far it has gone sideways. */
private data class RowDrag(val row: TaskRow, val pointer: androidx.compose.ui.geometry.Offset, val startX: Float, val grabY: Float)

/** Where a carried row would land — iOS's three targets. */
private sealed interface DropTarget {
    data class OnRow(val id: String) : DropTarget
    data class Line(val index: Int) : DropTarget
    data object Outdent : DropTarget
}

/**
 * The rows, with iOS's drag (AATD-42): press and hold a row, then drop it ON a row to make it that
 * row's subtask (an accent ring), on the LINE between rows to promote a subtask or — sorted by hand
 * — to reorder (an accent line), or pull it left past 40dp into the gutter to move it out a level
 * (the band with its arrow). The list follows the finger itself; TalkBack gets the same moves as
 * actions.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RowDragArea(
    state: TaskListState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onDrop: (String, String, String?, Int?) -> Unit,
    onDelete: (TaskRow) -> Unit,
    onComplete: (TaskRow) -> Unit,
    onOpenTask: (String) -> Unit,
    selectedTaskId: String?,
    focus: androidx.compose.ui.focus.FocusManager,
) {
    val colors = AstridTheme.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bounds = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var drag by remember { mutableStateOf<RowDrag?>(null) }
    val outdentPx = with(density) { 40.dp.toPx() }
    val manual = state.sortBy == "manual"
    fun target(d: RowDrag): DropTarget? {
        if (d.pointer.x - d.startX <= -outdentPx) return DropTarget.Outdent
        val y = d.pointer.y
        val rows = state.rows.withIndex().mapNotNull { (i, r) -> bounds[r.id]?.let { Triple(i, r, it) } }
        val hit = rows.firstOrNull { (_, _, b) -> y >= b.top && y <= b.bottom } ?: return null
        val (index, row, b) = hit
        val band = minOf(with(density) { 12.dp.toPx() }, b.height * 0.3f)
        return when {
            y < b.top + band -> DropTarget.Line(index)
            y > b.bottom - band -> DropTarget.Line(index + 1)
            row.id == d.row.id -> null
            else -> DropTarget.OnRow(row.id)
        }
    }
    val current = drag?.let(::target)
    fun finish() {
        val d = drag ?: return
        drag = null
        when (val t = target(d)) {
            is DropTarget.OnRow -> onDrop(d.row.id, "row", t.id, null)
            is DropTarget.Line -> {
                // The index among the other rows: the carried one leaves its old place first.
                val from = state.rows.indexOfFirst { it.id == d.row.id }
                onDrop(d.row.id, "line", null, if (from in 0 until t.index) t.index - 1 else t.index)
            }
            DropTarget.Outdent -> onDrop(d.row.id, "outdent", null, null)
            null -> Unit
        }
    }
    Box(
        Modifier.fillMaxSize()
            .reportOrigin { origin = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val d = drag ?: continue
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed) { drag = d.copy(pointer = change.position); change.consume() } else finish()
                    }
                }
            },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = Spacing.s8, end = Spacing.s8, top = Spacing.s8, bottom = Spacing.s8),
            verticalArrangement = Arrangement.spacedBy(Spacing.s8),
            modifier = Modifier.fillMaxSize().testTag("taskList").nestedScroll(dismissKeyboardOnDrag { focus.clearFocus() }),
        ) {
            itemsIndexed(state.rows, key = { _, it -> it.id }) { index, row ->
                val indentLabel = stringResource(R.string.tasks_nest_under_previous)
                val outdentLabel = stringResource(R.string.tasks_move_out_a_level)
                val upLabel = stringResource(R.string.tasks_move_up)
                val downLabel = stringResource(R.string.tasks_move_down)
                Box(
                    Modifier
                        .onGloballyPositionedCompat(relativeTo = origin) { bounds[row.id] = it }
                        .alpha(if (drag?.row?.id == row.id) 0.3f else 1f)
                        .pointerInput(row.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { at ->
                                    val b = bounds[row.id] ?: return@detectDragGesturesAfterLongPress
                                    val p = androidx.compose.ui.geometry.Offset(b.left + at.x, b.top + at.y)
                                    drag = RowDrag(row, p, p.x, at.y)
                                },
                                onDrag = { change, _ -> change.consume() },
                            )
                        }
                        .semantics {
                            customActions = buildList {
                                add(androidx.compose.ui.semantics.CustomAccessibilityAction(indentLabel) { onDrop(row.id, "indent", null, null); true })
                                if (row.depth > 0) add(androidx.compose.ui.semantics.CustomAccessibilityAction(outdentLabel) { onDrop(row.id, "outdent", null, null); true })
                                if (manual && index > 0) add(androidx.compose.ui.semantics.CustomAccessibilityAction(upLabel) { onDrop(row.id, "line", null, index - 1); true })
                                if (manual && index < state.rows.lastIndex) add(androidx.compose.ui.semantics.CustomAccessibilityAction(downLabel) { onDrop(row.id, "line", null, index + 1); true })
                            }
                        },
                ) {
                    SwipeToDelete(onDelete = { onDelete(row) }) {
                        TaskRowView(
                            row, onLeading = { onComplete(row) }, onOpen = { onOpenTask(row.id) },
                            isSelected = row.id == selectedTaskId, compact = selectedTaskId != null,
                        )
                    }
                    if (current == DropTarget.OnRow(row.id)) {
                        Box(Modifier.matchParentSize().border(2.dp, colors.accent, RoundedCornerShape(8.dp)))
                    }
                }
            }
        }
        val d = drag
        if (d != null) {
            // The insertion line, the outdent band, and the lifted row under the finger.
            when (val t = current) {
                is DropTarget.Line -> {
                    val y = state.rows.getOrNull(t.index)?.let { bounds[it.id]?.top?.minus(with(density) { 4.dp.toPx() }) }
                        ?: state.rows.lastOrNull()?.let { bounds[it.id]?.bottom?.plus(with(density) { 4.dp.toPx() }) } ?: 0f
                    Box(
                        Modifier.offset { androidx.compose.ui.unit.IntOffset(0, y.toInt()) }.padding(horizontal = Spacing.s8)
                            .fillMaxWidth().heightIn(min = 3.dp).clip(RoundedCornerShape(2.dp)).background(colors.accent),
                    )
                }
                DropTarget.Outdent -> Row(
                    Modifier.fillMaxHeight().width(56.dp).background(colors.accent.copy(alpha = 0.12f)),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardTab, null, tint = colors.accent, modifier = Modifier.graphicsLayerRotate180()) }
                else -> Unit
            }
            val height = bounds[d.row.id]?.height ?: 0f
            Box(
                Modifier.zIndex(1f).offset { androidx.compose.ui.unit.IntOffset(0, (d.pointer.y - d.grabY).toInt()) }
                    .padding(horizontal = Spacing.s8).fillMaxWidth().heightIn(min = with(density) { height.toDp() })
                    .shadow(8.dp, RoundedCornerShape(8.dp)),
            ) { TaskRowView(d.row, onLeading = {}, onOpen = {}) }
        }
    }
}

/** Bounds relative to [relativeTo] (the drag area's window position). */
private fun Modifier.onGloballyPositionedCompat(
    relativeTo: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    onBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
) = this.then(Modifier.onGloballyPositioned { coords ->
    val b = coords.boundsInWindow()
    onBounds(b.translate(-relativeTo))
})

private fun Modifier.reportOrigin(onOrigin: (androidx.compose.ui.geometry.Offset) -> Unit) =
    this.then(Modifier.onGloballyPositioned { onOrigin(it.boundsInWindow().topLeft) })

/** The outdent arrow points left: KeyboardTab mirrored. */
private fun Modifier.graphicsLayerRotate180() = this.then(Modifier.graphicsLayer { rotationZ = 180f })
