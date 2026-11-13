package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AATD-85 (step 5): iOS's system colours (`.purple`, `.green`, `.orange`, `.yellow`, `.blue`) are
 * named once, in `ui/theme` ([IosSystemColors]). A screen typing one of them as hex is drifting by
 * hand. Other literals (the passkey's own RGB, Tailwind colours) are not iOS system colours.
 */
class IosSystemColorsTokensTest {
    private val root = File("src/main/java/com/gracefultools/astrid/ui")
    private val systemHex = Regex("""Color\(0x[Ff]{2}(AF52DE|34C759|FF9500|FFCC00|007AFF)\)""", RegexOption.IGNORE_CASE)

    @Test
    fun `AATD-85 no screen types an iOS system colour as hex`() {
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val found = root.walkTopDown()
            .filter { it.extension == "kt" && it.parentFile.name != "theme" }
            .flatMap { file ->
                file.readLines().withIndex().filter { (_, line) -> systemHex.containsMatchIn(line) }
                    .map { (i, _) -> "${file.relativeTo(root)}:${i + 1}" }
            }.toList()
        assertTrue("an iOS system colour typed as hex (use IosSystemColors):\n" + found.joinToString("\n"), found.isEmpty())
    }
}
