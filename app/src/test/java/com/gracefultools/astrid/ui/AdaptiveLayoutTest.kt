package com.gracefultools.astrid.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.gracefultools.astrid.AppGraph
import com.gracefultools.astrid.DeepLinks
import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.Preferences
import com.gracefultools.astrid.ui.theme.AstridTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** AATD-26 / AATD-61: the iPad's panes on a tablet; the iPhone's pushed detail on a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class AdaptiveLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun core() = FakeCore().apply {
        answer("lists", """[{"id":"l1","name":"Home"}]""")
        answer("myTasksList", """{"id":"virtual:my-tasks","name":"My Tasks","isVirtual":true}""")
        answer("rowsForList", """{"total":1,"rows":[{"id":"t1","title":"Water plants"}]}""")
        answer("taskDetail", """{"task":{"id":"t1","title":"Water plants"},"descriptionBlocks":[],"comments":[],"subtasks":[]}""")
    }

    private fun show(core: FakeCore) {
        val graph = AppGraph(core, CoroutineScope(Dispatchers.Main), Preferences(ApplicationProvider.getApplicationContext()))
        compose.setContent { AstridTheme { AppRoot(graph, onSignedOut = {}, onDeepLink = DeepLinks()) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Water plants").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `AATD-26 on a wide screen the detail opens beside the list and the sidebar stays`() {
        show(core())
        assertEquals(0, compose.onAllNodesWithContentDescription("Open the sidebar").fetchSemanticsNodes().size)
        compose.onNodeWithText("Water plants").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Description").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag("detailPane")).assertIsDisplayed()
        // Both columns at once: the row in the list, and the title in the detail.
        assertEquals(true, compose.onAllNodes(hasText("Water plants")).fetchSemanticsNodes().size >= 2)
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `AATD-61 the list keeps its full width until a task is picked, and a second tap closes the pane`() {
        show(core())
        // No "Select a task" pane held open beside an untouched list (iPadTaskManagerView 380-436).
        assertEquals(0, compose.onAllNodes(hasTestTag("detailPane")).fetchSemanticsNodes().size)
        compose.onAllNodesWithText("Water plants")[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("detailPane")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Water plants")[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("detailPane")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    @Config(qualifiers = "w1200dp-h800dp")
    fun `AATD-61 a wide landscape window pins the list's messages at the right, out of the rotator`() {
        show(core())
        compose.onNode(hasTestTag("messagesPane")).assertIsDisplayed()
        // Messages beside the list are not a view of the main pane: no "Switch to messages".
        assertEquals(0, compose.onAllNodesWithContentDescription("Switch to messages").fetchSemanticsNodes().size)
        // Folded to its rail and back.
        compose.onNodeWithContentDescription("Hide messages").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Show messages").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Show messages").performClick()
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun `AATD-61 a wide portrait window keeps messages in the rotator`() {
        show(core())
        assertEquals(0, compose.onAllNodes(hasTestTag("messagesPane")).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithContentDescription("Switch to messages").fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun `AATD-26 on a phone there is no detail pane and the sidebar is a drawer`() {
        show(core())
        assertEquals(0, compose.onAllNodes(hasTestTag("detailPane")).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithContentDescription("Open the sidebar").fetchSemanticsNodes().size)
    }
}
