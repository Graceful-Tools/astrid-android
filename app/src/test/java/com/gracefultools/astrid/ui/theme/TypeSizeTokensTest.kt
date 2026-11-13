package com.gracefultools.astrid.ui.theme

import com.astrid.design.AstridTokens.TypeScale.Comfortable as Type
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AATD-85 (step 5): a font size equal to a type-scale token reads the token. `fontSize = 22.sp`
 * is `type.title3` typed out by hand, and drifts the day the token changes. Sizes with no token
 * (9, 10, 14, 20, 24, the timer's 88, …) are left to the design-metrics ratchet.
 */
class TypeSizeTokensTest {
    private val tokenSizes = listOf(
        Type.title1, Type.title2, Type.title3, Type.headline, Type.body, Type.callout, Type.subheadline,
        Type.footnote, Type.caption1, Type.caption2, Type.rowTitle,
    ).map { it.size.value }.toSet()

    @Test
    fun `AATD-85 a font size equal to a type token uses the token`() {
        val root = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val literal = Regex("""\bfontSize\s*=\s*(\d+(?:\.\d+)?)\.sp\b""")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().withIndex().flatMap { (i, line) ->
                literal.findAll(line).filter { it.groupValues[1].toFloat() in tokenSizes }
                    .map { "${file.relativeTo(root)}:${i + 1} ${it.value}" }
            }
        }.toList()
        assertTrue("hand-typed token font sizes:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
