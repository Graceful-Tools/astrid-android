package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.ui.theme.TouchLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import com.astrid.design.AstridTokens
import com.gracefultools.astrid.ui.common.SelectedRowAnchor
import com.gracefultools.astrid.ui.common.selectionOutline
import com.gracefultools.astrid.ui.common.selectionOutlineStyle
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import kotlin.math.roundToInt

/** How far the arrow reaches out of the panel, and half its height: web's 20 square turned 45°. */
private val ArrowDepth = Spacing.s12

/**
 * Task details opening out of the selected row (register DS7, Jon 2026-11-10), wherever the list
 * and the details share the screen. The panel wears the row's selection outline on the
 * `detail-panel-radius` corners, so the two read as one object, and an arrow on the edge facing
 * the row points at it and follows it over `motion.fast`. The side and the arrow's place come from
 * the core's callout placement (AATD-114), which keeps the arrow clear of the corners. When the
 * row is scrolled away the arrow hides; the panel stays. Entering and leaving over `motion.panel`
 * belongs to the host.
 */
@Composable
fun DetailsPanel(anchor: SelectedRowAnchor, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    val outline = selectionOutlineStyle()
    val radius = TouchLayout.detailPanelRadius
    val shape = RoundedCornerShape(radius)
    val density = LocalDensity.current
    var pane by remember { mutableStateOf<Rect?>(null) }
    Box(modifier.background(colors.bgPrimary).onGloballyPositioned { pane = it.boundsInRoot() }) {
        // The core places the callout (AATD-114): the row is the anchor, this pane the viewport it
        // must fit in, and the callout the outlined box as drawn (the pane's width, arrow included,
        // less its vertical inset). All in dp, as the rule wants.
        val row = anchor.bounds
        val frame = pane
        val placement = if (row != null && frame != null && !frame.isEmpty) {
            val d = density.density
            val calloutHeight = frame.height / d - (Spacing.s8 * 2).value
            remember(row, frame, d) { placeCallout(row.inDp(d), frame.inDp(d), frame.width / d, calloutHeight) }
        } else null
        val opensLeft = placement?.side == CalloutSide.LEFT
        Box(
            Modifier.fillMaxSize()
                .padding(
                    start = if (opensLeft) Spacing.s8 else ArrowDepth, top = Spacing.s8,
                    end = if (opensLeft) ArrowDepth else Spacing.s8, bottom = Spacing.s8,
                )
                .clip(shape)
                .selectionOutline(outline, shape)
                .testTag("detailPane"),
        ) { content() }

        val depth = with(density) { ArrowDepth.toPx() }
        // The arrow goes where the core said, down the callout's anchored edge, measured in this pane.
        val target = if (placement != null && frame != null) (placement.box.top + placement.arrowOffset) * density.density - frame.top else null
        val y by animateFloatAsState(
            target ?: 0f,
            if (com.gracefultools.astrid.ui.theme.LocalReducedMotion.current) androidx.compose.animation.core.snap() else tween(AstridTokens.Motion.fastDurationMillis),
            label = "detailsArrow",
        )
        if (target != null) {
            val fill = colors.pageBg
            Canvas(
                Modifier.align(if (opensLeft) Alignment.TopEnd else Alignment.TopStart)
                    .offset { IntOffset(0, (y - depth).roundToInt()) }
                    .size(ArrowDepth, ArrowDepth * 2)
                    .testTag("detailsArrow"),
            ) {
                // Drawn pointing left, mirrored when the callout opens on the row's left.
                scale(scaleX = if (opensLeft) -1f else 1f, scaleY = 1f) {
                    // Drawn a stroke past the panel's edge, so its fill covers that stretch of outline.
                    val base = size.width + outline.width.toPx()
                    val path = Path().apply {
                        moveTo(base, 0f); lineTo(0f, size.height / 2); lineTo(base, size.height); close()
                    }
                    drawPath(path, fill)
                    val edges = Path().apply { moveTo(size.width, 0f); lineTo(0f, size.height / 2); lineTo(size.width, size.height) }
                    drawPath(edges, outline.color, style = Stroke(outline.width.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
}

/** Root pixels to the core's dp rectangle. */
private fun Rect.inDp(density: Float) = CalloutRect(left / density, top / density, width / density, height / density)
