package com.gracefultools.astrid

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

/**
 * AATD-18 smoke: launch → My Tasks → a list → add → complete → detail, on a device, against the
 * real core. Screenshots land in the app's files dir (`adb pull`), for a person to compare with iOS.
 */
class SmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** To /data/local/tmp through the shell: the app's own files go when the test APK is uninstalled. */
    private fun shot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/astrid-$name.png").close()
        Thread.sleep(500)
    }

    private fun waitForRow(title: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasContentDescription("Complete $title")).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitFor(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aatd18_the_signed_in_app_lists_adds_completes_and_opens_tasks() {
        waitFor("My Tasks")
        waitFor("Water plants")
        shot("01-my-tasks")

        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        waitFor("Home")
        shot("02-sidebar")
        compose.onNode(hasTestTag("sidebar:l-home")).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("listTitle") and hasText("Home")).fetchSemanticsNodes().isNotEmpty()
        }
        waitForRow("Call mum")
        // Home holds two tasks; Work's report is not in it.
        compose.onAllNodes(hasContentDescription("Complete Send the report")).assertCountEquals(0)

        compose.onNode(hasSetTextAction()).performTextInput("Buy milk tomorrow")
        compose.onNode(hasSetTextAction()).performImeAction()
        // The smart parser keeps "Buy milk" as the title and reads "tomorrow" as the date.
        waitForRow("Buy milk")
        shot("03-home-after-add")

        // Back in My Tasks, a task added there stays there (assigned to the reader).
        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:virtual:my-tasks")).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("listTitle") and hasText("My Tasks")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction()).performTextInput("Book dentist")
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForRow("Book dentist")

        compose.onNodeWithContentDescription("Complete Call mum").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Mark Call mum not done").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Water plants").performClick()
        waitFor("Description")
        compose.onNodeWithText("blue", substring = true).assertIsDisplayed()
        shot("04-detail")
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithContentDescription(label: String) =
    onAllNodes(androidx.compose.ui.test.hasContentDescription(label))
