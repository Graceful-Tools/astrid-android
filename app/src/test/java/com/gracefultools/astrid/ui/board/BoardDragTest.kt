package com.gracefultools.astrid.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardDragTest {
    private val columns = mapOf("todo" to Rect(0f, 0f, 300f, 2000f), "doing" to Rect(312f, 0f, 612f, 2000f))
    private val cards = mapOf(
        "todo" to listOf(Rect(0f, 100f, 300f, 200f), Rect(0f, 210f, 300f, 310f)),
        "doing" to listOf(Rect(312f, 100f, 612f, 200f)),
    )

    @Test
    fun `AATD-20 a card dropped over a column lands before the first card below the finger`() {
        assertEquals(BoardDrag.Target("doing", 0), BoardDrag.target(Offset(400f, 120f), columns, cards))
        assertEquals(BoardDrag.Target("doing", 1), BoardDrag.target(Offset(400f, 500f), columns, cards))
        assertEquals(BoardDrag.Target("todo", 1), BoardDrag.target(Offset(10f, 220f), columns, cards))
    }

    @Test
    fun `AATD-20 a card dropped between columns goes nowhere`() {
        assertNull(BoardDrag.target(Offset(306f, 120f), columns, cards))
    }
}
