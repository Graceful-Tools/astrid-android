package com.gracefultools.astrid

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Against astrid.cc, signed in as the dedicated `uitest@astrid.cc` account — the one the iOS UI suite
 * uses, so both apps are checked against the same server and the same fixtures
 * (astrid-web `lib/uitest-fixtures.ts`). Run with `scripts/live-test.sh`, which mints the session and
 * resets the fixtures first. Without the cookie every test here skips.
 */
class LiveAccountTest {
    @get:Rule(order = 0) val notifications: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = androidx.compose.ui.test.junit4.createEmptyComposeRule()

    /** What was on screen when a test failed, for `adb pull`. */
    @get:Rule(order = 2) val onFailure = object : org.junit.rules.TestWatcher() {
        override fun failed(e: Throwable?, description: org.junit.runner.Description) = shot("FAILED-${description.methodName}")
    }

    private var scenario: androidx.test.core.app.ActivityScenario<MainActivity>? = null

    /** The activity starts once the account's data is in the cache, as it would after a sign-in. */
    @Before fun live() {
        assumeTrue("no astridLiveCookie: run scripts/live-test.sh", TestAstridApplication.liveCookie() != null)
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(30_000) { TestAstridApplication.liveReady.await() } }
        scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
    }

    @org.junit.After fun close() { scenario?.close() }

    private fun shot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/astrid-live-$name.png").close()
        Thread.sleep(500)
    }

    private fun waitFor(text: String, timeout: Long = 30_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForRow(title: String) = compose.waitUntil(30_000) {
        runCatching {
            compose.onNode(hasTestTag("taskList")).performScrollToNode(hasContentDescription("Complete $title"))
        }.isSuccess
    }

    @Test
    fun the_test_account_signs_in_and_reads_its_fixtures_from_the_server() {
        waitFor("My Tasks")
        waitForRow("UITEST Fixture — open task 1")
        shot("01-my-tasks")
    }

    @Test
    fun a_task_added_on_android_reaches_the_server_and_completes() {
        waitForRow("UITEST Fixture — open task 1")
        val title = "Android live ${System.currentTimeMillis()}"
        compose.onNode(hasSetTextAction()).performTextInput(title)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForRow(title)
        // Sent: the Outbox empties and the row stops saying so.
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Waiting to send", substring = true).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Complete $title").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Complete $title")).fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Waiting to send", substring = true).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun a_fixture_opens_in_detail() {
        waitForRow("UITEST Fixture — open task 2")
        compose.onNode(hasTestTag("taskList")).performScrollToNode(hasText("UITEST Fixture — open task 2"))
        compose.onNodeWithText("UITEST Fixture — open task 2").performClick()
        waitFor("Description")
        shot("02-detail")
    }
}
