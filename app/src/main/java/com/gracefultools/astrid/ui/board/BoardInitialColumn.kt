package com.gracefultools.astrid.ui.board

import com.gracefultools.astrid.model.BoardColumn

/**
 * Which column the board opens on — iOS's initialBoardColumnId. Waiting leads the board, but a
 * phone (one column to a screen) opens on the inbox column by kind, so Waiting is a swipe back.
 * A wide board starts at its leading column, where Waiting is in view.
 */
object BoardInitialColumn {
    fun index(columns: List<BoardColumn>, multiColumn: Boolean): Int =
        if (multiColumn) 0 else columns.indexOfFirst { it.kind == "inbox" }.coerceAtLeast(0)
}
