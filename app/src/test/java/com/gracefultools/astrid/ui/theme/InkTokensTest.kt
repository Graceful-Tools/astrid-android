package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AATD-97: text and icons in the accent, error, success, warning or info colour read that
 * meaning's ink for the theme (`AstridColors.accentInk`, `errorInk`…), as astrid-web does
 * (AWTD-1575) and iOS does. The base colours are fills: as text the accent is 3.7:1 on Dark's
 * card, and a status label on its own wash about 2:1. Fills, washes, borders, chart marks,
 * spinners and cursors keep the base colour, and so does anything nested in a call's arguments
 * (`Modifier.background(…)`), which this scan strips before it looks.
 */
class InkTokensTest {
    /** Calls whose colour argument is the colour of text or of an icon. */
    private val textCalls = listOf(
        "Text", "Icon", "SpanStyle",
        // ui/ helpers that draw their colour argument as a label or an icon.
        "Caption", "Caption2", "ProblemLabel", "FormButtonRow", "RowButton", "InlineButton", "ButtonRow",
        "PickRow", "ComposerButton", "RowIcon", "FeedbackLine", "ActionText", "Bullet", "Pill",
    )

    private val baseColour = Regex(
        """(?<![\w.])(?:com\.gracefultools\.astrid\.ui\.theme\.)?(?:Error|Success|Warning|Info)\b(?![\w(])""" +
            """|\b(?:colors|c|AstridTheme\.colors)\.(?:accent|error|success|warning|info)\b(?!\w)""" +
            """|\bMaterialTheme\.colorScheme\.(?:primary|error)\b(?!\w)""",
    )

    @Test
    fun `AATD-97 text and icons in a meaning's colour read its ink`() {
        val root = File("src/main/java/com/gracefultools/astrid/ui")
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val call = Regex("""(?<![\w.])(?:androidx\.compose\.material3\.)?(${textCalls.joinToString("|")})\(""")
        val offenders = root.walkTopDown()
            .filter { it.extension == "kt" && it.parentFile.name != "theme" }
            .flatMap { file ->
                val text = file.readText()
                call.findAll(text).mapNotNull { match ->
                    val open = match.range.last
                    val close = matchingParen(text, open) ?: return@mapNotNull null
                    val args = stripNested(text.substring(open + 1, close))
                    if (!baseColour.containsMatchIn(args)) return@mapNotNull null
                    val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                    "${file.relativeTo(root)}:$line ${match.groupValues[1]}(${args.replace(Regex("""\s+"""), " ").take(100)})"
                }
            }.toList()
        assertTrue("text or icons drawn in a base colour, not its ink:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `AATD-97 the scan sees a base colour as text and ignores it as a fill`() {
        assertTrue(baseColour.containsMatchIn(stripNested("""label, color = if (bad) Error else colors.textSecondary""")))
        assertTrue(baseColour.containsMatchIn(stripNested("""icon, null, tint = colors.accent""")))
        assertTrue(!baseColour.containsMatchIn(stripNested("""label, color = colors.accentInk, modifier = Modifier.background(colors.accent)""")))
        assertTrue(!baseColour.containsMatchIn(stripNested("""label, color = colors.errorInk""")))
    }

    private fun matchingParen(text: String, open: Int): Int? {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return i
            }
        }
        return null
    }

    /** Drops nested calls' and lambdas' contents — `background(…)`, `{ … }` — keeping `if (…)` conditions' branches. */
    private fun stripNested(args: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < args.length) {
            val ch = args[i]
            val isCall = ch == '(' && i > 0 && (args[i - 1].isLetterOrDigit() || args[i - 1] == '_')
            if (isCall || ch == '{') {
                val closeCh = if (ch == '(') ')' else '}'
                var depth = 0
                var j = i
                while (j < args.length) {
                    if (args[j] == ch) depth++ else if (args[j] == closeCh && --depth == 0) break
                    j++
                }
                out.append(ch).append(closeCh)
                i = j + 1
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }
}
