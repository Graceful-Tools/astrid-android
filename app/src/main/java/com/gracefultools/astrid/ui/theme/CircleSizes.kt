package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The sizes Android draws a round button or badge at (ADEV-196). iOS sizes each circle's frame by hand
 * and core's tokens have no scale for them yet, so they are named here after the iOS views they match;
 * when core adds one, these read it. A circle that stands in for a person reads [AvatarSize] instead.
 */
object CircleSize {
    /** The camera badge on your profile picture (iOS UserProfileView). */
    val badge = 24.dp
    /** A swatch in the list colour picker. */
    val swatch = 28.dp
    /** The board column's add button, in its tighter quick-add row. */
    val compact = 30.dp
    /** Header buttons, send, copy list, add a connection, clear a field, the weekday circles
     *  (iOS CustomRepeatingPatternEditor). */
    val sm = 32.dp
    /** Copy, delete an agent, the "no model" choice, the camera badge on edit profile
     *  (iOS EditProfileView, DefaultAgentPickerView). */
    val md = 36.dp
    /** Sidebar search and the comment composer's buttons (iOS ListSidebarView, PriorityButtonPicker). */
    val lg = 40.dp
    /** The close button on the full-screen timer. */
    val close = 56.dp
    /** The timer's start / pause button (iOS TaskTimerView). */
    val timer = 100.dp
}
