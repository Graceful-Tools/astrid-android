package com.gracefultools.astrid.ui.detail

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.ui.theme.AstridTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * ADEV-241 (iOS ADEV-189): from a task's comment bar, "Ask for a decision" opens the composer; a
 * question, two options, a criterion and one light post the decision markdown as a MARKDOWN
 * comment, or add it after the description. The lights and the preview come from the real core.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class DecisionComposerTest {
    @get:Rule val compose = createComposeRule()

    private fun core(description: String = "") = FakeCore().apply {
        answer(
            "taskDetail",
            """{"task":{"id":"t1","title":"Plan","description":"$description"},"fieldOrder":[],
                "descriptionBlocks":[],"comments":[],"subtasks":[]}""",
        )
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun field(tag: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun type(tag: String, text: String) = field(tag).performScrollTo().performTextInput(text)

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Post / Add, in the top bar outside the scrolling form. */
    private fun done() {
        compose.onNodeWithTag("decisionComposer.done").performClick()
        compose.waitForIdle()
    }

    private fun open(core: FakeCore) {
        val vm = TaskDetailViewModel(core, "t1", Dispatchers.Unconfined)
        compose.setContent { AstridTheme { TaskDetailScreen(vm, onBack = {}) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("detail:comment")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Attach a file")).performClick()
        compose.onNodeWithTag("detail:askForDecision").performClick()
        compose.waitForIdle()
        assertTrue("the composer opens", exists("decisionComposer"))
    }

    /** A question, two options, one criterion, and the first option rated decisive on it. */
    private fun writeDecision() {
        type("decisionComposer.question", "Ship today?")
        type("decisionComposer.option.0", "Ship")
        type("decisionComposer.option.1", "Wait")
        tap("decisionComposer.addCriterion")
        type("decisionComposer.criterion.0", "Cost")
        tap("decisionComposer.light.0.0.4")
    }

    private val expected = """
        **Decision:** Ship today?

        - **1.** Ship
        - **2.** Wait

        | Option | Cost |
        |---|---|
        | 1. Ship | ++ |
        | 2. Wait |  |
    """.trimIndent()

    @Test
    fun `ADEV-241 a question, two options and a light post the decision as a markdown comment`() {
        val core = core()
        open(core)
        writeDecision()
        assertTrue("the preview draws the trade-off grid", exists("tradeoff:key"))
        done()

        val sent = core.last("postComment")!!
        assertEquals(JsonPrimitive(expected), sent["content"])
        assertEquals(JsonPrimitive("MARKDOWN"), sent["type"])
        assertFalse("the composer closes", exists("decisionComposer"))
    }

    @Test
    fun `ADEV-241 In description adds the decision after the existing text and saves it`() {
        val core = core(description = "Notes.")
        open(core)
        writeDecision()
        compose.onNode(hasAnyAncestor(hasTestTag("decisionComposer.postTo")) and androidx.compose.ui.test.hasText("Description")).performScrollTo().performClick()
        done()

        assertNull("nothing is posted as a comment", core.last("postComment"))
        val changes = core.last("updateTask")!!["changes"] as JsonObject
        assertEquals(JsonPrimitive("Notes.\n\n$expected"), changes["description"])
    }

    @Test
    fun `ADEV-241 nothing is posted until there is a question and two options`() {
        val core = core()
        open(core)
        type("decisionComposer.question", "Ship today?")
        type("decisionComposer.option.0", "Ship")
        done()
        assertNull(core.last("postComment"))
        assertTrue("still open", exists("decisionComposer"))
    }
}
