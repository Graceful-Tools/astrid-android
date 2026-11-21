package com.gracefultools.astrid.ui.board

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.model.ProjectGoals
import com.gracefultools.astrid.ui.theme.AstridTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADEV-184: iOS ProjectGoalsStrip — a folded "Goals: N · Guardrails: N" that opens to the lists. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class ProjectGoalsStripTest {
    @get:Rule val compose = createComposeRule()

    private fun goals(json: String) = CoreJson.decodeFromString(ProjectGoals.serializer(), json)

    private val stated = goals("""{"goals":[{"id":"g1","statement":"Maximise long-term returns","measure":"IRR","target":"8%","current":"6%"},
        {"id":"g2","statement":"Stay liquid"}],
        "guardrails":[{"id":"r1","statement":"Break no laws","kind":"enforced"}],"canEdit":false}""")

    private fun show(value: ProjectGoals?) = compose.setContent { AstridTheme { ProjectGoalsStrip(value) } }

    @Test
    fun `ADEV-184 the strip is folded to the counts and opens to the goals and guardrails`() {
        show(stated)
        compose.onNodeWithText("Goals: 2 · Guardrails: 1").assertExists()
        compose.onAllNodesWithTag("projectGoals.goal").assertCountEquals(0)
        compose.onNodeWithTag("projectGoals.strip").performClick()
        compose.onNodeWithText("Maximise long-term returns").assertExists()
        compose.onNodeWithText("IRR · Target: 8% · Now: 6%").assertExists()
        compose.onNodeWithText("Break no laws").assertExists()
        compose.onNodeWithText("Enforced").assertExists()
        compose.onAllNodesWithTag("projectGoals.goal").assertCountEquals(2)
    }

    @Test
    fun `ADEV-184 a project with neither goals nor guardrails draws nothing`() {
        show(goals("""{"goals":[],"guardrails":[],"canEdit":true}"""))
        compose.onAllNodesWithTag("projectGoals.strip").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "de-w400dp-h900dp")
    fun `ADEV-184 the counts are translated`() {
        show(stated)
        compose.onNodeWithText("Ziele: 2 · Leitplanken: 1").assertExists()
    }
}
