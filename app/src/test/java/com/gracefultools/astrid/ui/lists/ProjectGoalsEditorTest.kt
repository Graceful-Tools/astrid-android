package com.gracefultools.astrid.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gracefultools.astrid.ui.theme.AstridTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADEV-184: iOS ProjectGoalsEditor, in List Settings → Admin. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h1600dp")
class ProjectGoalsEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `ADEV-184 Add goal and Add guardrail add empty rows, with no id, and Enforced marks a guardrail`() {
        var draft by mutableStateOf(GoalsDraft(goals = listOf(GoalRow(id = "g1", statement = "Maximise long-term returns"))))
        compose.setContent {
            AstridTheme {
                androidx.compose.foundation.layout.Column {
                    ProjectGoalsEditor(draft, changed = false, saving = false, saveFailed = false, onChange = { draft = it }, onSave = {})
                }
            }
        }
        compose.onAllNodesWithTag("projectGoals.goal").assertCountEquals(1)
        compose.onNodeWithText("Add goal").performClick()
        compose.onNodeWithText("Add guardrail").performClick()
        compose.onAllNodesWithTag("projectGoals.goal").assertCountEquals(2)
        compose.onAllNodesWithTag("projectGoals.guardrail").assertCountEquals(1)
        assertNull(draft.goals[1].id)
        compose.onNodeWithText("Enforced").performClick()
        assertEquals("enforced", draft.guardrailsWire().single()["kind"])
    }

    @Test
    fun `ADEV-184 a refused save says so`() {
        compose.setContent {
            AstridTheme {
                androidx.compose.foundation.layout.Column {
                    ProjectGoalsEditor(GoalsDraft(), changed = true, saving = false, saveFailed = true, onChange = {}, onSave = {})
                }
            }
        }
        compose.onNodeWithText("Could not save the goals and guardrails. Try again.").assertExists()
    }
}
