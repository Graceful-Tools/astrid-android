package com.gracefultools.astrid.ui.common

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.ui.theme.AstridTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** AATD-98: the Waiting prompt, as iOS's WaitingConditionPrompt draws it (AITD-631). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class WaitingConditionPromptTest {
    @get:Rule val compose = createComposeRule()

    private fun core(assignee: String?) = FakeCore().apply {
        val who = assignee?.let { ""","assignee":{"id":"$it","name":"Sam"}""" }.orEmpty()
        answer("taskDetail", """{"task":{"id":"t1","title":"Ship it"}$who,"descriptionBlocks":[],"comments":[],"subtasks":[]}""")
        answer("currentUser", """{"id":"u1"}""")
        answer("dueDateOnDay", """{"dueDateTime":"2026-11-20T00:00:00Z","isAllDay":true}""")
        answer("setTaskStatus") { sent ->
            if (sent["dueDateTime"] == null || sent["dueDateTime"] is JsonNull) {
                throw CoreFailure(FailureKind.REFUSED, "Waiting needs a date or a task", code = WaitingMoves.NEEDS_CONDITION)
            }
            JsonNull
        }
    }

    private fun refusedMove(core: FakeCore): WaitingMoves {
        val moves = WaitingMoves(core)
        runBlocking {
            moves.move("t1") { wait ->
                core.run(
                    command(
                        "setTaskStatus", "taskId" to "t1", "columnId" to "waiting",
                        "dueDateTime" to wait?.dueDateTime, "isAllDay" to wait?.isAllDay,
                        "waitOnTaskId" to wait?.waitOnTaskId, "keepAssignee" to (wait?.keepAssignee == true),
                    ),
                )
            }
        }
        compose.setContent { AstridTheme { WaitingConditionPrompt(moves) } }
        return moves
    }

    @Test
    fun `AATD-98 the prompt names the task, offers Keep assigned to Sam, and waiting until a day sends the core's date`() {
        val core = core(assignee = "u2")
        val moves = refusedMove(core)
        compose.onNodeWithText("“Ship it” will wait for a date or for another task.").assertExists()
        // Until… is first, on tomorrow: confirmable at once. (Waiting on… draws a text field, whose
        // cursor never lets Robolectric's clock idle in a dialog; WaitingMovesTest covers that path.)
        compose.onNodeWithText("Keep assigned to Sam").assertExists()
        compose.onNodeWithTag("waiting.prompt.keep").performClick()
        compose.onNodeWithTag("waiting.prompt.confirm").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { moves.state.value.request == null }
        assertEquals(JsonPrimitive(java.time.LocalDate.now().plusDays(1).toString()), core.last("dueDateOnDay")!!["day"])
        val sent = core.last("setTaskStatus")!!
        assertEquals(JsonPrimitive("2026-11-20T00:00:00Z"), sent["dueDateTime"])
        assertEquals(JsonPrimitive(true), sent["keepAssignee"])
    }

    @Test
    fun `AATD-98 with nobody assigned there is no keep toggle, and Cancel sends nothing`() {
        val core = core(assignee = null)
        val moves = refusedMove(core)
        compose.onNodeWithTag("waiting.prompt").assertExists()
        assertTrue(compose.onAllNodesWithTag("waiting.prompt.keep").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertNull(moves.state.value.request)
        assertEquals(1, core.kinds().count { it == "setTaskStatus" })
        assertTrue(compose.onAllNodesWithTag("waiting.prompt").fetchSemanticsNodes().isEmpty())
    }
}
