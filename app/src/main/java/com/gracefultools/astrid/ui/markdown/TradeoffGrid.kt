package com.gracefultools.astrid.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.astrid.design.AstridTokens
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TradeoffPalette
import com.gracefultools.astrid.ui.theme.TypeSize
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The core's `tradeoffGrid` block (astrid-core af42ba53, AWTD-1626; AATD-103), drawn as iOS
 * `TradeOffGridView` draws it: options numbered 1., 2., … and criteria lettered A, B, C by position
 * (neither is in the block), each rated cell on its colour, a colour key from the block's `key`,
 * and "Full screen" above. The core decides which tables are grids and what each cell rates.
 */
@Composable
internal fun TradeoffGrid(block: JsonObject, onReference: (String, String) -> Unit) {
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    val colors = AstridTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        Row(
            Modifier.clickable { fullScreen = true }.padding(vertical = Spacing.s2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.OpenInFull, null, tint = colors.textSecondary, modifier = Modifier.size(AstridTokens.Markdown.tradeoffSwatch))
            Text(stringResource(R.string.tradeoff_full_screen), fontSize = TypeSize.footnote, color = colors.textSecondary)
        }
        BoxWithConstraints {
            val available = maxWidth
            Column(Modifier.horizontalScroll(rememberScrollState())) { Table(block, onReference, available, wide = false) }
        }
        Key(block)
    }
    if (fullScreen) FullScreen(block, onReference) { fullScreen = false }
}

/** A grid on a screen of its own (iOS `TradeOffGridFullScreenView`): wider cells, scrolls both ways, closed with ✕. */
@Composable
private fun FullScreen(block: JsonObject, onReference: (String, String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("tradeoff:fullScreen"), color = AstridTheme.colors.bgPrimary) {
            Column {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, stringResource(R.string.actions_close), tint = AstridTheme.colors.textPrimary)
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val available = maxWidth
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(Spacing.s16),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
                    ) {
                        Table(block, onReference, available, wide = true)
                        Key(block)
                    }
                }
            }
        }
    }
}

private fun JsonObject.inlinesAt(name: String): List<List<JsonObject>> =
    (this[name] as? JsonArray)?.map { (it as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList() } ?: emptyList()

private fun plainText(inlines: List<JsonObject>) = inlines.joinToString("") {
    when (it.str("kind")) {
        "text" -> it.str("text").orEmpty()
        "reference" -> it.str("label").orEmpty()
        "lineBreak" -> " "
        else -> ""
    }
}

/** A rating's name by its core wire name (`veryPoor` … `veryGood`); also the composer's lights (ADEV-241). */
@Composable
internal fun ratingLabel(rating: String?): String = stringResource(
    when (rating) {
        "veryPoor" -> R.string.tradeoff_very_poor
        "poor" -> R.string.tradeoff_poor
        "mixed" -> R.string.tradeoff_mixed
        "good" -> R.string.tradeoff_good
        "veryGood" -> R.string.tradeoff_very_good
        else -> R.string.tradeoff_unrated
    },
)

/** Column letters as the core's `column_letter`: A … Z, then AA, AB, … */
internal fun columnLetter(index: Int): String {
    var n = index
    var out = ""
    do {
        out = ('A' + n % 26) + out
        n = n / 26 - 1
    } while (n >= 0)
    return out
}

@Composable
private fun Table(block: JsonObject, onReference: (String, String) -> Unit, available: Dp, wide: Boolean) {
    val colors = AstridTheme.colors
    val metrics = AstridTokens.Markdown
    val header = block.inlinesAt("header")
    val rows = block.arr("rows")
    // The header's; a row written with fewer cells is padded with empty ones (iOS).
    val columns = maxOf(header.size, 1 + (rows.maxOfOrNull { it.arr("cells").size } ?: 0))
    val criteria = (columns - 1).coerceAtLeast(1)
    // The option column runs up to a third of the width; a criterion wraps between the cell
    // tokens before the grid grows sideways and scrolls (iOS TradeOffGridMetrics, web --tradeoff-cell-*).
    val optionWidth = if (wide) metrics.tradeoffCellMax else minOf(available / 3, metrics.tradeoffCellMax)
    val fit = (available - optionWidth - metrics.tradeoffGap * criteria) / criteria
    val cellWidth = if (wide) metrics.tradeoffCellMax else fit.coerceIn(metrics.tradeoffCellMin, metrics.tradeoffCellMax)
    val shape = RoundedCornerShape(metrics.tradeoffCellRadius)
    val small = TypeSize.footnote

    Column(verticalArrangement = Arrangement.spacedBy(metrics.tradeoffGap)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(metrics.tradeoffGap)) {
            for (column in 0 until columns) {
                val cell = header.getOrElse(column) { emptyList() }
                val title = if (column == 0) {
                    inlineText(cell, onReference)
                } else {
                    val name = inlineText(cell, onReference)
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.textSecondary)) { append(columnLetter(column - 1) + "  ") }
                        append(name)
                    }
                }
                Text(
                    title, fontSize = small, fontWeight = FontWeight.SemiBold, color = colors.textPrimary,
                    textAlign = if (column == 0) TextAlign.Start else TextAlign.Center,
                    modifier = Modifier.width(if (column == 0) optionWidth else cellWidth).fillMaxHeight()
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s4),
                )
            }
        }
        rows.forEachIndexed { index, row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(metrics.tradeoffGap)) {
                val name = inlineText(row.arr("name"), onReference)
                Text(
                    buildAnnotatedString { append("${index + 1}. "); append(name) },
                    fontSize = small, fontWeight = FontWeight.SemiBold, color = colors.textPrimary,
                    modifier = Modifier.width(optionWidth).fillMaxHeight().padding(horizontal = Spacing.s4, vertical = Spacing.s4),
                )
                val cells = row.arr("cells")
                for (column in 0 until columns - 1) {
                    val cell = cells.getOrNull(column)
                    val rating = cell?.str("rating")
                    val inlines = cell?.arr("inlines") ?: emptyList()
                    val fill = TradeoffPalette.fill(rating)
                    val ink = TradeoffPalette.text(rating) ?: colors.textPrimary
                    val spoken = "${ratingLabel(rating)}. ${plainText(inlines)}".trim()
                    Text(
                        inlineText(inlines, onReference, onFill = if (fill != null) ink else null),
                        fontSize = small, color = ink, textAlign = TextAlign.Center,
                        modifier = Modifier.width(cellWidth).fillMaxHeight()
                            .background(fill ?: colors.textPrimary.copy(alpha = UNRATED_ALPHA), shape)
                            .semantics(mergeDescendants = true) { contentDescription = spoken }
                            .padding(horizontal = Spacing.s4, vertical = Spacing.s4),
                    )
                }
            }
        }
    }
}

/** The colour key: the block's `key`, the ratings this grid uses, worst to best. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Key(block: JsonObject) {
    val colors = AstridTheme.colors
    val metrics = AstridTokens.Markdown
    val key = (block["key"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?.filter { TradeoffPalette.fill(it) != null } ?: emptyList()
    if (key.isEmpty()) return
    FlowRow(
        Modifier.testTag("tradeoff:key").semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        for (rating in key) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4), verticalAlignment = Alignment.CenterVertically) {
                val swatch = remember(rating) { TradeoffPalette.fill(rating)!! }
                Box(
                    Modifier.size(metrics.tradeoffSwatch).background(swatch, RoundedCornerShape(Spacing.s2)),
                )
                Text(ratingLabel(rating), fontSize = TypeSize.footnote, color = colors.textSecondary)
            }
        }
    }
}

/** An unrated cell is the text colour at 5%, as iOS draws it. */
private const val UNRATED_ALPHA = 0.05f
