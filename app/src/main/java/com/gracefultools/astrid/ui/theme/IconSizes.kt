package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The icon sizes Android draws at (ADEV-196). iOS sizes its SF Symbols by hand and core's tokens have
 * no icon scale yet, so the steps are named here; when core adds one, these read it.
 */
object IconSize {
    /** Caption glyphs: a tag, a tick in a swatch, a verified mark. */
    val xs = 12.dp
    val sm = 14.dp
    /** Inline with body text: a warning, a field's icon, a small progress ring. */
    val md = 16.dp
    val lg = 18.dp
    /** A row's icon or chevron (settings, sidebar, list form). */
    val xl = 20.dp
    val xxl = 24.dp

    // Off the steps, named after the iOS view each matches. SF Symbols size with the text they sit in;
    // a Material icon is inset in its box, so its box runs a little larger than the iOS point size.

    /** A list's glyph beside its name: a task row's list chip, the public / shared mark in the sidebar
     *  and Public Lists (iOS TaskRowView and PublicListBrowserView, 12pt / `.caption`). */
    val chip = 13.dp
    /** Beside footnote text: the sidebar's filter mark and the List Settings tab icons
     *  (iOS ListSidebarView's filter, `.footnote`; ListSettingsModal's tabs). */
    val tab = 15.dp
    /** A field's icon in the task details' leading column. */
    val field = 19.dp
    /** A tappable glyph: copy list, cancel an invite, clear search, a subtask's tick, add a subtask,
     *  remove an attachment, a settings row's icon (iOS 20pt `xmark.circle.fill` / `plus.circle`, and
     *  SettingsView's 24-wide icon column). */
    val control = 22.dp
    /** The play / pause glyph on the full-screen timer's button (iOS TaskTimerView, 40pt). */
    val timer = 40.dp
}

/** The line of a progress ring (iOS `ProgressView`'s stroke). */
val SpinnerStroke = 2.dp
