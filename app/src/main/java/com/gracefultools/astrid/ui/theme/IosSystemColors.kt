package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SwiftUI's system colours where iOS writes them by name (`.purple`, `.green`, `.orange`,
 * `.yellow`, `.blue`), AATD-85. They are tints with a meaning on a screen (GitHub Issues,
 * Google Tasks, a stat tile, the favourite star, "access required"), not theme surfaces, so
 * they stay the same on every theme, as Android has drawn them so far.
 */
object IosSystemColors {
    val purple = Color(0xFFAF52DE)
    val green = Color(0xFF34C759)
    val orange = Color(0xFFFF9500)
    val yellow = Color(0xFFFFCC00)
    val blue = Color(0xFF007AFF)
}
