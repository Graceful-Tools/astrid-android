package com.gracefultools.astrid.ui.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.common.coreLabel
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS's sort and filter form (AATD-41): "Sort By", then "Filters" — each a row whose value opens a
 * menu of the core's choices — then "Clear All Filters". [extra] goes under the filters (a list's
 * Show subtasks, Favorite and Saved Filter toggles).
 */
@Composable
fun FiltersPanel(vm: FiltersViewModel, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        state.groups.firstOrNull { it.field == "sortBy" }?.let { sort ->
            SectionTitle(stringResource(R.string.filters_sort_by))
            Card { ChoiceRow(sort, vm::pick) }
        }
        SectionTitle(stringResource(R.string.lists_filters))
        Card {
            state.groups.filter { it.field != "sortBy" }.forEachIndexed { index, group ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = Spacing.s16), color = colors.divider)
                ChoiceRow(group, vm::pick)
            }
        }
        extra()
        Text(
            stringResource(R.string.lists_clear_all_filters), style = TextStyles.body,
            color = if (state.isFiltered) Error else colors.textMuted,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.large)).background(cardColor())
                .clickable(enabled = state.isFiltered) { vm.clearAll() }.padding(Spacing.s16),
        )
    }
}

@Composable
private fun cardColor(): Color = if (AstridTheme.colors.isDark) AstridTheme.colors.bgSecondary else Color.White

@Composable
private fun SectionTitle(text: String) = Text(
    text, style = TextStyles.footnote, color = AstridTheme.colors.textSecondary,
    modifier = Modifier.padding(start = Spacing.s16, top = Spacing.s8).semantics { heading() },
)

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.large)).background(cardColor())) { content() }
}

/** A Form picker row: the group's name, its current choice with ⇕, a menu of the choices. */
@Composable
private fun ChoiceRow(group: FilterGroup, onPick: (String, String) -> Unit) {
    val colors = AstridTheme.colors
    var open by remember { mutableStateOf(false) }
    val current = group.picks.firstOrNull { it.isSelected } ?: group.picks.firstOrNull()
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }.heightIn(min = 48.dp).padding(horizontal = Spacing.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(coreLabel(group.titleKey), style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(current?.let { coreLabel(it.titleKey) } ?: "", style = TextStyles.body, color = colors.textSecondary)
            Icon(Icons.Filled.UnfoldMore, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            group.picks.forEach { pick ->
                DropdownMenuItem(
                    text = { Text(coreLabel(pick.titleKey)) },
                    trailingIcon = { if (pick.isSelected) Icon(Icons.Filled.Check, null, tint = colors.accent) },
                    onClick = { open = false; onPick(pick.field, pick.value) },
                )
            }
        }
    }
}

/** iOS's MyTasksFilterSheet: "Sort & Filters", the panel, and Done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksFilterSheet(vm: FiltersViewModel, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bgTertiary,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s16), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.my_tasks_filters_title), style = TextStyles.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_done)) }
        }
        FiltersPanel(vm, Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s16).padding(bottom = Spacing.s24))
    }
}

/** iOS's list switches under the filters: "Show subtasks" (with its footer) and "Saved Filter". */
@Composable
fun ListSwitches(state: FiltersState, vm: FiltersViewModel) {
    val colors = AstridTheme.colors
    Card {
        SwitchRow(stringResource(R.string.lists_show_subtasks), state.showSubtasks, vm::setShowSubtasks)
        HorizontalDivider(Modifier.padding(start = Spacing.s16), color = colors.divider)
        SwitchRow(stringResource(R.string.lists_saved_filter), state.isSavedFilter, vm::setSavedFilter)
    }
    Text(
        stringResource(if (state.isSavedFilter) R.string.lists_saved_filter_description else R.string.lists_show_subtasks_footer),
        style = TextStyles.caption, color = colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.s16),
    )
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleableRow(on, onChange).heightIn(min = 48.dp).padding(horizontal = Spacing.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TextStyles.body, color = AstridTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = on, onCheckedChange = null)
    }
}

private fun Modifier.toggleableRow(on: Boolean, onChange: (Boolean) -> Unit) =
    this.toggleable(value = on, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
