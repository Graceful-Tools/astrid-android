package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/** How far a drag must go, or where it draws, when a task is dragged in a list or on a board (ADEV-196). */
object DragDistance {
    /** Dragged this far towards the leading edge, a subtask outdents. */
    val outdent = 40.dp
    /** The band at a row's top edge that drops above the row rather than onto it (at most 30% of the row). */
    val dropBand = 12.dp
    /** The insertion line sits this far outside the row it drops beside. */
    val insertionLine = 4.dp
    /** Within this of a board's edge, a dragged card scrolls the columns. */
    val edgeScroll = 48.dp
}
