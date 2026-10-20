package com.gracefultools.astrid.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.TaskDetail
import com.gracefultools.astrid.ui.common.AppErrorBanner
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.common.CheckboxArt
import com.gracefultools.astrid.ui.common.CoreLabels
import com.gracefultools.astrid.ui.common.RowFaceShape
import com.gracefultools.astrid.ui.common.initialsOf
import com.gracefultools.astrid.ui.markdown.MarkdownView
import com.gracefultools.astrid.ui.tasks.parseColor
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.PriorityColors
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import kotlinx.coroutines.launch

/**
 * iOS's TaskDetailViewNew (AATD-48): the "Task Details" header (tap the title to go back to the
 * top), the leading control beside the title in its box, then — under a divider — one row per field
 * with iOS's 34dp icon column instead of a label, the project state and "Waiting on", another
 * divider, the description, the parent link, the sub tasks and the comments, with the comment
 * composer floating at the bottom. The core decides which fields show and in what order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    vm: TaskDetailViewModel,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit = {},
    onOpenList: (String) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.copiedId) { state.copiedId?.let { vm.copyOpened(); onOpenTask(it) } }
    var replyTo by remember { mutableStateOf<com.gracefultools.astrid.model.CommentRow?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showTimer by rememberSaveable { mutableStateOf(false) }
    val online by com.gracefultools.astrid.ui.common.rememberIsOnline()
    Box(Modifier.fillMaxSize().background(colors.pageBg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            DetailHeader(state.detail, vm, onBack, onTitleTap = { scope.launch { scroll.animateScrollTo(0) } })
            val detail = state.detail
            if (detail != null) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { refreshing = true; vm.refresh(); scope.launch { kotlinx.coroutines.delay(600); refreshing = false } },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("taskDetail")) {
                        TitleBlock(detail, vm)
                        HorizontalDivider(color = colors.divider)
                        Spacer(Modifier.height(Spacing.s4))
                        for (field in detail.fieldOrder) when (field) {
                            "assignee" -> if (!detail.isCopyOnly) WhoRow(detail, state, vm)
                            "when" -> WhenRow(detail, state, vm)
                            "priority" -> if (!detail.isCopyOnly) PriorityRow(detail.task.priority, vm::setPriority)
                            "lists" -> ListsRow(detail, state, vm, onOpenList)
                        }
                        if (detail.isCopyOnly) detail.task.creator?.let { CreatedByRow(it) }
                        if (detail.task.identifier != null && detail.offersCopyIdentifier) TaskIdRow(detail.task.identifier)
                        detail.boardState?.let { ProjectStateRow(it, enabled = !detail.isCopyOnly, onPick = vm::setStatus) }
                        detail.blockers?.let { WaitingOnRow(it, state, vm, onOpenTask) }
                        OccurrencesRow(detail, onOpenTask)
                        ProjectFieldsRows(state, readOnly = detail.isCopyOnly, onSet = vm::setProjectField)
                        Spacer(Modifier.height(Spacing.s4))
                        HorizontalDivider(color = colors.divider)
                        DescriptionBlock(detail, vm::setDescription, onOpenTask, onOpenList)
                        AttachmentsSection(state.attachments, vm)
                        (detail.timer?.lastValue ?: detail.task.lastTimerValue)?.let { value ->
                            Text(
                                stringResource(R.string.task_edit_last_timer, value), fontSize = 14.sp, color = colors.textMuted,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s16, vertical = Spacing.s4),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        val source = detail.task.completedSource
                        if (detail.task.completed && source != null && source != "astrid") {
                            val provider = mapOf("google" to "Google Tasks", "github" to "GitHub", "apple" to "Apple Reminders")[source] ?: source
                            Text(
                                stringResource(R.string.task_detail_completed_via, provider), style = TextStyles.caption2, color = colors.textMuted,
                                modifier = Modifier.padding(horizontal = Spacing.s16, vertical = Spacing.s4),
                            )
                        }
                        detail.parent?.let { parent -> ParentLink(parent.title) { onOpenTask(parent.id) } }
                        SubtasksSection(detail, vm, onOpenTask)
                        CommentsSection(
                            detail,
                            onOpenFile = { file -> vm.open(file.id) { path -> openFile(context, path, file.mimeType) } },
                            fetch = { id, done -> vm.open(id, done) },
                            onReply = { replyTo = it },
                            onEdit = vm::editComment,
                            onDelete = vm::deleteComment,
                            onOpenTask = onOpenTask, onOpenList = onOpenList,
                            typingAgent = state.typingAgent,
                            offline = !online,
                        )
                        Spacer(Modifier.height(Spacing.s24))
                    }
                }
                CommentComposer(
                    state = state,
                    vm = vm,
                    replyTo = replyTo,
                    onCancelReply = { replyTo = null },
                    onSend = { text -> vm.postComment(text, replyTo?.id); replyTo = null },
                    onTimer = if (detail.isCopyOnly) null else ({ showTimer = true }),
                )
                if (showTimer) TaskTimerScreen(detail, vm, onClose = { showTimer = false })
            }
        }
        state.error?.let { message ->
            AppErrorBanner(message, vm::dismissError, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
            LaunchedEffect(message) { kotlinx.coroutines.delay(6_000); vm.dismissError() }
        }
    }
}

/** iOS's TaskDetailHeaderBar: back chevron, "Task Details" (scrolls to the top), the vertical menu. */
@Composable
private fun DetailHeader(detail: TaskDetail?, vm: TaskDetailViewModel, onBack: () -> Unit, onTitleTap: () -> Unit) {
    val colors = AstridTheme.colors
    var menu by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(colors.pageBg).padding(horizontal = Spacing.s8, vertical = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBackIos, stringResource(R.string.common_back), tint = colors.textPrimary,
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack).padding(12.dp).testTag("detail:back"),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.tasks_task_details), style = TextStyles.headline, color = colors.textPrimary,
                modifier = Modifier.clip(RoundedCornerShape(Radius.small)).clickable(onClick = onTitleTap).padding(Spacing.s4).semantics { heading() },
            )
        }
        Box {
            if (detail != null && !detail.isCopyOnly) {
                Icon(
                    Icons.Filled.MoreVert, stringResource(R.string.detail_more), tint = colors.textPrimary,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { menu = true }.padding(10.dp),
                )
            } else Spacer(Modifier.size(44.dp))
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.actions_copy)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = { menu = false; copying = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.actions_share)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) },
                    onClick = { menu = false; vm.share(); sharing = true },
                )
                HorizontalDivider()
                val canceled = detail?.isCanceled == true
                DropdownMenuItem(
                    text = { Text(stringResource(if (canceled) R.string.tasks_reopen else R.string.tasks_wont_do)) },
                    leadingIcon = { Icon(if (canceled) Icons.AutoMirrored.Filled.Undo else Icons.Outlined.Block, null) },
                    onClick = { menu = false; vm.toggleWontDo() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tasks_delete_task), color = Error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Error) },
                    onClick = { menu = false; confirmingDelete = true },
                )
            }
        }
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.tasks_delete_confirm)) },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; vm.delete() }) { Text(stringResource(R.string.actions_delete), color = Error) } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (copying && detail != null) CopyTaskSheet(vm, onDismiss = { copying = false })
    if (sharing && detail != null) ShareTaskSheet(vm, detail, onDismiss = { sharing = false })
}

/** The leading control (as the row's) beside the title in its box (19 medium, wraps). */
@Composable
private fun TitleBlock(detail: TaskDetail, vm: TaskDetailViewModel) {
    val colors = AstridTheme.colors
    var title by rememberSaveable(detail.task.id, detail.task.title) { mutableStateOf(detail.task.title) }
    var askSubtasks by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.s16, end = Spacing.s16, top = Spacing.s8, bottom = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        val label = stringResource(if (detail.task.completed) R.string.tasks_reopen_a11y else R.string.tasks_complete_a11y, detail.task.title)
        val tap = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !detail.isCopyOnly) {
                val openSubtasks = detail.subtasks.any { !it.completed }
                if (!detail.task.completed && openSubtasks) askSubtasks = true else vm.setCompleted(!detail.task.completed)
            }
            .semantics { role = Role.Checkbox; contentDescription = label }
        val assignee = detail.assignee
        if (assignee != null && detail.fieldOrder.firstOrNull() != "assignee" && assignee.id.isNotEmpty()) {
            Avatar(assignee.image, initialsOf(assignee.name, assignee.email), 34.dp, tap, shape = RowFaceShape,
                border = PriorityColors.getOrElse(detail.task.priority) { PriorityColors[0] })
        } else {
            CheckboxArt(detail.task.priority, detail.task.repeating != null && detail.task.repeating != "never", detail.task.completed, tap)
        }
        val shape = RoundedCornerShape(Radius.medium)
        Box(Modifier.weight(1f).clip(shape).background(colors.bgSecondary).padding(Spacing.s12)) {
            if (detail.isCopyOnly) {
                Text(title, style = TextStyles.rowTitle, color = colors.textPrimary)
            } else {
                BasicTextField(
                    value = title,
                    onValueChange = { value -> if (value.contains('\n')) { title = value.replace("\n", ""); focus.clearFocus() } else title = value },
                    textStyle = TextStyles.rowTitle.copy(
                        color = if (detail.task.completed) colors.textMuted else colors.textPrimary,
                        textDecoration = if (detail.task.completed) TextDecoration.LineThrough else null,
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) vm.setTitle(title) }.testTag("detail:title"),
                )
            }
        }
    }
    if (detail.isCanceled) {
        Text(
            stringResource(R.string.tasks_wont_do_chip), style = TextStyles.caption, color = colors.textSecondary,
            modifier = Modifier.padding(start = 62.dp, bottom = Spacing.s8).border(1.dp, colors.border, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
    if (askSubtasks) {
        AlertDialog(
            onDismissRequest = { askSubtasks = false },
            title = { Text(stringResource(R.string.task_detail_complete_subtasks_title)) },
            text = {
                Column {
                    TextButton(onClick = { askSubtasks = false; vm.completeWithSubtasks(true) }) { Text(stringResource(R.string.task_detail_complete_all_subtasks)) }
                    TextButton(onClick = { askSubtasks = false; vm.completeWithSubtasks(false) }) { Text(stringResource(R.string.task_detail_complete_only_this)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askSubtasks = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** iOS's TwoColumnRow: a 34dp column with the field's icon (or glyph), 12 apart, padded 16 x 8. */
@Composable
internal fun IconRow(label: String, icon: ImageVector? = null, glyph: String? = null, content: @Composable RowScope.() -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.s16, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Box(Modifier.width(34.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            if (glyph != null) Text(glyph, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            else if (icon != null) Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(19.dp))
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** A field's value box: bgSecondary, radius 8 — iOS's compact chip (8 x 4) or full box (12 x 10). */
@Composable
internal fun ValueBox(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.clip(RoundedCornerShape(Radius.medium)).background(AstridTheme.colors.bgSecondary)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = if (compact) 32.dp else 44.dp)
            .padding(horizontal = if (compact) Spacing.s8 else Spacing.s12, vertical = if (compact) Spacing.s4 else 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        content = content,
    )
}

@Composable
private fun WhoRow(detail: TaskDetail, state: TaskDetailState, vm: TaskDetailViewModel) {
    val colors = AstridTheme.colors
    var picking by remember { mutableStateOf(false) }
    IconRow(stringResource(R.string.tasks_assignee), Icons.Outlined.AccountCircle) {
        ValueBox(Modifier.fillMaxWidth(), onClick = { vm.loadAssignees(); picking = true }) {
            val who = detail.assignee
            if (who != null && who.id.isNotEmpty()) {
                Avatar(who.image, initialsOf(who.name, who.email), 24.dp)
                Text(who.display, style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Box(Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, colors.textMuted, CircleShape), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.tasks_unassigned_glyph), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
                }
                Text(stringResource(R.string.assignee_unassigned), style = TextStyles.body, color = colors.textMuted, modifier = Modifier.weight(1f))
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        }
    }
    if (picking) AssigneeSheet(state, vm, onPick = { picking = false; vm.setAssignee(it) }, onDismiss = { picking = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenRow(detail: TaskDetail, state: TaskDetailState, vm: TaskDetailViewModel) {
    val colors = AstridTheme.colors
    val hasDate = detail.task.dueDateTime != null
    var sheet by remember { mutableStateOf<String?>(null) }
    IconRow(stringResource(R.string.tasks_when), Icons.Outlined.CalendarMonth) {
        if (detail.isCopyOnly) {
            Text(detailDateLabel(detail) ?: stringResource(R.string.picker_no_due_date), style = TextStyles.body, color = colors.textPrimary)
            return@IconRow
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            ValueBox(compact = true, onClick = { vm.loadDueOptions(); sheet = "date" }) {
                val label = detailDateLabel(detail)
                Text(
                    label ?: stringResource(R.string.picker_no_due_date), style = TextStyles.body,
                    color = if (label == null) colors.textMuted else if (detail.isOverdue) Error else colors.textPrimary, maxLines = 1,
                )
            }
            if (hasDate) {
                ValueBox(compact = true, onClick = { vm.loadDueOptions(); sheet = "time" }) {
                    val time = detailTimeLabel(detail)
                    if (time != null) Text(time, style = TextStyles.body, color = colors.textPrimary)
                    else Icon(Icons.Outlined.TimerOff, stringResource(R.string.picker_add_time), tint = colors.textMuted, modifier = Modifier.size(18.dp))
                }
                ValueBox(compact = true, onClick = { vm.loadRepeats(); sheet = "repeat" }) {
                    val summary = CoreLabels.summary(LocalContext.current, detail.repeatSummary)
                    val repeats = detail.task.repeating != null && detail.task.repeating != "never"
                    Icon(
                        Icons.Outlined.Repeat,
                        stringResource(R.string.repeating_title), tint = if (repeats) colors.accent else colors.textMuted, modifier = Modifier.size(18.dp),
                    )
                    if (repeats && summary != null) Text(summary, style = TextStyles.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    when (sheet) {
        "date" -> DateSheet(detail, state, vm, onDismiss = { sheet = null })
        "time" -> TimeSheet(detail, state, vm, onDismiss = { sheet = null })
        "repeat" -> RepeatSheet(state, vm, onDismiss = { sheet = null })
    }
}

/** iOS's PriorityButtonPicker: ○ ! !! !!!, 40 x 40, radius 6, 12 apart; the row's mark is "!!!". */
@Composable
private fun PriorityRow(current: Int, onPick: (Int) -> Unit) {
    val names = listOf(R.string.priority_none, R.string.priority_low, R.string.priority_medium, R.string.priority_high)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    IconRow(stringResource(R.string.tasks_priority), glyph = "!!!") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            for (level in 0..3) {
                val tint = PriorityColors[level]
                val selected = level == current
                val spoken = stringResource(R.string.priority_a11y, stringResource(names[level]))
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(Radius.small))
                        .background(if (selected) tint else Color.Transparent)
                        .border(1.5.dp, tint, RoundedCornerShape(Radius.small))
                        .clickable { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onPick(level) }
                        .semantics(mergeDescendants = true) { this.selected = selected; role = Role.RadioButton; contentDescription = spoken },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (level == 0) "○" else "!".repeat(level), color = if (selected) Color.White else tint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListsRow(detail: TaskDetail, state: TaskDetailState, vm: TaskDetailViewModel, onOpenList: (String) -> Unit) {
    val colors = AstridTheme.colors
    var picking by remember { mutableStateOf(false) }
    IconRow(stringResource(R.string.navigation_lists), Icons.AutoMirrored.Filled.FormatListBulleted) {
        ValueBox(onClick = if (detail.isCopyOnly) null else ({ vm.loadListPicks(); picking = true })) {
            if (detail.listChips.isEmpty()) {
                Text(stringResource(R.string.picker_add_to_lists), style = TextStyles.body, color = colors.textMuted)
                Icon(Icons.Outlined.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    detail.listChips.forEach { chip -> ListBadge(chip.name, chip.color) }
                }
            }
        }
    }
    if (picking) ListsSheet(detail, state, vm, onDismiss = { picking = false })
}

/** iOS's ListBadge: the list's colour mark, its name in caption, on bgTertiary, radius 6. */
@Composable
internal fun ListBadge(name: String, color: String) {
    val colors = AstridTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(Radius.small)).background(colors.bgTertiary).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(parseColor(color)))
        Text(name, style = TextStyles.caption, color = colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun CreatedByRow(creator: com.gracefultools.astrid.model.UserSummary) {
    val colors = AstridTheme.colors
    IconRow(stringResource(R.string.tasks_created_by), Icons.Outlined.AccountCircle) {
        Avatar(creator.image, initialsOf(creator.name, creator.email), 28.dp)
        Spacer(Modifier.width(Spacing.s8))
        Text(creator.display, style = TextStyles.body, color = colors.textPrimary)
    }
}

/** iOS's TaskDetailIdentifierRow: the id, long press copies it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskIdRow(identifier: String) {
    val colors = AstridTheme.colors
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    IconRow(stringResource(R.string.tasks_task_id_label), Icons.Filled.Tag) {
        Box {
            Text(
                identifier, style = TextStyles.body, color = colors.textSecondary,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }).padding(vertical = Spacing.s4),
            )
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tasks_task_id_copy)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = { clipboard.setText(AnnotatedString(identifier)); menu = false },
                )
            }
        }
    }
}

/** iOS's ProjectStateQuickPicker: the board's columns, the current one tinted accent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectStateRow(state: com.gracefultools.astrid.model.BoardState, enabled: Boolean, onPick: (String) -> Unit) {
    val colors = AstridTheme.colors
    IconRow(stringResource(R.string.board_project_state), Icons.Outlined.GridView) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            state.chips.forEach { chip ->
                Text(
                    chip.name, style = TextStyles.subheadline,
                    color = if (chip.isCurrent) colors.accent else colors.textPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(Radius.medium))
                        .background(if (chip.isCurrent) colors.accent.copy(alpha = 0.15f) else colors.bgSecondary)
                        .clickable(enabled = enabled && !chip.isCurrent) { onPick(chip.id) }
                        .semantics { selected = chip.isCurrent }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** iOS's "Waiting on": blocker chips (struck when done, locked when hidden, x to stop), + to add. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaitingOnRow(section: com.gracefultools.astrid.model.BlockerSection, state: TaskDetailState, vm: TaskDetailViewModel, onOpenTask: (String) -> Unit) {
    val colors = AstridTheme.colors
    var adding by remember { mutableStateOf(false) }
    IconRow(stringResource(R.string.tasks_waiting_on_label), Icons.Outlined.HourglassEmpty) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8), modifier = Modifier.weight(1f)) {
            if (section.chips.isEmpty()) {
                ValueBox(compact = true, onClick = if (section.canEdit) ({ vm.searchBlockers(""); adding = true }) else null) {
                    Text(stringResource(R.string.tasks_waiting_on_empty), style = TextStyles.body, color = colors.textMuted)
                }
            }
            section.chips.forEach { blocker ->
                ValueBox(compact = true, onClick = if (blocker.hidden) null else ({ onOpenTask(blocker.id) })) {
                    if (blocker.hidden) Icon(Icons.Outlined.Lock, null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                    Text(
                        if (blocker.hidden) stringResource(R.string.tasks_waiting_on_hidden) else (blocker.title ?: blocker.identifier ?: ""),
                        style = TextStyles.subheadline, color = if (blocker.completed) colors.textMuted else colors.textPrimary,
                        textDecoration = if (blocker.completed) TextDecoration.LineThrough else null, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (section.canEdit) Icon(
                        Icons.Filled.Close, stringResource(R.string.tasks_waiting_on_remove), tint = colors.textMuted,
                        modifier = Modifier.size(16.dp).clip(CircleShape).clickable { vm.removeBlocker(blocker.id) },
                    )
                }
            }
            if (section.canEdit && section.chips.isNotEmpty()) {
                ValueBox(compact = true, onClick = { vm.searchBlockers(""); adding = true }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.tasks_waiting_on_add), tint = colors.textMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    if (adding) BlockerSheet(state, vm, onDismiss = { adding = false })
}

/** iOS's InlineTextAreaEditor: a caption label, the description in a box; tap to edit. */
@Composable
private fun DescriptionBlock(detail: TaskDetail, onSave: (String) -> Unit, onOpenTask: (String) -> Unit, onOpenList: (String) -> Unit) {
    val colors = AstridTheme.colors
    var editing by remember { mutableStateOf(false) }
    var text by rememberSaveable(detail.task.id, detail.task.description) { mutableStateOf(detail.task.description) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s16, vertical = Spacing.s12), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.detail_description), style = TextStyles.caption, color = colors.textSecondary)
        val shape = RoundedCornerShape(Radius.medium)
        if (editing && !detail.isCopyOnly) {
            LaunchedEffect(Unit) { focus.requestFocus() }
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = TextStyles.body.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).clip(shape).background(colors.bgTertiary).padding(Spacing.s12)
                    .focusRequester(focus).onFocusChanged { if (!it.isFocused && editing) { editing = false; onSave(text) } },
            )
            TextButton(onClick = { editing = false; onSave(text) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.actions_done)) }
        } else {
            Box(
                Modifier.fillMaxWidth().clip(shape).background(colors.bgSecondary).clickable(enabled = !detail.isCopyOnly) { editing = true }
                    .heightIn(min = 44.dp).padding(Spacing.s12),
            ) {
                if (detail.descriptionBlocks.isEmpty()) Text(stringResource(R.string.task_edit_add_description), style = TextStyles.body, color = colors.textMuted)
                else MarkdownView(detail.descriptionBlocks, onReference = { kind, id -> if (kind == "list") onOpenList(id) else onOpenTask(id) })
            }
        }
    }
}

/** iOS's "Parent task:" link. */
@Composable
private fun ParentLink(title: String, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.s16, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.Undo, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.task_detail_parent_task), style = TextStyles.caption, color = colors.textSecondary)
        Text(title, style = TextStyles.caption, fontWeight = FontWeight.Medium, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** iOS's SubtasksSectionView: "Sub tasks" and done/total, round checks, a title to open, "+ Add a sub task". */
@Composable
private fun SubtasksSection(detail: TaskDetail, vm: TaskDetailViewModel, onOpenTask: (String) -> Unit) {
    val colors = AstridTheme.colors
    var adding by rememberSaveable(detail.task.id) { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val addSubtaskLabel = stringResource(R.string.task_detail_add_subtask)
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s16, vertical = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            Text(stringResource(R.string.task_detail_subtasks), style = TextStyles.caption, color = colors.textSecondary)
            if (detail.subtasks.isNotEmpty()) {
                Text("${detail.subtasks.count { it.completed }}/${detail.subtasks.size}", style = TextStyles.caption, color = colors.textMuted)
            }
        }
        detail.subtasks.forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Icon(
                    if (sub.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    stringResource(if (sub.completed) R.string.tasks_reopen_a11y else R.string.tasks_complete_a11y, sub.title),
                    tint = if (sub.completed) colors.accent else colors.textMuted,
                    modifier = Modifier.size(22.dp).clip(CircleShape).clickable(enabled = !detail.isCopyOnly) { vm.setSubtaskCompleted(sub.id, !sub.completed) },
                )
                Text(
                    sub.title, style = TextStyles.body, color = if (sub.completed) colors.textMuted else colors.textPrimary,
                    textDecoration = if (sub.completed) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f).clickable { onOpenTask(sub.id) }.padding(vertical = 2.dp),
                )
            }
        }
        if (!detail.isCopyOnly) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            Icon(Icons.Outlined.AddCircleOutline, null, tint = colors.textMuted, modifier = Modifier.size(22.dp))
            Box(Modifier.weight(1f)) {
                if (adding.isEmpty()) Text(stringResource(R.string.task_detail_add_subtask), style = TextStyles.body, color = colors.textMuted.copy(alpha = 0.6f))
                BasicTextField(
                    value = adding, onValueChange = { adding = it.replace("\n", "") },
                    textStyle = TextStyles.body.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (adding.isBlank()) focus.clearFocus() else { vm.addSubtask(adding); adding = "" } }),
                    modifier = Modifier.fillMaxWidth().testTag("detail:addSubtask").semantics { contentDescription = addSubtaskLabel },
                )
            }
        }
    }
}

/**
 * iOS's RichTextInput in the detail: a floating card with a white bordered field ("Add a
 * comment..."), the paperclip menu (From Photos / From Documents / From Camera) whose files queue
 * as thumbnails with × until Send, and the paper plane — or, while there is nothing to send, the
 * timer. Typing @, # or ! opens the core's suggestions above the field. A reply shows who it answers.
 */
@Composable
private fun CommentComposer(
    state: TaskDetailState,
    vm: TaskDetailViewModel,
    replyTo: com.gracefultools.astrid.model.CommentRow?,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
    onTimer: (() -> Unit)?,
) {
    val colors = AstridTheme.colors
    val context = LocalContext.current
    var text by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    val shape = RoundedCornerShape(Radius.large)
    val commentLabel = stringResource(R.string.comments_add_placeholder)
    val canSend = text.text.isNotBlank() || state.queued.isNotEmpty()
    fun queue(uri: android.net.Uri) { copyIntoApp(context, uri)?.let { (path, name, mime) -> vm.queueFile(path, name, mime) } }
    val photos = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(MAX_QUEUED),
    ) { uris -> uris.forEach(::queue) }
    val documents = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris.forEach(::queue) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { taken -> cameraPath?.let { path -> if (taken) vm.queueFile(path, java.io.File(path).name, "image/jpeg") }; cameraPath = null }
    val hasCamera = remember { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY) }
    var attachMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.navigationBarsPadding().imePadding().padding(start = Spacing.s8, end = Spacing.s8, bottom = Spacing.s8).fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
            .clip(shape).background(colors.floatingBar).padding(horizontal = Spacing.s12, vertical = Spacing.s8),
    ) {
        if (state.suggestions.isNotEmpty()) SuggestionList(state.suggestions) { item ->
            vm.applySuggestion(text.text, text.selection.start, item) { newText, caret ->
                text = androidx.compose.ui.text.input.TextFieldValue(newText, androidx.compose.ui.text.TextRange(caret))
            }
        }
        if (replyTo != null) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.comments_replying_to, replyTo.authorName ?: ""), style = TextStyles.caption, color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.common_cancel), style = TextStyles.caption, color = colors.accent, modifier = Modifier.clickable(onClick = onCancelReply))
        }
        if (state.queued.isNotEmpty()) QueuedFiles(state.queued, vm::unqueueFile)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.medium)).background(colors.field)
                    .border(1.dp, colors.fieldBorder, RoundedCornerShape(Radius.medium)).heightIn(min = 40.dp, max = 200.dp).padding(horizontal = 12.dp, vertical = 9.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.text.isEmpty()) Text(stringResource(R.string.comments_add_placeholder), style = TextStyles.body, color = colors.inputPlaceholder)
                BasicTextField(
                    value = text,
                    onValueChange = { value ->
                        val moved = value.text != text.text || value.selection != text.selection
                        text = value
                        if (moved) vm.commentChanged(value.text, value.selection.start)
                    },
                    textStyle = TextStyles.body.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().testTag("detail:comment").semantics { contentDescription = commentLabel },
                )
            }
            Box {
                ComposerButton(Icons.Filled.AttachFile, stringResource(R.string.detail_attach), colors.accent) { attachMenu = true }
                DropdownMenu(attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.attachments_from_photos)) }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                        onClick = {
                            attachMenu = false
                            photos.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.attachments_from_documents)) }, leadingIcon = { Icon(Icons.Outlined.Description, null) },
                        onClick = { attachMenu = false; documents.launch(arrayOf("*/*")) },
                    )
                    if (hasCamera) DropdownMenuItem(
                        text = { Text(stringResource(R.string.attachments_from_camera)) }, leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                        onClick = {
                            attachMenu = false
                            val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
                            val file = java.io.File(dir, "photo-${System.currentTimeMillis()}.jpg")
                            cameraPath = file.absolutePath
                            camera.launch(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                        },
                    )
                }
            }
            if (canSend) {
                ComposerButton(Icons.AutoMirrored.Filled.Send, stringResource(R.string.detail_send), tint = colors.accent) {
                    onSend(text.text); text = androidx.compose.ui.text.input.TextFieldValue("")
                }
            } else if (onTimer != null) {
                ComposerButton(Icons.Outlined.Timer, stringResource(R.string.timer_open), tint = colors.accent, onClick = onTimer)
            }
        }
    }
}

/** iOS's staged files: 56dp thumbnails over the field, each with × to take it back out. */
@Composable
private fun QueuedFiles(files: List<QueuedFile>, onRemove: (String) -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 6.dp, bottom = Spacing.s8),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        files.forEach { file ->
            Box(Modifier.size(62.dp)) {
                Box(
                    Modifier.align(Alignment.BottomStart).size(56.dp).clip(RoundedCornerShape(Radius.small)).background(colors.field),
                    contentAlignment = Alignment.Center,
                ) {
                    if (file.mimeType?.startsWith("image/") == true) {
                        coil.compose.AsyncImage(
                            model = java.io.File(file.path), contentDescription = file.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Description, file.name, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                        Text(file.name.substringAfterLast('.', "").uppercase().take(5), fontSize = 9.sp, fontWeight = FontWeight.Medium, color = colors.textMuted)
                    }
                }
                Icon(
                    Icons.Filled.Cancel, stringResource(R.string.attachments_remove_a11y, file.name), tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onRemove(file.path) },
                )
            }
        }
    }
}

/** iOS's autocomplete popup: people (and agents), lists or tasks the core found for the trigger. */
@Composable
private fun SuggestionList(items: List<com.gracefultools.astrid.model.CommentSuggestion>, onPick: (com.gracefultools.astrid.model.CommentSuggestion) -> Unit) {
    val colors = AstridTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = Spacing.s8).heightIn(max = 220.dp).clip(RoundedCornerShape(Radius.medium))
            .background(colors.field).border(1.dp, colors.fieldBorder, RoundedCornerShape(Radius.medium)).verticalScroll(rememberScrollState()),
    ) {
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(item) }.heightIn(min = 44.dp).padding(horizontal = Spacing.s12, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
            ) {
                when {
                    item.kind == "list" -> Text("#", style = TextStyles.headline, color = colors.textMuted, modifier = Modifier.width(20.dp))
                    item.kind == "task" -> Text("!", style = TextStyles.headline, color = colors.textMuted, modifier = Modifier.width(20.dp))
                    item.isAgent -> Icon(Icons.Outlined.SmartToy, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    else -> Icon(Icons.Outlined.AccountCircle, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.label, style = TextStyles.body, color = if (item.completed) colors.textMuted else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                    )
                    val secondary = if (item.isAgent) stringResource(R.string.profile_ai_agent) else item.secondary
                    if (!secondary.isNullOrBlank()) Text(secondary, style = TextStyles.caption2, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** At most this many files wait for one comment — iOS's photo picker limit, which its queue shares. */
private const val MAX_QUEUED = 10

@Composable
private fun ComposerButton(icon: ImageVector, label: String, onClick: () -> Unit) = ComposerButton(icon, label, AstridTheme.colors.textSecondary, onClick)

@Composable
private fun ComposerButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(colors.field).border(1.dp, colors.fieldBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp)) }
}

/** iOS's date chip: Today / Tomorrow / Yesterday, else "Mon Oct 20 2026". */
@Composable
internal fun detailDateLabel(detail: TaskDetail): String? {
    val iso = detail.task.dueDateTime ?: return null
    when (detail.due.key) {
        "today" -> return stringResource(R.string.time_today)
        "tomorrow" -> return stringResource(R.string.time_tomorrow)
        "yesterday" -> return stringResource(R.string.time_yesterday)
    }
    val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return null
    val zone = if (detail.task.isAllDay) java.time.ZoneOffset.UTC else java.time.ZoneId.systemDefault()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEMMMdyyyy")
    return instant.atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
}

/** The time chip's text: the reader's local time of a timed task; null for an all-day one. */
@Composable
internal fun detailTimeLabel(detail: TaskDetail): String? {
    if (detail.task.isAllDay) return null
    val instant = detail.task.dueDateTime?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() } ?: return null
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return instant.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale))
}

/** Copy a picked document into app storage, where the core can read it: (path, name, MIME type). */
internal fun copyIntoApp(context: android.content.Context, uri: android.net.Uri): Triple<String, String, String?>? = runCatching {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "attachment"
    val dir = java.io.File(context.cacheDir, "outgoing").apply { mkdirs() }
    val target = java.io.File(dir, "${System.currentTimeMillis()}-${name.replace('/', '_')}")
    resolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
    Triple(target.absolutePath, name, resolver.getType(uri))
}.getOrNull()

/** Hand a downloaded file to whatever opens its type, through the app's FileProvider. */
internal fun openFile(context: android.content.Context, path: String, mimeType: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", java.io.File(path))
    val view = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(android.content.Intent.createChooser(view, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
