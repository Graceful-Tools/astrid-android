package com.gracefultools.astrid

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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

    /** A sidebar entry (tagged `sidebar:<id>`), not a task row whose chip says the same name. */
    private val sidebarRow = androidx.compose.ui.test.SemanticsMatcher("a sidebar row") {
        it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("sidebar:") == true
    }

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

    /** Every main screen, captured for a person to set beside iOS's (scripts/live-test.sh --tour). */
    @Test
    fun tour() {
        waitForRow("UITEST Fixture — open task 1")
        shot("tour-01-my-tasks")
        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        waitFor("UI Test List")
        shot("tour-02-sidebar")
        compose.onAllNodes(sidebarRow and hasText("UI Test List")).onFirst().performClick()
        compose.waitForIdle(); Thread.sleep(1500)
        shot("tour-03-board")
        // A list with a board opens on it, as iOS's does; the rotator turns to the list.
        runCatching { compose.onNodeWithContentDescription("Show as list").performClick() }
        compose.waitForIdle(); Thread.sleep(800)
        shot("tour-03b-list")
        compose.onNode(hasTestTag("taskList")).performScrollToNode(hasText("UITEST Fixture — open task 2"))
        compose.onNodeWithText("UITEST Fixture — open task 2").performClick()
        waitFor("Description")
        shot("tour-04-detail")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:search")).performClick()
        compose.waitForIdle(); Thread.sleep(800)
        shot("tour-05-search")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:settings")).performClick()
        compose.waitForIdle(); Thread.sleep(1500)
        shot("tour-06-settings")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:profile")).performClick()
        compose.waitForIdle(); Thread.sleep(2000)
        shot("tour-07-profile")
    }
}
