package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADEV-196 (step 3, layout): every size of 24 dp and up reads a token. Rounded tiles and pictures read
 * [TileSize], large glyphs read [IconSize] and circles read [CircleSize]. A screen never types one in.
 */
class PictureSizeTokensTest {
    @Test
    fun `ADEV-196 the tile and large glyph sizes keep their values`() {
        assertEquals(28f, TileSize.back.value)
        assertEquals(32f, TileSize.menu.value)
        assertEquals(40f, TileSize.priority.value)
        assertEquals(48f, TileSize.stat.value)
        assertEquals(56f, TileSize.attachment.value)
        assertEquals(64f, TileSize.listImage.value)
        assertEquals(88f, TileSize.logo.value)
        assertEquals(28f, IconSize.dismiss.value)
        assertEquals(32f, IconSize.lead.value)
        assertEquals(36f, IconSize.empty.value)
        assertEquals(44f, IconSize.banner.value)
        assertEquals(48f, IconSize.emptyLarge.value)
        assertEquals(56f, IconSize.done.value)
        assertEquals(64f, IconSize.preview.value)
        assertEquals(42f, CircleSize.repeatFrame.value)
    }

    @Test
    fun `ADEV-196 no picture, tile or large glyph types its size by hand`() {
        val base = File("src/main/java/com/gracefultools/astrid")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val typed = Regex("""\bsize(?:\(|\s*=)\s*(\d+)\.dp\b""")
        val offenders = listOf("ui", "platform").map { File(base, it) }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/theme/") }.flatMap { file ->
                file.readLines().withIndex().filter { (_, line) ->
                    typed.findAll(line.substringBefore("//")).any { it.groupValues[1].toInt() >= 24 }
                }.map { (i, line) -> "${file.relativeTo(base)}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertTrue("sizes typed by hand:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
