package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 4, type): a font size comes from the token type scale ([TypeSize]) or, where iOS
 * types a size the scale doesn't have, a named size in ui/theme ([IosTypeSize]). Never an `N.sp`
 * typed into a screen.
 */
class TypeSizeLiteralsTest {
    @Test
    fun `ADEV-196 the sizes iOS types by hand keep their values`() {
        assertEquals(14f, IosTypeSize.glyph.value)
        assertEquals(9f, IosTypeSize.fileBadge.value)
        assertEquals(10f, IosTypeSize.badge.value)
        assertEquals(20f, IosTypeSize.triageTitle.value)
        assertEquals(21.6f, IosTypeSize.wordmark.value)
        assertEquals(24f, IosTypeSize.figure.value)
        assertEquals(24f, IosTypeSize.timerTitle.value)
        assertEquals(88f, IosTypeSize.timerDigits.value)
        assertEquals(20f, IosTypeSize.timerAction.value)
    }

    @Test
    fun `ADEV-196 no screen types a font size by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val literal = Regex("""\b\d+(?:\.\d+)?\.sp\b""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> literal.containsMatchIn(line.substringBefore("//")) }
                    .map { (i, _) -> "${file.relativeTo(base)}:${i + 1}" }
            }.toList()
        }
        assertTrue("hand-typed font sizes:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
