package com.gracefultools.astrid.ui.detail

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
 * `detail-panel-radius` corners, so the two read as one object, and an arrow on its leading edge
 * points at the row and follows it over `motion.fast`. When the row is scrolled away the arrow
 * hides; the panel stays. Entering and leaving over `motion.panel` belongs to the host.
 */
@Composable
fun DetailsPanel(anchor: SelectedRowAnchor, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    val outline = selectionOutlineStyle()
    val radius = AstridTokens.Layout.Comfortable.detailPanelRadius
    val shape = RoundedCornerShape(radius)
    val density = LocalDensity.current
    var top by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    Box(modifier.background(colors.bgPrimary).onGloballyPositioned { top = it.positionInRoot().y; height = it.size.height }) {
        Box(
            Modifier.fillMaxSize()
                .padding(start = ArrowDepth, top = Spacing.s8, end = Spacing.s8, bottom = Spacing.s8)
                .clip(shape)
                .selectionOutline(outline, shape)
                .testTag("detailPane"),
        ) { content() }

        val depth = with(density) { ArrowDepth.toPx() }
        // Keep the arrow on the straight part of the edge, clear of the rounded corners.
        val low = with(density) { (Spacing.s8 + radius).toPx() } + depth
        val high = height - low
        val target = anchor.centerY?.minus(top)?.takeIf { it in low..high }
        val y by animateFloatAsState(
            target ?: 0f, tween(AstridTokens.Motion.fastDurationMillis), label = "detailsArrow",
        )
        if (target != null) {
            val fill = colors.pageBg
            Canvas(
                Modifier.offset { IntOffset(0, (y - depth).roundToInt()) }
                    .size(ArrowDepth, ArrowDepth * 2)
                    .testTag("detailsArrow"),
            ) {
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
