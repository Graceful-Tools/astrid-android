package com.gracefultools.astrid.ui.common

import androidx.compose.ui.graphics.Color
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.DarkColors
import com.gracefultools.astrid.ui.theme.LightColors
import com.gracefultools.astrid.ui.theme.OceanColors
import org.junit.Assert.assertEquals
import java.io.File
import org.junit.Test

/**
 * ADEV-196 (register DS21): the checkbox is astrid-core's art, drawn in the priority tokens, as
 * iOS's TaskCheckboxAsset and web's CheckboxArt draw it. It is no longer iOS's 16 fixed-colour
 * PNGs, whose low-priority box was always #328ACC, whatever the accent or theme.
 */
class CheckboxArtTest {
    @Test
    fun `ADEV-196 the box, or the arrows when it repeats, then the check once done`() {
        assertEquals(listOf(R.drawable.checkbox_box), checkboxLayers(checked = false, repeating = false))
        assertEquals(listOf(R.drawable.checkbox_box, R.drawable.checkbox_check), checkboxLayers(checked = true, repeating = false))
        assertEquals(listOf(R.drawable.checkbox_repeat), checkboxLayers(checked = false, repeating = true))
        assertEquals(listOf(R.drawable.checkbox_repeat, R.drawable.checkbox_check), checkboxLayers(checked = true, repeating = true))
    }

    @Test
    fun `ADEV-196 the box takes the priority token, and no priority the muted text`() {
        for (colors in listOf(LightColors, DarkColors, OceanColors)) {
            assertEquals(colors.textMuted, checkboxTint(0, colors))
            assertEquals(colors.tokens.priorityLow, checkboxTint(1, colors))
            assertEquals(colors.tokens.priorityMedium, checkboxTint(2, colors))
            assertEquals(colors.tokens.priorityHigh, checkboxTint(3, colors))
            assertEquals(colors.textMuted, checkboxTint(7, colors))
        }
        // Low is the accent on light themes and the accent's lighter ink on Dark (DS26).
        // The old art's box was #328ACC in every theme.
        assertEquals(LightColors.tokens.accent, checkboxTint(1, LightColors))
        assertEquals(Color(0xFF60A5FA), checkboxTint(1, DarkColors))
    }

    @Test
    fun `ADEV-196 none of iOS's 16 checkbox PNGs is left in the resources`() {
        val leftovers = File("src/main/res").walkTopDown()
            .filter { it.isFile && it.name.startsWith("check_box") }
            .map { it.relativeTo(File("src/main/res")).path }
            .sorted().toList()
        assertEquals("the core's checkbox art replaced these (Jon, 2026-11-24: \"Yes. Delete\")", emptyList<String>(), leftovers)
    }
}
