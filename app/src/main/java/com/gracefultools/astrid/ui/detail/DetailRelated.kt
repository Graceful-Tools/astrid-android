package com.gracefultools.astrid.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ArrowDropDownCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.AttachmentFile
import com.gracefultools.astrid.model.ProjectFieldRow
import com.gracefultools.astrid.model.TaskDetail
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ── Occurrences (AATD-52, F18) ───────────────────────────────────────────────────────────────

/**
 * iOS's TaskDetailOccurrencesRow: "Previous" / "Next" chips opening the occurrence on top of this
 * one. The links are the task's own `previousOccurrenceId` / `nextOccurrenceId` as the core sends
 * them; a task outside a chain draws nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OccurrencesRow(detail: TaskDetail, onOpenTask: (String) -> Unit) {
    val self = detail.task.id
    val previous = detail.task.previousOccurrenceId?.takeIf { it.isNotEmpty() && it != self }
    val next = detail.task.nextOccurrenceId?.takeIf { it.isNotEmpty() && it != self }
    if (previous == null && next == null) return
    val colors = AstridTheme.colors
    IconRow(stringResource(R.string.tasks_occurrences_label), Icons.Outlined.Repeat) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            previous?.let { id ->
                ValueBox(compact = true, onClick = { onOpenTask(id) }) {
                    Icon(Icons.Filled.ChevronLeft, null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.tasks_occurrences_previous), style = TextStyles.body, color = colors.textPrimary, maxLines = 1)
                }
            }
            next?.let { id ->
                ValueBox(compact = true, onClick = { onOpenTask(id) }) {
                    Icon(Icons.Filled.ChevronRight, null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.tasks_occurrences_next), style = TextStyles.body, color = colors.textPrimary, maxLines = 1)
                }
            }
        }
    }
}

// ── GitHub project fields (AATD-52, F19) ─────────────────────────────────────────────────────

/**
 * iOS's TaskDetailProjectFieldsRows: one row per field the core answers (none off a GitHub board),
 * each with its type's editor — a menu for a single select or an iteration, a calendar for a date,
 * a field committed on Done for text and numbers — and iOS's words when GitHub refuses.
 */
@Composable
internal fun ProjectFieldsRows(state: TaskDetailState, readOnly: Boolean, onSet: (String, kotlinx.serialization.json.JsonElement?) -> Unit) {
    state.projectFields.forEach { row ->
        IconRow(row.name, projectFieldIcon(row.dataType)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                ProjectFieldEditor(row, readOnly) { onSet(row.id, it) }
                val error = state.projectFieldError?.takeIf { it.fieldId == row.id }
                if (error != null) {
                    Text(
                        stringResource(
                            when (error.reason) {
                                ProjectFieldError.Reason.AUTH_REQUIRED -> R.string.tasks_project_fields_error_auth_required
                                ProjectFieldError.Reason.SSO_REQUIRED -> R.string.tasks_project_fields_error_sso_required
                                ProjectFieldError.Reason.SAVE_FAILED -> R.string.tasks_project_fields_error_save_failed
                            },
                        ),
                        style = TextStyles.caption, color = Error,
                    )
                }
            }
        }
    }
}

private fun projectFieldIcon(type: String): ImageVector = when (type) {
    "number" -> Icons.Outlined.Numbers
    "date" -> Icons.Outlined.CalendarMonth
    "single_select" -> Icons.Outlined.ArrowDropDownCircle
    "iteration" -> Icons.Outlined.Sync
    else -> Icons.AutoMirrored.Filled.FormatAlignLeft
}

/** The value as words: an option's or iteration's name, a day, a number, or the text. */
@Composable
private fun projectFieldText(row: ProjectFieldRow): String? {
    val raw = (row.value as? JsonPrimitive) ?: return null
    val text = raw.contentOrNull ?: return null
    return when (row.dataType) {
        "single_select" -> row.options.firstOrNull { it.id == text }?.name ?: text
        "iteration" -> row.iterations.firstOrNull { it.id == text }?.title ?: text
        "date" -> projectDay(text)?.let {
            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        } ?: text
        "number" -> raw.doubleOrNull?.let { number ->
            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            java.text.NumberFormat.getNumberInstance(locale).format(number)
        } ?: text
        else -> text
    }
}

/** GitHub's day is a `yyyy-MM-dd` (or midnight UTC): the same calendar day here. */
private fun projectDay(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.take(10)) }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectFieldEditor(row: ProjectFieldRow, readOnly: Boolean, onSave: (kotlinx.serialization.json.JsonElement?) -> Unit) {
    val colors = AstridTheme.colors
    val shown = projectFieldText(row)
    val empty = stringResource(R.string.tasks_project_fields_empty)
    @Composable
    fun Chip(onClick: (() -> Unit)?) = ValueBox(compact = true, onClick = if (readOnly) null else onClick) {
        Text(shown ?: empty, style = TextStyles.body, color = if (shown == null) colors.textMuted else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (row.dataType) {
        "single_select", "iteration" -> {
            var open by remember { mutableStateOf(false) }
            Box {
                Chip { open = true }
                DropdownMenu(open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.tasks_project_fields_clear)) }, onClick = { open = false; onSave(null) })
                    if (row.dataType == "single_select") row.options.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name) }, onClick = { open = false; onSave(JsonPrimitive(option.id)) })
                    } else row.iterations.forEach { iteration ->
                        DropdownMenuItem(text = { Text(iteration.title) }, onClick = { open = false; onSave(JsonPrimitive(iteration.id)) })
                    }
                }
            }
        }
        "date" -> {
            var picking by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Chip { picking = true }
                if (!readOnly && shown != null) {
                    Icon(
                        Icons.Outlined.Cancel, stringResource(R.string.tasks_project_fields_clear), tint = colors.textMuted,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onSave(null) }.padding(6.dp),
                    )
                }
            }
            if (picking) {
                val day = (row.value as? JsonPrimitive)?.contentOrNull?.let(::projectDay)
                val picker = rememberDatePickerState(initialSelectedDateMillis = day?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
                DatePickerDialog(
                    onDismissRequest = { picking = false },
                    confirmButton = {
                        TextButton(onClick = {
                            picking = false
                            picker.selectedDateMillis?.let { onSave(JsonPrimitive(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())) }
                        }) { Text(stringResource(R.string.detail_done)) }
                    },
                    dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_cancel)) } },
                ) { DatePicker(picker) }
            }
        }
        else -> if (readOnly) Chip(null) else {
            val editable = (row.value as? JsonPrimitive)?.let { value ->
                if (row.dataType == "number") value.doubleOrNull?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: value.contentOrNull
                else value.contentOrNull
            }.orEmpty()
            var draft by remember(row.id, editable) { mutableStateOf(editable) }
            val focus = LocalFocusManager.current
            fun commit() {
                val trimmed = draft.trim()
                if (trimmed == editable) return
                if (trimmed.isEmpty()) { onSave(null); return }
                if (row.dataType == "number") {
                    // Typed in the reader's own format ("2,5" in German), sent as a number.
                    val number = trimmed.replace(',', '.').toDoubleOrNull() ?: run { draft = editable; return }
                    onSave(JsonPrimitive(number))
                } else onSave(JsonPrimitive(trimmed))
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.medium)).background(colors.bgSecondary).padding(horizontal = Spacing.s8, vertical = 6.dp),
            ) {
                if (draft.isEmpty()) Text(empty, style = TextStyles.body, color = colors.textMuted)
                BasicTextField(
                    draft, { draft = it }, singleLine = true,
                    textStyle = TextStyles.body.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = if (row.dataType == "number") KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit(); focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) commit() }.semantics { contentDescription = row.name },
                )
            }
        }
    }
}

// ── Attachments (AATD-52, F21) ───────────────────────────────────────────────────────────────

/**
 * iOS's TaskAttachmentSectionView: "Attachments (n)" over a strip of 64dp thumbnails of every file
 * on the task; a tap opens the gallery across all of them, starting at that one.
 */
@Composable
internal fun AttachmentsSection(files: List<AttachmentFile>, vm: TaskDetailViewModel) {
    if (files.isEmpty()) return
    val colors = AstridTheme.colors
    var viewing by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        Text(
            stringResource(R.string.attachments_count, files.size), style = TextStyles.footnote, color = colors.textMuted,
            modifier = Modifier.padding(horizontal = Spacing.s16).semantics { heading() },
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.s16), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            files.forEachIndexed { index, file ->
                Thumbnail(file, vm, 64.dp, Modifier.clickable(role = Role.Image, onClickLabel = file.name) { viewing = index })
            }
        }
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = Spacing.s8))
    }
    viewing?.let { start -> AttachmentGallery(files, start, vm) { viewing = null } }
}

/** A file's local path: the cached one, or the core downloads it the first time it is drawn. */
@Composable
private fun rememberLocalPath(file: AttachmentFile, vm: TaskDetailViewModel, wanted: Boolean): String? {
    var path by remember(file.id) { mutableStateOf(file.path?.takeIf { file.isCached }) }
    LaunchedEffect(file.id, wanted) { if (wanted && path == null) vm.open(file.id) { path = it } }
    return path
}

@Composable
private fun Thumbnail(file: AttachmentFile, vm: TaskDetailViewModel, size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val colors = AstridTheme.colors
    val shape = RoundedCornerShape(Radius.small)
    Box(modifier.size(size).clip(shape).background(colors.bgSecondary).semantics { contentDescription = file.name }, contentAlignment = Alignment.Center) {
        val path = rememberLocalPath(file, vm, wanted = file.isImage)
        if (file.isImage && path != null) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                Text(file.name.substringAfterLast('.', "").uppercase().take(5), fontSize = 9.sp, fontWeight = FontWeight.Medium, color = colors.textMuted)
            }
        }
    }
}

/**
 * iOS's Quick Look across the task's files: swipe between them; a picture is drawn here, any other
 * file offers Open, which hands it to whatever opens its type.
 */
@Composable
private fun AttachmentGallery(files: List<AttachmentFile>, start: Int, vm: TaskDetailViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start) { files.size }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val file = files[page]
                val path = rememberLocalPath(file, vm, wanted = true)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        path == null -> CircularProgressIndicator(color = Color.White)
                        file.isImage -> AsyncImage(model = File(path), contentDescription = file.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s16)) {
                            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(64.dp))
                            Text(file.name, color = Color.White, style = TextStyles.body, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Spacing.s24))
                            TextButton(onClick = { openFile(context, path, file.mimeType) }) { Text(stringResource(R.string.attachments_open), color = Color.White) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    files.getOrNull(pager.currentPage)?.name.orEmpty(), color = Color.White, style = TextStyles.headline, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = Spacing.s8),
                )
                Icon(
                    Icons.Filled.Close, stringResource(R.string.actions_close), tint = Color.White,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).padding(10.dp),
                )
            }
        }
    }
}

// ── Timer (AATD-52, F24) ─────────────────────────────────────────────────────────────────────

/**
 * iOS's TaskTimerView, over the core's timer: black, the task's title, the time in large monospaced
 * digits, play / pause, and COMPLETE TASK. The core's timer counts the session up from when it
 * started (`detail.timer.startedAt`) and records it on the task when stopped, which leaves the
 * "Last:" caption; iOS's counts down from a chosen duration and rings, which the core does not keep.
 */
@Composable
internal fun TaskTimerScreen(detail: TaskDetail, vm: TaskDetailViewModel, onClose: () -> Unit) {
    val timer = detail.timer
    val running = timer?.isRunning == true
    val startedAt = timer?.startedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); delay(1_000) } }
    val seconds = if (running && startedAt != null) ((now - startedAt) / 1000).coerceAtLeast(0) else 0L
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().padding(Spacing.s16), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Icon(
                    Icons.Filled.Close, stringResource(R.string.actions_close), tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClose).padding(14.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                detail.task.title, color = Color.White.copy(alpha = 0.7f), fontSize = 24.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Spacing.s16),
            )
            Spacer(Modifier.height(48.dp))
            Text(
                "%d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 88.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1,
            )
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier.size(100.dp).clip(CircleShape).background(Color.White)
                    .clickable(role = Role.Button) { if (running) vm.stopTimer() else vm.startTimer() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(if (running) R.string.timer_stop else R.string.timer_start), tint = Color.Black, modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(Spacing.s24))
            if (!detail.task.completed && !detail.isCopyOnly) {
                Row(
                    Modifier.width(260.dp).height(60.dp).clip(RoundedCornerShape(30.dp)).background(Color(0xFF34C759))
                        .clickable(role = Role.Button) { vm.completeFromTimer(); onClose() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.Black)
                    Spacer(Modifier.width(Spacing.s8))
                    Text(stringResource(R.string.timer_complete_task), color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            (timer?.lastValue ?: detail.task.lastTimerValue)?.let { last ->
                Spacer(Modifier.height(Spacing.s16))
                Text(stringResource(R.string.task_edit_last_timer, last), color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
            }
            Spacer(Modifier.weight(2f))
        }
    }
}
