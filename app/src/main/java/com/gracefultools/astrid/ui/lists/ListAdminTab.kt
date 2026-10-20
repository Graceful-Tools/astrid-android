package com.gracefultools.astrid.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.detail.PickRow
import com.gracefultools.astrid.ui.detail.PickerSheet
import com.gracefultools.astrid.ui.tasks.parseColor
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Which of the Admin tab's pickers is open. */
private enum class AdminPicker { ASSIGNEE, DUE_DATE, DUE_TIME, REPEATING, RECENTLY_COMPLETED, SINCE_DATE, CUSTOM_COLOR }

/** iOS's default due-date choices: stored value to label. */
private val DUE_DATES = listOf(
    "none" to R.string.lists_none, "today" to R.string.time_today, "tomorrow" to R.string.lists_tomorrow,
    "next_week" to R.string.lists_next_week, "next_month" to R.string.lists_next_month,
)

/** iOS's default due times (`HH:mm`), after All Day. */
private val DUE_TIMES = listOf("09:00", "12:00", "14:00", "17:00", "18:00", "20:00")

private val REPEATS = listOf(
    "never" to R.string.lists_never, "daily" to R.string.lists_daily, "weekly" to R.string.lists_weekly, "monthly" to R.string.lists_monthly,
)

/**
 * iOS ListAdminTab, section for section: List Information, List Appearance, Default Task Settings,
 * [integrations slot], Project board, Advanced, Danger Zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListAdminTab(state: ListSettingsState, vm: ListSettingsViewModel, integrations: @Composable () -> Unit = {}) {
    val settings = state.settings ?: return
    val colors = AstridTheme.colors
    var picker by remember { mutableStateOf<AdminPicker?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDisableBoard by remember { mutableStateOf(false) }
    val pickImage = rememberImagePicker(vm::setImage)
    val locale = LocalConfiguration.current.locales[0]
    val timeLabel: (String) -> String = { hhmm ->
        runCatching { LocalTime.parse(hhmm).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }.getOrDefault(hhmm)
    }

    FormColumn {
        // List Information — saved as typed (debounced in the view model).
        FormSection(stringResource(R.string.lists_list_information)) {
            var name by rememberSaveable(settings.listId) { mutableStateOf(settings.name) }
            var description by rememberSaveable(settings.listId, state.detail != null) { mutableStateOf(state.detail?.description.orEmpty()) }
            FormRow {
                FormTextField(name, stringResource(R.string.lists_list_name), singleLine = true) { name = it; vm.editName(it) }
            }
            FormDivider()
            FormRow {
                FormTextField(description, stringResource(R.string.detail_description), singleLine = false) { description = it; vm.editDescription(it) }
            }
        }

        // List Appearance — the image row only while list images are shown (iOS AITD-468).
        FormSection(stringResource(R.string.lists_list_appearance)) {
            if (state.showListImages) {
                FormRow(Modifier.padding(vertical = Spacing.s4)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(Radius.large)).background(parseColor(settings.color)), contentAlignment = Alignment.Center) {
                        state.imageSource?.let { source ->
                            AsyncImage(model = source, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
                        }
                        if (state.imageUploading) CircularProgressIndicator(Modifier.size(24.dp), color = colors.accentText, strokeWidth = 2.dp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                        Text(stringResource(R.string.lists_list_image), style = TextStyles.body, color = colors.textPrimary)
                        Text(stringResource(R.string.lists_list_image_description), style = TextStyles.caption2, color = colors.textSecondary)
                    }
                    TextButton(onClick = pickImage, enabled = !state.imageUploading) {
                        Text(stringResource(R.string.actions_change), style = TextStyles.body, color = colors.accent)
                    }
                }
                FormDivider()
            }
            Column(Modifier.padding(horizontal = Spacing.s16, vertical = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Text(stringResource(R.string.lists_list_color), style = TextStyles.body, color = colors.textPrimary)
                ColorSwatches(settings.colorChoices, settings.color, vm::pickColor) { picker = AdminPicker.CUSTOM_COLOR }
            }
        }

        // Default Task Settings — each one through updateList's default* field.
        FormSection(stringResource(R.string.lists_default_task_settings)) {
            val defaults = settings.defaults
            if (settings.supports.priority) {
                FormRow {
                    Text(stringResource(R.string.tasks_priority), style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    PriorityButtons(defaults.priority, vm::setDefaultPriority)
                }
                FormDivider()
            }
            val assigneeLabel = when (val id = defaults.assigneeId) {
                null -> stringResource(R.string.lists_task_creator)
                "unassigned" -> stringResource(R.string.assignee_unassigned)
                else -> settings.members.firstOrNull { it.who == id }?.display ?: state.owner?.takeIf { it.id == id }?.display ?: id
            }
            FormPickerRow(stringResource(R.string.tasks_assignee), assigneeLabel) { picker = AdminPicker.ASSIGNEE }
            if (settings.supports.dueDate) {
                FormDivider()
                FormPickerRow(stringResource(R.string.tasks_due_date), stringResource(DUE_DATES.firstOrNull { it.first == defaults.dueDate }?.second ?: R.string.lists_none)) { picker = AdminPicker.DUE_DATE }
                FormDivider()
                FormPickerRow(stringResource(R.string.tasks_due_time), defaults.dueTime?.let(timeLabel) ?: stringResource(R.string.lists_all_day)) { picker = AdminPicker.DUE_TIME }
            }
            FormDivider()
            FormPickerRow(stringResource(R.string.lists_repeating), stringResource(REPEATS.firstOrNull { it.first == defaults.repeating }?.second ?: R.string.lists_never)) { picker = AdminPicker.REPEATING }
        }

        // iOS's "AI Coding Agent" repository picker and "External sync" (ListAdminTab 365-539).
        integrations()

        // Project board.
        FormSection(
            stringResource(R.string.lists_project_board),
            footer = stringResource(if (settings.projectId == null) R.string.lists_create_board_footer else R.string.lists_disable_board_footer),
        ) {
            if (settings.projectId != null) {
                FormButtonRow(Icons.Outlined.ViewWeek, stringResource(R.string.lists_disable_board), Error, enabled = !state.boardBusy) { confirmDisableBoard = true }
            } else {
                FormButtonRow(
                    Icons.Outlined.ViewWeek, stringResource(R.string.lists_create_board), colors.accent, enabled = !state.boardBusy,
                    trailing = if (state.boardBusy) ({ CircularProgressIndicator(Modifier.size(18.dp), color = colors.accent, strokeWidth = 2.dp) }) else null,
                ) { vm.createBoard() }
            }
            state.boardError?.let { failure ->
                Text(
                    stringResource(if (failure.creating) R.string.lists_board_create_failed else R.string.lists_board_disable_failed, failure.message),
                    style = TextStyles.footnote, color = Error, modifier = Modifier.padding(start = Spacing.s16, end = Spacing.s16, bottom = Spacing.s12),
                )
            }
        }

        // Advanced — the recently completed window.
        FormSection(stringResource(R.string.lists_advanced), footer = stringResource(R.string.lists_recently_completed_footer)) {
            val preset = state.recentlyCompletedPreset
            FormPickerRow(stringResource(R.string.lists_recently_completed), stringResource(preset.label)) { picker = AdminPicker.RECENTLY_COMPLETED }
            if (preset == RecentlyCompletedPreset.SINCE_SPECIFIC_DATE) {
                FormDivider()
                val since = state.recentlyCompletedSince?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                FormPickerRow(
                    stringResource(R.string.lists_since),
                    since?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) ?: "",
                ) { picker = AdminPicker.SINCE_DATE }
            }
        }

        // Danger Zone.
        if (settings.canDeleteList) {
            FormSection(stringResource(R.string.messages_danger_zone)) {
                FormButtonRow(Icons.Outlined.Delete, stringResource(R.string.lists_delete), Error) { confirmDelete = true }
            }
        }
    }

    val close = { picker = null }
    when (picker) {
        AdminPicker.ASSIGNEE -> PickerSheet(stringResource(R.string.tasks_assignee), close) {
            val current = settings.defaults.assigneeId
            PickRow(stringResource(R.string.lists_task_creator), current == null) { vm.setDefaultAssignee(null); close() }
            PickRow(stringResource(R.string.assignee_unassigned), current == "unassigned") { vm.setDefaultAssignee("unassigned"); close() }
            val people = listOfNotNull(state.owner) + settings.members.mapNotNull { m -> m.user ?: m.who?.let { com.gracefultools.astrid.model.UserSummary(it, m.name, m.email) } }
            people.distinctBy { it.id }.forEach { person ->
                PickRow(person.display, current == person.id) { vm.setDefaultAssignee(person.id); close() }
            }
        }
        AdminPicker.DUE_DATE -> PickerSheet(stringResource(R.string.tasks_due_date), close) {
            DUE_DATES.forEach { (value, label) -> PickRow(stringResource(label), settings.defaults.dueDate == value) { vm.setDefaultDueDate(value); close() } }
        }
        AdminPicker.DUE_TIME -> PickerSheet(stringResource(R.string.tasks_due_time), close) {
            PickRow(stringResource(R.string.lists_all_day), settings.defaults.dueTime == null) { vm.setDefaultDueTime(null); close() }
            DUE_TIMES.forEach { value -> PickRow(timeLabel(value), settings.defaults.dueTime == value) { vm.setDefaultDueTime(value); close() } }
        }
        AdminPicker.REPEATING -> PickerSheet(stringResource(R.string.lists_repeating), close) {
            REPEATS.forEach { (value, label) -> PickRow(stringResource(label), settings.defaults.repeating == value) { vm.setDefaultRepeating(value); close() } }
        }
        AdminPicker.RECENTLY_COMPLETED -> PickerSheet(stringResource(R.string.lists_recently_completed), close) {
            RecentlyCompletedPreset.entries.forEach { preset ->
                PickRow(stringResource(preset.label), state.recentlyCompletedPreset == preset) {
                    vm.setRecentlyCompleted(preset, state.recentlyCompletedSince); close()
                }
            }
        }
        AdminPicker.SINCE_DATE -> {
            val initial = state.recentlyCompletedSince?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
            val dateState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = close,
                confirmButton = {
                    TextButton(onClick = {
                        dateState.selectedDateMillis?.let { millis ->
                            val picked = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            vm.setRecentlyCompleted(RecentlyCompletedPreset.SINCE_SPECIFIC_DATE, picked.toString())
                        }
                        close()
                    }) { Text(stringResource(R.string.actions_done)) }
                },
                dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.common_cancel)) } },
            ) { DatePicker(dateState) }
        }
        AdminPicker.CUSTOM_COLOR -> CustomColorSheet(settings.color, vm::pickCustomColor) { vm.flushEdits(); close() }
        null -> Unit
    }

    if (confirmDelete) {
        ConfirmAlert(
            stringResource(R.string.lists_delete), stringResource(R.string.lists_delete_confirm_message), stringResource(R.string.actions_delete),
            onConfirm = vm::delete, onDismiss = { confirmDelete = false },
        )
    }
    if (confirmDisableBoard) {
        ConfirmAlert(
            stringResource(R.string.lists_disable_board_title), stringResource(R.string.lists_disable_board_message), stringResource(R.string.lists_disable),
            onConfirm = vm::disableBoard, onDismiss = { confirmDisableBoard = false },
        )
    }
}

/** A borderless Form text field with a placeholder, as iOS's TextField inside a Form row. */
@Composable
internal fun FormTextField(value: String, placeholder: String, singleLine: Boolean, onChange: (String) -> Unit) {
    val colors = AstridTheme.colors
    Box(Modifier.fillMaxWidth()) {
        if (value.isEmpty()) Text(placeholder, style = TextStyles.body, color = colors.inputPlaceholder)
        BasicTextField(
            value, onChange, singleLine = singleLine, maxLines = if (singleLine) 1 else 8,
            textStyle = TextStyles.body.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        )
    }
}
