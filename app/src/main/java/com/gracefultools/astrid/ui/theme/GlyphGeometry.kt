package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The measures of the two shapes Android draws itself rather than taking from an icon set (ADEV-196).
 * Each copies the iOS view of the same name.
 */

/** iOS's HamburgerMenuIcon: two 19.2 x 2 lines with rounded ends, 5 apart, in a 24 frame. */
object HamburgerGlyph {
    val lineWidth = 19.2.dp
    val lineHeight = 2.dp
    val lineGap = 5.dp
    val lineRadius = 1.5.dp
}

/**
 * iOS's AstridSpeechBubble pointer: an outer triangle in the border colour with an inner one in the
 * bubble colour over it, reaching left from the bubble's edge towards the character.
 */
object BubblePointer {
    /** How far each triangle's tip reaches left of the bubble. */
    val outerReach = 16.dp
    val innerReach = 13.dp
    /** How far the triangles' bases sit inside the bubble, so no seam shows. */
    val overlap = 3.dp
    /** Half of each triangle's base. */
    val outerHalfHeight = 12.dp
    val innerHalfHeight = 9.5.dp
}
