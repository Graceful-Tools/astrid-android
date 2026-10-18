package com.gracefultools.astrid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The four themes iOS offers and stores (`ThemeMode.swift`): Ocean (the default), Light, Dark, and
 * Auto, which follows the system. The wire names are the ones the core's `theme` command stores.
 */
enum class ThemeMode(val wire: String) {
    OCEAN("ocean"), LIGHT("light"), DARK("dark"), AUTO("auto");

    companion object {
        fun of(wire: String?): ThemeMode = entries.firstOrNull { it.wire == wire } ?: OCEAN
    }
}

/** Resolve a mode to its palette. Auto reads the system, as iOS's `effectiveTheme` does. */
fun colorsFor(mode: ThemeMode, systemDark: Boolean): AstridColors = when (mode) {
    ThemeMode.OCEAN -> OceanColors
    ThemeMode.LIGHT -> LightColors
    ThemeMode.DARK -> DarkColors
    ThemeMode.AUTO -> if (systemDark) DarkColors else LightColors
}

/** iOS `Theme.spacing*` and `radius*`. */
object Spacing {
    val s2 = 2.dp; val s4 = 4.dp; val s8 = 8.dp; val s12 = 12.dp; val s16 = 16.dp
    val s20 = 20.dp; val s24 = 24.dp; val s32 = 32.dp; val s40 = 40.dp; val s48 = 48.dp
}

object Radius {
    val small = 6.dp; val medium = 8.dp; val large = 12.dp; val xLarge = 16.dp
}

/** iOS `Theme.Typography`: the system type scale at iOS's point sizes, as sp so it scales. */
val AstridTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    bodySmall = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 12.sp),
)

val LocalAstridColors = staticCompositionLocalOf { OceanColors }

/** The palette in force. Read colours from here, never from Material's scheme directly. */
object AstridTheme {
    val colors: AstridColors @Composable get() = LocalAstridColors.current
}

@Composable
fun AstridTheme(mode: ThemeMode = ThemeMode.OCEAN, content: @Composable () -> Unit) {
    val colors = colorsFor(mode, isSystemInDarkTheme())
    // Material components (dialogs, pickers, menus) draw from this scheme, mapped from the iOS
    // palette so they sit in the same theme as everything else.
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.accentText,
            background = colors.bgPrimary, onBackground = colors.textPrimary,
            surface = colors.bgSecondary, onSurface = colors.textPrimary,
            surfaceVariant = colors.bgTertiary, onSurfaceVariant = colors.textSecondary,
            outline = colors.border, error = Error,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.accentText,
            background = colors.bgPrimary, onBackground = colors.textPrimary,
            surface = colors.bgSecondary, onSurface = colors.textPrimary,
            surfaceVariant = colors.bgTertiary, onSurfaceVariant = colors.textSecondary,
            outline = colors.border, error = Error,
        )
    }
    CompositionLocalProvider(LocalAstridColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AstridTypography, content = content)
    }
}
