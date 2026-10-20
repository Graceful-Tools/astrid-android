package com.gracefultools.astrid.ui.reminders

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.ui.theme.AstridTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** AATD-54: the screens a tapped reminder opens, drawn and pressed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class ReminderScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun core() = FakeCore().apply {
        answer("taskDetail") { command ->
            val id = (command["taskId"] as JsonPrimitive).content
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"task":{"id":"$id","title":"Task $id"},"descriptionBlocks":[],"comments":[],"subtasks":[]}""",
            )
        }
    }

    @Test
    fun `AATD-54 one reminder shows iOS's card, and 1 week snoozes for a week`() {
        val core = core()
        var finished: Set<String>? = null
        compose.setContent {
            AstridTheme {
                ReminderOverlay(ReminderViewModel(core, listOf("t1"), dispatcher = Dispatchers.Main), onOpenTask = {}, onFinished = { finished = it })
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Snooze 15 minutes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Task t1").assertExists()
        compose.onNodeWithText("1 week").performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) { finished != null }
        assertEquals(JsonPrimitive(10080), core.last("snoozeReminder")!!["minutes"])
        assertEquals(setOf("t1"), finished)
    }

    @Test
    fun `AATD-54 several reminders deal the triage deck until all are caught up`() {
        val core = core()
        compose.setContent {
            AstridTheme {
                ReminderOverlay(ReminderViewModel(core, listOf("t1", "t2"), dispatcher = Dispatchers.Main), onOpenTask = {}, onFinished = {})
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Triage").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 of 2").assertExists()
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithText("Complete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("All caught up").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(JsonPrimitive("t2"), core.last("completeTask")!!["taskId"])
    }
}
