package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AATD-85 (step 5): a shadow's colour is named once, in `ui/theme`. iOS draws two shadows over and
 * over (FloatingHeader's black 0.08, and the bottom bars' black 0.1); a screen that types out
 * `ambientColor = Color.Black.copy(…)` is one of those drifting by hand.
 */
class ShadowTokensTest {
    @Test
    fun `AATD-85 no screen gives a shadow a literal colour`() {
        val root = File("src/main/java/com/gracefultools/astrid/ui")
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val literal = Regex("""(ambientColor|spotColor)\s*=\s*Color\.""")
        val offenders = root.walkTopDown().filter { it.extension == "kt" && it.parentFile.name != "theme" }.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) -> literal.containsMatchIn(line) }
                .map { (i, _) -> "${file.relativeTo(root)}:${i + 1}" }
        }.toList()
        assertTrue("shadows with a hand-typed colour:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `AATD-85 the two shadows are iOS's`() {
        assertEquals(Color.Black.copy(alpha = 0.08f), AstridShadows.floatingHeader.color)
        assertEquals(Color.Black.copy(alpha = 0.1f), AstridShadows.bottomBar.color)
    }
}
