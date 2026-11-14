package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.Brand
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * AATD-105 (astrid-web AWTD-1630): the full-screen description's draft preview renders through the
 * core's `renderMarkdown`, which, built with `require-base-url`, leaves a relative link relative
 * unless the caller names an origin. The preview names this build's server, so `/lists/abc` opens
 * there rather than as an unopenable path.
 */
class DescriptionRenderOriginTest {
    @Test
    fun `AATD-105 a relative link in a description draft opens on this build's server`() {
        val blocks = rendered("[home](/lists/abc)")
        assertNotNull("the core renders the draft", blocks)
        val links = blocks!!.flatMap { (it["inlines"] as? JsonArray).orEmpty() }
            .mapNotNull { ((it as JsonObject)["link"] as? JsonPrimitive)?.content }
        assertEquals(listOf("${Brand.baseUrl}/lists/abc"), links)
    }
}
