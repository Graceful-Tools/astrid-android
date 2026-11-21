package com.gracefultools.astrid.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.Goal
import com.gracefultools.astrid.model.Guardrail
import com.gracefultools.astrid.model.ProjectGoals
import com.gracefultools.astrid.ui.agents.SegmentedControl
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import java.util.concurrent.atomic.AtomicLong

private val nextKey = AtomicLong()

/** A goal row being edited. [key] keeps a row's identity across edits and removals; [id] is the server's, null for a new row. */
data class GoalRow(
    val id: String? = null,
    val statement: String = "",
    val measure: String = "",
    val target: String = "",
    val current: String = "",
    val key: Long = nextKey.incrementAndGet(),
)

data class GuardrailRow(
    val id: String? = null,
    val statement: String = "",
    val enforced: Boolean = false,
    val key: Long = nextKey.incrementAndGet(),
)

/**
 * What the goals editor holds while List Settings is open (ADEV-184; iOS ProjectGoalsDraft). Rows
 * compare by content, so an edit undone reads as unchanged.
 */
data class GoalsDraft(val goals: List<GoalRow> = emptyList(), val guardrails: List<GuardrailRow> = emptyList()) {
    override fun equals(other: Any?): Boolean = other is GoalsDraft &&
        goals.map { it.copy(key = 0) } == other.goals.map { it.copy(key = 0) } &&
        guardrails.map { it.copy(key = 0) } == other.guardrails.map { it.copy(key = 0) }

    override fun hashCode(): Int = goals.size * 31 + guardrails.size

    /** `setProjectGoals.goals`: a new row has no id, and an empty optional field is left out (as iOS sends it). */
    fun goalsWire(): List<Map<String, String>> = goals.map { row ->
        buildMap {
            row.id?.let { put("id", it) }
            put("statement", row.statement)
            row.measure.takeIf { it.isNotBlank() }?.let { put("measure", it) }
            row.target.takeIf { it.isNotBlank() }?.let { put("target", it) }
            row.current.takeIf { it.isNotBlank() }?.let { put("current", it) }
        }
    }

    fun guardrailsWire(): List<Map<String, String>> = guardrails.map { row ->
        buildMap {
            row.id?.let { put("id", it) }
            put("statement", row.statement)
            put("kind", if (row.enforced) "enforced" else "stated")
        }
    }

    companion object {
        fun of(goals: ProjectGoals) = GoalsDraft(
            goals = goals.goals.map { g: Goal -> GoalRow(g.id, g.statement, g.measure.orEmpty(), g.target.orEmpty(), g.current.orEmpty()) },
            guardrails = goals.guardrails.map { r: Guardrail -> GuardrailRow(r.id, r.statement, r.isEnforced) },
        )
    }
}

/**
 * iOS ProjectGoalsEditor (Edit Project…'s Goals and Guardrails sections). Android has no Edit
 * Project sheet, so it sits in List Settings → Admin beside the project board, with its own Save
 * (docs/PARITY.md). The whole value is saved at once; the server trims and drops blank rows.
 */
@Composable
internal fun ProjectGoalsEditor(
    draft: GoalsDraft,
    changed: Boolean,
    saving: Boolean,
    saveFailed: Boolean,
    onChange: (GoalsDraft) -> Unit,
    onSave: () -> Unit,
) {
    val colors = AstridTheme.colors
    FormSection(stringResource(R.string.project_goals_goals_heading), footer = stringResource(R.string.project_goals_intro)) {
        draft.goals.forEach { row ->
            fun set(new: GoalRow) = onChange(draft.copy(goals = draft.goals.map { if (it.key == row.key) new else it }))
            Column(Modifier.fillMaxWidth().testTag("projectGoals.goal").padding(start = Spacing.s16, top = Spacing.s8, bottom = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        FormTextField(row.statement, stringResource(R.string.project_goals_goal_placeholder), singleLine = false) { set(row.copy(statement = it)) }
                    }
                    RemoveButton { onChange(draft.copy(goals = draft.goals.filterNot { it.key == row.key })) }
                }
                Row(Modifier.padding(end = Spacing.s16), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                    Box(Modifier.weight(1f)) { FormTextField(row.measure, stringResource(R.string.project_goals_measure_placeholder), singleLine = true) { set(row.copy(measure = it)) } }
                    Box(Modifier.weight(1f)) { FormTextField(row.target, stringResource(R.string.project_goals_target_placeholder), singleLine = true) { set(row.copy(target = it)) } }
                    Box(Modifier.weight(1f)) { FormTextField(row.current, stringResource(R.string.project_goals_current_placeholder), singleLine = true) { set(row.copy(current = it)) } }
                }
            }
            FormDivider()
        }
        FormButtonRow(Icons.Outlined.Add, stringResource(R.string.project_goals_add_goal), colors.accentInk) {
            onChange(draft.copy(goals = draft.goals + GoalRow()))
        }
    }

    FormSection(stringResource(R.string.project_goals_guardrails_heading), footer = stringResource(R.string.project_goals_kind_hint)) {
        draft.guardrails.forEach { row ->
            fun set(new: GuardrailRow) = onChange(draft.copy(guardrails = draft.guardrails.map { if (it.key == row.key) new else it }))
            Column(Modifier.fillMaxWidth().testTag("projectGoals.guardrail").padding(start = Spacing.s16, top = Spacing.s8, bottom = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        FormTextField(row.statement, stringResource(R.string.project_goals_guardrail_placeholder), singleLine = false) { set(row.copy(statement = it)) }
                    }
                    RemoveButton { onChange(draft.copy(guardrails = draft.guardrails.filterNot { it.key == row.key })) }
                }
                Box(Modifier.padding(end = Spacing.s16)) {
                    SegmentedControl(
                        listOf(stringResource(R.string.project_goals_kind_stated), stringResource(R.string.project_goals_kind_enforced)),
                        selected = if (row.enforced) 1 else 0,
                    ) { set(row.copy(enforced = it == 1)) }
                }
            }
            FormDivider()
        }
        FormButtonRow(Icons.Outlined.Add, stringResource(R.string.project_goals_add_guardrail), colors.accentInk) {
            onChange(draft.copy(guardrails = draft.guardrails + GuardrailRow()))
        }
    }

    if (changed || saving || saveFailed) {
        FormSection {
            FormButtonRow(
                Icons.Outlined.Check, stringResource(R.string.actions_save), colors.accentInk, enabled = changed && !saving,
                trailing = if (saving) ({ CircularProgressIndicator(Modifier.size(Spacing.s16), color = colors.accent, strokeWidth = Spacing.s2) }) else null,
                onClick = onSave,
            )
        }
        if (saveFailed) {
            Text(
                stringResource(R.string.project_goals_save_failed), style = TextStyles.footnote, color = colors.errorInk,
                modifier = Modifier.padding(horizontal = Spacing.s32),
            )
        }
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.project_goals_remove), tint = AstridTheme.colors.errorInk, modifier = Modifier.size(Spacing.s20))
    }
}
