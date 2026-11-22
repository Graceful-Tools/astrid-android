package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): a round button or badge is sized from [CircleSize] (or [AvatarSize] when
 * it stands in for a person), never a size typed into a screen. Status dots under 24 dp are out of scope.
 */
class CircleSizeTokensTest {
    @Test
    fun `ADEV-196 the circle sizes keep their values`() {
        assertEquals(24f, CircleSize.badge.value)
        assertEquals(28f, CircleSize.swatch.value)
        assertEquals(30f, CircleSize.compact.value)
        assertEquals(32f, CircleSize.sm.value)
        assertEquals(36f, CircleSize.md.value)
        assertEquals(40f, CircleSize.lg.value)
        assertEquals(56f, CircleSize.close.value)
        assertEquals(100f, CircleSize.timer.value)
        assertEquals(40f, IconSize.timer.value)
    }

    @Test
    fun `ADEV-196 no round button or badge types its size by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val typed = Regex("""\bsize\(\s*(\d+)\.dp\s*\)\s*\.clip\(\s*(?:androidx\.compose\.foundation\.shape\.)?CircleShape\s*\)""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex().filter { (_, line) ->
                    typed.findAll(line.substringBefore("//")).any { it.groupValues[1].toInt() >= 24 }
                }.map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertTrue("circles sized by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
