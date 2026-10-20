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

/**
 * Material components (dialogs, date and time pickers, menus, snackbars, switches, chips, sheets)
 * draw from this scheme. Every role is mapped from the iOS palette, so none of Material's stock
 * lavender shows through in a picker or a switch track (AATD-33).
 */
fun materialScheme(c: AstridColors): androidx.compose.material3.ColorScheme {
    val sheet = if (c.isDark) c.bgSecondary else androidx.compose.ui.graphics.Color.White
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent, onPrimary = c.accentText,
        primaryContainer = c.bgSelected, onPrimaryContainer = c.textPrimary,
        inversePrimary = c.accent,
        secondary = c.accent, onSecondary = c.accentText,
        secondaryContainer = c.bgSelected, onSecondaryContainer = c.textPrimary,
        tertiary = Info, onTertiary = androidx.compose.ui.graphics.Color.White,
        tertiaryContainer = c.bgTertiary, onTertiaryContainer = c.textPrimary,
        background = c.bgPrimary, onBackground = c.textPrimary,
        surface = sheet, onSurface = c.textPrimary,
        surfaceVariant = c.bgTertiary, onSurfaceVariant = c.textSecondary,
        surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
        inverseSurface = c.textPrimary, inverseOnSurface = c.bgPrimary,
        error = Error, onError = androidx.compose.ui.graphics.Color.White,
        errorContainer = Error.copy(alpha = 0.12f), onErrorContainer = Error,
        outline = c.borderHover, outlineVariant = c.border,
        scrim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
        surfaceBright = sheet, surfaceDim = c.bgTertiary,
        surfaceContainerLowest = sheet, surfaceContainerLow = sheet, surfaceContainer = sheet,
        surfaceContainerHigh = if (c.isDark) c.bgTertiary else c.bgSecondary,
        surfaceContainerHighest = c.bgTertiary,
    )
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

/** iOS's named styles, for text that is not a Material component's. */
object TextStyles {
    /** headline: list titles, header titles, section titles. */
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp)
    val callout = TextStyle(fontSize = 16.sp)
    /** subheadline: row metadata, list chips. */
    val subheadline = TextStyle(fontSize = 15.sp)
    val footnote = TextStyle(fontSize = 13.sp)
    val caption = TextStyle(fontSize = 12.sp)
    val caption2 = TextStyle(fontSize = 11.sp)
    /** A row's title: 17 x 1.1. */
    val rowTitle = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium)
}

/** iOS Theme.shadowSmall/Medium/Large as Compose elevations, and the floating header's own. */
object Elevation {
    val small = 1.dp; val medium = 3.dp; val large = 10.dp; val floating = 4.dp
}

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
    val scheme = materialScheme(colors)
    CompositionLocalProvider(LocalAstridColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AstridTypography, content = content)
    }
}
