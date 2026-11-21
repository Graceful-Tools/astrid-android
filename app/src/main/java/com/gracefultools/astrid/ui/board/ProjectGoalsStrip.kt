package com.gracefultools.astrid.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.text.font.FontWeight
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.Goal
import com.gracefultools.astrid.model.ProjectGoals
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS ProjectGoalsStrip (ADEV-184; web `components/projects/ProjectGoalsStrip.tsx`): a project
 * board's folded "Goals: N · Guardrails: N" that opens to the lists — a goal with its measure,
 * "Target: …" and "Now: …", a guardrail with an "Enforced" mark. Nothing when there is neither.
 */
@Composable
fun ProjectGoalsStrip(goals: ProjectGoals?, modifier: Modifier = Modifier) {
    if (goals == null || goals.isEmpty) return
    val colors = AstridTheme.colors
    var expanded by rememberSaveable(goals.projectId) { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.s16, vertical = Spacing.s8)) {
        // iOS's DisclosureGroup label: the counts in secondary caption, a chevron that turns down when open.
        Row(
            Modifier.fillMaxWidth().testTag("projectGoals.strip")
                .clickable(role = Role.Button) { expanded = !expanded }
                // TalkBack hears it as something that opens and closes, as iOS's DisclosureGroup is read.
                .semantics { if (expanded) collapse { expanded = false; true } else expand { expanded = true; true } },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Text(stringResource(R.string.project_goals_counts, goals.goals.size, goals.guardrails.size), style = TextStyles.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = colors.accentInk, modifier = Modifier.size(Spacing.s16),
            )
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(top = Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            if (goals.goals.isNotEmpty()) {
                Heading(stringResource(R.string.project_goals_goals_heading))
                goals.goals.forEach { goal ->
                    Column(Modifier.testTag("projectGoals.goal"), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        Text(goal.statement, style = TextStyles.caption, color = colors.textPrimary)
                        goalDetail(goal)?.let { Text(it, style = TextStyles.caption, color = colors.textSecondary) }
                    }
                }
            }
            if (goals.guardrails.isNotEmpty()) {
                Heading(stringResource(R.string.project_goals_guardrails_heading))
                goals.guardrails.forEach { guardrail ->
                    Row(Modifier.testTag("projectGoals.guardrail"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                        Text(guardrail.statement, style = TextStyles.caption, color = colors.textPrimary, modifier = Modifier.weight(1f, fill = false))
                        if (guardrail.isEnforced) {
                            Text(
                                stringResource(R.string.project_goals_kind_enforced), style = TextStyles.caption2, color = colors.textSecondary, maxLines = 1,
                                modifier = Modifier.clip(CircleShape)
                                    .background(if (colors.isDark) colors.bgTertiary else colors.neutral.copy(alpha = 0.1f))
                                    .padding(horizontal = Spacing.s8, vertical = Spacing.s2),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = TextStyles.caption, fontWeight = FontWeight.Bold, color = AstridTheme.colors.textSecondary)

/** A goal's measure, target and current value on one line, e.g. "IRR · Target: 8% · Now: 6%" (iOS `detail`). */
@Composable
private fun goalDetail(goal: Goal): String? {
    val parts = listOfNotNull(
        goal.measure?.takeIf { it.isNotBlank() },
        goal.target?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.project_goals_target_value, it) },
        goal.current?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.project_goals_current_value, it) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
