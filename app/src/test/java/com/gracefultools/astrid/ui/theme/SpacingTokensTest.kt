package com.gracefultools.astrid.ui.theme

import com.astrid.design.AstridTokens.Space
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AATD-85 (step 5): padding or a gap whose value is a spacing token reads the token.
 * `padding(horizontal = 12.dp)` is `space.12` typed out by hand, and drifts the day the token
 * changes. Only padding and gaps are spacing — `padding`, `spacedBy`, `PaddingValues` and a
 * `Spacer`'s height or width. Sizes (`size(32.dp)`) and values with no token (6, 10, 14, …) are
 * left to the design-metrics ratchet.
 */
class SpacingTokensTest {
    private val tokenValues = listOf(
        Space.s2, Space.s4, Space.s8, Space.s12, Space.s16, Space.s20, Space.s24, Space.s32, Space.s40, Space.s48,
    ).map { it.value.toInt() }.toSet()

    @Test
    fun `AATD-85 padding or a gap equal to a spacing token uses the token`() {
        val root = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val spacingCall = Regex("""\b(?:padding|spacedBy|PaddingValues)\(([^()]*)\)|Spacer\(Modifier\.(?:height|width)\(([^()]*)\)\)""")
        val literal = Regex("""(?<![\w.])(\d+)\.dp\b""")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            val text = file.readText()
            spacingCall.findAll(text).filter { call ->
                literal.findAll(call.value).any { it.groupValues[1].toInt() in tokenValues }
            }.map { call ->
                val line = text.substring(0, call.range.first).count { it == '\n' } + 1
                "${file.relativeTo(root)}:$line ${call.value.replace(Regex("""\s+"""), " ")}"
            }
        }.toList()
        assertTrue("hand-typed token spacing:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
