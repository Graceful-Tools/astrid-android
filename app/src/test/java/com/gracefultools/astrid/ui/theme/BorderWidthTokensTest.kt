package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): every border and stroke width reads [BorderWidth]. A screen never types
 * one in, so a change to the outline weight is made once.
 */
class BorderWidthTokensTest {
    @Test
    fun `ADEV-196 the border widths keep their values`() {
        assertEquals(1f, BorderWidth.thin.value)
        assertEquals(1.5f, BorderWidth.mark.value)
        assertEquals(2f, BorderWidth.thick.value)
        assertEquals(3f, BorderWidth.heavy.value)
    }

    @Test
    fun `ADEV-196 no border or stroke types its width by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val typed = Regex("""(\.border\(\s*(?:width\s*=\s*)?|Stroke\(\s*width\s*=\s*|BorderStroke\(\s*|borderWidth\s*:\s*Dp\s*=\s*|\belse\s+)\d+(\.\d+)?\.dp\b""")
        val borderLine = Regex("""\.border\(|Stroke\(|borderWidth""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex().filter { (_, line) ->
                    val code = line.substringBefore("//")
                    borderLine.containsMatchIn(code) && typed.containsMatchIn(code)
                }.map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertTrue("border widths typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
