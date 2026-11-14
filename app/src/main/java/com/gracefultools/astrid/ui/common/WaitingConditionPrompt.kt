package com.gracefultools.astrid.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.Blocker
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "What is it waiting for?" — iOS's `WaitingConditionPrompt` (AITD-631), drawn whenever [moves]
 * holds a move into Waiting the core refused for want of a condition (AATD-98).
 *
 * - **Waiting until…** a day after today (the core makes it an all-day date), or
 * - **Waiting on…** a task, found with the blocker picker's search,
 * - and **Keep assigned to me / <name>**, off by default; hidden when nobody is assigned.
 *
 * Confirm sends the same move again with the answer; Cancel sends nothing. Copy is iOS's
 * `waiting.prompt.*` keys. An alert rather than iOS's half sheet, like the status picker it follows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingConditionPrompt(moves: WaitingMoves) {
    val state by moves.state.collectAsStateWithLifecycle()
    val request = state.request ?: return
    val colors = AstridTheme.colors
    val scope = rememberCoroutineScope()
    val tomorrow = remember(request.taskId) { LocalDate.now().plusDays(1) }

    var waitOnTask by remember(request.taskId) { mutableStateOf(false) }
    var day by remember(request.taskId) { mutableStateOf(tomorrow) }
    var choosingDay by remember(request.taskId) { mutableStateOf(false) }
    var query by remember(request.taskId) { mutableStateOf("") }
    var picked by remember(request.taskId) { mutableStateOf<Blocker?>(null) }
    var keep by remember(request.taskId) { mutableStateOf(false) }

    LaunchedEffect(request.taskId, query, waitOnTask) {
        if (!waitOnTask) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS) // a newer keystroke cancels this one, as iOS's `.task(id:)` does
        moves.search(query)
    }

    val condition: WaitCondition? = if (waitOnTask) {
        picked?.let { WaitCondition.on(it.id, it.title.orEmpty(), keep) }
    } else {
        day.takeIf { it.isAfter(LocalDate.now()) }?.let { WaitCondition.until(it, keep) }
    }

    AlertDialog(
        onDismissRequest = moves::cancel,
        modifier = Modifier.testTag("waiting.prompt"),
        title = { Text(stringResource(R.string.waiting_prompt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                Text(
                    if (request.title.isEmpty()) stringResource(R.string.waiting_prompt_description)
                    else stringResource(R.string.waiting_prompt_description_for, request.title),
                    style = TextStyles.subheadline, color = colors.textSecondary,
                )
                val modes = listOf(R.string.waiting_prompt_until, R.string.waiting_prompt_on)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("waiting.prompt.mode")) {
                    modes.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = waitOnTask == (index == 1),
                            onClick = { waitOnTask = index == 1 },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                if (waitOnTask) {
                    picked?.let {
                        Text(stringResource(R.string.waiting_prompt_picked, it.title.orEmpty()), style = TextStyles.subheadline, color = colors.textPrimary)
                    }
                    // The blocker picker's own search field, as iOS shares its search.
                    com.gracefultools.astrid.ui.detail.SearchField(query, stringResource(R.string.waiting_prompt_task_label)) { query = it }
                    if (query.isNotBlank() && state.candidates.isEmpty()) {
                        Text(stringResource(R.string.tasks_waiting_on_no_results), style = TextStyles.caption, color = colors.textSecondary)
                    }
                    // At most 30 hits (the search's limit): a plain scrolling column, since an alert
                    // measures its content by intrinsics, which a lazy list cannot answer.
                    Column(Modifier.heightIn(max = Spacing.s48 * 4).verticalScroll(rememberScrollState())) {
                        state.candidates.forEach { hit ->
                            Row(
                                Modifier.fillMaxWidth().clickable { picked = hit }.padding(vertical = Spacing.s8).testTag("waiting.prompt.hit.${hit.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    hit.title ?: hit.identifier.orEmpty(), style = TextStyles.body, color = colors.textPrimary,
                                    textDecoration = if (hit.completed) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f),
                                )
                                if (picked?.id == hit.id) Icon(Icons.Filled.Check, null, tint = colors.accentInk)
                            }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().clickable { choosingDay = true }.padding(vertical = Spacing.s8).testTag("waiting.prompt.date"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.waiting_prompt_date_label), style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = TextStyles.body, color = colors.accentInk)
                    }
                    if (!day.isAfter(LocalDate.now())) {
                        Text(stringResource(R.string.waiting_prompt_date_in_past), style = TextStyles.caption, color = colors.errorInk)
                    }
                }
                when (request.keep) {
                    WaitingMoves.Keep.ME -> stringResource(R.string.waiting_prompt_keep_assigned_to_me)
                    WaitingMoves.Keep.OTHER -> stringResource(
                        R.string.waiting_prompt_keep_assigned_to,
                        request.assigneeName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.waiting_prompt_the_assignee),
                    )
                    null -> null
                }?.let { label ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Switch(checked = keep, onCheckedChange = { keep = it }, modifier = Modifier.testTag("waiting.prompt.keep"))
                    }
                }
                when (state.problem) {
                    WaitingMoves.Problem.CYCLE -> stringResource(R.string.tasks_waiting_on_cycle_error)
                    WaitingMoves.Problem.ADD_FAILED -> stringResource(R.string.tasks_waiting_on_add_error)
                    WaitingMoves.Problem.OTHER -> state.problemMessage
                    null -> null
                }?.let { Text(it, style = TextStyles.caption, color = colors.errorInk) }
            }
        },
        confirmButton = {
            AstridTextButton(
                onClick = { condition?.let { scope.launch { moves.confirm(it) } } },
                enabled = condition != null && !state.sending,
                modifier = Modifier.testTag("waiting.prompt.confirm"),
            ) { Text(stringResource(R.string.waiting_prompt_confirm)) }
        },
        dismissButton = { AstridTextButton(onClick = moves::cancel) { Text(stringResource(R.string.common_cancel)) } },
    )

    if (choosingDay) {
        val firstDay = tomorrow.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                // Only a day after today: today would wake the task at once (web's `dateInPast`).
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= firstDay
            },
        )
        DatePickerDialog(
            onDismissRequest = { choosingDay = false },
            confirmButton = {
                AstridTextButton(onClick = {
                    dateState.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    choosingDay = false
                }) { Text(stringResource(R.string.actions_done)) }
            },
            dismissButton = { AstridTextButton(onClick = { choosingDay = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(dateState) }
    }
}

private const val SEARCH_DEBOUNCE_MS = 200L
