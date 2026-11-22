package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.DefaultShadowColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): every shadow reads [AstridShadows]. A screen never types an elevation in.
 */
class ElevationTokensTest {
    @Test
    fun `ADEV-196 the shadows keep their values`() {
        assertEquals(1f, AstridShadows.segmentThumb.elevation.value)
        assertEquals(6f, AstridShadows.accountCard.elevation.value)
        assertEquals(8f, AstridShadows.liftedCard.elevation.value)
        assertEquals(8f, AstridShadows.triageCard.elevation.value)
        assertEquals(20f, AstridShadows.dialog.elevation.value)
        // These were drawn with Compose's default colour; the token keeps it, so nothing changes on screen.
        listOf(AstridShadows.segmentThumb, AstridShadows.accountCard, AstridShadows.liftedCard, AstridShadows.triageCard, AstridShadows.dialog)
            .forEach { assertEquals(DefaultShadowColor, it.color) }
    }

    @Test
    fun `ADEV-196 no shadow types its elevation by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val typed = Regex("""\.shadow\(\s*(?:elevation\s*=\s*)?\d+(?:\.\d+)?\.dp\b""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex().filter { (_, line) -> typed.containsMatchIn(line.substringBefore("//")) }
                    .map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertTrue("shadows typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
