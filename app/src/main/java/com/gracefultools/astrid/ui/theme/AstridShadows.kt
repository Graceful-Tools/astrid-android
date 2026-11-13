package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A shadow as Compose draws one: an elevation and the colour of its ambient and spot light.
 * iOS's `.shadow(color:radius:y:)` has no elevation, so [elevation] is the Compose value that
 * reads closest to iOS's radius on a device.
 */
data class AstridShadow(val elevation: Dp, val color: Color)

/**
 * The shadows iOS repeats by hand (AATD-85). The core's `shadow.*` tokens are web's card shadows
 * and iOS does not use them at these places, so these follow iOS.
 */
object AstridShadows {
    /** FloatingHeader: black 0.08, radius 4, y 2. */
    val floatingHeader = AstridShadow(elevation = 4.dp, color = Color.Black.copy(alpha = 0.08f))

    /** The quick-add bar and the comment composer (QuickAddTaskView, RichTextInput): black 0.1, radius 8, y −2. */
    val bottomBar = AstridShadow(elevation = 6.dp, color = Color.Black.copy(alpha = 0.1f))
}

/** Draws [shadow] around [shape]. */
fun Modifier.shadow(shadow: AstridShadow, shape: Shape): Modifier =
    shadow(shadow.elevation, shape, ambientColor = shadow.color, spotColor = shadow.color)
