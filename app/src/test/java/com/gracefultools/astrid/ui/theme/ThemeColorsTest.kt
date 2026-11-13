package com.gracefultools.astrid.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.text.TextLayoutResult
import com.astrid.design.AstridBrand
import com.astrid.design.AstridTokens
import com.gracefultools.astrid.model.RepeatPattern
import com.gracefultools.astrid.ui.detail.CustomRepeatDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AATD-85 step 5 (colours): sheets and form cards read one theme colour instead of each screen
 * spelling out "dark: bg-secondary, otherwise white", and text on the accent reads the brand's
 * `accentText` (iOS `Brand.accentTextColorHex`) instead of hard white.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class ThemeColorsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `AATD-85 a sheet is white on Light and Ocean and bg-secondary on Dark, and is Material's surface`() {
        assertEquals(Color.White, LightColors.sheet)
        assertEquals(Color.White, OceanColors.sheet)
        assertEquals(DarkColors.bgSecondary, DarkColors.sheet)
        for (c in listOf(LightColors, DarkColors, OceanColors)) assertEquals(c.sheet, materialScheme(c).surface)
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp")
    fun `AATD-85 a chosen weekday in a custom repeat draws the brand's accentText, not white`() {
        val ink = Color(0xFF111111)
        val pale = AstridColors(tokens = AstridTokens.light(AstridBrand(accent = Color(0xFFFFE066), accentText = ink)))
        compose.setContent {
            CompositionLocalProvider(LocalAstridColors provides pale) {
                CustomRepeatDialog(RepeatPattern(unit = "weeks", interval = 1, weekdays = listOf("wednesday")), null, { _, _ -> }, {})
            }
        }
        val label = compose.onNode(hasContentDescription("Wednesday"), useUnmergedTree = true).onChildAt(0).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        label.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals(ink, layouts.single().layoutInput.style.color)
    }
}
