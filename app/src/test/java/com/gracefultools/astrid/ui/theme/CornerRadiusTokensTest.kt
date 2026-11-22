package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, register DS13): a corner radius comes from the radius tokens, a Compose shape
 * (a rectangle or a capsule) or a named shape in ui/theme, never a `RoundedCornerShape(N.dp)`
 * typed into a screen.
 */
class CornerRadiusTokensTest {
    @Test
    fun `ADEV-196 the radius scale is core's`() {
        assertEquals(6f, Radius.small.value)
        assertEquals(8f, Radius.medium.value)
        assertEquals(12f, Radius.large.value)
        assertEquals(16f, Radius.xLarge.value)
    }

    @Test
    fun `ADEV-196 the segmented control keeps iOS's track and thumb`() {
        assertEquals(9f, SegmentedShapes.trackRadius.value)
        assertEquals(7f, SegmentedShapes.thumbRadius.value)
    }

    @Test
    fun `ADEV-196 no screen types a corner radius by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val literal = Regex("""RoundedCornerShape\(\s*\d+(?:\.\d+)?\.dp""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex().filter { (_, line) -> literal.containsMatchIn(line) }
                    .map { (i, _) -> "${file.relativeTo(base)}:${i + 1}" }
            }.toList()
        }
        assertTrue("hand-typed corner radii:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
