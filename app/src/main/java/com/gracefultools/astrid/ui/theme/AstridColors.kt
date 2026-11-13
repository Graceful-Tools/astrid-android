package com.gracefultools.astrid.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.gracefultools.astrid.Brand
import kotlin.math.roundToInt

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
    /** A task row's card: Ocean white 0.8 (1.0 selected), Light glass, Dark bgPrimary (TaskRowView.getCardBackground). */
    val card: Color = bgPrimary,
    val cardSelected: Color = card,
    val cardBorder: Color = border,
    /** Floating headers, the quick-add bar, the comment composer: white on Ocean (FloatingHeader). */
    val floating: Color = headerBg,
    /** The quick-add and composer bar behind its field (white 0.8 on Ocean). */
    val floatingBar: Color = floating,
    /** A text field inside a floating bar: white with a 1pt border on Ocean and Light, black on Dark. */
    val field: Color = inputBg,
    val fieldBorder: Color = inputBorder,
    /** List chips on rows (getBadgeBackground / getBorderColor). */
    val chipBg: Color = bgTertiary,
    val chipBorder: Color = border,
    /** Task detail's page: cyan under white 0.8 on Ocean (TaskDetailViewNew). */
    val pageBg: Color = bgPrimary,
    /** bgHover / buttonHover and the focus ring (Theme.swift). */
    val pressed: Color = bgActive,
    val borderFocus: Color = Accent,
    /** iOS's Divider: neutral grey on every light theme (Ocean's border is cyan, its dividers are not). */
    val divider: Color = if (isDark) border else Color(229, 231, 235),
    /**
     * Each meaning's colour as text or an icon on this theme's own surfaces (astrid-core's `-ink`
     * tokens, DS25, AWTD-1598). The base colours ([accent], [Error], [Success]…) are fills, washes
     * and borders.
     */
    val accentInk: Color = AccentInk,
    val successInk: Color = rgb(21, 128, 61),
    val warningInk: Color = rgb(180, 83, 9),
    val errorInk: Color = rgb(185, 28, 28),
    val infoInk: Color = rgb(14, 116, 144),
    /** Tokens `priority-low`: the accent, and on Dark the accent's light ink (DS26). */
    val priorityLow: Color = Accent,
) {
    val accent: Color get() = Accent
    val accentHover: Color get() = AccentHover
    val accentText: Color get() = AccentText

    /** Priority colours, tokens `priority-*`. Index is the wire priority (0 none … 3 highest). */
    val priority: List<Color> get() = listOf(rgb(107, 114, 128), priorityLow, rgb(251, 191, 36), Overdue)

    /** A chosen priority's fill, under a white mark: low stays the accent on every theme (DS26). */
    val priorityFill: List<Color> get() = listOf(rgb(107, 114, 128), Accent, rgb(251, 191, 36), Overdue)
}

private fun rgb(r: Int, g: Int, b: Int) = Color(r, g, b)

/**
 * iOS `Brand.color(hex)`: a `#rrggbb` / `#rrggbbaa` string that [Brand.resolveAccentHex] has already
 * checked, as a colour.
 */
fun brandColor(hex: String): Color {
    val digits = hex.removePrefix("#")
    val v = digits.toLong(16)
    return if (digits.length == 8) {
        Color(((v shr 24) and 0xFF).toInt(), ((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
    } else {
        Color(((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
    }
}

/** The brand's accent (iOS `Brand.accentColor`, AATD-75) — Astrid blue (blue-600) — and its pressed state. */
val Accent = brandColor(Brand.accentColorHex)
val AccentHover = brandColor(Brand.accentHoverColorHex)
val AccentText = brandColor(Brand.accentTextColorHex)

/**
 * The accent as text on a light card and on a dark one: tokens `brand.accent-ink` /
 * `accent-ink-dark` for Astrid. The tokens cannot derive a partner's, so a partner accent's is
 * worked out from it as astrid-web does (`lib/brand/css-vars.ts`) — unless the brand sets its own
 * (`astridAccentInkColor` / `astridAccentInkDarkColor`, AATD-90).
 */
val AccentInk = brandInk(
    Brand.accentInkHex, Accent, Color.White,
    if (Brand.accentColorHex == Brand.DEFAULT_ACCENT_HEX) rgb(29, 78, 216) else accentInk(Accent, Color.White, 0),
)
val AccentInkDark = brandInk(
    Brand.accentInkDarkHex, Accent, rgb(31, 41, 55),
    if (Brand.accentColorHex == Brand.DEFAULT_ACCENT_HEX) rgb(96, 165, 250) else accentInk(Accent, rgb(31, 41, 55), 255),
)

val Success = rgb(34, 197, 94)
val Warning = rgb(245, 158, 11)
/** Tokens `error`: red-600 since DS26 (AWTD-1598), dark enough for a white label. */
val Error = rgb(220, 38, 38)
val Info = rgb(6, 182, 212)
/** Tokens `overdue` (and `priority-high`): unchanged by DS26, so no longer the same as [Error]. */
val Overdue = rgb(239, 68, 68)

/** WCAG AA for body text, which astrid-core holds every ink to. */
private const val INK_CONTRAST = 4.5

/** How strong a wash is: the accent at 12% over the card. */
private const val WASH_OPACITY = 0.12f

internal fun contrast(a: Color, b: Color): Float {
    val la = a.luminance(); val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Whether [ink] reads at 4.5:1 on [card] and on [accent]'s wash there. */
private fun inkReads(ink: Color, accent: Color, card: Color): Boolean {
    val wash = accent.copy(alpha = WASH_OPACITY).compositeOver(card)
    return contrast(ink, card) >= INK_CONTRAST && contrast(ink, wash) >= INK_CONTRAST
}

/**
 * A brand's own accent ink ([configuredHex], from `Brand`) if it reads on [card] and the accent's
 * wash there, else [derived]. make-brand-variant.sh refuses an unreadable one; this keeps a
 * hand-edited brand.properties from shipping unreadable text.
 */
fun brandInk(configuredHex: String?, accent: Color, card: Color, derived: Color): Color {
    val ink = configuredHex?.let(::brandColor) ?: return derived
    return if (inkReads(ink, accent.copy(alpha = 1f), card)) ink else derived
}

/**
 * astrid-web `accentInk`: [accent] moved towards [toward] (0 on a light card, 255 on a dark one) in
 * twentieths until it reads at 4.5:1 on [card] and on its own wash there.
 */
fun accentInk(accent: Color, card: Color, toward: Int): Color {
    val solid = accent.copy(alpha = 1f)
    val channels = listOf(solid.red, solid.green, solid.blue).map { (it * 255).roundToInt() }
    for (step in 0..20) {
        val (r, g, b) = channels.map { (it + (toward - it) * (step / 20f)).roundToInt() }
        val ink = Color(r, g, b)
        if (inkReads(ink, solid, card)) return ink
    }
    return Color(toward, toward, toward)
}

val LightColors = AstridColors(
    bgPrimary = Color.White, bgSecondary = rgb(249, 250, 251), bgTertiary = rgb(243, 244, 246),
    bgActive = rgb(229, 231, 235), bgSelected = rgb(239, 246, 255), bgSelectedBorder = rgb(191, 219, 254),
    border = rgb(229, 231, 235), borderHover = rgb(209, 213, 219),
    inputBg = Color.White, inputBorder = rgb(209, 213, 219), inputPlaceholder = rgb(156, 163, 175),
    textPrimary = rgb(17, 24, 39), textSecondary = rgb(75, 85, 99), textMuted = rgb(107, 114, 128),
    textInverted = Color.White, headerBg = rgb(249, 250, 251), headerBorder = rgb(229, 231, 235),
    buttonBg = Color.White, buttonBorder = rgb(209, 213, 219), isDark = false,
    // Liquid Glass on white: ultra-thin material reads as a faint grey card with a hairline edge.
    card = rgb(249, 250, 251), cardSelected = Accent.copy(alpha = 0.12f), cardBorder = rgb(229, 231, 235),
    floating = rgb(249, 250, 251).copy(alpha = 0.96f), floatingBar = rgb(249, 250, 251).copy(alpha = 0.96f),
    field = Color.White, fieldBorder = rgb(180, 184, 188),
    chipBg = rgb(243, 244, 246), chipBorder = rgb(229, 231, 235),
    pageBg = Color.White, pressed = rgb(243, 244, 246),
)

val DarkColors = AstridColors(
    bgPrimary = rgb(17, 24, 39), bgSecondary = rgb(31, 41, 55), bgTertiary = rgb(55, 65, 81),
    bgActive = rgb(75, 85, 99), bgSelected = rgb(30, 58, 138), bgSelectedBorder = AccentInkDark,
    border = rgb(75, 85, 99), borderHover = rgb(107, 114, 128),
    inputBg = Color.Black, inputBorder = rgb(75, 85, 99), inputPlaceholder = rgb(156, 163, 175),
    textPrimary = Color.White, textSecondary = rgb(209, 213, 219), textMuted = rgb(156, 163, 175),
    textInverted = rgb(17, 24, 39), headerBg = rgb(31, 41, 55), headerBorder = rgb(75, 85, 99),
    buttonBg = rgb(55, 65, 81), buttonBorder = rgb(75, 85, 99), isDark = true,
    card = rgb(17, 24, 39), cardSelected = rgb(31, 41, 55), cardBorder = rgb(75, 85, 99),
    floating = rgb(31, 41, 55), floatingBar = rgb(31, 41, 55),
    field = Color.Black, fieldBorder = rgb(75, 85, 99),
    chipBg = rgb(31, 41, 55), chipBorder = rgb(75, 85, 99),
    pageBg = rgb(17, 24, 39), pressed = rgb(55, 65, 81),
    // DS26: focus lines, the selection outline and low priority read the light blue on Dark.
    borderFocus = AccentInkDark, priorityLow = AccentInkDark,
    accentInk = AccentInkDark, successInk = rgb(74, 222, 128), warningInk = rgb(251, 191, 36),
    errorInk = rgb(248, 113, 113), infoInk = rgb(34, 211, 238),
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
    card = Color.White.copy(alpha = 0.8f), cardSelected = Color.White, cardBorder = rgb(136, 220, 248),
    floating = Color.White, floatingBar = Color.White.copy(alpha = 0.8f),
    field = Color.White, fieldBorder = rgb(180, 184, 188),
    chipBg = rgb(243, 244, 246), chipBorder = rgb(136, 220, 248),
    // Cyan under white 0.8, flattened.
    pageBg = rgb(231, 248, 254), pressed = rgb(200, 204, 209),
)
