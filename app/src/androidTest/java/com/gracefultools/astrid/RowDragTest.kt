package com.gracefultools.astrid

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** AATD-42 on a device: a row carried onto another becomes its subtask, drawn one level in. */
class RowDragTest {
    @get:Rule(order = 0) val notifications: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aatd42_a_row_dropped_on_another_becomes_its_subtask() {
        waitFor("My Tasks")
        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:l-home")).performClick()
        waitFor("Call mum")
        waitFor("Water plants")
        val target = compose.onNode(hasContentDescription("Complete Water plants")).fetchSemanticsNode().boundsInRoot
        val before = compose.onNode(hasContentDescription("Complete Call mum")).fetchSemanticsNode().boundsInRoot
        compose.onNode(hasText("Call mum")).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            val dy = target.center.y - before.center.y
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(0f, dy / 10)); advanceEventTime(16) }
        }
        compose.onNode(hasTestTag("taskList")).performTouchInput { up() }
        // Nested: its checkbox now sits a level (16dp) further in than the parent's.
        compose.waitUntil(10_000) {
            val parent = compose.onAllNodes(hasContentDescription("Complete Water plants")).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            val child = compose.onAllNodes(hasContentDescription("Complete Call mum")).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            parent != null && child != null && child.left > parent.left + 10f
        }
        assertTrue(true)
    }
}
