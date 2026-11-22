package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 5, motion, and step 7, the ratchet counts durations): no screen types an animation
 * or on-screen timing in. Durations, staggers, spring stiffness and waits read [MotionTiming] and
 * [ScreenTiming] in ui/theme, or the core's `AstridTokens.Motion`.
 */
class MotionLiteralsTest {
    @Test
    fun `ADEV-196 the named timings keep their values`() {
        assertEquals(400, MotionTiming.typingDotBounceMillis) // iOS AgentTypingIndicator: easeInOut(0.4)
        assertEquals(150, MotionTiming.typingDotStaggerMillis) // iOS: .delay(0.15) per dot
        assertEquals(400f, MotionTiming.boardFlingStiffness)
        assertEquals(6_000L, ScreenTiming.errorBannerMillis)
        assertEquals(2_000L, ScreenTiming.copiedMillis) // iOS: 2 s
        assertEquals(3_000L, ScreenTiming.flashMillis) // iOS: 3 s
        assertEquals(500L, ScreenTiming.sheetFocusMillis)
        assertEquals(300L, ScreenTiming.searchFocusMillis)
        assertEquals(600L, ScreenTiming.refreshIndicatorMillis)
        assertEquals(16L, ScreenTiming.frameMillis)
        assertEquals(1_000L, ScreenTiming.clockTickMillis)
    }

    @Test
    fun `ADEV-196 no screen types a duration in`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val typed = Regex("""\b(?:tween|delay|StartOffset)\(\s*[\d_]*\d|\b(?:durationMillis|delayMillis|stiffness)\s*=\s*\d|StartOffset\([^)]*\*\s*\d""")
        val offenders = File(base, "ui").walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) -> typed.containsMatchIn(line.substringBefore("//")) }
                .map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
        }.toList()
        assertTrue("timing typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
