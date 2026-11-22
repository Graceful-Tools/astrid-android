package com.gracefultools.astrid.ui.markdown

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import com.astrid.design.AstridTokens.TypeScale.Comfortable as Type
import com.gracefultools.astrid.ui.theme.AstridTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ADEV-196 (core register DS24): markdown text reads the tokens' type scale. Headings h1 to h4 are
 * `type.comfortable.markdown-h1` to `-h4` (28 / 22 / 17 / 17, semibold), as web reads them
 * (AWTD-1625); h5 and h6 stay at h4 so a heading is never smaller than the paragraph under it
 * (iOS `MarkdownText.headingFont`). A paragraph is `body` and code is `footnote`, iOS's
 * `MarkdownStyle` body and code. Android had written 24 / 21 / 19 / 17 / 16 / 15, a 16 paragraph
 * and 14 code.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class MarkdownTypeTest {
    @get:Rule val compose = createComposeRule()

    private fun heading(level: Int) = """{"kind":"heading","level":$level,"inlines":[{"kind":"text","text":"H$level"}]}"""

    private fun show() {
        val json = "[" + (1..6).joinToString(",") { heading(it) } +
            """,{"kind":"paragraph","inlines":[{"kind":"text","text":"Para"}]}""" +
            """,{"kind":"code","text":"val x = 1"}]"""
        val blocks = (Json.parseToJsonElement(json) as JsonArray).map { it as JsonObject }
        compose.setContent { AstridTheme { MarkdownView(blocks) } }
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    @Test
    fun `ADEV-196 markdown headings read the token type scale`() {
        show()
        val expected = listOf(Type.markdownH1, Type.markdownH2, Type.markdownH3, Type.markdownH4, Type.markdownH4, Type.markdownH4)
        expected.forEachIndexed { i, token ->
            val style = compose.onNodeWithText("H${i + 1}").layout().layoutInput.style
            assertEquals("h${i + 1} size", token.size, style.fontSize)
            assertEquals("h${i + 1} weight", token.weight, style.fontWeight ?: FontWeight.Normal)
        }
    }

    @Test
    fun `ADEV-196 a markdown paragraph is body and code is footnote`() {
        show()
        assertEquals(Type.body.size, compose.onNodeWithText("Para").layout().layoutInput.style.fontSize)
        assertEquals(Type.footnote.size, compose.onNodeWithText("val x = 1").layout().layoutInput.style.fontSize)
    }
}
