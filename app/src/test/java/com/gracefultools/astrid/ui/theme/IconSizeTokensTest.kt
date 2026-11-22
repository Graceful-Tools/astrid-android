package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): an icon or progress ring is sized from [IconSize], and a ring's line
 * is [SpinnerStroke]. Never one of those values typed into a screen.
 */
class IconSizeTokensTest {
    @Test
    fun `ADEV-196 the icon steps keep their values`() {
        assertEquals(12f, IconSize.xs.value)
        assertEquals(14f, IconSize.sm.value)
        assertEquals(16f, IconSize.md.value)
        assertEquals(18f, IconSize.lg.value)
        assertEquals(20f, IconSize.xl.value)
        assertEquals(24f, IconSize.xxl.value)
        assertEquals(2f, SpinnerStroke.value)
    }

    @Test
    fun `ADEV-196 the off-step icon sizes are named after the iOS views they match`() {
        assertEquals(13f, IconSize.chip.value)
        assertEquals(15f, IconSize.tab.value)
        assertEquals(19f, IconSize.field.value)
        assertEquals(22f, IconSize.control.value)
    }

    @Test
    fun `ADEV-196 no icon types an off-step size by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        val offStep = Regex("""\b(?:size|width|height)\(\s*(?:13|15|19|22)\.dp\b""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                calls(file.readText(), offStep).map { "${file.relativeTo(base)}: $it" }
            }.toList()
        }
        assertTrue("icons sized by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `ADEV-196 no icon or progress ring types a step size by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                calls(file.readText(), step).map { "${file.relativeTo(base)}: $it" }
            }.toList()
        }
        assertTrue("icons and progress rings sized by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    private val step = Regex("""\b(?:size|width|height)\(\s*(?:12|14|16|18|20|24)\.dp\b|strokeWidth\s*=\s*2\.dp\b""")

    /** The `Icon(…)` and `CircularProgressIndicator(…)` calls whose size (or stroke) matches [typed]. Padding is not a size. */
    private fun calls(source: String, typed: Regex): List<String> {
        return Regex("""\b(?:Icon|CircularProgressIndicator)\(""").findAll(source).mapNotNull { open ->
            var depth = 0
            var end = open.range.last
            while (end < source.length) {
                when (source[end]) { '(' -> depth++; ')' -> if (--depth == 0) break }
                end++
            }
            source.substring(open.range.first, minOf(end + 1, source.length))
                .takeIf { call -> typed.containsMatchIn(call.lines().joinToString(" ") { it.substringBefore("//") }) }
                ?.lines()?.first()?.trim()
        }.toList()
    }
}
