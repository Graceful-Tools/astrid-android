package com.gracefultools.astrid.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * A decision's trade-off grid as an editor writes it (ADEV-241): iOS `AstridCore/DecisionGrid.swift`
 * (`markdown`, `Cell.markdown`, `escape`), which ports astrid-web `lib/decision-grid.ts`
 * `serializeGrid`. Which tokens are ratings is the core's (`ratingKey`); reading a grid back is
 * the core's too (`tradeoffGrid`, `renderMarkdown`), so only the writing side lives here.
 * The grid never gets a score or total column (web ADEV-178): the person decides.
 */
object DecisionGrid {
    /** `rating` is the token as written (`++`, `~`), or null for an empty cell; `text` the reason after it. */
    data class Cell(val rating: String?, val text: String) : java.io.Serializable {
        /** `<rating> <text>`, either part left out when empty. */
        val markdown: String get() = listOfNotNull(rating, text).filter { it.isNotEmpty() }.joinToString(" ")
    }

    data class Row(val name: String, val cells: List<Cell>)

    /** One entry of the core's key: the token, its wire name (`veryPoor` … `veryGood`), worst (0) to best (4). */
    data class Rating(val token: String, val labelKey: String, val rank: Int)

    /** A header, a separator, then one numbered row per option. No score column, ever. */
    fun markdown(optionLabel: String, criteria: List<String>, rows: List<Row>): String {
        val header = listOf(optionLabel.ifEmpty { "Option" }) + criteria
        val lines = mutableListOf(
            "| " + header.joinToString(" | ", transform = ::escape) + " |",
            "|" + header.joinToString("|") { "---" } + "|",
        )
        rows.forEachIndexed { i, row ->
            val cells = criteria.indices.map { c -> row.cells.getOrNull(c) ?: Cell(null, "") }
            val line = listOf("${i + 1}. ${row.name}") + cells.map { it.markdown }
            lines += "| " + line.joinToString(" | ", transform = ::escape) + " |"
        }
        return lines.joinToString("\n")
    }

    /**
     * The tokens a picker offers, worst to best, from the core's key — the plain ones only: the
     * emoji forms are read and kept as written, but not offered (iOS `ratingTokens`).
     */
    fun ratingTokens(): List<Rating> = keyed

    private val keyed: List<Rating> by lazy {
        (CoreRules.ask(command("ratingKey")) as JsonArray).map { it.jsonObject }
            .map { Rating(it["token"]!!.jsonPrimitive.content, it["labelKey"]!!.jsonPrimitive.content, it["rank"]!!.jsonPrimitive.int) }
            .filter { r -> r.token.all { it.code < 128 } }
    }

    private fun escape(text: String): String =
        text.replace("\r\n", " ").replace("\n", " ").replace("\\", "\\\\").replace("|", "\\|").trim()
}

/**
 * A decision being written in task details (ADEV-241; iOS `DecisionDraft`, ADEV-189 and ADEV-231):
 * the question, its options and, if wanted, a trade-off grid rated in traffic-light colours. It
 * becomes ordinary markdown in the format astrid-web `docs/DECISIONS.md` describes, posted as a
 * comment or added to the description, so every app draws it the same way. Nothing about a
 * decision is kept anywhere but that text.
 */
data class DecisionDraft(
    val question: String = "",
    val options: List<Option> = listOf(Option(), Option()),
    /** The option marked `(recommended)`, if any. */
    val recommended: String? = null,
    val criteria: List<Criterion> = emptyList(),
    private val cells: Map<Pair<String, String>, DecisionGrid.Cell> = emptyMap(),
) : java.io.Serializable {
    data class Option(val id: String = UUID.randomUUID().toString(), val text: String = "") : java.io.Serializable

    /** `detail` is what it means, optional (ADEV-231): one line of the key under the grid. */
    data class Criterion(val id: String = UUID.randomUUID().toString(), val name: String = "", val detail: String = "") : java.io.Serializable

    enum class Problem {
        NoQuestion,
        TooFewOptions,

        /** A reason with no colour: the table would no longer read as a grid. */
        ReasonWithoutRating,
    }

    fun cell(option: String, criterion: String): DecisionGrid.Cell = cells[option to criterion] ?: DecisionGrid.Cell(null, "")

    fun set(cell: DecisionGrid.Cell, option: String, criterion: String): DecisionDraft = copy(cells = cells + ((option to criterion) to cell))

    private val namedOptions get() = options.filter { line(it.text).isNotEmpty() }
    private val namedCriteria get() = criteria.filter { line(it.name).isNotEmpty() }

    /** Only the cells of options and criteria that are written down. */
    private val keptCells get() = namedOptions.flatMap { o -> namedCriteria.map { cell(o.id, it.id) } }

    val problem: Problem?
        get() = when {
            line(question).isEmpty() -> Problem.NoQuestion
            namedOptions.size < 2 -> Problem.TooFewOptions
            keptCells.any { it.rating == null && line(it.text).isNotEmpty() } -> Problem.ReasonWithoutRating
            else -> null
        }

    /** A grid only once a cell has a colour; criteria alone add nothing. */
    private val grid: String?
        get() {
            if (namedCriteria.isEmpty() || keptCells.none { it.rating != null }) return null
            return DecisionGrid.markdown(
                "Option", namedCriteria.map { line(it.name) },
                namedOptions.map { o ->
                    DecisionGrid.Row(line(o.text), namedCriteria.map { c -> cell(o.id, c.id).let { DecisionGrid.Cell(it.rating, line(it.text)) } })
                },
            )
        }

    val markdown: String
        get() {
            val lines = mutableListOf("**Decision:** ${line(question)}", "")
            namedOptions.forEachIndexed { i, option ->
                val mark = if (option.id == recommended) " (recommended)" else ""
                lines += "- **${i + 1}.** ${line(option.text)}$mark"
            }
            grid?.let { table ->
                lines += listOf("", table)
                val key = namedCriteria.filter { line(it.detail).isNotEmpty() }.map { "- **${line(it.name)}:** ${line(it.detail)}" }
                if (key.isNotEmpty()) lines += listOf("") + key
            }
            return lines.joinToString("\n")
        }

    /** `description` with the decision after what is already there. */
    fun appendedTo(description: String): String = append(markdown, description)

    companion object {
        /** The grid's columns, as web's guide asks: three to five; one or two are allowed. */
        const val MAX_CRITERIA = 5

        /** `description` with `markdown` after what is already there, a blank line between. */
        fun append(markdown: String, description: String): String =
            if (description.isBlank()) markdown else description.trimEnd() + "\n\n" + markdown

        /** One line: a decision's question, options and cells never span lines. */
        private fun line(text: String): String =
            text.split("\r\n", "\n", "\r").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    }
}
