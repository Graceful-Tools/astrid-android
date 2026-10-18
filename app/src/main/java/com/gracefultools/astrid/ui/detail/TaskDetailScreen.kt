package com.gracefultools.astrid.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.TaskDetail
import com.gracefultools.astrid.ui.common.CoreLabels
import com.gracefultools.astrid.ui.common.coreLabel
import com.gracefultools.astrid.ui.markdown.MarkdownView
import com.gracefultools.astrid.ui.tasks.dueText
import com.gracefultools.astrid.ui.tasks.parseColor
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.PriorityColors
import com.gracefultools.astrid.ui.theme.Spacing
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.ZoneOffset

/** iOS's TaskDetailView, pushed full-screen from the row: title, then the fields in the core's order. */
@Composable
fun TaskDetailScreen(vm: TaskDetailViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }
    Box(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(state.detail, onBack, onDelete = vm::delete)
            val detail = state.detail
            if (detail != null) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s16),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s12),
                ) {
                    TitleRow(detail, vm)
                    Card {
                        detail.fieldOrder.forEachIndexed { index, field ->
                            if (index > 0) HorizontalDivider(color = colors.border)
                            when (field) {
                                "assignee" -> AssigneeField(detail, state, vm)
                                "when" -> WhenField(detail, state, vm)
                                "priority" -> PriorityField(detail.task.priority, vm::setPriority)
                                "lists" -> ListsField(detail, state, vm)
                            }
                        }
                        HorizontalDivider(color = colors.border)
                        RepeatField(detail, state, vm)
                        if (detail.task.dueDateTime != null) {
                            HorizontalDivider(color = colors.border)
                            ReminderField(state, vm)
                        }
                    }
                    DescriptionCard(detail, vm::setDescription)
                    if (detail.subtasks.isNotEmpty()) {
                        Section(stringResource(R.string.detail_subtasks))
                        Card {
                            detail.subtasks.forEach { sub ->
                                Row(Modifier.fillMaxWidth().padding(Spacing.s12), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (sub.completed) Icons.Filled.Check else Icons.Filled.Check, null,
                                        tint = if (sub.completed) colors.accent else Color.Transparent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(Spacing.s8))
                                    Text(sub.title, color = colors.textPrimary, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                    Section(stringResource(R.string.detail_comments))
                    Comments(detail, onOpenFile = { file -> vm.open(file.id) { path -> openFile(context, path, file.mimeType) } })
                    Spacer(Modifier.size(Spacing.s24))
                }
                val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                ) { uri -> uri?.let { copyIntoApp(context, it) }?.let { (path, name, mime) -> vm.attach(path, name, mime) } }
                CommentComposer(vm::postComment, onAttach = { picker.launch("*/*") })
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
}

@Composable
private fun TopBar(detail: TaskDetail?, onBack: () -> Unit, onDelete: () -> Unit) {
    val colors = AstridTheme.colors
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth().padding(Spacing.s4), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back), tint = colors.textPrimary) }
        Spacer(Modifier.weight(1f))
        if (detail?.showsIdentifier == true) detail.task.identifier?.let { Text(it, color = colors.textSecondary, fontSize = 15.sp) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.detail_more), tint = colors.textPrimary) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                detail?.link?.let { link ->
                    DropdownMenuItem(text = { Text(stringResource(R.string.detail_copy_link)) }, onClick = { clipboard.setText(AnnotatedString(link)); menu = false })
                }
                if (detail?.isCopyOnly != true) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.tasks_delete), color = Error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun TitleRow(detail: TaskDetail, vm: TaskDetailViewModel) {
    val colors = AstridTheme.colors
    val priority = PriorityColors.getOrElse(detail.task.priority) { PriorityColors[0] }
    var title by rememberSaveable(detail.task.id, detail.task.title) { mutableStateOf(detail.task.title) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val square = RoundedCornerShape(8.dp)
        Box(
            Modifier.size(34.dp).clip(square)
                .then(if (detail.task.completed) Modifier.background(priority) else Modifier.border(2.dp, priority, square))
                .clickable(enabled = !detail.isCopyOnly) { vm.setCompleted(!detail.task.completed) }
                .semantics { role = Role.Checkbox; contentDescription = detail.task.title },
            contentAlignment = Alignment.Center,
        ) { if (detail.task.completed) Icon(Icons.Filled.Check, null, tint = Color.White) }
        Spacer(Modifier.width(Spacing.s12))
        TextField(
            value = title,
            onValueChange = { title = it.replace("\n", "") },
            readOnly = detail.isCopyOnly,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = colors.accent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) vm.setTitle(title) },
        )
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = AstridTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (colors.isDark) colors.bgSecondary else Color.White)
            .border(0.5.dp, colors.border, RoundedCornerShape(12.dp)),
    ) { content() }
}

@Composable
private fun Section(title: String) =
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AstridTheme.colors.textSecondary, modifier = Modifier.padding(top = Spacing.s8))

@Composable
private fun FieldRow(label: String, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.s16, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.textSecondary, fontSize = 15.sp, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f)) { value() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WhenField(detail: TaskDetail, state: TaskDetailState, vm: TaskDetailViewModel) {
    val colors = AstridTheme.colors
    var picking by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(false) }
    val label = dueText(detail.due) ?: stringResource(R.string.tasks_no_due_date)
    FieldRow(stringResource(R.string.detail_when), onClick = { vm.loadDueOptions(); picking = true }) {
        Text(label, color = if (detail.isOverdue) Error else colors.textPrimary, fontSize = 16.sp)
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                Row {
                    TextButton(onClick = { picking = false; clock = true }) { Text(stringResource(R.string.detail_pick_time)) }
                    TextButton(onClick = { picking = false; calendar = true }) { Text(stringResource(R.string.detail_pick_date)) }
                }
            },
            dismissButton = { TextButton(onClick = { picking = false; vm.setDue(null, false) }) { Text(stringResource(R.string.detail_clear_date)) } },
            title = { Text(stringResource(R.string.detail_when)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    state.dueOptions?.dates?.forEach { pick ->
                        Chip(duePickLabel(pick.titleKey), pick.isSelected) { picking = false; vm.setDue(pick.dueDateTime, true) }
                    }
                    state.dueOptions?.times?.forEach { pick ->
                        Chip(pick.hour?.let { java.time.LocalTime.of(it, 0).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)) } ?: pick.titleKey, pick.isSelected) {
                            picking = false; vm.setDue(pick.dueDateTime, false)
                        }
                    }
                }
            },
        )
    }
    if (clock) {
        val now = detail.task.dueDateTime?.takeIf { !detail.task.isAllDay }
            ?.let { runCatching { java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()) }.getOrNull() }
        val time = androidx.compose.material3.rememberTimePickerState(
            initialHour = now?.hour ?: 9, initialMinute = now?.minute ?: 0,
            is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current),
        )
        AlertDialog(
            onDismissRequest = { clock = false },
            confirmButton = { TextButton(onClick = { clock = false; vm.pickTime(time.hour, time.minute) }) { Text(stringResource(R.string.detail_done)) } },
            dismissButton = { TextButton(onClick = { clock = false }) { Text(stringResource(R.string.common_cancel)) } },
            text = { androidx.compose.material3.TimePicker(time) },
        )
    }
    if (calendar) {
        val initial = detail.task.dueDateTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(onClick = {
                    calendar = false
                    picker.selectedDateMillis?.let { vm.pickDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                }) { Text(stringResource(R.string.detail_done)) }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun duePickLabel(key: String): String = coreLabel(key)

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    Text(
        text, color = if (selected) colors.accentText else colors.textPrimary, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accent else colors.bgTertiary)
            .clickable(onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun PriorityField(current: Int, onPick: (Int) -> Unit) {
    val names = listOf(R.string.priority_none, R.string.priority_low, R.string.priority_medium, R.string.priority_high)
    FieldRow(stringResource(R.string.detail_priority)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (level in 0..3) {
                val tint = PriorityColors[level]
                val selected = level == current
                // Spoken as its name ("High"), never as the glyph ("!!!").
                val spoken = stringResource(R.string.priority_a11y, stringResource(names[level]))
                Box(
                    Modifier.size(40.dp, 32.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) tint else Color.Transparent)
                        .border(1.5.dp, tint, RoundedCornerShape(8.dp))
                        .clickable { onPick(level) }
                        .semantics(mergeDescendants = true) { this.selected = selected; role = Role.RadioButton; contentDescription = spoken },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (level == 0) "–" else "!".repeat(level), color = if (selected) Color.White else tint, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListsField(detail: TaskDetail, state: TaskDetailState, vm: TaskDetailViewModel) {
    val colors = AstridTheme.colors
    var picking by remember { mutableStateOf(false) }
    FieldRow(stringResource(R.string.detail_lists), onClick = { vm.loadListPicks(); picking = true }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (detail.listChips.isEmpty()) Text("—", color = colors.textMuted)
            detail.listChips.forEach { chip ->
                Text(chip.name, fontSize = 14.sp, color = colors.textPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(parseColor(chip.color).copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
    if (picking) {
        val picks = state.listPicks
        val chosen = remember(picks) { (picks?.selected?.map { it.id } ?: detail.task.listIds).toMutableSet() }
        var tick by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.detail_lists)) },
            confirmButton = { TextButton(onClick = { picking = false; vm.setLists(chosen.toList()) }) { Text(stringResource(R.string.detail_save)) } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_cancel)) } },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    @Suppress("UNUSED_EXPRESSION") tick
                    ((picks?.selected ?: emptyList()) + (picks?.options ?: emptyList())).distinctBy { it.id }.forEach { list ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (!chosen.remove(list.id)) chosen.add(list.id); tick++
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = list.id in chosen, onCheckedChange = { if (!chosen.remove(list.id)) chosen.add(list.id); tick++ })
                            Text(list.name, color = colors.textPrimary)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun DescriptionCard(detail: TaskDetail, onSave: (String) -> Unit) {
    val colors = AstridTheme.colors
    var editing by remember { mutableStateOf(false) }
    var text by rememberSaveable(detail.task.id, detail.task.description) { mutableStateOf(detail.task.description) }
    Section(stringResource(R.string.detail_description))
    Card {
        if (editing && !detail.isCopyOnly) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(Spacing.s8).onFocusChanged { if (!it.isFocused && editing) { editing = false; onSave(text) } },
            )
            TextButton(onClick = { editing = false; onSave(text) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.detail_done)) }
        } else {
            Box(Modifier.fillMaxWidth().clickable(enabled = !detail.isCopyOnly) { editing = true }.padding(Spacing.s16)) {
                if (detail.descriptionBlocks.isEmpty()) {
                    Text(stringResource(R.string.detail_add_description), color = colors.textMuted, fontSize = 16.sp)
                } else {
                    MarkdownView(detail.descriptionBlocks)
                }
            }
        }
    }
}

@Composable
private fun Comments(detail: TaskDetail, onOpenFile: (com.gracefultools.astrid.model.FileRow) -> Unit) {
    val colors = AstridTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        detail.comments.filter { it.showsText }.forEach { comment ->
            Column(
                Modifier.fillMaxWidth().padding(start = if (comment.isReply) 24.dp else 0.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (comment.isMine) colors.bgSelected else if (colors.isDark) colors.bgSecondary else Color.White)
                    .padding(Spacing.s12),
            ) {
                Text(comment.authorName ?: "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.textSecondary)
                Text(comment.content, fontSize = 16.sp, color = if (comment.isSystem) colors.textMuted else colors.textPrimary)
                comment.files.forEach { file ->
                    Row(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgTertiary)
                            .clickable { onOpenFile(file) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.AttachFile, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(file.name, fontSize = 14.sp, color = colors.textPrimary)
                    }
                }
                if (comment.isPending) Text(stringResource(R.string.tasks_waiting_to_send), fontSize = 12.sp, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun CommentComposer(onSend: (String) -> Unit, onAttach: () -> Unit) {
    val colors = AstridTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().background(colors.headerBg).navigationBarsPadding().imePadding().padding(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onAttach) { Icon(Icons.Filled.AttachFile, stringResource(R.string.detail_attach), tint = colors.textSecondary) }
        TextField(
            value = text, onValueChange = { text = it }, placeholder = { Text(stringResource(R.string.detail_add_comment)) },
            shape = RoundedCornerShape(12.dp), maxLines = 4,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.inputBg, unfocusedContainerColor = colors.inputBg,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.detail_send), tint = colors.accent)
        }
    }
}


/** Who: the core's assignee options, first of them "nobody". */
@Composable
private fun AssigneeField(detail: TaskDetail, state: TaskDetailState, vm: TaskDetailViewModel) {
    val colors = AstridTheme.colors
    var picking by remember { mutableStateOf(false) }
    FieldRow(stringResource(R.string.detail_assignee), onClick = if (detail.isCopyOnly) null else ({ vm.loadAssignees(); picking = true })) {
        Text(detail.assignee?.display ?: stringResource(R.string.detail_unassigned), color = colors.textPrimary, fontSize = 16.sp)
    }
    if (picking) {
        PickerDialog(stringResource(R.string.detail_assignee), onDismiss = { picking = false }) {
            state.assignees?.options?.forEach { option ->
                val label = option.name ?: if (option.userId == null) stringResource(R.string.detail_unassigned) else option.userId
                PickerRow(label, selected = option.userId == state.assignees.assigneeId) { picking = false; vm.setAssignee(option.userId) }
            }
        }
    }
}

/** Repeat: the core's presets, worded by its keys; the summary row says what is set now. */
@Composable
private fun RepeatField(detail: TaskDetail, state: TaskDetailState, vm: TaskDetailViewModel) {
    val colors = AstridTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val summary = CoreLabels.summary(context, detail.repeatSummary) ?: coreLabel("repeat.none")
    FieldRow(stringResource(R.string.detail_repeat), onClick = if (detail.isCopyOnly) null else ({ vm.loadRepeats(); picking = true })) {
        Text(summary, color = colors.textPrimary, fontSize = 16.sp)
    }
    var custom by remember { mutableStateOf(false) }
    if (picking) {
        PickerDialog(stringResource(R.string.detail_repeat), onDismiss = { picking = false }) {
            state.repeats?.presets?.forEach { preset ->
                PickerRow(coreLabel(preset.titleKey), preset.isSelected) {
                    picking = false
                    if (preset.value == "custom") custom = true else vm.setRepeating(preset.value)
                }
            }
        }
    }
    if (custom) {
        CustomRepeatDialog(
            initial = state.repeats?.pattern,
            onSave = { unit, interval, weekdays, end, after, until ->
                custom = false
                vm.setCustomRepeat(unit, interval, weekdays, end, after, until)
            },
            onDismiss = { custom = false },
        )
    }
}

/** Reminder: the core's offsets from the due time. */
@Composable
private fun ReminderField(state: TaskDetailState, vm: TaskDetailViewModel) {
    val colors = AstridTheme.colors
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadReminders() }
    val current = state.reminders?.picks?.firstOrNull { it.isSelected }?.titleKey ?: "reminder.none"
    FieldRow(stringResource(R.string.detail_reminder), onClick = { vm.loadReminders(); picking = true }) {
        Text(coreLabel(current), color = colors.textPrimary, fontSize = 16.sp)
    }
    if (picking) {
        PickerDialog(stringResource(R.string.detail_reminder), onDismiss = { picking = false }) {
            state.reminders?.picks?.forEach { pick ->
                PickerRow(coreLabel(pick.titleKey), pick.isSelected) { picking = false; vm.setReminder(pick.reminderTime) }
            }
        }
    }
}

@Composable
private fun PickerDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
    )
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .semantics { this.selected = selected }.padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.textPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, tint = colors.accent)
    }
}

/** Copy a picked document into app storage, where the core can read it: (path, name, MIME type). */
private fun copyIntoApp(context: android.content.Context, uri: android.net.Uri): Triple<String, String, String?>? = runCatching {
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
private fun openFile(context: android.content.Context, path: String, mimeType: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", java.io.File(path))
    val view = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(android.content.Intent.createChooser(view, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
