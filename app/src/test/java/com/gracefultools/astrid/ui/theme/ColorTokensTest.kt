package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 2): every colour a screen draws comes from the theme. A `Color(0x…)` or
 * `Color.White` typed into a screen is a design value outside the tokens, so screens read
 * [AstridColors], [ImmersiveColors] or another named palette in ui/theme instead.
 */
class ColorTokensTest {
    private val named = Regex("""\bColor\.(?:White|Black|Gray|LightGray|DarkGray|Red|Green|Blue|Yellow|Cyan|Magenta)\b""")
    private val literal = Regex("""\bColor\(\s*(?:0x|\d|red\s*=)""")

    @Test
    fun `ADEV-196 screens outside ui-theme write no colour by hand`() {
        val root = File("src/main/java/com/gracefultools/astrid/ui")
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val offenders = root.walkTopDown()
            .filter { it.extension == "kt" && !it.relativeTo(root).path.startsWith("theme") }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> named.containsMatchIn(line) || literal.containsMatchIn(line) }
                    .map { (i, line) -> "${file.relativeTo(root)}:${i + 1} ${line.trim()}" }
            }.toList()
        assertTrue("hand-written colours:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `ADEV-196 one segmented-control track per theme, iOS's on the light themes`() {
        val iosTrack = Color(0xFFEEEEEF)
        assertEquals(iosTrack, LightColors.segmentTrack)
        assertEquals(iosTrack, OceanColors.segmentTrack)
        assertEquals(Color.White, LightColors.segmentThumb)
        assertEquals(DarkColors.bgTertiary, DarkColors.segmentTrack)
        assertEquals(DarkColors.bgActive, DarkColors.segmentThumb)
    }
}
