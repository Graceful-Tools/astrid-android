package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout, and step 7, the ratchet): no screen types a dp in. Every size, gap, width,
 * distance and drawing measure reads a token in ui/theme. Only `0.dp` is allowed, because zero is not
 * a design value.
 */
class DpLiteralsTest {
    @Test
    fun `ADEV-196 the last named sizes keep their values`() {
        assertEquals(48f, PaneWidth.rail.value)
        assertEquals(320f, PaneWidth.messagesBesideBoard.value)
        assertEquals(360f, PaneWidth.sidebarMax.value)
        assertEquals(40f, DragDistance.outdent.value)
        assertEquals(12f, DragDistance.dropBand.value)
        assertEquals(4f, DragDistance.insertionLine.value)
        assertEquals(48f, DragDistance.edgeScroll.value)
        assertEquals(19.2f, HamburgerGlyph.lineWidth.value)
        assertEquals(2f, HamburgerGlyph.lineHeight.value)
        assertEquals(5f, HamburgerGlyph.lineGap.value)
        assertEquals(1.5f, HamburgerGlyph.lineRadius.value)
        assertEquals(16f, BubblePointer.outerReach.value)
        assertEquals(13f, BubblePointer.innerReach.value)
        assertEquals(3f, BubblePointer.overlap.value)
        assertEquals(12f, BubblePointer.outerHalfHeight.value)
        assertEquals(9.5f, BubblePointer.innerHalfHeight.value)
        assertEquals(28.8f, FrameSize.sidebarLogoColumn.value)
        assertEquals(80f, FrameSize.reminderCharacter.value)
        assertEquals(24f, FrameSize.replyIndent.value)
        assertEquals(64f, TileSize.thumbnail.value)
        assertEquals(22f, SidebarShapes.groupRadius.value)
    }

    @Test
    fun `ADEV-196 no screen types a dp in`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val typed = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\.dp\b""")
        val offenders = File(base, "ui").walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                typed.findAll(line.substringBefore("//")).any { it.groupValues[1].toDouble() != 0.0 }
            }.map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
        }.toList()
        assertTrue("dp typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
