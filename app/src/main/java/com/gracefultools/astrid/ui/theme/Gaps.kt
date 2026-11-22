package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The paddings and gaps iOS types between core's spacing steps (ADEV-196). [Spacing] carries the
 * scale (2, 4, 8, 12, 16, …); these are the off-scale values iOS uses as they are, kept in one place
 * so that retiring one to the scale is a one-line change. Named by value like [Spacing], with the
 * places each sits, plus three named insets.
 */
object Gap {
    /** A sidebar row's vertical padding; the segmented control's thumb inset; tight stacked lines. */
    val s1 = 1.dp
    /** A title over its subtitle; the typing dots; a comment's timestamp. */
    val s3 = 3.dp
    /** The glyph inset in a small round button; an icon beside its label. */
    val s5 = 5.dp
    /** Chips, pills and inline rows of small items; compact row padding. */
    val s6 = 6.dp
    /** A board column header's vertical padding. */
    val s9 = 9.dp
    /** A row's vertical padding; the glyph inset in a 44 dp icon button; a chip's sides. */
    val s10 = 10.dp
    /** A sidebar list row's vertical padding. */
    val s13 = 13.dp
    /** A menu row's vertical padding; the glyph inset in the close button. */
    val s14 = 14.dp
    /** The sidebar section heading's indent, under the logo column. */
    val s28 = 28.dp

    /** Room left under a list or above a banner for the floating composer and buttons. */
    val floatingClearance = 96.dp
    /** A sidebar divider's leading inset, past the row's picture. */
    val sidebarDividerInset = 56.dp
    /** A settings divider's leading inset, past the row's icon (16 + 24 + 12). */
    val settingsDividerInset = 52.dp
}
