package com.gracefultools.astrid.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.gracefultools.astrid.ui.tasks.ViewMode

/**
 * How a tablet splits its window into panes — iOS's iPadPaneLayout (Task a34d0163) and
 * iPadTaskManagerView, cut at Android's window size classes (AATD-61). Pure, so the layout is
 * pinned by tests rather than by looking at an emulator.
 *
 * - **Below 600dp:** one pane; a task is pushed.
 * - **From 600dp:** the list keeps its full width until a task is selected; then the detail opens
 *   as a pane beside it, and tapping the same task again closes it.
 * - **From 840dp:** the sidebar is permanent. In landscape the selected list's messages sit in a
 *   pane at the right — 45% of what the sidebar leaves, or a 48dp rail when hidden — and the
 *   rotator loses its messages step. A board may go full screen, dropping the sidebar and messages.
 */
object PaneLayout {
    const val DETAIL_MIN_WIDTH = 600
    const val WIDE_MIN_WIDTH = 840
    /** iOS messagesShare: under a half, so the task list always stays the wider of the two. */
    const val MESSAGES_SHARE = 0.45f
    /** iOS: the detail's share when it has no messages pane to cover. */
    const val DETAIL_SHARE = 0.5f
    /** iOS messagesRailWidth: what a hidden messages pane leaves — room for its show button. */
    val RAIL: Dp = 48.dp
    /** iOS messagesWidthBesideBoard: narrow beside a board, so its columns get the room. */
    val MESSAGES_BESIDE_BOARD: Dp = 320.dp

    fun showsDetailPane(widthDp: Int) = widthDp >= DETAIL_MIN_WIDTH

    /** The sidebar has a pane of its own — except under a full-screen board (iOS showsSlidingPicker). */
    fun permanentSidebar(widthDp: Int, boardFullScreen: Boolean) = widthDp >= WIDE_MIN_WIDTH && !boardFullScreen

    /** iOS columns == 3: a wide window in landscape. */
    fun pinsMessages(widthDp: Int, heightDp: Int) = widthDp >= WIDE_MIN_WIDTH && widthDp > heightDp

    /** iOS showsMessages: beside a list or a board, for a list with a channel, unless the board is full screen. */
    fun showsMessagesPane(widthDp: Int, heightDp: Int, mode: ViewMode, hasChannel: Boolean, boardFullScreen: Boolean) =
        pinsMessages(widthDp, heightDp) && hasChannel && !boardFullScreen && (mode == ViewMode.LIST || mode == ViewMode.BOARD)

    /** iOS offersFullScreen: a board affordance, on a wide window. */
    fun offersFullScreen(widthDp: Int, mode: ViewMode) = widthDp >= WIDE_MIN_WIDTH && mode == ViewMode.BOARD

    /** With messages pinned beside, "messages" is no longer a view of the main pane. */
    fun effectiveMode(mode: ViewMode, messagesPinned: Boolean) =
        if (messagesPinned && mode == ViewMode.MESSAGES) ViewMode.LIST else mode

    /** Tapping the selected task again closes the pane (iOS TaskListView 871-872). */
    fun toggle(selected: String?, tapped: String): String? = if (selected == tapped) null else tapped

    /** Pane widths, left to right, of the area right of the sidebar. A pane not shown is 0 wide. */
    data class Panes(
        val list: Dp,
        val messages: Dp,
        val detail: Dp,
        /** The detail sits where the messages were: "task details appearing over list messages". */
        val detailCoversMessages: Boolean = false,
    )

    fun widths(content: Dp, showsMessages: Boolean, messagesHidden: Boolean, besideBoard: Boolean, detailOpen: Boolean): Panes {
        if (!showsMessages) {
            val detail = if (detailOpen) content * DETAIL_SHARE else 0.dp
            return Panes(list = content - detail, messages = 0.dp, detail = detail)
        }
        val share = content * MESSAGES_SHARE
        val messages = when {
            messagesHidden -> RAIL
            besideBoard -> min(MESSAGES_BESIDE_BOARD, share)
            else -> share
        }
        if (!detailOpen) return Panes(list = content - messages, messages = messages, detail = 0.dp)
        // A full pane of messages is room for a task: the detail takes its place. A rail or the
        // narrow board pane is not, so the detail opens beside them at iOS's 40% instead.
        if (!messagesHidden && !besideBoard) return Panes(list = content - messages, messages = 0.dp, detail = messages, detailCoversMessages = true)
        val detail = content * 0.4f
        return Panes(list = content - messages - detail, messages = messages, detail = detail)
    }
}
