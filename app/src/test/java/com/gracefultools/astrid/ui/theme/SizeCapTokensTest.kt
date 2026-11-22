package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): every `widthIn`/`heightIn`/`sizeIn`/`defaultMinSize` bound reads [MaxSize]
 * (or another size token). A screen never types one in.
 */
class SizeCapTokensTest {
    @Test
    fun `ADEV-196 the size caps keep their values`() {
        assertEquals(300f, MaxSize.bubble.value)
        assertEquals(560f, MaxSize.speechBubble.value)
        assertEquals(520f, MaxSize.errorBanner.value)
        assertEquals(260f, MaxSize.inlineImageWidth.value)
        assertEquals(200f, MaxSize.inlineImageHeight.value)
        assertEquals(200f, MaxSize.commentBox.value)
        assertEquals(220f, MaxSize.mentionPicker.value)
        assertEquals(420f, MaxSize.signInColumn.value)
        assertEquals(200f, MaxSize.pickerValue.value)
        assertEquals(500f, MaxSize.reminderColumn.value)
        assertEquals(340f, MaxSize.dialog.value)
    }

    @Test
    fun `ADEV-196 no size cap is typed by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val call = Regex("""\b(?:widthIn|heightIn|sizeIn|defaultMinSize|requiredWidthIn|requiredHeightIn)\(([^()]*(?:\([^()]*\)[^()]*)*)\)""")
        val literal = Regex("""\d+(?:\.\d+)?\.dp\b""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex().filter { (_, line) ->
                    call.findAll(line.substringBefore("//")).any { literal.containsMatchIn(it.groupValues[1]) }
                }.map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertTrue("size caps typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
