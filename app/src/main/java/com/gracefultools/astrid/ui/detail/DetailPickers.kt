package com.gracefultools.astrid.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.TaskDetail
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.common.coreLabel
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import java.time.Instant
import java.time.ZoneOffset

/** A bottom sheet the way iOS's pickers are: a title with Close, then the rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = AstridTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (colors.isDark) colors.bgSecondary else Color.White,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s16), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TextStyles.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_close)) }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s16).padding(bottom = Spacing.s24),
            verticalArrangement = Arrangement.spacedBy(Spacing.s8),
            content = content,
        )
    }
}

/** iOS's picker row: the label, a check when chosen; selected rows tinted accent 15% with a border. */
@Composable
internal fun PickRow(label: String, selected: Boolean, color: Color? = null, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    val shape = RoundedCornerShape(Radius.medium)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.15f) else colors.bgSecondary)
            .border(1.dp, if (selected) colors.accent else Color.Transparent, shape)
            .clickable(onClick = onClick).semantics { this.selected = selected }
            .heightIn(min = 48.dp).padding(horizontal = Spacing.s12, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyles.body, color = color ?: colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = TextStyles.caption2, color = colors.textMuted)
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = color ?: colors.accent, modifier = Modifier.size(18.dp))
    }
}

/**
 * iOS's InlineAssigneePicker: nobody first, then faces with "You" / "AI Agent", then "Someone
 * else" — an email (or a person found by typing a name) is given access to the task's list and
 * assigned (AATD-51).
 */
@Composable
internal fun AssigneeSheet(state: TaskDetailState, vm: TaskDetailViewModel, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    LaunchedEffect(Unit) { vm.resetSomeoneElse() }
    LaunchedEffect(state.someoneElse.assigned) { if (state.someoneElse.assigned) { vm.resetSomeoneElse(); onDismiss() } }
    PickerSheet(stringResource(R.string.tasks_assignee), onDismiss) {
        val options = state.assignees?.options
        if (options == null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = colors.accent)
        options?.forEach { option ->
            val caption = when {
                option.isCurrentUser -> stringResource(R.string.assignee_you)
                option.isAgent -> stringResource(R.string.profile_ai_agent)
                else -> null
            }
            // Several at once where the list allows it (a GitHub board): each tap toggles, and the
            // sheet stays open (iOS AssigneeSelection, AATD-51).
            val multiple = state.assignees.allowsMultiple
            PickRow(
                label = option.name ?: if (option.userId == null) stringResource(R.string.assignee_unassigned) else "",
                selected = if (multiple) option.userId?.let { it in state.assignees.assigneeIds } ?: state.assignees.assigneeIds.isEmpty()
                else option.userId == state.assignees.assigneeId,
                subtitle = caption,
                leading = {
                    if (option.userId == null) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(colors.bgTertiary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.PersonOff, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                        }
                    } else Avatar(option.image, option.initials, 32.dp)
                },
            ) { if (multiple) vm.toggleAssignee(option.userId) else onPick(option.userId) }
        }
        SomeoneElseSection(state.someoneElse, vm)
    }
}

/** iOS's "Someone else": "Invite by Email", Assign, up to five people found, and why it failed. */
@Composable
private fun SomeoneElseSection(section: SomeoneElse, vm: TaskDetailViewModel) {
    val colors = AstridTheme.colors
    var query by remember { mutableStateOf("") }
    val email = query.trim()
    // iOS's isAssignableEmail: enough to be worth asking the server about.
    val assignable = email.contains('@') && email.contains('.')
    Spacer(Modifier.size(Spacing.s4))
    Text(stringResource(R.string.assignee_someone_else), style = TextStyles.caption, color = colors.textSecondary)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        val inviteLabel = stringResource(R.string.members_invite_email)
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(Radius.medium)).background(colors.field)
                .border(1.dp, colors.fieldBorder, RoundedCornerShape(Radius.medium)).heightIn(min = 40.dp).padding(horizontal = Spacing.s12),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) Text(inviteLabel, style = TextStyles.body, color = colors.inputPlaceholder)
            BasicTextField(
                query, { query = it; vm.searchPeople(it) }, singleLine = true,
                textStyle = TextStyles.body.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done, autoCorrectEnabled = false,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (assignable) vm.assignByEmail(email) }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = inviteLabel },
            )
        }
        TextButton(onClick = { vm.assignByEmail(email) }, enabled = assignable) { Text(stringResource(R.string.actions_assign)) }
    }
    section.suggestions.forEach { person ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.small)).background(colors.bgSecondary)
                .clickable { person.email?.let { query = it; vm.assignByEmail(it) } }.padding(horizontal = Spacing.s12, vertical = Spacing.s8),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        ) {
            Icon(Icons.Outlined.AccountCircle, null, tint = colors.textMuted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(person.name?.takeIf { it.isNotBlank() } ?: person.email.orEmpty(), style = TextStyles.body, color = colors.textPrimary)
                if (!person.name.isNullOrBlank()) Text(person.email.orEmpty(), style = TextStyles.caption, color = colors.textMuted)
            }
        }
    }
    val error = if (section.needsList) stringResource(R.string.assignee_needs_a_list) else section.error
    if (error != null) Text(error, style = TextStyles.caption, color = Error)
}

/** iOS's InlineDatePicker sheet: "No due date" (red), the quick picks, and a calendar that saves on a tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateSheet(detail: TaskDetail, state: TaskDetailState, vm: TaskDetailViewModel, onDismiss: () -> Unit) {
    PickerSheet(stringResource(R.string.tasks_due_date), onDismiss) {
        PickRow(stringResource(R.string.picker_no_due_date), selected = detail.task.dueDateTime == null, color = Error) { vm.setDue(null, false); onDismiss() }
        state.dueOptions?.dates?.forEach { pick ->
            PickRow(coreLabel(pick.titleKey), pick.isSelected) { vm.setDue(pick.dueDateTime, true); onDismiss() }
        }
        val initial = detail.task.dueDateTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        val calendar = rememberDatePickerState(initialSelectedDateMillis = initial)
        LaunchedEffect(calendar) {
            snapshotFlow { calendar.selectedDateMillis }.collect { millis ->
                if (millis != null && millis != initial) {
                    vm.pickDay(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    onDismiss()
                }
            }
        }
        DatePicker(calendar, title = null, headline = null, showModeToggle = false, colors = DatePickerDefaults.colors(containerColor = Color.Transparent))
    }
}

/** iOS's InlineTimePicker sheet: "All day" (red), Morning (9 AM) … Night (9 PM), and a clock with Set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeSheet(detail: TaskDetail, state: TaskDetailState, vm: TaskDetailViewModel, onDismiss: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    PickerSheet(stringResource(R.string.picker_time), onDismiss) {
        PickRow(stringResource(R.string.picker_all_day), selected = detail.task.isAllDay, color = Error) {
            // Keep the day, drop the time: the core's all-day instant for the task's local day.
            detail.task.dueDateTime?.let { iso ->
                runCatching { Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }.getOrNull()?.let(vm::pickDay)
            }
            onDismiss()
        }
        state.dueOptions?.times?.forEach { pick ->
            val hour = pick.hour?.let { java.time.LocalTime.of(it, 0).format(java.time.format.DateTimeFormatter.ofPattern("h a", locale)) }
            val label = coreLabel(pick.titleKey) + (hour?.let { " ($it)" } ?: "")
            PickRow(label, pick.isSelected) { pick.hour?.let { vm.pickTime(it, 0) }; onDismiss() }
        }
        val now = detail.task.dueDateTime?.takeIf { !detail.task.isAllDay }
            ?.let { runCatching { Instant.parse(it).atZone(java.time.ZoneId.systemDefault()) }.getOrNull() }
        val clock = rememberTimePickerState(
            initialHour = now?.hour ?: 9, initialMinute = now?.minute ?: 0,
            is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(clock) }
        TextButton(onClick = { vm.pickTime(clock.hour, clock.minute); onDismiss() }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.actions_set), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** iOS's InlineRepeatPicker: every preset with a check; Custom opens the editor. */
@Composable
internal fun RepeatSheet(state: TaskDetailState, vm: TaskDetailViewModel, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf(false) }
    if (!custom) PickerSheet(stringResource(R.string.repeating_title), onDismiss) {
        state.repeats?.presets?.forEach { preset ->
            PickRow(coreLabel(preset.titleKey), preset.isSelected) {
                if (preset.value == "custom") custom = true else { vm.setRepeating(preset.value); onDismiss() }
            }
        }
    }
    if (custom) {
        CustomRepeatDialog(
            initial = state.repeats?.pattern,
            initialRepeatFrom = state.repeats?.repeatFrom,
            onSave = { pattern, repeatFrom -> vm.setCustomRepeat(pattern, repeatFrom); onDismiss() },
            onDismiss = onDismiss,
        )
    }
}

/** iOS's InlineListsPicker: "Search lists...", list marks, round checks; each tap saves. */
@Composable
internal fun ListsSheet(detail: TaskDetail, state: TaskDetailState, vm: TaskDetailViewModel, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    var query by remember { mutableStateOf("") }
    PickerSheet(stringResource(R.string.navigation_lists), onDismiss) {
        SearchField(query, stringResource(R.string.picker_search_lists)) { query = it; vm.loadListPicks(it) }
        val picks = state.listPicks
        val chosen = picks?.selected?.map { it.id }?.toSet() ?: detail.task.listIds.toSet()
        val all = ((picks?.selected ?: emptyList()) + (picks?.options ?: emptyList())).distinctBy { it.id }
        if (picks != null && all.isEmpty()) Text(stringResource(R.string.picker_no_lists_found), style = TextStyles.body, color = colors.textMuted)
        all.forEach { list ->
            val on = list.id in chosen
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.medium)).clickable {
                    vm.setLists(if (on) (chosen - list.id).toList() else (chosen + list.id).toList())
                    vm.loadListPicks(query)
                }.heightIn(min = 44.dp).padding(horizontal = Spacing.s8),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            ) {
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(com.gracefultools.astrid.ui.tasks.parseColor(list.color)))
                Text(list.name, style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(if (on) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (on) colors.accent else colors.textMuted)
            }
        }
    }
}

/** iOS's TaskDetailBlockersRow picker: "Search tasks…", candidates, "No matching tasks". */
@Composable
internal fun BlockerSheet(state: TaskDetailState, vm: TaskDetailViewModel, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    var query by remember { mutableStateOf("") }
    PickerSheet(stringResource(R.string.tasks_waiting_on_add), onDismiss) {
        SearchField(query, stringResource(R.string.tasks_waiting_on_search_placeholder)) { query = it; vm.searchBlockers(it) }
        if (state.blockerCandidates.isEmpty()) Text(stringResource(R.string.tasks_waiting_on_no_results), style = TextStyles.body, color = colors.textMuted)
        state.blockerCandidates.forEach { candidate ->
            PickRow(listOfNotNull(candidate.identifier, candidate.title).joinToString("  "), selected = false) { vm.addBlocker(candidate.id); onDismiss() }
        }
    }
}

@Composable
internal fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.medium)).background(colors.bgTertiary).heightIn(min = 40.dp).padding(horizontal = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        Icon(Icons.Filled.Search, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = TextStyles.body, color = colors.inputPlaceholder)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyles.body.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** iOS's CopyTaskView: the target list (My Tasks only by default), Include Comments, Copy Task. */
@Composable
internal fun CopyTaskSheet(vm: TaskDetailViewModel, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<String?>(null) }
    var comments by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadListPicks("") }
    PickerSheet(stringResource(R.string.tasks_copy_task), onDismiss) {
        Text(stringResource(R.string.lists_copy_to_list), style = TextStyles.caption, color = colors.textSecondary)
        PickRow(stringResource(R.string.copy_my_tasks_only), target == null) { target = null }
        val lists = ((state.listPicks?.selected ?: emptyList()) + (state.listPicks?.options ?: emptyList())).distinctBy { it.id }
        lists.forEach { list -> PickRow(list.name, target == list.id) { target = list.id } }
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.lists_include_comments), style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Switch(comments, { comments = it })
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.large)).background(colors.accent)
                .clickable { vm.copy(target, comments); onDismiss() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.tasks_copy_task), style = TextStyles.headline, color = colors.accentText) }
    }
}

/** iOS's ShareTargetView for a task: the privacy note, the link, Copy to Clipboard, Share. */
@Composable
internal fun ShareTaskSheet(vm: TaskDetailViewModel, detail: TaskDetail, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    PickerSheet(stringResource(R.string.share_share_task), onDismiss) {
        Text(stringResource(R.string.share_private_task_message), style = TextStyles.subheadline, color = colors.textSecondary)
        val link = state.shareLink
        when {
            state.shareFailed -> Text(stringResource(R.string.share_link_failed), style = TextStyles.body, color = Error)
            link == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = colors.accent, strokeWidth = 2.dp)
                Text(stringResource(R.string.share_generating_link), style = TextStyles.body, color = colors.textSecondary)
            }
            else -> {
                Text(link, style = TextStyles.subheadline, color = colors.textPrimary, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.medium)).background(colors.bgSecondary).padding(Spacing.s12))
                Text(stringResource(R.string.share_share_link_hint), style = TextStyles.caption, color = colors.textMuted)
                PickRow(stringResource(if (copied) R.string.share_link_copied else R.string.share_copy_link), copied, leading = { Icon(Icons.Outlined.ContentCopy, null, tint = colors.accent) }) {
                    clipboard.setText(AnnotatedString(link)); copied = true
                }
                PickRow(stringResource(R.string.actions_share), false, leading = { Icon(Icons.Outlined.Share, null, tint = colors.accent) }) {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, detail.task.title).putExtra(android.content.Intent.EXTRA_TEXT, link)
                    context.startActivity(android.content.Intent.createChooser(send, null))
                }
            }
        }
    }
}
