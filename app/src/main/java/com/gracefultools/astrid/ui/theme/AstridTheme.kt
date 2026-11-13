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
import com.astrid.design.AstridTextStyle
import com.astrid.design.AstridTokens
import com.astrid.design.AstridTokens.Space
import com.astrid.design.AstridTokens.TypeScale.Comfortable as Type

/**
 * The four themes iOS offers and stores (`ThemeMode.swift`): Ocean (the default), Light, Dark, and
 * Auto, which follows the system. The wire names are the ones the core's `theme` command stores.
 */
enum class ThemeMode(val wire: String) {
    OCEAN("ocean"), LIGHT("light"), DARK("dark"), AUTO("auto");

    companion object {
        /** The mode stored as [wire], or [fallback] when it is blank or unknown (a brand's default, AATD-90). */
        fun of(wire: String?, fallback: ThemeMode = OCEAN): ThemeMode =
            wire?.trim()?.lowercase()?.let { w -> entries.firstOrNull { it.wire == w } } ?: fallback
    }
}

/** Resolve a mode to its palette. Auto reads the system, as iOS's `effectiveTheme` does. */
fun colorsFor(mode: ThemeMode, systemDark: Boolean): AstridColors = when (mode) {
    ThemeMode.OCEAN -> OceanColors
    ThemeMode.LIGHT -> LightColors
    ThemeMode.DARK -> DarkColors
    ThemeMode.AUTO -> if (systemDark) DarkColors else LightColors
}

/** The tokens' `space.*` (iOS `Theme.spacing*`). */
object Spacing {
    val s2 = Space.s2; val s4 = Space.s4; val s8 = Space.s8; val s12 = Space.s12; val s16 = Space.s16
    val s20 = Space.s20; val s24 = Space.s24; val s32 = Space.s32; val s40 = Space.s40; val s48 = Space.s48
}

/** The tokens' `radius.*` (iOS `Theme.radius*`). */
object Radius {
    val small = AstridTokens.Radius.sm; val medium = AstridTokens.Radius.md
    val large = AstridTokens.Radius.lg; val xLarge = AstridTokens.Radius.xl
}

/**
 * Material components (dialogs, date and time pickers, menus, snackbars, switches, chips, sheets)
 * draw from this scheme. Every role is mapped from the iOS palette, so none of Material's stock
 * lavender shows through in a picker or a switch track (AATD-33).
 */
fun materialScheme(c: AstridColors): androidx.compose.material3.ColorScheme {
    val sheet = c.sheet
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
        errorContainer = Error.copy(alpha = 0.12f), onErrorContainer = c.errorInk,
        outline = c.borderHover, outlineVariant = c.border,
        scrim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
        surfaceBright = sheet, surfaceDim = c.bgTertiary,
        surfaceContainerLowest = sheet, surfaceContainerLow = sheet, surfaceContainer = sheet,
        surfaceContainerHigh = if (c.isDark) c.bgTertiary else c.bgSecondary,
        surfaceContainerHighest = c.bgTertiary,
    )
}

/** iOS `Theme.Typography` (the tokens' comfortable type scale) on Material's slots, as sp so it scales. */
val AstridTypography = Typography(
    displaySmall = style(Type.title1),
    headlineMedium = style(Type.title2),
    headlineSmall = style(Type.title3),
    titleMedium = style(Type.headline),
    bodyLarge = style(Type.body),
    bodyMedium = style(Type.callout),
    bodySmall = style(Type.subheadline),
    labelLarge = style(Type.subheadline).copy(fontWeight = FontWeight.Medium),
    labelMedium = style(Type.footnote),
    labelSmall = style(Type.caption1),
)

private fun style(t: AstridTextStyle) = TextStyle(fontSize = t.size, fontWeight = t.weight)

/** iOS's named styles (the tokens' comfortable type scale), for text that is not a Material component's. */
object TextStyles {
    /** headline: list titles, header titles, section titles. */
    val headline = style(Type.headline)
    val body = style(Type.body)
    val callout = style(Type.callout)
    /** subheadline: row metadata, list chips. */
    val subheadline = style(Type.subheadline)
    val footnote = style(Type.footnote)
    val caption = style(Type.caption1)
    val caption2 = style(Type.caption2)
    /** A row's title: 17 x 1.1. */
    val rowTitle = style(Type.rowTitle)
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
