package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The full-screen black screens, the same on every theme, as iOS draws them with `.black` and
 * `.white` (TaskTimerView, the attachment viewer): ADEV-196. Also the dark scrims and badges
 * laid over photos and the app.
 */
object ImmersiveColors {
    val background = Color.Black
    val content = Color.White
    /** Secondary text and icons: iOS `.white.opacity(0.7)`. */
    val contentSecondary = Color.White.copy(alpha = 0.7f)
    /** Hints: iOS `.white.opacity(0.5)`. */
    val contentTertiary = Color.White.copy(alpha = 0.5f)
    /** The big round button and its mark (iOS's white circle under a black icon). */
    val control = Color.White
    val onControl = Color.Black
    /** A badge over a photo (an attachment's remove button). */
    val badge = Color.Black.copy(alpha = 0.6f)
    /** The dim behind a reminder card: iOS's overlay at 0.5. */
    val scrim = Color.Black.copy(alpha = 0.5f)
    /** The hairline round iOS's raised segment. */
    val hairline = Color.Black.copy(alpha = 0.04f)
}

/** The custom-colour button's hue wheel (iOS ColorPicker's swatch): a sweep through the spectrum. */
val HueWheel = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
