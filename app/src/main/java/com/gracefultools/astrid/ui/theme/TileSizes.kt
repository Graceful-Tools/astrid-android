package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The sizes Android draws a rounded square at: a tile, a picture or a square button (ADEV-196). iOS
 * sizes each frame by hand and core's tokens have no scale for them yet, so they are named here after
 * the iOS views they match; when core adds one, these read it. A circle reads [CircleSize] instead.
 */
object TileSize {
    /** The floating header's back chevron (iOS FloatingHeader). */
    val back = 28.dp
    /** The search screen's menu button (iOS SearchView's hamburger). */
    val menu = 32.dp
    /** A priority choice in the list form and the task details (iOS PriorityButtonPicker). */
    val priority = 40.dp
    /** The icon tile on a profile stat card (iOS UserProfileView's stat cards). */
    val stat = 48.dp
    /** An attachment's thumbnail in the task details (iOS TaskDetailView's attachments). */
    val attachment = 56.dp
    /** The list image in List Settings → Admin (iOS ListSettingsModal). */
    val listImage = 64.dp
    /** The app logo on the sign-in screen (iOS SignInView). */
    val logo = 88.dp
}
