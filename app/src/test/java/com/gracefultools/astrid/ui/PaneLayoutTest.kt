package com.gracefultools.astrid.ui

import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.ui.tasks.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AATD-61: iOS iPadPaneLayout, at Android's window size classes. */
class PaneLayoutTest {
    @Test
    fun `AATD-61 the list keeps its full width until a task is selected`() {
        val closed = PaneLayout.widths(1000.dp, showsMessages = false, messagesHidden = false, besideBoard = false, detailOpen = false)
        assertEquals(1000.dp, closed.list)
        assertEquals(0.dp, closed.detail)
        val open = PaneLayout.widths(1000.dp, showsMessages = false, messagesHidden = false, besideBoard = false, detailOpen = true)
        assertEquals(500.dp, open.detail)
        assertEquals(500.dp, open.list)
    }

    @Test
    fun `AATD-61 tapping the selected task again closes the pane`() {
        assertEquals("t1", PaneLayout.toggle(null, "t1"))
        assertNull(PaneLayout.toggle("t1", "t1"))
        assertEquals("t2", PaneLayout.toggle("t1", "t2"))
    }

    @Test
    fun `AATD-61 messages are pinned only in landscape on a wide window`() {
        assertTrue(PaneLayout.showsMessagesPane(1200, 800, ViewMode.LIST, hasChannel = true, boardFullScreen = false))
        assertTrue(PaneLayout.showsMessagesPane(1200, 800, ViewMode.BOARD, hasChannel = true, boardFullScreen = false))
        assertFalse(PaneLayout.showsMessagesPane(900, 1200, ViewMode.LIST, hasChannel = true, boardFullScreen = false))
        assertFalse(PaneLayout.showsMessagesPane(800, 600, ViewMode.LIST, hasChannel = true, boardFullScreen = false))
        assertFalse(PaneLayout.showsMessagesPane(1200, 800, ViewMode.LIST, hasChannel = false, boardFullScreen = false))
        assertFalse(PaneLayout.showsMessagesPane(1200, 800, ViewMode.BOARD, hasChannel = true, boardFullScreen = true))
    }

    @Test
    fun `AATD-61 the messages pane is 45 percent of what remains, or a 48dp rail`() {
        val shown = PaneLayout.widths(1000.dp, showsMessages = true, messagesHidden = false, besideBoard = false, detailOpen = false)
        assertEquals(450.dp, shown.messages)
        assertEquals(550.dp, shown.list)
        val rail = PaneLayout.widths(1000.dp, showsMessages = true, messagesHidden = true, besideBoard = false, detailOpen = false)
        assertEquals(48.dp, rail.messages)
        assertEquals(952.dp, rail.list)
        val board = PaneLayout.widths(1000.dp, showsMessages = true, messagesHidden = false, besideBoard = true, detailOpen = false)
        assertEquals(320.dp, board.messages)
    }

    @Test
    fun `AATD-61 a task opens over the messages pane, keeping the list in view`() {
        val panes = PaneLayout.widths(1000.dp, showsMessages = true, messagesHidden = false, besideBoard = false, detailOpen = true)
        assertTrue(panes.detailCoversMessages)
        assertEquals(450.dp, panes.detail)
        assertEquals(0.dp, panes.messages)
        assertEquals(550.dp, panes.list)
        val beside = PaneLayout.widths(1000.dp, showsMessages = true, messagesHidden = true, besideBoard = false, detailOpen = true)
        assertFalse(beside.detailCoversMessages)
        assertEquals(48.dp, beside.messages)
        assertEquals(400.dp, beside.detail)
    }

    @Test
    fun `AATD-61 pinned messages leave the rotator, and full screen is a wide board's`() {
        assertEquals(ViewMode.LIST, PaneLayout.effectiveMode(ViewMode.MESSAGES, messagesPinned = true))
        assertEquals(ViewMode.MESSAGES, PaneLayout.effectiveMode(ViewMode.MESSAGES, messagesPinned = false))
        assertTrue(PaneLayout.offersFullScreen(1000, ViewMode.BOARD))
        assertFalse(PaneLayout.offersFullScreen(1000, ViewMode.LIST))
        assertFalse(PaneLayout.offersFullScreen(700, ViewMode.BOARD))
        assertTrue(PaneLayout.permanentSidebar(1000, boardFullScreen = false))
        assertFalse(PaneLayout.permanentSidebar(1000, boardFullScreen = true))
        assertFalse(PaneLayout.permanentSidebar(700, boardFullScreen = false))
    }
}
