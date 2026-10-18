package com.gracefultools.astrid.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The iOS app's palette (astrid-ios `Utilities/Theme.swift`), token for token. A colour here that
 * differs from iOS is a design divergence and needs a reason in docs/PARITY.md.
 */
@Immutable
data class AstridColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val bgTertiary: Color,
    val bgActive: Color,
    val bgSelected: Color,
    val bgSelectedBorder: Color,
    val border: Color,
    val borderHover: Color,
    val inputBg: Color,
    val inputBorder: Color,
    val inputPlaceholder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textInverted: Color,
    val headerBg: Color,
    val headerBorder: Color,
    val buttonBg: Color,
    val buttonBorder: Color,
    val isDark: Boolean,
) {
    val accent: Color get() = Accent
    val accentHover: Color get() = AccentHover
    val accentText: Color get() = AccentText
}

private fun rgb(r: Int, g: Int, b: Int) = Color(r, g, b)

/** Brand.swift `defaultAccentHex` — Astrid blue (Tailwind blue-500), and its pressed state. */
val Accent = rgb(59, 130, 246)
val AccentHover = rgb(37, 99, 235)
val AccentText = Color.White

val Success = rgb(34, 197, 94)
val Warning = rgb(245, 158, 11)
val Error = rgb(239, 68, 68)
val Info = rgb(6, 182, 212)

/** Priority colours, iOS `Theme.priority*`. Index is the wire priority (0 none … 3 highest). */
val PriorityColors = listOf(rgb(107, 114, 128), Accent, rgb(251, 191, 36), rgb(239, 68, 68))

val LightColors = AstridColors(
    bgPrimary = Color.White, bgSecondary = rgb(249, 250, 251), bgTertiary = rgb(243, 244, 246),
    bgActive = rgb(229, 231, 235), bgSelected = rgb(239, 246, 255), bgSelectedBorder = rgb(191, 219, 254),
    border = rgb(229, 231, 235), borderHover = rgb(209, 213, 219),
    inputBg = Color.White, inputBorder = rgb(209, 213, 219), inputPlaceholder = rgb(156, 163, 175),
    textPrimary = rgb(17, 24, 39), textSecondary = rgb(75, 85, 99), textMuted = rgb(107, 114, 128),
    textInverted = Color.White, headerBg = rgb(249, 250, 251), headerBorder = rgb(229, 231, 235),
    buttonBg = Color.White, buttonBorder = rgb(209, 213, 219), isDark = false,
)

val DarkColors = AstridColors(
    bgPrimary = rgb(17, 24, 39), bgSecondary = rgb(31, 41, 55), bgTertiary = rgb(55, 65, 81),
    bgActive = rgb(75, 85, 99), bgSelected = rgb(30, 58, 138), bgSelectedBorder = Accent,
    border = rgb(75, 85, 99), borderHover = rgb(107, 114, 128),
    inputBg = Color.Black, inputBorder = rgb(75, 85, 99), inputPlaceholder = rgb(156, 163, 175),
    textPrimary = Color.White, textSecondary = rgb(209, 213, 219), textMuted = rgb(156, 163, 175),
    textInverted = rgb(17, 24, 39), headerBg = rgb(31, 41, 55), headerBorder = rgb(75, 85, 99),
    buttonBg = rgb(55, 65, 81), buttonBorder = rgb(75, 85, 99), isDark = true,
)

/** iOS's default theme: cyan behind white cards, silver inputs. */
val OceanColors = AstridColors(
    bgPrimary = rgb(136, 220, 248), bgSecondary = rgb(249, 250, 251), bgTertiary = rgb(243, 244, 246),
    bgActive = rgb(229, 231, 235), bgSelected = rgb(239, 246, 255), bgSelectedBorder = rgb(191, 219, 254),
    border = rgb(136, 220, 248), borderHover = rgb(209, 213, 219),
    inputBg = rgb(220, 223, 228), inputBorder = rgb(180, 184, 188), inputPlaceholder = rgb(107, 114, 128),
    textPrimary = rgb(17, 24, 39), textSecondary = rgb(75, 85, 99), textMuted = rgb(107, 114, 128),
    textInverted = Color.White, headerBg = rgb(136, 220, 248), headerBorder = rgb(136, 220, 248),
    buttonBg = rgb(220, 223, 228), buttonBorder = rgb(180, 184, 188), isDark = false,
)
