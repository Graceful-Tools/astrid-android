package com.gracefultools.astrid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
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

    // The core's inbox column (VIRTUAL_INBOX_COLUMN_ID), by id: its name has changed with the core
    // ("Inbox", "Ideas", "Ideas/Inbox"), and matching the old name is what broke this test.
    private val inboxColumn = hasTestTag("board.column.__virtual_inbox__")

    private fun waitFor(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aatd20_a_card_dragged_into_the_next_column_lands_there() {
        waitFor("My Tasks")
        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:l-launch")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("listTitle") and hasText("Launch")).fetchSemanticsNodes().isNotEmpty() }
        // A list with a board opens on it, as iOS's does, one column to a phone's screen (iOS's
        // boardColumnsVisible). Waiting leads the board but a phone opens on the inbox column, "Ideas"
        // (ADEV-135). Page to Ready, where "Plan launch" sits.
        compose.onNode(hasTestTag("board")).performTouchInput { swipeLeft() }
        waitFor("Plan launch")
        val width = compose.onNode(hasTestTag("board")).fetchSemanticsNode().boundsInRoot.width

        // Press, hold, carry it to the left edge and keep it there: the board scrolls back under the
        // finger. Held for a fixed time it would carry on past Ideas into Waiting, which now leads
        // the board, so hold only until Ideas covers the middle, then bring the finger there and drop.
        // The clock is stepped by hand: left to run, it scrolls through Ideas before the first check.
        // boundsInRoot is clipped to the screen, so it is Ideas' right edge that says how far in it is.
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Plan launch")).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            repeat(12) { moveBy(Offset(-(center.x - 8f) / 12, 0f)); advanceEventTime(16) }
        }
        var frames = 0
        while (compose.onAllNodes(inboxColumn).fetchSemanticsNodes().none { it.boundsInRoot.right >= width * 3 / 4 }) {
            assertTrue("the board never scrolled back to Ideas while the card was held at the edge", frames < 300)
            compose.mainClock.advanceTimeByFrame()
            frames++
        }
        compose.onNode(hasTestTag("board")).performTouchInput {
            moveTo(center)
            advanceEventTime(16)
            up()
        }
        compose.mainClock.autoAdvance = true

        // The card now sits in the Ideas column.
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Plan launch") and hasAnyAncestor(inboxColumn)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
