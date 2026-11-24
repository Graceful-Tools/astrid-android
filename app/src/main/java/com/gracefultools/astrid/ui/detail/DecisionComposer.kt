package com.gracefultools.astrid.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.astrid.design.AstridTokens
import com.gracefultools.astrid.R
import com.gracefultools.astrid.core.DecisionDraft
import com.gracefultools.astrid.core.DecisionGrid
import com.gracefultools.astrid.ui.common.AstridTextButton
import com.gracefultools.astrid.ui.lists.FormDivider
import com.gracefultools.astrid.ui.lists.FormRow
import com.gracefultools.astrid.ui.lists.FormSection
import com.gracefultools.astrid.ui.lists.FormTextField
import com.gracefultools.astrid.ui.lists.SegmentedTabs
import com.gracefultools.astrid.ui.markdown.MarkdownView
import com.gracefultools.astrid.ui.markdown.ratingLabel
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.IconSize
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import com.gracefultools.astrid.ui.theme.TradeoffPalette

/**
 * Write a decision from a task's details (ADEV-241; iOS `DecisionComposerView`, ADEV-189): a
 * question, its options, and, if wanted, criteria rated per option in traffic-light colours.
 * The draft and its markdown are [DecisionDraft]; this screen only edits it and hands the result
 * back with where it goes — the caller posts the comment or saves the description.
 */
@Composable
fun DecisionComposer(onDone: (markdown: String, toDescription: Boolean) -> Unit, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    var draft by rememberSaveable { mutableStateOf(DecisionDraft()) }
    var toDescription by rememberSaveable { mutableStateOf(false) }
    val problem = draft.problem
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onDismiss)
        Column(Modifier.fillMaxSize().background(colors.bgSecondary).safeDrawingPadding().imePadding().testTag("decisionComposer")) {
            Box(Modifier.fillMaxWidth().background(colors.bgPrimary).padding(horizontal = Spacing.s8)) {
                AstridTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(stringResource(R.string.common_cancel), style = TextStyles.body, color = colors.accentInk)
                }
                Text(
                    stringResource(R.string.decision_composer_title), style = TextStyles.headline, color = colors.textPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
                AstridTextButton(
                    onClick = { onDone(draft.markdown, toDescription); onDismiss() },
                    enabled = problem == null,
                    modifier = Modifier.align(Alignment.CenterEnd).testTag("decisionComposer.done"),
                ) {
                    Text(
                        stringResource(if (toDescription) R.string.decision_composer_add else R.string.decision_composer_post),
                        style = TextStyles.body, fontWeight = FontWeight.SemiBold,
                        color = if (problem == null) colors.accentInk else colors.textMuted,
                    )
                }
            }
            HorizontalDivider(color = colors.divider)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Spacing.s16),
                verticalArrangement = Arrangement.spacedBy(Spacing.s24),
            ) {
                FormSection(title = stringResource(R.string.decision_composer_question)) {
                    FormRow {
                        Box(Modifier.weight(1f).testTag("decisionComposer.question")) {
                            FormTextField(draft.question, stringResource(R.string.decision_composer_question_placeholder), singleLine = false) {
                                draft = draft.copy(question = it)
                            }
                        }
                    }
                }
                OptionsSection(draft, problem) { draft = it }
                CriteriaSection(draft) { draft = it }
                if (draft.criteria.any { it.name.isNotEmpty() }) {
                    draft.options.filter { it.text.isNotEmpty() }.forEach { option ->
                        RatingsSection(draft, option, problem) { draft = it }
                    }
                }
                FormSection(
                    title = stringResource(R.string.decision_composer_post_to),
                    footer = stringResource(if (toDescription) R.string.decision_composer_description_hint else R.string.decision_composer_comment_hint),
                ) {
                    SegmentedTabs(
                        listOf(null to stringResource(R.string.decision_composer_as_comment), null to stringResource(R.string.decision_composer_in_description)),
                        selected = if (toDescription) 1 else 0,
                        onSelect = { toDescription = it == 1 },
                        modifier = Modifier.padding(Spacing.s8).testTag("decisionComposer.postTo"),
                    )
                }
                if (problem == null) {
                    val blocks = remember(draft.markdown) { rendered(draft.markdown) }
                    FormSection(title = stringResource(R.string.decision_composer_preview)) {
                        Box(Modifier.fillMaxWidth().padding(Spacing.s16).testTag("decisionComposer.preview")) {
                            if (blocks != null) MarkdownView(blocks, onReference = { _, _ -> })
                            else Text(draft.markdown, style = TextStyles.body, color = colors.textPrimary)
                        }
                    }
                }
                Spacer(Modifier.padding(bottom = Spacing.s24))
            }
        }
    }
}

@Composable
private fun OptionsSection(draft: DecisionDraft, problem: DecisionDraft.Problem?, onChange: (DecisionDraft) -> Unit) {
    val colors = AstridTheme.colors
    FormSection(
        title = stringResource(R.string.decision_composer_options),
        footer = stringResource(
            if (problem == DecisionDraft.Problem.TooFewOptions) R.string.decision_composer_needs_two_options else R.string.decision_composer_recommend_hint,
        ),
    ) {
        val recommendedLabel = stringResource(R.string.decision_recommended)
        val removeLabel = stringResource(R.string.actions_delete)
        draft.options.forEachIndexed { index, option ->
            if (index > 0) FormDivider()
            FormRow {
                Box(Modifier.weight(1f).testTag("decisionComposer.option.$index")) {
                    FormTextField(option.text, stringResource(R.string.decision_composer_option_placeholder), singleLine = true) { text ->
                        onChange(draft.copy(options = draft.options.map { if (it.id == option.id) it.copy(text = text) else it }))
                    }
                }
                val isRecommended = draft.recommended == option.id
                IconButton(
                    onClick = { onChange(draft.copy(recommended = if (isRecommended) null else option.id)) },
                    modifier = Modifier.semantics { selected = isRecommended }.testTag("decisionComposer.star.$index"),
                ) {
                    Icon(if (isRecommended) Icons.Filled.Star else Icons.Outlined.StarOutline, recommendedLabel, tint = colors.accentInk)
                }
                // iOS's swipe to delete, as a button: never below two options.
                if (draft.options.size > 2) IconButton(onClick = {
                    onChange(draft.copy(options = draft.options.filterNot { it.id == option.id }, recommended = draft.recommended.takeIf { it != option.id }))
                }) { Icon(Icons.Filled.Close, removeLabel, tint = colors.textMuted, modifier = Modifier.size(IconSize.lg)) }
            }
        }
        FormDivider()
        AddRow(stringResource(R.string.decision_composer_add_option), "decisionComposer.addOption") {
            onChange(draft.copy(options = draft.options + DecisionDraft.Option()))
        }
    }
}

@Composable
private fun CriteriaSection(draft: DecisionDraft, onChange: (DecisionDraft) -> Unit) {
    val colors = AstridTheme.colors
    val removeLabel = stringResource(R.string.actions_delete)
    FormSection(title = stringResource(R.string.decision_composer_criteria), footer = stringResource(R.string.decision_composer_criteria_hint)) {
        draft.criteria.forEachIndexed { index, criterion ->
            fun edit(change: (DecisionDraft.Criterion) -> DecisionDraft.Criterion) =
                onChange(draft.copy(criteria = draft.criteria.map { if (it.id == criterion.id) change(it) else it }))
            if (index > 0) FormDivider()
            FormRow {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    Box(Modifier.testTag("decisionComposer.criterion.$index")) {
                        FormTextField(criterion.name, stringResource(R.string.decision_composer_criterion_placeholder), singleLine = true) { name ->
                            edit { it.copy(name = name) }
                        }
                    }
                    if (criterion.name.isNotEmpty()) Box(Modifier.testTag("decisionComposer.criterionDetail.$index")) {
                        FormTextField(criterion.detail, stringResource(R.string.decision_composer_criterion_detail_placeholder), singleLine = false) { detail ->
                            edit { it.copy(detail = detail) }
                        }
                    }
                }
                IconButton(onClick = { onChange(draft.copy(criteria = draft.criteria.filterNot { it.id == criterion.id })) }) {
                    Icon(Icons.Filled.Close, removeLabel, tint = colors.textMuted, modifier = Modifier.size(IconSize.lg))
                }
            }
        }
        if (draft.criteria.size < DecisionDraft.MAX_CRITERIA) {
            if (draft.criteria.isNotEmpty()) FormDivider()
            AddRow(stringResource(R.string.decision_composer_add_criterion), "decisionComposer.addCriterion") {
                onChange(draft.copy(criteria = draft.criteria + DecisionDraft.Criterion()))
            }
        }
    }
}

@Composable
private fun RatingsSection(draft: DecisionDraft, option: DecisionDraft.Option, problem: DecisionDraft.Problem?, onChange: (DecisionDraft) -> Unit) {
    val colors = AstridTheme.colors
    val optionIndex = draft.options.indexOfFirst { it.id == option.id }
    FormSection(
        title = option.text,
        footer = if (problem == DecisionDraft.Problem.ReasonWithoutRating) stringResource(R.string.decision_composer_reason_needs_colour) else null,
    ) {
        val named = draft.criteria.filter { it.name.isNotEmpty() }
        named.forEachIndexed { index, criterion ->
            val cell = draft.cell(option.id, criterion.id)
            if (index > 0) FormDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s16, vertical = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    Text(criterion.name, style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    DecisionGrid.ratingTokens().forEach { rating ->
                        TrafficLight(rating, selected = cell.rating == rating.token, tag = "decisionComposer.light.$optionIndex.$index.${rating.rank}") {
                            val token = if (cell.rating == rating.token) null else rating.token
                            onChange(draft.set(cell.copy(rating = token), option.id, criterion.id))
                        }
                    }
                }
                Box(Modifier.testTag("decisionComposer.note.$optionIndex.$index")) {
                    FormTextField(cell.text, stringResource(R.string.decision_composer_reason_placeholder), singleLine = true) { text ->
                        onChange(draft.set(draft.cell(option.id, criterion.id).copy(text = text), option.id, criterion.id))
                    }
                }
            }
        }
    }
}

/** One traffic light: the grid's own colour for the token, ringed when picked; a second tap clears it (iOS `lightButton`). */
@Composable
private fun TrafficLight(rating: DecisionGrid.Rating, selected: Boolean, tag: String, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    val label = ratingLabel(rating.labelKey)
    Box(
        Modifier.size(Spacing.s24).alpha(if (selected) 1f else AstridTokens.State.disabledOpacity).clip(CircleShape)
            .background(TradeoffPalette.fill(rating.labelKey) ?: colors.textSecondary, CircleShape)
            .then(if (selected) Modifier.border(AstridTokens.Size.Comfortable.selectionOutline, colors.textPrimary, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected }
            .testTag(tag),
    )
}

@Composable
private fun AddRow(label: String, tag: String, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    FormRow(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(Icons.Filled.Add, null, tint = colors.accentInk, modifier = Modifier.size(IconSize.xl))
        Text(label, style = TextStyles.body, color = colors.accentInk, modifier = Modifier.weight(1f))
    }
}
