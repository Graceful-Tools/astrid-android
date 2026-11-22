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
}

/** The line of a progress ring (iOS `ProgressView`'s stroke). */
val SpinnerStroke = 2.dp
