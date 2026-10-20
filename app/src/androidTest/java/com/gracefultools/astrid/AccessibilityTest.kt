package com.gracefultools.astrid

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * AATD-18: every control a person can tap says what it is to TalkBack — a content description, or
 * text of its own or of its merged children. Walked over the list, the sidebar and the detail.
 */
class AccessibilityTest {
    @get:Rule(order = 0) val notifications: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun SemanticsNode.label(): String {
        val config = config
        val described = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
        val text = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
        val edit = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        val children = children.joinToString(" ") { it.label() }
        return listOf(described, text, edit, children).joinToString(" ").trim()
    }

    private fun assertEveryTappableIsLabelled(screen: String) {
        compose.waitForIdle()
        val unlabelled = compose.onAllNodes(hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes()
            .filter { it.label().isBlank() }
        assertTrue("$screen: ${unlabelled.size} tappable controls with no label: ${unlabelled.map { it.boundsInRoot }}", unlabelled.isEmpty())
    }

    @Test
    fun aatd18_every_tappable_control_is_labelled() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("My Tasks").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Water plants", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEveryTappableIsLabelled("list")

        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("sidebar:l-home")).fetchSemanticsNodes().isNotEmpty() }
        assertEveryTappableIsLabelled("sidebar")
        compose.onNode(hasTestTag("sidebar:l-home")).performClick()

        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Water plants")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Water plants")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Description").fetchSemanticsNodes().isNotEmpty() }
        assertEveryTappableIsLabelled("detail")
        androidx.test.espresso.Espresso.pressBack()

        // AATD-64: the screens iOS parity added — search, settings and its pages, profile, list settings.
        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:search")).performClick()
        compose.waitForIdle()
        assertEveryTappableIsLabelled("search")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()

        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:settings")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Reminders").fetchSemanticsNodes().isNotEmpty() }
        assertEveryTappableIsLabelled("settings")
        for (page in listOf("Reminders", "Appearance")) {
            compose.onNode(hasText(page) and androidx.compose.ui.test.hasClickAction()).performClick()
            compose.waitForIdle()
            assertEveryTappableIsLabelled("settings/$page")
            androidx.test.espresso.Espresso.pressBack()
        }
        androidx.test.espresso.Espresso.pressBack()

        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:profile")).performClick()
        compose.waitForIdle()
        assertEveryTappableIsLabelled("profile")
        androidx.test.espresso.Espresso.pressBack()

        compose.onNodeWithContentDescription("Open the sidebar").performClick()
        compose.onNode(hasTestTag("sidebar:l-home")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescriptionExactly("List Settings")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescriptionExactly("List Settings")).performClick()
        compose.waitForIdle()
        assertEveryTappableIsLabelled("list settings")
    }

    private fun hasContentDescriptionExactly(text: String) = androidx.compose.ui.test.hasContentDescription(text)
}
