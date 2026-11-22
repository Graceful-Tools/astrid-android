package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The sizes Android draws a small dot or colour swatch at, under 24 dp (ADEV-196). iOS sizes each
 * frame by hand and core's tokens have no scale for them yet, so they are named here after the iOS
 * views they match; when core adds one, these read it.
 */
object DotSize {
    /** One of the three agent typing dots (iOS AgentTypingIndicator). */
    val typing = 6.dp
    /** An agent's active dot and the insights legend dot (iOS AgentHubScreens' status circle). */
    val status = 8.dp
    /** The list colour on a list chip in the task details. */
    val swatch = 8.dp
    /** The accent dot on the sidebar's My Tasks row (iOS MacRootView's My Tasks circle). */
    val accent = 12.dp
    /** The list colour in the task details' list picker. */
    val swatchLarge = 16.dp
}
