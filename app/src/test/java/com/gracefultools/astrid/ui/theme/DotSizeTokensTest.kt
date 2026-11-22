package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): every dot and swatch under 24 dp reads [DotSize] (or another size token).
 * A screen never types one in.
 */
class DotSizeTokensTest {
    @Test
    fun `ADEV-196 the dot sizes keep their values`() {
        assertEquals(6f, DotSize.typing.value)
        assertEquals(8f, DotSize.status.value)
        assertEquals(8f, DotSize.swatch.value)
        assertEquals(12f, DotSize.accent.value)
        assertEquals(16f, DotSize.swatchLarge.value)
    }

    @Test
    fun `ADEV-196 no dot or swatch types its size by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val typed = Regex("""\bsize(?:\(|\s*=)\s*(\d+(?:\.\d+)?)\.dp\b""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex().filter { (_, line) ->
                    typed.findAll(line.substringBefore("//")).any { it.groupValues[1].toDouble() < 24 }
                }.map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertTrue("sizes typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
