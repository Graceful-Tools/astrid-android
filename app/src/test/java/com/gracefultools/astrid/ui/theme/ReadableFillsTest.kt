package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.gracefultools.astrid.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AWTD-1598: the shared tokens (astrid-core `design/tokens.json`, registers DS25 and DS26) made the
 * accent and error fills dark enough for a white label, added an "ink" per meaning for text on the
 * theme's own surfaces, and moved Dark's focus line, selection outline and low priority to the
 * lighter blue. Overdue and high priority are unchanged.
 */
class ReadableFillsTest {
    private fun rgb(r: Int, g: Int, b: Int) = Color(r, g, b)

    @Test
    fun `AWTD-1598 the accent and error fills are the darker shared tokens`() {
        assertEquals("#2563eb", Brand.DEFAULT_ACCENT_HEX)
        assertEquals("#2563eb", Brand.accentColorHex)
        assertEquals("#1d4ed8", Brand.accentHoverColorHex)
        assertEquals(rgb(37, 99, 235), Accent)
        assertEquals(rgb(29, 78, 216), AccentHover)
        assertEquals(rgb(220, 38, 38), Error)
    }

    @Test
    fun `AWTD-1598 overdue and high priority keep their red`() {
        assertEquals(rgb(239, 68, 68), Overdue)
        for (c in listOf(LightColors, DarkColors, OceanColors)) assertEquals(rgb(239, 68, 68), c.priority[3])
    }

    @Test
    fun `AWTD-1598 light themes read the dark inks`() {
        for (c in listOf(LightColors, OceanColors)) {
            assertEquals(rgb(29, 78, 216), c.accentInk)
            assertEquals(rgb(21, 128, 61), c.successInk)
            assertEquals(rgb(180, 83, 9), c.warningInk)
            assertEquals(rgb(185, 28, 28), c.errorInk)
            assertEquals(rgb(14, 116, 144), c.infoInk)
            assertEquals(Accent, c.borderFocus)
            assertEquals(Accent, c.priority[1])
            assertEquals(Accent, c.priorityFill[1])
        }
    }

    @Test
    fun `AWTD-1598 Dark reads the light inks, and its focus, selection and low priority are the light blue`() {
        val lightBlue = rgb(96, 165, 250)
        with(DarkColors) {
            assertEquals(lightBlue, accentInk)
            assertEquals(rgb(74, 222, 128), successInk)
            assertEquals(rgb(251, 191, 36), warningInk)
            assertEquals(rgb(248, 113, 113), errorInk)
            assertEquals(rgb(34, 211, 238), infoInk)
            assertEquals(lightBlue, borderFocus)
            assertEquals(lightBlue, bgSelectedBorder)
            assertEquals(lightBlue, priority[1])
            // The low-priority fill stays the accent, under its white mark; only the line and text move.
            assertEquals(Accent, priorityFill[1])
        }
    }

    @Test
    fun `AWTD-1598 the Material scheme's error is the fill and its container text the ink`() {
        for (c in listOf(LightColors, DarkColors, OceanColors)) {
            val scheme = materialScheme(c)
            assertEquals(Error, scheme.error)
            assertEquals(c.errorInk, scheme.onErrorContainer)
        }
    }

    @Test
    fun `AWTD-1598 a partner accent gets inks that read on the card and on its own wash`() {
        val teal = rgb(13, 148, 136)
        for ((card, toward) in listOf(Color.White to 0, rgb(31, 41, 55) to 255)) {
            val ink = accentInk(teal, card, toward)
            val wash = teal.copy(alpha = 0.12f).compositeOver(card)
            assertTrue("ink $ink on $card", contrast(ink, card) >= 4.5)
            assertTrue("ink $ink on its wash", contrast(ink, wash) >= 4.5)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
