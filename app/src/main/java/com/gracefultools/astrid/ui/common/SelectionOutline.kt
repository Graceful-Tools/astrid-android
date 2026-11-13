package com.gracefultools.astrid.ui.common

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.astrid.design.AstridTokens
import com.gracefultools.astrid.ui.theme.AstridTheme

/** What the selection outline draws: its width and its colour, opacity included. */
data class SelectionOutlineStyle(val width: Dp, val color: Color)

/** The selection outline an element wears, exposed to tests (AATD-85, DS5). */
val SelectionOutline = SemanticsPropertyKey<SelectionOutlineStyle>("SelectionOutline")

/**
 * The selection outline (register DS5, Jon 2026-11-10): `border-focus` at
 * `state.selection-outline-opacity`, `size.selection-outline` wide (Android is a touch layout),
 * drawn inside the edge so selecting moves nothing. No fill change, no glow. The selected row and
 * the details panel both wear it, so the two read as one object (DS7).
 */
@Composable
fun selectionOutlineStyle(): SelectionOutlineStyle = SelectionOutlineStyle(
    width = AstridTokens.Size.Comfortable.selectionOutline,
    color = AstridTheme.colors.borderFocus.copy(alpha = AstridTokens.State.selectionOutlineOpacity),
)

/** Draws [style] inside the edge of [shape]. */
fun Modifier.selectionOutline(style: SelectionOutlineStyle, shape: Shape): Modifier = this
    .border(style.width, style.color, shape)
    .semantics { this[SelectionOutline] = style }

/**
 * Where the selected row sits on screen, so the details panel can point its arrow at it (DS7).
 * The row reports its vertical centre in root coordinates while it is composed and selected, and
 * clears it when it leaves — scrolled out of a lazy list, or deselected — so the arrow hides.
 */
class SelectedRowAnchor {
    private var owner: String? = null
    var centerY by mutableStateOf<Float?>(null)
        private set

    fun report(id: String, y: Float) { owner = id; centerY = y }
    fun clear(id: String) { if (owner == id) { owner = null; centerY = null } }
}

/** Provided where the list and the details share the screen; null on a phone's single pane. */
val LocalSelectedRowAnchor = staticCompositionLocalOf<SelectedRowAnchor?> { null }

/** On the selected row: keeps [LocalSelectedRowAnchor] at this element's vertical centre. */
@Composable
fun Modifier.reportsSelectedRow(id: String, isSelected: Boolean): Modifier {
    val anchor = LocalSelectedRowAnchor.current ?: return this
    if (!isSelected) return this
    DisposableEffect(anchor, id) { onDispose { anchor.clear(id) } }
    return onGloballyPositioned { anchor.report(id, it.positionInRoot().y + it.size.height / 2f) }
}
