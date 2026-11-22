package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The fixed widths and heights Android gives a few views (ADEV-196). iOS sets each frame by hand and
 * core's tokens have no frame scale yet, so they are named here after the views they size; when core
 * adds one, these read it.
 */
object FrameSize {
    /** The board card lifted under the finger while it is dragged. */
    val liftedCard = 284.dp
    /** The tinted zone at a task list's leading edge that outdents a dragged subtask. */
    val outdentZone = 56.dp
    /** The completions chart on a list's insights tab. */
    val insightsChart = 176.dp
    /** The bullet or number column in front of a markdown list item. */
    val listMarker = 24.dp
    /** The bar down the leading edge of a markdown quote. */
    val quoteBar = 3.dp
    /** A markdown table cell. */
    val tableCell = 120.dp
    /** The row the three agent typing dots bounce in. */
    val typingRow = 16.dp
    /** The timer's Mark as done pill. */
    val timerDoneWidth = 260.dp
    val timerDoneHeight = 60.dp
    /** The number between a repeat stepper's minus and plus. */
    val stepperValue = 36.dp
    /** The # or ! in front of a list or task in the mention picker. */
    val mentionGlyph = 20.dp
}
