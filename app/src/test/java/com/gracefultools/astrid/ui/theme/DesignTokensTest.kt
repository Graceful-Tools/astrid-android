package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.Color
import com.astrid.design.AstridBrand
import com.astrid.design.AstridPalette
import com.astrid.design.AstridTokens
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AATD-85: the palette is astrid-core's generated tokens (`design/generated/android/AstridTokens.kt`,
 * vendored at the pinned core revision), not a hand copy of iOS's `Theme.swift`. Every colour the
 * tokens name and the app draws reads the token's value, on every theme.
 */
class DesignTokensTest {
    private val astrid = AstridBrand(
        accent = Accent, accentHover = AccentHover, accentInk = AccentInk,
        accentInkDark = AccentInkDark, accentText = AccentText,
    )

    private val themes = listOf(
        Triple("light", LightColors, AstridTokens.light(astrid)),
        Triple("dark", DarkColors, AstridTokens.dark(astrid)),
        Triple("ocean", OceanColors, AstridTokens.ocean(astrid)),
    )

    private fun named(c: AstridColors): Map<String, Color> = mapOf(
        "accent" to c.accent, "accentHover" to c.accentHover, "accentText" to c.accentText,
        "accentInk" to c.accentInk, "successInk" to c.successInk, "warningInk" to c.warningInk,
        "errorInk" to c.errorInk, "infoInk" to c.infoInk,
        "bgPrimary" to c.bgPrimary, "bgSecondary" to c.bgSecondary, "bgTertiary" to c.bgTertiary,
        "bgActive" to c.bgActive, "bgSelected" to c.bgSelected, "bgSelectedBorder" to c.bgSelectedBorder,
        "border" to c.border, "borderHover" to c.borderHover, "borderFocus" to c.borderFocus,
        "inputBg" to c.inputBg, "inputBorder" to c.inputBorder, "inputPlaceholder" to c.inputPlaceholder,
        "textPrimary" to c.textPrimary, "textSecondary" to c.textSecondary, "textMuted" to c.textMuted,
        "textInverted" to c.textInverted, "headerBg" to c.headerBg, "headerBorder" to c.headerBorder,
        "buttonBg" to c.buttonBg, "buttonBorder" to c.buttonBorder, "priorityLow" to c.priorityLow,
        "priorityNone" to c.priority[0], "priorityMedium" to c.priority[2], "priorityHigh" to c.priority[3],
    )

    private fun named(p: AstridPalette): Map<String, Color> = mapOf(
        "accent" to p.accent, "accentHover" to p.accentHover, "accentText" to p.accentText,
        "accentInk" to p.accentInk, "successInk" to p.successInk, "warningInk" to p.warningInk,
        "errorInk" to p.errorInk, "infoInk" to p.infoInk,
        "bgPrimary" to p.bgPrimary, "bgSecondary" to p.bgSecondary, "bgTertiary" to p.bgTertiary,
        "bgActive" to p.bgActive, "bgSelected" to p.bgSelected, "bgSelectedBorder" to p.bgSelectedBorder,
        "border" to p.border, "borderHover" to p.borderHover, "borderFocus" to p.borderFocus,
        "inputBg" to p.inputBg, "inputBorder" to p.inputBorder, "inputPlaceholder" to p.inputPlaceholder,
        "textPrimary" to p.textPrimary, "textSecondary" to p.textSecondary, "textMuted" to p.textMuted,
        "textInverted" to p.textInverted, "headerBg" to p.headerBg, "headerBorder" to p.headerBorder,
        "buttonBg" to p.buttonBg, "buttonBorder" to p.buttonBorder, "priorityLow" to p.priorityLow,
        "priorityNone" to p.priorityNone, "priorityMedium" to p.priorityMedium, "priorityHigh" to p.priorityHigh,
    )

    @Test
    fun `AATD-85 every theme's colours are the generated tokens`() {
        for ((name, colors, tokens) in themes) {
            val want = named(tokens)
            val got = named(colors)
            val wrong = want.keys.filter { want[it] != got[it] }.map { "$it: ${got[it]} != token ${want[it]}" }
            assertEquals("$name differs from the tokens", emptyList<String>(), wrong)
        }
    }

    @Test
    fun `AATD-85 Ocean's text is the darker text from the tokens (DS1)`() {
        assertEquals(Color(0xFF000000), OceanColors.textPrimary)
        assertEquals(Color(0xFF374151), OceanColors.textSecondary)
        assertEquals(Color(0xFF4B5563), OceanColors.textMuted)
    }

    @Test
    fun `AATD-85 the token colours the app had no name for are reachable from the palette`() {
        for ((_, colors, tokens) in themes) {
            assertEquals(tokens.agentBubble, colors.tokens.agentBubble)
            assertEquals(tokens.categoryPurple, colors.tokens.categoryPurple)
            assertEquals(tokens.error, colors.error)
            assertEquals(tokens.success, colors.success)
            assertEquals(tokens.overdue, colors.overdue)
        }
    }
}
