package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SwiftUI's system colours where iOS writes them by name (`.green`, `.orange`, `.yellow`,
 * `.blue`), AATD-85. They are tints with a meaning on a screen (Google Tasks, a stat tile, the
 * favourite star, "access required"), not theme surfaces, so they stay the same on every theme,
 * as Android has drawn them so far. The purple is gone: an agent reads `tokens.agentInk` and a
 * category tile `tokens.categoryPurpleInk`, as iOS does (ADEV-196, DS27/DS28).
 */
object IosSystemColors {
    val green = Color(0xFF34C759)
    val orange = Color(0xFFFF9500)
    val yellow = Color(0xFFFFCC00)
    val blue = Color(0xFF007AFF)
    /** The purple-indigo of "Add a Passkey". */
    val indigo = Color(0xFF5957D6)
}
