package com.gracefultools.astrid.ui.detail

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreRules
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.ui.theme.AstridTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ADEV-257 (iOS AITD-579, ADEV-229): a decision posted in the thread is answered there. With a
 * grid of the same options it is one choosable grid and no cards; a row asks, then posts the
 * core's reply. Without a grid its options are cards that post at once. An answered decision
 * takes no tap. The decision is read and rendered by the real core.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h1600dp")
class DecisionThreadScreenTest {
    @get:Rule val compose = createComposeRule()

    private val cards = "**Decision:** Ship today?\n\n- **1.** Ship\n- **2.** Wait"
    private val withGrid = "$cards\n\n| Option | Cost |\n|---|---|\n| 1. Ship | ++ |\n| 2. Wait | - |"

    private fun comment(id: String, content: String, author: String, minute: Int) = buildJsonObject {
        put("id", id); put("content", content); put("authorId", author); put("authorName", author)
        put("createdAt", "2026-11-24T10:%02d:00Z".format(minute))
        put("blocks", CoreRules.ask(command("renderMarkdown", "text" to content)) as JsonArray)
    }

    private fun open(vararg comments: kotlinx.serialization.json.JsonObject): FakeCore {
        val thread = buildJsonArray { comments.forEach { add(it) } }
        val core = FakeCore().apply {
            answer("taskDetail", """{"task":{"id":"t1","title":"Plan"},"fieldOrder":[],"descriptionBlocks":[],"comments":$thread,"subtasks":[]}""")
        }
        val vm = TaskDetailViewModel(core, "t1", Dispatchers.Unconfined)
        compose.setContent { AstridTheme { TaskDetailScreen(vm, onBack = {}) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("detail:comment")).fetchSemanticsNodes().isNotEmpty() }
        return core
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `ADEV-257 a decision with a grid is one choosable grid, and a confirmed row posts its reply`() {
        val core = open(comment("q", withGrid, "agent", 1))
        assertTrue("the grid's rows are the options", exists("decision.row.2"))
        assertFalse("no cards beside the grid", exists("decision.option.1"))

        compose.onNodeWithTag("decision.row.2").performScrollTo().performClick()
        compose.waitForIdle()
        assertNull("a row asks before it posts", core.last("postComment"))
        compose.onNodeWithTag("decision.confirm").performClick()
        compose.waitForIdle()
        assertEquals(JsonPrimitive("2 — Wait"), core.last("postComment")!!["content"])
    }

    @Test
    fun `ADEV-257 a decision without a grid shows cards that post at once`() {
        val core = open(comment("q", cards, "agent", 1))
        assertTrue(exists("decision.option.1"))
        compose.onNodeWithTag("decision.option.1").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(JsonPrimitive("1 — Ship"), core.last("postComment")!!["content"])
    }

    @Test
    fun `ADEV-257 an answered decision takes no tap`() {
        val core = open(comment("q", withGrid, "agent", 1), comment("r", "1 — Ship", "jon", 2))
        // Not a button any more, so it merges into the bubble: find it unmerged.
        compose.onNodeWithTag("decision.row.2", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse("no confirmation once answered", exists("decision.confirm"))
        assertNull(core.last("postComment"))
    }
}
