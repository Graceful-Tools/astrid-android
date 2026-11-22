package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 7, the ratchet): no screen types an English sentence in. Words a person reads come
 * from strings.xml through stringResource, product names included, so every locale can carry them.
 * Same rule as scripts/report-design-metrics.py's hardcodedCopy: a capitalised word and at least one
 * more ("Close the sheet", "Google Tasks"); keys, URLs and templates that start with a value are not copy.
 */
class HardcodedCopyTest {
    @Test
    fun `ADEV-196 no screen types a sentence in`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val string = Regex(""""((?:[^"\\\n]|\\.)*)"""")
        val sentence = Regex("""^[A-Z][a-z']*(?:[ ,.!?:;…–—-]+\S+)+""")
        val offenders = File(base, "ui").walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
            file.readLines().withIndex().filter { (_, raw) ->
                val line = raw.trim()
                if (line.startsWith("*") || line.startsWith("/*") || line.startsWith("//")) return@filter false
                string.findAll(line.replace(Regex("""\s//\s.*$"""), "")).any { sentence.containsMatchIn(it.groupValues[1]) }
            }.map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
        }.toList()
        assertTrue("sentences typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
