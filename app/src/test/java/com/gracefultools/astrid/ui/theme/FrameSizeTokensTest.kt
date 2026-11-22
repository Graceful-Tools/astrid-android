package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): every fixed width and height reads [FrameSize] (or another size token).
 * A screen never types one in. Gap spacers and `widthIn`/`heightIn` caps are separate slices.
 */
class FrameSizeTokensTest {
    @Test
    fun `ADEV-196 the frame sizes keep their values`() {
        assertEquals(284f, FrameSize.liftedCard.value)
        assertEquals(56f, FrameSize.outdentZone.value)
        assertEquals(176f, FrameSize.insightsChart.value)
        assertEquals(24f, FrameSize.listMarker.value)
        assertEquals(3f, FrameSize.quoteBar.value)
        assertEquals(120f, FrameSize.tableCell.value)
        assertEquals(16f, FrameSize.typingRow.value)
        assertEquals(260f, FrameSize.timerDoneWidth.value)
        assertEquals(60f, FrameSize.timerDoneHeight.value)
        assertEquals(36f, FrameSize.stepperValue.value)
        assertEquals(20f, FrameSize.mentionGlyph.value)
        assertEquals(50f, ControlHeight.signIn.value)
    }

    @Test
    fun `ADEV-196 no width or height is typed by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val typed = Regex("""\b(?:width|height)\(\s*\d+(?:\.\d+)?\.dp\s*\)""")
        val gap = Regex("""Spacer\(Modifier\.(?:width|height)\(\s*\d+(?:\.\d+)?\.dp\s*\)\)""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex().filter { (_, line) ->
                    typed.containsMatchIn(line.substringBefore("//").replace(gap, ""))
                }.map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertTrue("widths and heights typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
