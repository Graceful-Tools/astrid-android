package com.gracefultools.astrid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
        // A list with a board opens on it, as iOS's does, one column to a phone's screen (iOS's
        // boardColumnsVisible): Inbox first. Page to Ready, where "Plan launch" sits.
        compose.onNode(hasTestTag("board")).performTouchInput { swipeLeft() }
        waitFor("Plan launch")
        val width = compose.onNode(hasTestTag("board")).fetchSemanticsNode().boundsInRoot.width

        // Press, hold, carry it to the left edge and keep it there: the board pages back to Inbox
        // under the finger, and the drop lands the card there.
        compose.onNode(hasText("Plan launch")).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            repeat(12) { moveBy(Offset(-(center.x - 8f) / 12, 0f)); advanceEventTime(16) }
        }
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNode(hasTestTag("board")).performTouchInput { up() }

        // Inbox is on screen again, holding the card.
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Inbox")).fetchSemanticsNodes().any { it.boundsInRoot.left in 0f..(width / 2) } &&
                compose.onAllNodes(hasText("Plan launch")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
