package com.gracefultools.astrid.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.RepeatPattern
import com.gracefultools.astrid.ui.theme.AstridTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** The wire's weekday names, Monday first — the order iOS's editor offers them. */
private val WEEKDAYS = DayOfWeek.entries.map { it to it.name.lowercase() }

/**
 * iOS's custom repeat editor: every N days / weeks (on which weekdays) / months / years, ending
 * never, after N times, or on a date. It builds the pattern; the core alone interprets it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomRepeatDialog(
    initial: RepeatPattern?,
    onSave: (unit: String, interval: Int, weekdays: List<String>, endCondition: String?, endAfter: Int?, endUntil: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AstridTheme.colors
    var unit by remember { mutableStateOf(initial?.unit ?: "weeks") }
    var interval by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    val weekdays = remember { mutableStateListOf<String>().apply { addAll(initial?.weekdays.orEmpty()) } }
    var end by remember { mutableStateOf(initial?.endCondition ?: "never") }
    var endAfter by remember { mutableStateOf((initial?.endAfterOccurrences ?: 10).toString()) }
    var endUntil by remember { mutableStateOf(initial?.endUntilDate) }
    var pickingDate by remember { mutableStateOf(false) }

    @Composable
    fun Choice(label: String, chosen: Boolean, onClick: () -> Unit) = Text(
        label, fontSize = 15.sp, color = if (chosen) colors.accentText else colors.textPrimary,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (chosen) colors.accent else colors.bgTertiary)
            .clickable(onClick = onClick).semantics { selected = chosen }.padding(horizontal = 12.dp, vertical = 6.dp),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat_custom_title)) },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    unit, interval.toIntOrNull() ?: 1, WEEKDAYS.map { it.second }.filter { it in weekdays },
                    end.takeUnless { it == "never" }, endAfter.toIntOrNull(), endUntil,
                )
            }) { Text(stringResource(R.string.detail_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.repeat_every), color = colors.textPrimary)
                    OutlinedTextField(
                        interval, { interval = it.filter(Char::isDigit).take(3) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(72.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("days" to R.string.repeat_unit_days, "weeks" to R.string.repeat_unit_weeks,
                        "months" to R.string.repeat_unit_months, "years" to R.string.repeat_unit_years).forEach { (value, label) ->
                        Choice(stringResource(label), unit == value) { unit = value }
                    }
                }
                if (unit == "weeks") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        WEEKDAYS.forEach { (day, wire) ->
                            Choice(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), wire in weekdays) {
                                if (!weekdays.remove(wire)) weekdays.add(wire)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.repeat_ends), color = colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(stringResource(R.string.repeat_ends_never), end == "never") { end = "never" }
                    Choice(stringResource(R.string.repeat_ends_after), end == "after_occurrences") { end = "after_occurrences" }
                    Choice(endUntil?.take(10) ?: stringResource(R.string.repeat_ends_on_date), end == "until_date") { end = "until_date"; pickingDate = true }
                }
                if (end == "after_occurrences") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            endAfter, { endAfter = it.filter(Char::isDigit).take(4) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(88.dp),
                        )
                        Text(stringResource(R.string.repeat_times), color = colors.textPrimary)
                    }
                }
            }
        },
    )
    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = endUntil?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() })
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickingDate = false
                    // An all-day date, as the wire stores one: UTC midnight of the chosen day.
                    picker.selectedDateMillis?.let { endUntil = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toString() }
                }) { Text(stringResource(R.string.detail_done)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(picker) }
    }
}
