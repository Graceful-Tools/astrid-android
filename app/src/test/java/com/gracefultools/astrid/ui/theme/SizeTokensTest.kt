package com.gracefultools.astrid.ui.theme

import com.astrid.design.AstridTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3): a size the tokens name reads the token. `44.dp` is the comfortable hit
 * target, `34.dp` the leading control and `0.5.dp` the hairline, typed out by hand; they drift the
 * day the token changes.
 */
class SizeTokensTest {
    private val tokenSizes = mapOf(
        "44" to TouchSize.hitTarget, "34" to TouchSize.leadingControl, "0.5" to AstridTokens.Size.hairline,
    )

    @Test
    fun `ADEV-196 the token sizes are what the screens use`() {
        assertEquals(44f, TouchSize.hitTarget.value)
        assertEquals(34f, TouchSize.leadingControl.value)
        assertEquals(0.5f, AstridTokens.Size.hairline.value)
    }

    @Test
    fun `ADEV-196 a size equal to a size token uses the token`() {
        val root = File("src/main/java/com/gracefultools/astrid/ui")
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val literal = Regex("""(?<![\d.])(\d+(?:\.\d+)?)\.dp\b""")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().withIndex().flatMap { (i, line) ->
                literal.findAll(line).filter { it.groupValues[1] in tokenSizes }
                    .map { "${file.relativeTo(root)}:${i + 1} ${it.value}" }
            }
        }.toList()
        assertTrue("hand-typed token sizes:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
