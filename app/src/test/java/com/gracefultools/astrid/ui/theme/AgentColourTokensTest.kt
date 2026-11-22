package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (register DS27, DS28; iOS ADEV-165): an agent's name or icon reads the palette's
 * `agentInk` (a tinted circle behind it `agentBubble`), and a category tile reads a `category<Hue>`
 * pair. The system purple is one colour in every theme and unchecked for contrast, so no screen
 * draws it.
 */
class AgentColourTokensTest {
    private val root = File("src/main/java/com/gracefultools/astrid/ui")

    private fun lines(): Sequence<Pair<String, String>> = root.walkTopDown()
        .filter { it.extension == "kt" }
        .flatMap { file -> file.readLines().withIndex().map { (i, line) -> "${file.relativeTo(root)}:${i + 1}" to line } }

    @Test
    fun `ADEV-196 no screen draws the system purple`() {
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val found = lines().filter { (_, line) -> "IosSystemColors.purple" in line }.map { it.first }.toList()
        assertTrue("the system purple (read tokens.agentInk / agentBubble or a category pair):\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun `ADEV-196 an agent in the mention suggestions is drawn in the agent ink`() {
        val agentIcon = lines().filter { (_, line) -> "item.isAgent ->" in line }.toList()
        assertTrue("the mention suggestion's agent branch was not found", agentIcon.isNotEmpty())
        val wrong = agentIcon.filterNot { (_, line) -> "agentInk" in line }.map { it.first }
        assertTrue("an agent's mention icon is not tokens.agentInk:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `ADEV-196 the agent ink follows the theme`() {
        assertEquals(LightColors.tokens.agentInk, OceanColors.tokens.agentInk)
        assertTrue("dark's agent ink should be its lighter shade", LightColors.tokens.agentInk != DarkColors.tokens.agentInk)
    }
}
