package com.gracefultools.astrid.ui.theme

import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.gracefultools.astrid.ui.common.TypingDotLift
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.gracefultools.astrid.model.LeadingControl
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.common.AgentTypingIndicator
import com.gracefultools.astrid.ui.common.CheckboxScale
import com.gracefultools.astrid.ui.tasks.TaskRowView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ADEV-196, register DS17: with Android's "Remove animations" on (animator duration scale 0),
 * nothing springs, bounces or pops, as iOS's ReducedMotion does under Reduce Motion. Robolectric's
 * test clock ignores the system scale, so this checks the app's own reading of the setting.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class ReducedMotionTest {
    @get:Rule val compose = createComposeRule()

    private val resolver get() = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
    private fun removeAnimations(on: Boolean) =
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (on) 0f else 1f)

    @After fun restore() { removeAnimations(false) }

    private val haptics = mutableListOf<HapticFeedbackType>()
    private val recorder = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { haptics += hapticFeedbackType }
    }

    private fun popTrace(): List<Float> {
        val row = TaskRow(id = "t1", title = "Water plants", priority = 2, leading = LeadingControl(kind = "checkbox"))
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                AstridTheme { TaskRowView(row, onLeading = {}, onOpen = {}) }
            }
        }
        compose.mainClock.autoAdvance = false
        val label = "Complete Water plants"
        compose.onNodeWithContentDescription(label, useUnmergedTree = true).performClick()
        return (0 until 60).map {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithContentDescription(label, useUnmergedTree = true).fetchSemanticsNode().config[CheckboxScale]
        }
    }

    private fun dotTrace(): List<Float> {
        compose.mainClock.autoAdvance = false
        compose.setContent { AstridTheme { AgentTypingIndicator("Claude") } }
        return (0 until 40).map {
            compose.mainClock.advanceTimeBy(50)
            compose.onNodeWithTag("typingDot0", useUnmergedTree = true).fetchSemanticsNode().config[TypingDotLift]
        }
    }

    @Test
    fun `ADEV-196 reads Remove animations as reduced motion`() {
        removeAnimations(true)
        assertTrue(ReducedMotion.isEnabled(ApplicationProvider.getApplicationContext()))
        removeAnimations(false)
        assertTrue(!ReducedMotion.isEnabled(ApplicationProvider.getApplicationContext()))
    }

    @Test
    fun `ADEV-196 with Remove animations on the completion pop does not play but the haptic does`() {
        removeAnimations(true)
        val trace = popTrace()
        assertTrue("checkbox should stay at 1: $trace", trace.all { it == 1f })
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics)
    }

    @Test
    fun `ADEV-196 with Remove animations on the typing dots stay still`() {
        removeAnimations(true)
        val trace = dotTrace()
        assertEquals("dots should not bounce: $trace", 1, trace.distinct().size)
    }

    @Test
    fun `ADEV-196 with animations on the typing dots still bounce`() {
        val trace = dotTrace()
        assertTrue("dots should bounce: $trace", trace.distinct().size > 1)
    }
}
