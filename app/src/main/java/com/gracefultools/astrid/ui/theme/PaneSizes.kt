package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The fixed widths of the app's panes (ADEV-196). iOS sets each by hand and core's tokens have no
 * pane scale yet, so they are named here after the iOS values they match.
 */
object PaneWidth {
    /** iOS messagesRailWidth: what a hidden messages pane leaves, room for its show button. */
    val rail = 48.dp
    /** iOS messagesWidthBesideBoard: narrow beside a board, so its columns get the room. */
    val messagesBesideBoard = 320.dp
    /** The sidebar drawer is about 85% of a phone's width, but never wider than this. */
    val sidebarMax = 360.dp
}
