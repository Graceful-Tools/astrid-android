package com.gracefultools.astrid.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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

/** AATD-26: the iPad's columns on a tablet; the iPhone's pushed detail on a phone. */
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
    @Config(qualifiers = "w400dp-h800dp")
    fun `AATD-26 on a phone there is no detail pane and the sidebar is a drawer`() {
        show(core())
        assertEquals(0, compose.onAllNodes(hasTestTag("detailPane")).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithContentDescription("Open the sidebar").fetchSemanticsNodes().size)
    }
}
