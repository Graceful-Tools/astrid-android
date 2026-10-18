package com.gracefultools.astrid

import android.content.Intent
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

/** AATD-16: text shared from another app opens the add sheet, prefilled, and becomes a task. */
class ShareTest {
    @get:Rule(order = 0) val notifications: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Test
    fun aatd16_a_shared_link_becomes_a_task() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val share = Intent(Intent.ACTION_SEND).setClass(context, MainActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Read the launch post")
            .putExtra(Intent.EXTRA_TEXT, "https://example.com/launch")
        ActivityScenario.launch<MainActivity>(share).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Read the launch post", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Add task") and androidx.compose.ui.test.hasClickAction()).performClick()
            compose.waitUntil(10_000) {
                runCatching {
                    compose.onNode(hasTestTag("taskList"))
                        .performScrollToNode(hasContentDescription("Complete Read the launch post https://example.com/launch"))
                }.isSuccess
            }
        }
    }
}
