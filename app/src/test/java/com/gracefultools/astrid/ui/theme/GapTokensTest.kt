package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): every padding or gap reads a token. On-scale values read [Spacing]
 * (AATD-85, SpacingTokensTest); the off-scale ones iOS types between the steps read [Gap]. Only
 * `0.dp` (no gap) may be typed. Canvas geometry (`N.dp.toPx()`) is drawing, not spacing.
 */
class GapTokensTest {
    @Test
    fun `ADEV-196 the off-scale gaps keep their values`() {
        assertEquals(1f, Gap.s1.value)
        assertEquals(3f, Gap.s3.value)
        assertEquals(5f, Gap.s5.value)
        assertEquals(6f, Gap.s6.value)
        assertEquals(9f, Gap.s9.value)
        assertEquals(10f, Gap.s10.value)
        assertEquals(13f, Gap.s13.value)
        assertEquals(14f, Gap.s14.value)
        assertEquals(28f, Gap.s28.value)
        assertEquals(96f, Gap.floatingClearance.value)
        assertEquals(56f, Gap.sidebarDividerInset.value)
        assertEquals(52f, Gap.settingsDividerInset.value)
    }

    @Test
    fun `ADEV-196 no padding or gap is typed by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val call = Regex("""\b(?:padding|spacedBy|PaddingValues|Spacer\(Modifier\.(?:height|width))\(([^()]*(?:\([^()]*\)[^()]*)*)\)""")
        val literal = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\.dp\b""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex().filter { (_, line) ->
                    call.findAll(line.substringBefore("//")).any { c ->
                        literal.findAll(c.groupValues[1]).any { it.groupValues[1].toDouble() != 0.0 }
                    }
                }.map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertTrue("paddings or gaps typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
