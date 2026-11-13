package com.gracefultools.astrid.ui.tasks

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.gracefultools.astrid.model.LeadingControl
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.common.CheckboxScale
import com.gracefultools.astrid.ui.theme.AstridTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AATD-85, register DS16 (Jon, 2026-11-10): completing a task pops the checkbox to
 * `state.complete-pop-scale` on `motion.spring` and the phone taps back. Reopening does neither.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class CompletionPopTest {
    @get:Rule val compose = createComposeRule()

    private val haptics = mutableListOf<HapticFeedbackType>()
    private val recorder = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { haptics += hapticFeedbackType }
    }

    private val open = TaskRow(id = "t1", title = "Water plants", priority = 2, leading = LeadingControl(kind = "checkbox"))

    private fun show(row: TaskRow) = compose.setContent {
        CompositionLocalProvider(LocalHapticFeedback provides recorder) {
            AstridTheme { TaskRowView(row, onLeading = {}, onOpen = {}) }
        }
    }

    private fun checkboxScale(label: String): Float =
        compose.onNodeWithContentDescription(label, useUnmergedTree = true)
            .fetchSemanticsNode().config[CheckboxScale]

    /** Taps the checkbox and records its scale on every frame until the spring settles. */
    private fun tapAndTrace(label: String): List<Float> {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(label, useUnmergedTree = true).performClick()
        return (0 until 60).map { compose.mainClock.advanceTimeByFrame(); checkboxScale(label) }
    }

    @Test
    fun `AATD-85 completing a task pops the checkbox past 1 and settles back`() {
        show(open)
        val trace = tapAndTrace("Complete Water plants")
        val peak = trace.max()
        assertTrue("checkbox should pop, peaked at $peak", peak > 1.15f)
        assertTrue("never past the token's 1.28 by much, peaked at $peak", peak <= 1.28f + 0.05f)
        assertEquals(1f, trace.last(), 0.01f)
    }

    @Test
    fun `AATD-85 completing a task gives a confirm haptic`() {
        show(open)
        compose.onNodeWithContentDescription("Complete Water plants", useUnmergedTree = true).performClick()
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics)
    }

    @Test
    fun `AATD-85 reopening a task neither pops nor taps back`() {
        show(open.copy(completed = true))
        val trace = tapAndTrace("Mark Water plants not done")
        assertTrue("no pop on reopen: $trace", trace.all { it == 1f })
        assertTrue("no haptic on reopen: $haptics", haptics.isEmpty())
    }
}
