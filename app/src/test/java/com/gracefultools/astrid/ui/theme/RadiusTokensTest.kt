package com.gracefultools.astrid.ui.theme

import com.astrid.design.AstridTokens
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AATD-85 (step 5): a corner whose radius is a design token reads the token. `RoundedCornerShape(8.dp)`
 * is `radius.md` typed out by hand, and drifts the day the token changes. Radii the tokens do not
 * name (2, 3, 4, 7, …) are left to the design-metrics ratchet.
 */
class RadiusTokensTest {
    private val tokenValues = listOf(
        AstridTokens.Radius.sm, AstridTokens.Radius.md, AstridTokens.Radius.lg, AstridTokens.Radius.xl,
    ).map { it.value.toInt() }.toSet()

    @Test
    fun `AATD-85 a corner equal to a radius token uses the token`() {
        val root = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val literal = Regex("""RoundedCornerShape\(\s*(\d+)\.dp\s*\)""")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().withIndex().flatMap { (i, line) ->
                literal.findAll(line).filter { it.groupValues[1].toInt() in tokenValues }
                    .map { "${file.relativeTo(root)}:${i + 1} ${it.value}" }
            }
        }.toList()
        assertTrue("hand-typed token radii:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
