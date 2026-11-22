package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
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

    // ADEV-196: the elevations screens typed in, drawn in Compose's default shadow colour as before.

    /** The chosen segment's thumb in a segmented control (list form, hub form). */
    val segmentThumb = AstridShadow(elevation = 1.dp, color = DefaultShadowColor)

    /** The account card floating at the foot of the sidebar. */
    val accountCard = AstridShadow(elevation = 6.dp, color = DefaultShadowColor)

    /** A task card lifted while it is dragged, in the list and on the board. */
    val liftedCard = AstridShadow(elevation = 8.dp, color = DefaultShadowColor)

    /** The reminder triage card that is swiped left or right. */
    val triageCard = AstridShadow(elevation = 8.dp, color = DefaultShadowColor)

    /** A card shown over a dimmed screen: the reminder and the update prompt. */
    val dialog = AstridShadow(elevation = 20.dp, color = DefaultShadowColor)
}

/** Draws [shadow] around [shape]. */
fun Modifier.shadow(shadow: AstridShadow, shape: Shape): Modifier =
    shadow(shadow.elevation, shape, ambientColor = shadow.color, spotColor = shadow.color)
