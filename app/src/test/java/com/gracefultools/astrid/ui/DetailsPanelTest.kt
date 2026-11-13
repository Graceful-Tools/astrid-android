package com.gracefultools.astrid.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.astrid.design.AstridTokens
import com.gracefultools.astrid.AppGraph
import com.gracefultools.astrid.DeepLinks
import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.Preferences
import com.gracefultools.astrid.model.LeadingControl
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.common.SelectionOutline
import com.gracefultools.astrid.ui.common.SelectionOutlineStyle
import com.gracefultools.astrid.ui.tasks.TaskRowView
import com.gracefultools.astrid.ui.theme.AstridTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AATD-85, registers DS5 and DS7 (Jon, 2026-11-10): the selected row wears the shared selection
 * outline, and where the list and the details share the screen the details open out of that row —
 * the same outline on the panel, and an arrow on its leading edge level with the row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class DetailsPanelTest {
    @get:Rule val compose = createComposeRule()

    private val row = TaskRow(id = "t1", title = "Water plants", priority = 0, leading = LeadingControl(kind = "checkbox"))

    private fun expectedOutline(focus: Color) = SelectionOutlineStyle(
        width = AstridTokens.Size.Comfortable.selectionOutline,
        color = focus.copy(alpha = AstridTokens.State.selectionOutlineOpacity),
    )

    private fun outlineOf(tag: String) =
        compose.onNode(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SelectionOutline)

    @Test
    @Config(qualifiers = "w400dp-h900dp")
    fun `AATD-85 a selected row wears border-focus at 55 percent, 2,5 wide`() {
        var focus = Color.Unspecified
        compose.setContent { AstridTheme { focus = AstridTheme.colors.borderFocus; TaskRowView(row, onLeading = {}, onOpen = {}, isSelected = true) } }
        assertEquals(expectedOutline(focus), outlineOf("taskRow"))
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp")
    fun `AATD-85 an unselected row has no selection outline`() {
        compose.setContent { AstridTheme { TaskRowView(row, onLeading = {}, onOpen = {}) } }
        assertEquals(null, outlineOf("taskRow"))
    }

    private fun showApp() {
        val core = FakeCore().apply {
            answer("sidebar", """{"myTasks":{"id":"virtual:my-tasks","name":"My Tasks","isVirtual":true},"lists":[{"id":"l1","name":"Home"}]}""")
            answer("rowsForList", """{"total":2,"rows":[{"id":"t0","title":"Feed the cat"},{"id":"t1","title":"Water plants"}]}""")
            answer("taskDetail", """{"task":{"id":"t1","title":"Water plants"},"descriptionBlocks":[],"comments":[],"subtasks":[]}""")
        }
        val graph = AppGraph(core, CoroutineScope(Dispatchers.Main), Preferences(ApplicationProvider.getApplicationContext()))
        compose.setContent { AstridTheme { AppRoot(graph, onSignedOut = {}, onDeepLink = DeepLinks()) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Water plants").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `AATD-85 the details pane wears the selection outline and its arrow is level with the row`() {
        showApp()
        compose.onAllNodesWithText("Water plants")[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("detailPane")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()

        val outline = outlineOf("detailPane")
        assertTrue("the pane should wear the selection outline", outline != null)
        assertEquals(AstridTokens.Size.Comfortable.selectionOutline, outline!!.width)
        assertEquals(AstridTokens.State.selectionOutlineOpacity, outline.color.alpha, 0.01f)

        val selectedRow = compose.onAllNodes(hasTestTag("taskRow"), useUnmergedTree = true).fetchSemanticsNodes()
            .single { it.config.getOrNull(SelectionOutline) != null }
        val arrow = compose.onNode(hasTestTag("detailsArrow"), useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(selectedRow.boundsInRoot.center.y, arrow.boundsInRoot.center.y, 2f)
        // On the pane's leading edge, pointing back at the list.
        val pane = compose.onNode(hasTestTag("detailPane"), useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("arrow should sit at the pane's leading edge", arrow.boundsInRoot.right <= pane.boundsInRoot.left + 1f)
        assertFalse(arrow.boundsInRoot.isEmpty)
    }
}
