package com.gracefultools.astrid.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.MonthWeekday
import com.gracefultools.astrid.model.RepeatPattern
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.TextStyle

/** The wire's weekday names, Monday first — the order iOS's editor offers them. */
private val WEEKDAYS = DayOfWeek.entries.map { it to it.name.lowercase() }
private val UNITS = listOf("days" to R.string.repeat_unit_days, "weeks" to R.string.repeat_unit_weeks, "months" to R.string.repeat_unit_months, "years" to R.string.repeat_unit_years)
private val ORDINALS = listOf(R.string.repeating_1st, R.string.repeating_2nd, R.string.repeating_3rd, R.string.repeating_4th, R.string.repeating_5th)
private val END_CONDITIONS = listOf("never" to R.string.repeating_never, "after_occurrences" to R.string.repeating_after_occurrences, "until_date" to R.string.repeating_until_date)

/**
 * iOS's CustomRepeatingPatternEditor (AATD-50), as a sheet: "Repeat every" N (1–99) and a unit; for
 * weeks the weekday circles "M T W T F S S" (the last one cannot be turned off); for months "Same
 * date every month" on a day, or "Same weekday and week of month"; for years a month and a day; the
 * Repeat Mode (from completion or from the due date — the task's `repeatFrom`); the end (Never,
 * After X occurrences, Until a date); then Cancel and Close, which saves. It builds the pattern;
 * the core alone interprets it.
 *
 * iOS's wheels are steppers here (Android has no wheel picker). The live "Preview" is worded by the
 * core's rules door (`repeatSummary`, core 4c7fdb6), the same words a saved repeat gets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRepeatDialog(
    initial: RepeatPattern?,
    initialRepeatFrom: String?,
    onSave: (RepeatPattern, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AstridTheme.colors
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var pattern by remember {
        mutableStateOf(
            (initial ?: RepeatPattern()).let { it.copy(unit = it.unit ?: "weeks", interval = (it.interval ?: 1).coerceIn(1, 99), endCondition = it.endCondition ?: "never") },
        )
    }
    var repeatFrom by remember { mutableStateOf(initialRepeatFrom ?: "COMPLETION_DATE") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (colors.isDark) colors.bgPrimary else Color.White,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s16).padding(bottom = Spacing.s24),
            verticalArrangement = Arrangement.spacedBy(Spacing.s16),
        ) {
            Text(stringResource(R.string.repeating_custom_pattern), style = TextStyles.headline, color = colors.textPrimary, modifier = Modifier.semantics { heading() })

            Section(stringResource(R.string.repeating_repeat_every)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                    Stepper(pattern.interval ?: 1, 1..99, stringResource(R.string.repeating_repeat_every)) { pattern = pattern.copy(interval = it) }
                    MenuBox(
                        label = stringResource(UNITS.first { it.first == pattern.unit }.second),
                        choices = UNITS.map { (value, label) -> stringResource(label) to value },
                        modifier = Modifier.weight(1f),
                    ) { unit ->
                        pattern = when (unit) {
                            // A month or a year needs a day to land on before it means anything.
                            "months" -> pattern.copy(unit = unit, monthRepeatType = pattern.monthRepeatType ?: "same_date", monthDay = pattern.monthDay ?: 1)
                            "years" -> pattern.copy(unit = unit, month = pattern.month ?: 1, day = pattern.day ?: 1)
                            else -> pattern.copy(unit = unit)
                        }
                    }
                }
            }

            when (pattern.unit) {
                "weeks" -> Section(stringResource(R.string.repeating_repeat_on_days)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                        WEEKDAYS.forEach { (day, wire) ->
                            val chosen = pattern.weekdays.orEmpty().contains(wire)
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(if (chosen) colors.accent else colors.bgSecondary)
                                    .toggleable(value = chosen, role = Role.Checkbox) {
                                        val current = pattern.weekdays.orEmpty()
                                        val next = if (chosen) current - wire else current + wire
                                        // iOS: at least one weekday stays selected.
                                        if (next.isNotEmpty()) pattern = pattern.copy(weekdays = WEEKDAYS.map { it.second }.filter { it in next })
                                    }
                                    .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, locale) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.getDisplayName(TextStyle.NARROW, locale), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (chosen) Color.White else colors.textMuted,
                                )
                            }
                        }
                    }
                }
                "months" -> Section(stringResource(R.string.repeating_repeat_type)) {
                    val sameDate = stringResource(R.string.repeating_same_date_monthly)
                    val sameWeekday = stringResource(R.string.repeating_same_weekday_monthly)
                    MenuBox(
                        label = if (pattern.monthRepeatType == "same_weekday") sameWeekday else sameDate,
                        choices = listOf(sameDate to "same_date", sameWeekday to "same_weekday"),
                        modifier = Modifier.fillMaxWidth(),
                    ) { type ->
                        pattern = if (type == "same_date") pattern.copy(monthRepeatType = type, monthDay = 1, monthWeekday = null)
                        else pattern.copy(monthRepeatType = type, monthWeekday = MonthWeekday("monday", 1), monthDay = null)
                    }
                    if (pattern.monthRepeatType == "same_weekday") {
                        val weekday = pattern.monthWeekday ?: MonthWeekday()
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                            Section(stringResource(R.string.repeating_weekday), Modifier.weight(1f)) {
                                MenuBox(
                                    label = DayOfWeek.valueOf(weekday.weekday.uppercase()).getDisplayName(TextStyle.FULL, locale),
                                    choices = WEEKDAYS.map { (day, wire) -> day.getDisplayName(TextStyle.FULL, locale) to wire },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { pattern = pattern.copy(monthWeekday = weekday.copy(weekday = it)) }
                            }
                            Section(stringResource(R.string.repeating_week_of_month), Modifier.weight(1f)) {
                                MenuBox(
                                    label = stringResource(ORDINALS[(weekday.weekOfMonth - 1).coerceIn(0, 4)]),
                                    choices = ORDINALS.mapIndexed { index, label -> stringResource(label) to (index + 1).toString() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { pattern = pattern.copy(monthWeekday = weekday.copy(weekOfMonth = it.toInt())) }
                            }
                        }
                    } else {
                        Section(stringResource(R.string.repeating_day_of_month)) {
                            Stepper(pattern.monthDay ?: 1, 1..31, stringResource(R.string.repeating_day_of_month)) { pattern = pattern.copy(monthDay = it) }
                        }
                    }
                }
                "years" -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                    Section(stringResource(R.string.repeating_month), Modifier.weight(1f)) {
                        MenuBox(
                            label = Month.of((pattern.month ?: 1).coerceIn(1, 12)).getDisplayName(TextStyle.FULL_STANDALONE, locale),
                            choices = Month.entries.map { it.getDisplayName(TextStyle.FULL_STANDALONE, locale) to it.value.toString() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { pattern = pattern.copy(month = it.toInt()) }
                    }
                    Section(stringResource(R.string.repeating_day)) {
                        Stepper(pattern.day ?: 1, 1..31, stringResource(R.string.repeating_day)) { pattern = pattern.copy(day = it) }
                    }
                }
            }

            Section(stringResource(R.string.repeating_repeat_mode)) {
                listOf(
                    Triple("COMPLETION_DATE", R.string.repeating_mode_completion_date, R.string.repeating_from_completion),
                    Triple("DUE_DATE", R.string.repeating_mode_due_date, R.string.repeating_from_due_date),
                ).forEach { (mode, title, caption) ->
                    val chosen = repeatFrom == mode
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.small)).background(colors.bgSecondary)
                            .selectable(selected = chosen, role = Role.RadioButton) { repeatFrom = mode }
                            .padding(horizontal = Spacing.s12, vertical = Spacing.s8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(title), style = TextStyles.body, color = colors.textPrimary)
                            Text(stringResource(caption), style = TextStyles.caption2, color = colors.textMuted)
                        }
                        if (chosen) Icon(Icons.Filled.Check, null, tint = colors.accent)
                    }
                }
            }

            Section(stringResource(R.string.repeating_end_condition)) {
                MenuBox(
                    label = stringResource(END_CONDITIONS.firstOrNull { it.first == pattern.endCondition }?.second ?: R.string.repeating_never),
                    choices = END_CONDITIONS.map { (value, label) -> stringResource(label) to value },
                    modifier = Modifier.fillMaxWidth(),
                ) { end -> pattern = pattern.copy(endCondition = end, endAfterOccurrences = if (end == "after_occurrences") pattern.endAfterOccurrences ?: 1 else pattern.endAfterOccurrences) }
                if (pattern.endCondition == "after_occurrences") {
                    Section(stringResource(R.string.repeating_num_occurrences)) {
                        Stepper(pattern.endAfterOccurrences ?: 1, 1..99, stringResource(R.string.repeating_num_occurrences)) { pattern = pattern.copy(endAfterOccurrences = it) }
                    }
                }
                if (pattern.endCondition == "until_date") {
                    Section(stringResource(R.string.repeating_end_date)) { EndDatePicker(pattern.endUntilDate) { pattern = pattern.copy(endUntilDate = it) } }
                }
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            val preview = remember(pattern, repeatFrom) {
                runCatching {
                    val data = (com.gracefultools.astrid.core.CoreJson.encodeToJsonElement(com.gracefultools.astrid.model.RepeatPattern.serializer(), pattern)
                        as kotlinx.serialization.json.JsonObject).let { kotlinx.serialization.json.JsonObject(it + ("type" to kotlinx.serialization.json.JsonPrimitive("custom"))) }
                    com.gracefultools.astrid.core.CoreRules.ask(
                        com.gracefultools.astrid.core.command("repeatSummary", "repeating" to "custom", "repeatingData" to data, "repeatFrom" to repeatFrom),
                    )
                }.getOrNull()
            }
            com.gracefultools.astrid.ui.common.CoreLabels.summary(context, preview)?.let { words ->
                Section(stringResource(R.string.repeating_preview)) {
                    Text(words, style = TextStyles.body, color = colors.textPrimary)
                }
            }

            Row(Modifier.padding(top = Spacing.s8), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                SheetButton(stringResource(R.string.common_cancel), Modifier.weight(1f), onDismiss)
                SheetButton(stringResource(R.string.actions_close), Modifier.weight(1f)) { onSave(pattern, repeatFrom) }
            }
        }
    }
}

/** A caption1 label over its control, as each of iOS's editor sections is. */
@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        Text(title, style = TextStyles.caption, color = AstridTheme.colors.textSecondary)
        content()
    }
}

/** iOS's Menu label: the current choice and a chevron, on bgSecondary; tapping lists the choices. */
@Composable
private fun MenuBox(label: String, choices: List<Pair<String, String>>, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val colors = AstridTheme.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.small)).background(colors.bgSecondary)
                .clickable(role = Role.DropdownList) { open = true }.heightIn(min = 44.dp).padding(horizontal = Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            choices.forEach { (text, value) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) }) }
        }
    }
}

/** iOS's number wheel, as − N + within [range]. */
@Composable
private fun Stepper(value: Int, range: IntRange, label: String, onChange: (Int) -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(Radius.small)).background(colors.bgSecondary),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Remove, stringResource(R.string.repeating_decrease, label), tint = if (value > range.first) colors.accent else colors.textMuted,
            modifier = Modifier.size(44.dp).clickable(enabled = value > range.first) { onChange(value - 1) }.padding(12.dp),
        )
        Text(value.toString(), style = TextStyles.headline, color = colors.textPrimary, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Icon(
            Icons.Filled.Add, stringResource(R.string.repeating_increase, label), tint = if (value < range.last) colors.accent else colors.textMuted,
            modifier = Modifier.size(44.dp).clickable(enabled = value < range.last) { onChange(value + 1) }.padding(12.dp),
        )
    }
}

/** iOS's graphical end-date calendar: today or later, saved as the all-day instant of that day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndDatePicker(current: String?, onPick: (String) -> Unit) {
    val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = current?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
        },
    )
    LaunchedEffect(picker) {
        snapshotFlow { picker.selectedDateMillis }.collect { millis ->
            // An all-day date, as the wire stores one: UTC midnight of the chosen day.
            millis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toString()) }
        }
    }
    DatePicker(picker, title = null, headline = null, showModeToggle = false, colors = DatePickerDefaults.colors(containerColor = Color.Transparent))
}

@Composable
private fun SheetButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    Box(
        modifier.clip(RoundedCornerShape(Radius.medium)).background(colors.bgSecondary).clickable(role = Role.Button, onClick = onClick).padding(vertical = Spacing.s12),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = TextStyles.body, color = colors.textSecondary) }
}
