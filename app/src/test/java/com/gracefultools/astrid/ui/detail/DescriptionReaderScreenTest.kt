package com.gracefultools.astrid.ui.detail

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.ui.theme.AstridTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AATD-102 (iOS AITD-649): on an editable task, tapping the description opens the full-screen
 * description already editing; the expand button opens a written one for reading; Done saves only
 * a change; a read-only task never opens an editor.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class DescriptionReaderScreenTest {
    @get:Rule val compose = createComposeRule()

    private val blocks = """[{"kind":"paragraph","inlines":[{"text":"Buy paint"}]}]"""

    private fun core(description: String, copyOnly: Boolean = false) = FakeCore().apply {
        val text = description.replace("\n", "\\n")
        answer(
            "taskDetail",
            """{"task":{"id":"t1","title":"Plan","description":"$text"},"fieldOrder":[],"isCopyOnly":$copyOnly,
                "descriptionBlocks":${if (description.isEmpty()) "[]" else blocks},"comments":[],"subtasks":[]}""",
        )
    }

    private fun show(core: FakeCore) {
        val vm = TaskDetailViewModel(core, "t1", Dispatchers.Unconfined)
        compose.setContent { AstridTheme { TaskDetailScreen(vm, onBack = {}) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("detail:description")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `AATD-102 tapping a written description opens the full-screen editor with its text`() {
        show(core("Buy paint"))
        compose.onNodeWithTag("detail:description").performClick()
        compose.waitForIdle()
        assertTrue("the full-screen editor opens", exists("descriptionReader.editor"))
        compose.onNodeWithTag("descriptionReader.editor").assertTextEquals("Buy paint")
    }

    @Test
    fun `AATD-102 tapping an empty description opens the full-screen editor`() {
        show(core(""))
        compose.onNodeWithTag("detail:description").performClick()
        compose.waitForIdle()
        assertTrue(exists("descriptionReader.editor"))
    }

    @Test
    fun `AATD-102 Done in the full-screen editor saves the changed description through the core`() {
        val core = core("Buy paint")
        show(core)
        compose.onNodeWithTag("detail:description").performClick()
        compose.onNodeWithTag("descriptionReader.editor").performTextReplacement("Buy blue paint")
        compose.onNodeWithTag("descriptionReader.done").performClick()
        compose.waitForIdle()
        val changes = core.last("updateTask")!!["changes"] as JsonObject
        assertEquals(JsonPrimitive("Buy blue paint"), changes["description"])
        assertTrue("the reader closes", !exists("descriptionReader.editor"))
    }

    @Test
    fun `AATD-102 Done without a change writes nothing`() {
        val core = core("Buy paint")
        show(core)
        compose.onNodeWithTag("detail:description").performClick()
        compose.onNodeWithTag("descriptionReader.done").performClick()
        compose.waitForIdle()
        assertEquals(null, core.last("updateTask"))
    }

    @Test
    fun `AATD-102 the expand button opens a written description for reading, and Edit switches to the editor`() {
        show(core("Buy paint"))
        compose.onNodeWithTag("detail:descriptionExpand").performClick()
        compose.waitForIdle()
        assertTrue(exists("descriptionReader.reading"))
        compose.onNodeWithTag("descriptionReader.toggleMode").performClick()
        compose.waitForIdle()
        assertTrue(exists("descriptionReader.editor"))
    }

    @Test
    fun `AATD-102 a read-only task never opens an editor`() {
        show(core("Buy paint", copyOnly = true))
        compose.onNodeWithTag("detail:description").performClick()
        compose.waitForIdle()
        assertTrue(!exists("descriptionReader.editor"))
        compose.onNodeWithTag("detail:descriptionExpand").performClick()
        compose.waitForIdle()
        assertTrue(exists("descriptionReader.reading"))
        assertTrue("no Edit toggle on a read-only task", !exists("descriptionReader.toggleMode"))
    }
}
