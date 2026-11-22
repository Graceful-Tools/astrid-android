package com.gracefultools.astrid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shapes the radius tokens don't name (ADEV-196, register DS13). Each one copies an iOS system
 * control's geometry, so it lives here, named, rather than typed into a screen.
 */
object SegmentedShapes {
    /** iOS's segmented picker: the track, and the raised thumb inside it. */
    val trackRadius = 9.dp
    val thumbRadius = 7.dp
    val track = RoundedCornerShape(trackRadius)
    val thumb = RoundedCornerShape(thumbRadius)
}

/** The sidebar's floating account card and the profile button inside it, as iOS draws them. */
object AccountCardShapes {
    val card = RoundedCornerShape(24.dp)
    val profile = RoundedCornerShape(20.dp)
}

/** A list's colour swatch: a square softened by a fifth of its side (16 dp → ~3, 8 dp → ~2). */
val SwatchShape = RoundedCornerShape(percent = 20)
