package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The caps Android puts on a view's width or height with `widthIn`/`heightIn` (ADEV-196). iOS sets
 * each `maxWidth`/`maxHeight` by hand and core's tokens have no frame scale yet, so they are named here
 * after the views they cap; when core adds one, these read it.
 */
object MaxSize {
    /** A chat or comment bubble. */
    val bubble = 300.dp
    /** Astrid's speech bubble on an empty or welcome screen. */
    val speechBubble = 560.dp
    /** iOS's AppErrorBanner. */
    val errorBanner = 520.dp
    /** An image shown inline in a comment or description. */
    val inlineImageWidth = 260.dp
    val inlineImageHeight = 200.dp
    /** The comment box grows to this height, then scrolls. */
    val commentBox = 200.dp
    /** The mention picker above the comment box. */
    val mentionPicker = 220.dp
    /** The sign-in buttons' column. */
    val signInColumn = 420.dp
    /** A dropdown row's chosen value, before it ellipsizes. */
    val pickerValue = 200.dp
    /** The reminder screen's column and triage card. */
    val reminderColumn = 500.dp
    /** A dialog card, such as the app update prompt. */
    val dialog = 340.dp
}
