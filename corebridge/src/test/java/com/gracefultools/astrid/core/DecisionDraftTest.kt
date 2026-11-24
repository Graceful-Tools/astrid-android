package com.gracefultools.astrid.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADEV-241. A decision written from task details: iOS `DecisionDraftTests` (ADEV-189, ADEV-231),
 * case for case, so the markdown Android writes is byte for byte what iOS and the Mac write.
 */
class DecisionDraftTest {
    private fun draft(question: String, options: List<String>): DecisionDraft =
        DecisionDraft(question = question, options = options.map { DecisionDraft.Option(text = it) })

    @Test
    fun `ADEV-241 a question and options write the decision format`() {
        var d = draft("Ship today?", listOf("Ship today", "Wait for the review"))
        d = d.copy(recommended = d.options[0].id)
        assertEquals(
            """
            **Decision:** Ship today?

            - **1.** Ship today (recommended)
            - **2.** Wait for the review
            """.trimIndent(),
            d.markdown,
        )
    }

    @Test
    fun `ADEV-241 blank options are left out and the rest renumbered`() {
        val d = draft("Where?", listOf("Lisbon", "  ", "Cabin"))
        assertTrue(d.markdown, d.markdown.endsWith("- **1.** Lisbon\n- **2.** Cabin"))
    }

    @Test
    fun `ADEV-241 rated criteria add the trade-off grid`() {
        var d = draft("Where?", listOf("Lisbon", "Cabin"))
        d = d.copy(criteria = listOf(DecisionDraft.Criterion(name = "Cost"), DecisionDraft.Criterion(name = "Fun")))
        d = d.set(DecisionGrid.Cell("~", "flights"), d.options[0].id, d.criteria[0].id)
            .set(DecisionGrid.Cell("++", "sun"), d.options[0].id, d.criteria[1].id)
            .set(DecisionGrid.Cell("+", ""), d.options[1].id, d.criteria[0].id)
        assertTrue(
            d.markdown,
            d.markdown.endsWith(
                """
                - **2.** Cabin

                | Option | Cost | Fun |
                |---|---|---|
                | 1. Lisbon | ~ flights | ++ sun |
                | 2. Cabin | + |  |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `ADEV-241 a pipe in a note is escaped so the row stays one row`() {
        var d = draft("Where?", listOf("Lisbon", "Cabin"))
        d = d.copy(criteria = listOf(DecisionDraft.Criterion(name = "Cost")))
        d = d.set(DecisionGrid.Cell("++", "cheap | quiet"), d.options[1].id, d.criteria[0].id)
        assertTrue(d.markdown, d.markdown.endsWith("| 2. Cabin | ++ cheap \\| quiet |"))
    }

    @Test
    fun `ADEV-241 criteria with no ratings add no grid`() {
        var d = draft("Where?", listOf("Lisbon", "Cabin"))
        d = d.copy(criteria = listOf(DecisionDraft.Criterion(name = "Cost")))
        assertFalse(d.markdown.contains("|"))
    }

    @Test
    fun `ADEV-241 what stops it being posted`() {
        assertEquals(DecisionDraft.Problem.NoQuestion, draft(" ", listOf("A", "B")).problem)
        assertEquals(DecisionDraft.Problem.TooFewOptions, draft("Which?", listOf("A", "")).problem)
        var d = draft("Which?", listOf("A", "B"))
        assertNull(d.problem)
        d = d.copy(criteria = listOf(DecisionDraft.Criterion(name = "Cost")))
        d = d.set(DecisionGrid.Cell(null, "cheap"), d.options[0].id, d.criteria[0].id)
        assertEquals("an unrated reason would stop the table reading as a grid", DecisionDraft.Problem.ReasonWithoutRating, d.problem)
    }

    @Test
    fun `ADEV-241 a line break in an option stays on its line`() {
        val d = draft("Which\nway?", listOf("Left\nturn", "Right"))
        assertTrue(d.markdown, d.markdown.startsWith("**Decision:** Which way?"))
        assertTrue(d.markdown, d.markdown.contains("- **1.** Left turn\n"))
    }

    @Test
    fun `ADEV-241 added to a description after what is there`() {
        val d = draft("Which?", listOf("A", "B"))
        assertEquals(d.markdown, d.appendedTo(""))
        assertEquals("Notes.\n\n" + d.markdown, d.appendedTo("Notes.\n\n"))
        assertEquals("Notes.\n\n" + d.markdown, d.appendedTo("Notes.  \n"))
    }

    private fun rated(): DecisionDraft {
        var d = draft("Where?", listOf("Lisbon", "Cabin"))
        d = d.copy(criteria = listOf(DecisionDraft.Criterion(name = "Cost"), DecisionDraft.Criterion(name = "Fun")))
        return d.set(DecisionGrid.Cell("+", "cheap flights"), d.options[0].id, d.criteria[0].id)
    }

    @Test
    fun `ADEV-241 a criterion's meaning is the key under the grid (ADEV-231)`() {
        val base = rated()
        val d = base.copy(criteria = listOf(base.criteria[0].copy(detail = "what the company pays,\nall in"), base.criteria[1]))
        assertTrue(
            d.markdown,
            d.markdown.endsWith(
                """
                | 2. Cabin |  |  |

                - **Cost:** what the company pays, all in
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `ADEV-241 no meanings no key (ADEV-231)`() {
        assertTrue(rated().markdown, rated().markdown.endsWith("| 2. Cabin |  |  |"))
    }

    @Test
    fun `ADEV-241 no grid no key (ADEV-231)`() {
        var d = rated()
        d = d.set(DecisionGrid.Cell(null, ""), d.options[0].id, d.criteria[0].id)
        d = d.copy(criteria = listOf(d.criteria[0].copy(detail = "what the company pays"), d.criteria[1]))
        assertFalse("a key with no grid explains nothing", d.markdown.contains("Cost"))
    }

    @Test
    fun `ADEV-241 at most five criteria, as web's guide asks`() {
        assertEquals(5, DecisionDraft.MAX_CRITERIA)
    }

    // --- Against the real core: what the composer writes is what the apps read. ---

    @Test
    fun `ADEV-241 the lights are the core's five plain tokens, worst to best`() {
        assertEquals(listOf("--", "-", "~", "+", "++"), DecisionGrid.ratingTokens().map { it.token })
        assertEquals(listOf("veryPoor", "poor", "mixed", "good", "veryGood"), DecisionGrid.ratingTokens().map { it.labelKey })
    }

    @Test
    fun `ADEV-241 the written decision reads as a decision and one grid`() {
        var d = draft("Where?", listOf("Lisbon", "Cabin"))
        d = d.copy(recommended = d.options[1].id, criteria = listOf(DecisionDraft.Criterion(name = "Cost")))
        d = d.set(DecisionGrid.Cell("--", "dear"), d.options[0].id, d.criteria[0].id)
            .set(DecisionGrid.Cell("++", "cheap | quiet"), d.options[1].id, d.criteria[0].id)

        val decision = CoreRules.ask(command("decision", "text" to d.markdown)).jsonObject
        val options = decision["options"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(false, true), options.map { it["recommended"]?.jsonPrimitive?.content == "true" })

        val blocks = (CoreRules.ask(command("renderMarkdown", "text" to d.markdown)) as JsonArray).map { it as JsonObject }
        val grids = blocks.filter { (it["kind"] as? JsonPrimitive)?.content == "tradeoffGrid" }
        assertEquals(1, grids.size)
        val ratings = grids[0]["rows"]!!.jsonArray.map { row ->
            row.jsonObject["cells"]!!.jsonArray.map { it.jsonObject["rating"]?.jsonPrimitive?.content }
        }
        assertEquals(listOf(listOf("veryPoor"), listOf("veryGood")), ratings)
    }
}
