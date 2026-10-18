package com.gracefultools.astrid.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Where a drag ends up: which column the finger is over and where among its cards. Pure geometry,
 * so it is tested without a screen; what the drop MEANS is the core's (dropBoardCard).
 */
object BoardDrag {
    data class Target(val columnId: String, val index: Int)

    /**
     * @param columns each column's bounds, in window coordinates
     * @param cards each column's cards' bounds, top to bottom, the dragged card left out
     */
    fun target(pointer: Offset, columns: Map<String, Rect>, cards: Map<String, List<Rect>>): Target? {
        val (columnId, _) = columns.entries.firstOrNull { (_, bounds) -> pointer.x in bounds.left..bounds.right } ?: return null
        val inColumn = cards[columnId].orEmpty()
        // Before the first card whose middle is below the finger; after them all otherwise.
        val index = inColumn.indexOfFirst { pointer.y < it.center.y }.let { if (it < 0) inColumn.size else it }
        return Target(columnId, index)
    }
}
