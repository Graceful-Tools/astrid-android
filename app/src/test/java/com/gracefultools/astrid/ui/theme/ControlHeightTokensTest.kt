package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): a row or control's minimum height reads [ControlHeight] (or a layout
 * token), never a height typed into a screen.
 */
class ControlHeightTokensTest {
    @Test
    fun `ADEV-196 the control heights keep their values`() {
        assertEquals(3f, ControlHeight.dropLine.value)
        assertEquals(27f, ControlHeight.rowMetadata.value)
        assertEquals(30f, ControlHeight.segmentThumb.value)
        assertEquals(32f, ControlHeight.sm.value)
        assertEquals(36f, ControlHeight.md.value)
        assertEquals(40f, ControlHeight.field.value)
        assertEquals(48f, ControlHeight.settingRow.value)
        assertEquals(56f, ControlHeight.colourPreview.value)
        assertEquals(80f, ControlHeight.notes.value)
        assertEquals(220f, ControlHeight.triageCard.value)
    }

    @Test
    fun `ADEV-196 no row or control types its minimum height by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val typed = Regex("""\bheightIn\(\s*min\s*=\s*[\d.]+\.dp""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex().filter { (_, line) -> typed.containsMatchIn(line.substringBefore("//")) }
                    .map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertTrue("minimum heights typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
