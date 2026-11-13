package com.gracefultools.astrid.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.astrid.design.AstridAppearance
import com.astrid.design.AstridBrand
import com.astrid.design.AstridPalette
import com.astrid.design.AstridTokens
import com.gracefultools.astrid.Brand
import kotlin.math.roundToInt

/**
 * The palette in force: astrid-core's generated tokens for the theme ([tokens], AATD-85), plus the
 * surfaces iOS draws that the tokens do not name (cards, floating bars, fields, chips). A
 * token-named colour here that differs from its token is a design divergence and needs a reason in
 * docs/PARITY.md.
 */
@Immutable
data class AstridColors(
    /** The theme's generated tokens (`com.astrid.design.AstridTokens`), including the ones below. */
    val tokens: AstridPalette,
    val bgPrimary: Color = tokens.bgPrimary,
    val bgSecondary: Color = tokens.bgSecondary,
    val bgTertiary: Color = tokens.bgTertiary,
    val bgActive: Color = tokens.bgActive,
    val bgSelected: Color = tokens.bgSelected,
    val bgSelectedBorder: Color = tokens.bgSelectedBorder,
    val border: Color = tokens.border,
    val borderHover: Color = tokens.borderHover,
    val inputBg: Color = tokens.inputBg,
    val inputBorder: Color = tokens.inputBorder,
    val inputPlaceholder: Color = tokens.inputPlaceholder,
    val textPrimary: Color = tokens.textPrimary,
    val textSecondary: Color = tokens.textSecondary,
    val textMuted: Color = tokens.textMuted,
    val textInverted: Color = tokens.textInverted,
    val headerBg: Color = tokens.headerBg,
    val headerBorder: Color = tokens.headerBorder,
    val buttonBg: Color = tokens.buttonBg,
    val buttonBorder: Color = tokens.buttonBorder,
    val isDark: Boolean = tokens.appearance == AstridAppearance.DARK,
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
    /** Sheets, dialogs and grouped form cards: white on the light themes, `bg-secondary` on Dark (Material's surface). */
    val sheet: Color = if (isDark) bgSecondary else Color.White,
    /** List chips on rows (getBadgeBackground / getBorderColor). */
    val chipBg: Color = bgTertiary,
    val chipBorder: Color = border,
    /** Task detail's page: cyan under white 0.8 on Ocean (TaskDetailViewNew). */
    val pageBg: Color = bgPrimary,
    /** bgHover / buttonHover (tokens) and the focus ring. */
    val pressed: Color = bgActive,
    val borderFocus: Color = tokens.borderFocus,
    /** iOS's Divider: neutral grey on every light theme (Ocean's border is cyan, its dividers are not). */
    val divider: Color = if (isDark) border else LightTokens.border,
    /**
     * Each meaning's colour as text or an icon on this theme's own surfaces (the `-ink` tokens,
     * DS25, AWTD-1598). The base colours ([accent], [error], [success]…) are fills, washes and borders.
     */
    val accentInk: Color = tokens.accentInk,
    val successInk: Color = tokens.successInk,
    val warningInk: Color = tokens.warningInk,
    val errorInk: Color = tokens.errorInk,
    val infoInk: Color = tokens.infoInk,
    /** Tokens `priority-low`: the accent, and on Dark the accent's light ink (DS26). */
    val priorityLow: Color = tokens.priorityLow,
) {
    val accent: Color get() = tokens.accent
    val accentHover: Color get() = tokens.accentHover
    val accentText: Color get() = tokens.accentText
    val success: Color get() = tokens.success
    val warning: Color get() = tokens.warning
    val error: Color get() = tokens.error
    val info: Color get() = tokens.info
    val overdue: Color get() = tokens.overdue

    /** Priority colours, tokens `priority-*`. Index is the wire priority (0 none … 3 highest). */
    val priority: List<Color> get() = listOf(tokens.priorityNone, priorityLow, tokens.priorityMedium, tokens.priorityHigh)

    /** A chosen priority's fill, under a white mark: low stays the accent on every theme (DS26). */
    val priorityFill: List<Color> get() = listOf(tokens.priorityNone, tokens.accent, tokens.priorityMedium, tokens.priorityHigh)
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

/** Astrid's brand as the tokens take it: the accent and its inks from [Brand] (AATD-75, AATD-90). */
val AstridBrandColors = AstridBrand(
    accent = Accent, accentHover = AccentHover, accentInk = AccentInk,
    accentInkDark = AccentInkDark, accentText = AccentText,
)

private val LightTokens = AstridTokens.light(AstridBrandColors)
private val DarkTokens = AstridTokens.dark(AstridBrandColors)
private val OceanTokens = AstridTokens.ocean(AstridBrandColors)

// The meanings' fills are the same on every theme; only their inks differ.
val Success = LightTokens.success
val Warning = LightTokens.warning
/** Tokens `error`: red-600 since DS26 (AWTD-1598), dark enough for a white label. */
val Error = LightTokens.error
val Info = LightTokens.info
/** Tokens `overdue` (and `priority-high`): unchanged by DS26, so no longer the same as [Error]. */
val Overdue = LightTokens.overdue

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
    tokens = LightTokens,
    // Liquid Glass on white: ultra-thin material reads as a faint grey card with a hairline edge.
    card = LightTokens.bgSecondary, cardSelected = Accent.copy(alpha = 0.12f),
    floating = LightTokens.bgSecondary.copy(alpha = 0.96f), floatingBar = LightTokens.bgSecondary.copy(alpha = 0.96f),
    field = Color.White, fieldBorder = OceanTokens.inputBorder,
    pressed = LightTokens.buttonHover,
)

val DarkColors = AstridColors(
    tokens = DarkTokens,
    cardSelected = DarkTokens.bgSecondary,
    floating = DarkTokens.bgSecondary,
    chipBg = DarkTokens.bgSecondary,
    pressed = DarkTokens.bgHover,
)

/** iOS's default theme: cyan behind white cards, silver inputs, and the darker text (DS1, AATD-85). */
val OceanColors = AstridColors(
    tokens = OceanTokens,
    card = Color.White.copy(alpha = 0.8f), cardSelected = Color.White,
    floating = Color.White, floatingBar = Color.White.copy(alpha = 0.8f),
    field = Color.White,
    // Cyan under white 0.8, flattened.
    pageBg = rgb(231, 248, 254), pressed = OceanTokens.buttonHover,
)
