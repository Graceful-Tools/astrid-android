package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): an avatar is sized from [AvatarSize], never a number typed into a screen.
 */
class AvatarSizeTokensTest {
    @Test
    fun `ADEV-196 the avatar sizes keep the iOS values`() {
        assertEquals(22f, AvatarSize.xxs.value)
        assertEquals(24f, AvatarSize.xs.value)
        assertEquals(28f, AvatarSize.sm.value)
        assertEquals(32f, AvatarSize.md.value)
        assertEquals(36f, AvatarSize.lg.value)
        assertEquals(40f, AvatarSize.xl.value)
        assertEquals(60f, AvatarSize.account.value)
        assertEquals(80f, AvatarSize.profile.value)
        assertEquals(120f, AvatarSize.profileEdit.value)
    }

    @Test
    fun `ADEV-196 no avatar types its size by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                calls(file.readText()).map { "${file.relativeTo(base)}: $it" }
            }.toList()
        }
        assertTrue("avatars sized by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** The `Avatar(…)` calls whose size is a typed-in number. A border width is not the size. */
    private fun calls(source: String): List<String> {
        val typed = Regex("""\b\d+(?:\.\d+)?\.dp\b""")
        return Regex("""\bAvatar\(""").findAll(source).mapNotNull { open ->
            if (source.substring(maxOf(0, open.range.first - 4), open.range.first).contains("fun ")) return@mapNotNull null
            var depth = 0
            var end = open.range.last
            while (end < source.length) {
                when (source[end]) { '(' -> depth++; ')' -> if (--depth == 0) break }
                end++
            }
            val call = source.substring(open.range.first, minOf(end + 1, source.length))
            val args = call.lines().joinToString(" ") { it.substringBefore("//") }.replace(Regex("""borderWidth\s*=\s*[^,)]+"""), "")
            call.lines().first().trim().takeIf { typed.containsMatchIn(args) }
        }.toList()
    }
}
