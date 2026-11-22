package com.gracefultools.astrid.ui.board

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.astrid.design.AstridTokens
import com.gracefultools.astrid.model.Board
import com.gracefultools.astrid.model.BoardColumn
import com.gracefultools.astrid.ui.common.CHARACTER_TAG
import com.gracefultools.astrid.ui.theme.AstridTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADEV-196: the board's layout comes from the tokens' comfortable density, as iOS's
 * ProjectStatusBoardView and BoardColumnView read it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w1000dp-h900dp-mdpi")
class BoardLayoutTokensTest {
    @get:Rule val compose = createComposeRule()

    private val layout = AstridTokens.Layout.Comfortable

    private fun show() = compose.setContent {
        AstridTheme {
            BoardContent(
                BoardState(listId = "l", board = Board(listId = "l", columns = listOf(
                    BoardColumn(id = "todo", name = "To do"),
                    BoardColumn(id = "doing", name = "Doing"),
                ))),
                onOpenTask = {}, onMove = { _, _ -> }, onComplete = {},
            )
        }
    }

    @Test
    fun `ADEV-196 columns sit board-column-gap apart, half of it at the screen edge`() {
        show()
        val first = compose.onNodeWithTag("board.column.todo").getBoundsInRoot()
        val second = compose.onNodeWithTag("board.column.doing").getBoundsInRoot()
        assertEquals(layout.boardColumnGap / 2, first.left)
        assertEquals(layout.boardColumnGap, second.left - first.right)
    }

    @Test
    fun `ADEV-196 an empty column draws Astrid at empty-art in an empty-column-min-height band`() {
        show()
        val art = compose.onNodeWithTag("board.column.todo.empty", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(layout.emptyColumnMinHeight, art.bottom - art.top)
        val character = compose.onAllNodesWithTag(CHARACTER_TAG, useUnmergedTree = true)[0].getBoundsInRoot()
        assertEquals(layout.emptyArt, character.right - character.left)
    }
}
