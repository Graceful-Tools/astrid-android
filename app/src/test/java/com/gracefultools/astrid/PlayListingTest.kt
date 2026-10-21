package com.gracefultools.astrid

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AATD-19: the Play listing draft lives in `src/main/play/` (Gradle Play Publisher's layout) and
 * stays inside Play Console's limits, so the first upload pastes it as it is. Every permission the
 * manifest asks for is explained in `permissions.md`, which the Play declarations are copied from.
 */
class PlayListingTest {
    private val play = File("src/main/play")
    private fun text(path: String) = File(play, path).readText().trimEnd()

    @Test
    fun `AATD-19 the English listing fits Play's limits`() {
        val limits = mapOf(
            "listings/en-US/title.txt" to 30,
            "listings/en-US/short-description.txt" to 80,
            "listings/en-US/full-description.txt" to 4000,
            "release-notes/en-US/default.txt" to 500,
        )
        for ((path, limit) in limits) {
            val body = text(path)
            assertTrue("$path is empty", body.isNotBlank())
            assertTrue("$path is ${body.length} characters; Play allows $limit", body.length <= limit)
        }
        assertEquals("Astrid", text("listings/en-US/title.txt").substringBefore(':').trim())
    }

    @Test
    fun `AATD-19 every permission the manifest asks for is declared`() {
        val asked = Regex("""uses-permission\s+android:name="([^"]+)"""")
            .findAll(File("src/main/AndroidManifest.xml").readText())
            .map { it.groupValues[1].substringAfterLast('.') }
            .toList()
        assertTrue(asked.isNotEmpty())
        val declared = text("permissions.md")
        for (permission in asked) {
            assertTrue("$permission is not explained in src/main/play/permissions.md", "`$permission`" in declared)
        }
    }
}
