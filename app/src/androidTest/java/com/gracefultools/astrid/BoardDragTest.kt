package com.gracefultools.astrid

import androidx.compose.ui.geometry.Offset
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

/** AATD-20: press, hold and drag a card into the column beside it. */
class BoardDragTest {
    @get:Rule(order = 0) val notifications: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aatd20_a_card_dragged_into_the_next_column_lands_there() {
        waitFor("My Tasks")
        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:l-launch")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("listTitle") and hasText("Launch")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Show as board").performClick()
        waitFor("Plan launch")
        val columnWidth = with(compose.density) { 312.dp.toPx() }

        val before = compose.onNode(hasText("Plan launch")).fetchSemanticsNode().boundsInRoot
        assertTrue("starts in Ready, the second column", before.left > columnWidth / 2)

        compose.onNode(hasText("Plan launch")).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            repeat(10) { moveBy(Offset(-columnWidth / 10, 0f)); advanceEventTime(16) }
            up()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Plan launch")).fetchSemanticsNodes().any { it.boundsInRoot.left < columnWidth / 2 }
        }
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
