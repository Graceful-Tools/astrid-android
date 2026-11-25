package com.gracefultools.astrid.ui.detail

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AATD-114 (astrid-core `011080d4`): the tablet's task callout is placed by the core's
 * `calloutPlacement` rule — side, box and arrow — not by arithmetic in the shell. Every case of the
 * core-owned fixture (`contracts/fixtures/callout-placement.json`, copied at the pinned revision into
 * `src/test/resources/core-contracts`) goes through [placeCallout] and must come back as the core
 * answered it.
 */
class CalloutPlacementTest {
    private val fixture = Json.parseToJsonElement(File("src/test/resources/core-contracts/callout-placement.json").readText()).jsonObject

    private fun JsonObject.f(key: String) = getValue(key).jsonPrimitive.float
    private fun JsonObject.rect() = CalloutRect(f("left"), f("top"), f("width"), f("height"))

    @Test
    fun `AATD-114 every fixture case is placed exactly as the core's rule places it`() {
        val cases = fixture.getValue("cases").jsonArray.map { it.jsonObject }
        assertEquals("the fixture's eight cases", 8, cases.size)
        for (case in cases) {
            val name = case.getValue("name").jsonPrimitive.content
            val request = case.getValue("request").jsonObject
            val want = case.getValue("answer").jsonObject.getValue("value").jsonObject
            val got = placeCallout(
                anchor = request.getValue("anchor").jsonObject.rect(),
                viewport = request.getValue("viewport").jsonObject.rect(),
                width = request.f("width"),
                height = request.f("height"),
            )
            val side = if (want.getValue("side").jsonPrimitive.content == "left") CalloutSide.LEFT else CalloutSide.RIGHT
            assertEquals(name, CalloutPlacement(side, want.rect(), want.f("arrowOffset")), got)
        }
    }
}
