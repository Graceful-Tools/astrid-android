package com.gracefultools.astrid.ui.board

import com.gracefultools.astrid.model.BoardColumn
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADEV-135 (iOS ADEV-128, BoardInitialColumnTests): Waiting now leads the board, but a phone still
 * opens on the inbox column ("Ideas") — Waiting is one swipe back. The first page is the inbox
 * column by kind, never simply the first column.
 */
class BoardInitialColumnTest {
    private fun column(id: String, kind: String = "status") = BoardColumn(id = id, name = id, kind = kind)

    private val waitingFirst = listOf(
        column("waiting"), column("__virtual_inbox__", "inbox"), column("ready"),
        column("doing"), column("__virtual_done__", "done"),
    )

    @Test
    fun `ADEV-135 a phone opens on the inbox column even when Waiting is first`() {
        assertEquals(1, BoardInitialColumn.index(waitingFirst, multiColumn = false))
    }

    @Test
    fun `ADEV-135 a wide board starts at the leading column so Waiting is visible`() {
        assertEquals(0, BoardInitialColumn.index(waitingFirst, multiColumn = true))
    }

    @Test
    fun `ADEV-135 a board with no inbox column opens on its first`() {
        assertEquals(0, BoardInitialColumn.index(listOf(column("ready"), column("doing")), multiColumn = false))
        assertEquals(0, BoardInitialColumn.index(emptyList(), multiColumn = false))
    }
}
