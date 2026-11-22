package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The widths Android draws an outline or stroke at (ADEV-196). iOS types each `lineWidth` by hand (1,
 * 1.5, 2 and 3) and core's tokens have no border scale yet, so they are named here after the views
 * they match; when core adds one, these read it.
 */
object BorderWidth {
    /** Field, card, chip and pill outlines, and the selection outline in pickers (iOS lineWidth 1). */
    val thin = 1.dp
    /** The tinted outline round a small icon square, and the empty-assignee circle (iOS lineWidth 1.5). */
    val mark = 1.5.dp
    /** The checkbox square, avatar rings, the drop-target outline and the insights chart line
     *  (iOS lineWidth 2). */
    val thick = 2.dp
    /** The board column's ring while a card hovers over it (iOS BoardColumnView, lineWidth 3) and the
     *  reminder triage card once a choice is pending. */
    val heavy = 3.dp
}
