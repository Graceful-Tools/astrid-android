package com.gracefultools.astrid.ui.tasks

import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gracefultools.astrid.model.LeadingControl
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.model.UserSummary
import com.gracefultools.astrid.ui.theme.AstridTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AATD-84 (web AWTD-1571): a repeating task that shows a face or the "U" mark draws the repeat
 * arrows around it, as your own repeating checkbox already does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class RepeatFrameTest {
    @get:Rule val compose = createComposeRule()

    private fun show(row: TaskRow, onLeading: () -> Unit = {}) = compose.setContent {
        AstridTheme { TaskRowView(row, onLeading = onLeading, onOpen = {}) }
    }

    private val someoneElse = TaskRow(
        id = "t1", title = "Water plants", priority = 2, isRepeating = true,
        leading = LeadingControl(kind = "avatar", userId = "u2", initial = "AB"),
        assignee = UserSummary(id = "u2", name = "Ann Bee"),
    )

    @Test
    fun `AATD-84 a repeating task assigned to someone else frames their face in the repeat arrows`() {
        var taps = 0
        show(someoneElse) { taps++ }
        compose.onNodeWithTag(REPEAT_FRAME_TAG, useUnmergedTree = true)
            .assertExists().assertWidthIsEqualTo(42.dp).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `AATD-84 a repeating unassigned task frames the U mark in the repeat arrows`() {
        show(someoneElse.copy(leading = LeadingControl(kind = "unassigned"), assignee = null))
        compose.onNodeWithTag(REPEAT_FRAME_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("U", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `AATD-84 a face or U that does not repeat keeps its plain square`() {
        show(someoneElse.copy(isRepeating = false))
        compose.onNodeWithTag(REPEAT_FRAME_TAG, useUnmergedTree = true).assertDoesNotExist()
    }
}
