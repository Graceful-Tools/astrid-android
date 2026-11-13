package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AATD-85 (step 5): iOS's neutral grey (SwiftUI `.gray`) and the white it draws on a coloured fill
 * (a chosen priority, a list colour, the delete swipe, the error banner) are named once, in
 * `ui/theme` (`AstridColors.neutral`, `AstridColors.onFill`). A screen typing `Color.Gray`, or giving
 * an icon or text a literal white, is one of those drifting by hand. The full-screen timer and
 * attachment viewer (detail/DetailRelated.kt) are black and white by design, as on iOS.
 */
class NeutralOnFillTokensTest {
    private val root = File("src/main/java/com/gracefultools/astrid/ui")

    private fun offenders(pattern: Regex, skip: Set<String> = emptySet()): List<String> {
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        return root.walkTopDown()
            .filter { it.extension == "kt" && it.parentFile.name != "theme" && it.relativeTo(root).path !in skip }
            .flatMap { file ->
                file.readLines().withIndex().filter { (_, line) -> pattern.containsMatchIn(line.substringBefore("//")) }
                    .map { (i, _) -> "${file.relativeTo(root)}:${i + 1}" }
            }.toList()
    }

    @Test
    fun `AATD-85 no screen types iOS's neutral grey`() {
        val found = offenders(Regex("""\bColor\.Gray\b"""))
        assertTrue("Color.Gray typed in a screen (use AstridColors.neutral):\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun `AATD-85 no screen gives an icon or text a literal white`() {
        val found = offenders(
            Regex("""(\b(tint|color)|ThumbColor)\s*=\s*[^,]*\bColor\.White\b|\bfg\s*=\s*[^,]*\bColor\.White\b|Success,\s*Color\.White"""),
            skip = setOf("detail/DetailRelated.kt"),
        )
        assertTrue("a literal white mark in a screen (use AstridColors.onFill):\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun `AATD-85 neutral and onFill are iOS's grey and white on every theme`() {
        for (palette in listOf(OceanColors, LightColors, DarkColors)) {
            assertEquals(Color.Gray, palette.neutral)
            assertEquals(Color.White, palette.onFill)
        }
    }
}
