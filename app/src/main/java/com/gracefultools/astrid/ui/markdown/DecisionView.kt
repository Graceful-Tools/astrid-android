package com.gracefultools.astrid.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.astrid.design.AstridTokens
import com.gracefultools.astrid.Brand
import com.gracefultools.astrid.R
import com.gracefultools.astrid.core.CoreRules
import com.gracefultools.astrid.core.DecisionResponse
import com.gracefultools.astrid.core.Decisions
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.BorderWidth
import com.gracefultools.astrid.ui.theme.Gap
import com.gracefultools.astrid.ui.theme.IconSize
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TypeSize
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Markdown that may hold a decision (ADEV-257; iOS `MarkdownView` + `DecisionDisplay`). The core says
 * whether `source` is one. A decision with a grid of the same options draws as its question and the
 * grid alone, each row the option to choose (ADEV-229); otherwise its options are cards. `response`
 * is the thread's: null — a description, the composer's preview — shows the options without taking
 * a tap. Anything that is not a decision draws as `blocks`, unchanged.
 */
@Composable
fun DecisionMarkdown(source: String, blocks: List<JsonObject>, response: DecisionResponse?, onReference: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val decision = remember(source) { Decisions.parse(source) }
    if (decision == null) {
        MarkdownView(blocks, modifier, onReference)
        return
    }
    val before = remember(decision) { render(decision.before) }
    val prompt = remember(decision) { render(decision.prompt).firstOrNull()?.arr("inlines").orEmpty() }
    val after = remember(decision) { render(decision.after) }
    val gridAt = remember(decision) { if (Decisions.choiceGrid(decision) != null) after.indexOfFirst { it.str("kind") == "tradeoffGrid" } else -1 }
    val state = DecisionResponse.state(response, decision)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        if (before.isNotEmpty()) MarkdownView(before, onReference = onReference)
        if (prompt.isNotEmpty()) {
            Text(inlineText(prompt, onReference), fontSize = TypeSize.body, fontWeight = FontWeight.SemiBold, color = AstridTheme.colors.textPrimary)
        }
        if (gridAt >= 0) {
            if (gridAt > 0) MarkdownView(after.subList(0, gridAt), onReference = onReference)
            TradeoffGrid(after[gridAt], onReference, GridChoice(decision, state) { option -> response?.tap(option, decision) })
            if (gridAt + 1 < after.size) MarkdownView(after.subList(gridAt + 1, after.size), onReference = onReference)
        } else {
            decision.options.forEach { option -> DecisionCard(option, state) { response?.tap(option, decision) } }
            if (after.isNotEmpty()) MarkdownView(after, onReference = onReference)
        }
    }
}

/** One option as a card you tap (iOS `DecisionCardsView`): ringed and ticked once chosen, the others dimmed. */
@Composable
private fun DecisionCard(option: Decisions.Option, state: DecisionResponse.State, onTap: () -> Unit) {
    val colors = AstridTheme.colors
    val chosen = state == DecisionResponse.State.Chosen(option.number)
    val dimmed = !chosen && state != DecisionResponse.State.Open && state != DecisionResponse.State.ReadOnly
    val shape = RoundedCornerShape(Radius.medium)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(colors.textPrimary.copy(alpha = CARD_WASH_ALPHA))
            .border(if (chosen) BorderWidth.thick else BorderWidth.thin, if (chosen) colors.accent else colors.textPrimary.copy(alpha = CARD_EDGE_ALPHA), shape)
            .then(if (state == DecisionResponse.State.Open) Modifier.clickable(role = Role.Button, onClick = onTap) else Modifier)
            .alpha(if (dimmed) AstridTokens.State.disabledOpacity else 1f)
            .testTag("decision.option.${option.number}")
            .padding(horizontal = Spacing.s12, vertical = Gap.s10),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DecisionNumberBadge(option.number, chosen)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Text(option.text, fontSize = TypeSize.body, color = colors.textPrimary)
            if (option.recommended) {
                Text(stringResource(R.string.decision_recommended), fontSize = TypeSize.caption1, fontWeight = FontWeight.SemiBold, color = colors.accentInk)
            }
        }
        if (chosen) Icon(Icons.Filled.CheckCircle, null, tint = colors.accentInk, modifier = Modifier.size(IconSize.xl))
    }
}

private fun render(markdown: String): List<JsonObject> = if (markdown.isBlank()) emptyList() else runCatching {
    (CoreRules.ask(command("renderMarkdown", "text" to markdown, "origin" to Brand.baseUrl)) as JsonArray).map { it as JsonObject }
}.getOrDefault(emptyList())

/** A card's fill and edge: the text colour at 5% and 15%, as iOS draws them. */
private const val CARD_WASH_ALPHA = 0.05f
private const val CARD_EDGE_ALPHA = 0.15f
