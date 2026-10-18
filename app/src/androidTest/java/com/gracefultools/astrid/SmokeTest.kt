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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

/**
 * AATD-18 smoke: launch → My Tasks → a list → add → complete → detail, on a device, against the
 * real core. Screenshots land in the app's files dir (`adb pull`), for a person to compare with iOS.
 */
class SmokeTest {
    // Granted up front: the system's own permission dialog would cover the app (and its tree).
    @get:Rule(order = 0) val notifications: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    /** To /data/local/tmp through the shell: the app's own files go when the test APK is uninstalled. */
    private fun shot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/astrid-$name.png").close()
        Thread.sleep(500)
    }

    /** A row may be below the fold, and a lazy list composes only what is on screen: scroll to it. */
    private fun waitForRow(title: String) = compose.waitUntil(10_000) {
        runCatching {
            compose.onNode(hasTestTag("taskList")).performScrollToNode(hasContentDescription("Complete $title"))
        }.isSuccess
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

        // Completed in My Tasks, it is either shown done or filtered out (the core's completion
        // window decides); either way its "Complete" control is gone.
        compose.onNodeWithContentDescription("Complete Call mum").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Complete Call mum")).fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithText("Water plants").performClick()
        waitFor("Description")
        compose.onNodeWithText("blue", substring = true).assertIsDisplayed()
        shot("04-detail")
    }

    @Test
    fun aatd12_a_project_list_shows_as_a_board_and_a_card_moves_through_the_status_picker() {
        waitFor("My Tasks")
        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:l-launch")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("listTitle") and hasText("Launch")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Show as board").performClick()
        // Columns are 300dp wide; the first two are on a phone's screen (Inbox, Ready).
        waitFor("Ready")
        shot("05-board")
        // A board card's leading control opens the status picker (the core's `openPicker`).
        compose.onNodeWithContentDescription("Complete Write copy").performClick()
        waitFor("Move to")
        compose.onAllNodes(hasText("Doing") and androidx.compose.ui.test.hasClickAction()).onFirst().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Move to").fetchSemanticsNodes().isEmpty() }
        shot("06-board-after-move")
    }
}
