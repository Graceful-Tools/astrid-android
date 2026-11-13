package com.gracefultools.astrid.ui.tasks

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.gracefultools.astrid.Preferences
import com.gracefultools.astrid.model.LeadingControl
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.LocalRowTitleLines
import com.gracefultools.astrid.ui.theme.RowTitleLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AATD-85, register DS19 (Jon, 2026-11-10): a row's title wraps without limit by default, and the
 * Appearance setting "Task titles" chooses wrap, two lines or one, kept on this device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class RowTitleLinesTest {
    @get:Rule val compose = createComposeRule()

    private val title = "Water the plants on the balcony, then the ones in the hall, then repot the fern " +
        "that has outgrown its pot, and finally sweep up the soil that always ends up on the floor"
    private val row = TaskRow(id = "t1", title = title, priority = 2, leading = LeadingControl(kind = "checkbox"))

    private fun titleLines(lines: RowTitleLines?): Int {
        compose.setContent {
            AstridTheme {
                if (lines == null) TaskRowView(row, onLeading = {}, onOpen = {})
                else CompositionLocalProvider(LocalRowTitleLines provides lines) { TaskRowView(row, onLeading = {}, onOpen = {}) }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single().lineCount
    }

    @Test
    fun `AATD-85 a long title wraps without limit by default`() {
        assertTrue("the title needs more than two lines here", titleLines(null) > 2)
    }

    @Test
    fun `AATD-85 Two lines stops a row title at two lines`() {
        assertEquals(2, titleLines(RowTitleLines.TWO))
    }

    @Test
    fun `AATD-85 One line keeps a row title on one line`() {
        assertEquals(1, titleLines(RowTitleLines.ONE))
    }

    @Test
    fun `AATD-85 the Task titles choice is kept on this device, under web's values`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(RowTitleLines.WRAP, Preferences(context).rowTitleLines.value)
        Preferences(context).setRowTitleLines(RowTitleLines.TWO)
        assertEquals(RowTitleLines.TWO, Preferences(context).rowTitleLines.value)
        assertEquals("2", context.getSharedPreferences("astrid", 0).getString("rowTitleLines", null))
    }

    @Test
    fun `AATD-85 an unknown stored value reads as Wrap`() {
        assertEquals(RowTitleLines.WRAP, RowTitleLines.of("3"))
        assertEquals(RowTitleLines.WRAP, RowTitleLines.of(null))
        assertEquals(RowTitleLines.ONE, RowTitleLines.of("1"))
    }
}
