package com.gracefultools.astrid.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A comment read as a Decision (ADEV-257; iOS `Decisions` in AstridCore). Which comment is one, and
 * what it asks, is the core's `decision` rule. The rules door does not expose the core's `reply` and
 * `chosen_number`, so they are mirrored here exactly as iOS mirrors them (`decisions.rs`).
 */
object Decisions {
    data class Option(val letter: String, val number: Int, val text: String, val recommended: Boolean)

    /** `before` and `after` are the markdown around the question and its option list. */
    data class Decision(val before: String, val prompt: String, val options: List<Option>, val after: String)

    /** The core's reading of `text`, or null when it is not a Decision (or the core cannot answer). */
    fun parse(text: String): Decision? {
        if (text.isBlank()) return null
        val answer = runCatching { CoreRules.ask(command("decision", "text" to text)) }.getOrNull()
        if (answer == null || answer is JsonNull) return null
        val o = answer.jsonObject
        return Decision(
            before = o["before"]?.jsonPrimitive?.content.orEmpty(),
            prompt = o["prompt"]?.jsonPrimitive?.content.orEmpty(),
            options = o["options"]!!.jsonArray.map { it.jsonObject }.map {
                Option(it["letter"]!!.jsonPrimitive.content, it["number"]!!.jsonPrimitive.int, it["text"]!!.jsonPrimitive.content, it["recommended"]!!.jsonPrimitive.boolean)
            },
            after = o["after"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    /** The comment a tap posts: the option's number, then its words (core `decisions::reply`). */
    fun reply(option: Option): String = if (option.text.isEmpty()) option.number.toString() else "${option.number} — ${option.text}"

    /**
     * The number of the option a reply picks: the number it starts with, or the letter the asker
     * wrote, alone or followed by something that is not a letter or number in any script
     * (core `decisions::chosen_number`). "2 — Wait", "1. go" and "b" choose; "2nd thoughts",
     * "10 minutes" and "Béatrice doit vérifier" do not.
     */
    fun chosenNumber(reply: String, options: List<Option>): Int? {
        val body = reply.trimStart()
        val lead = body.takeWhile { it.code < 128 && it.isLetterOrDigit() }
        if (lead.isEmpty()) return null
        val next = body.drop(lead.length).firstOrNull()
        if (next != null && next.isLetterOrDigit()) return null
        if (lead.all { it.isDigit() }) {
            val number = lead.toIntOrNull() ?: return null
            return number.takeIf { n -> options.any { it.number == n } }
        }
        return options.firstOrNull { it.letter == lead.uppercase() }?.number
    }

    /**
     * The trade-off grid that is this decision's choice (ADEV-229, iOS `DecisionDisplay`): the first
     * grid after the options with one row per option, row n being option n; null when there is none,
     * and the decision keeps its cards.
     */
    fun choiceGrid(decision: Decision): JsonObject? {
        if (decision.after.isBlank()) return null
        val blocks = runCatching { CoreRules.ask(command("renderMarkdown", "text" to decision.after)) as JsonArray }.getOrNull() ?: return null
        val grid = blocks.map { it.jsonObject }.firstOrNull { (it["kind"] as? JsonPrimitive)?.content == "tradeoffGrid" } ?: return null
        return grid.takeIf { (it["rows"] as? JsonArray)?.size == decision.options.size }
    }

    fun gridIsTheChoice(decision: Decision): Boolean = choiceGrid(decision) != null
}

/**
 * Whether a question asked in a comment is still open, and what answered it (ADEV-257; iOS
 * `DecisionThread`, AITD-579). The server keeps no decision state: the thread is the only record.
 */
object DecisionThread {
    data class Comment(val id: String, val content: String, val authorId: String?, val isSystem: Boolean = false, val createdAt: String? = null)

    /**
     * The first comment after `asked` that answers it, or null while nobody has: a reply from someone
     * else, or the asker's own reply choosing one of its options (ADEV-232). A system line and the
     * asker's follow-up in words are not answers.
     */
    fun answer(asked: Comment, comments: List<Comment>): Comment? {
        val position = comments.indexOfFirst { it.id == asked.id }
        if (position < 0) return null
        val options = Decisions.parse(asked.content)?.options.orEmpty()
        return comments.drop(position + 1)
            .filter { isLater(it, asked) }
            .firstOrNull { reply ->
                !reply.isSystem && (reply.authorId != asked.authorId || Decisions.chosenNumber(reply.content, options) != null)
            }
    }

    /** Threads arrive in order; a timestamp, where both have one, settles a row out of place (ISO 8601 sorts as text). */
    private fun isLater(reply: Comment, asked: Comment): Boolean {
        val replied = reply.createdAt ?: return true
        val at = asked.createdAt ?: return true
        return replied >= at
    }
}

/**
 * What a thread hands a decision it draws: the reply that answered it, if any, and how to answer
 * (iOS `DecisionResponse`). A description has none, so it only shows the options.
 */
class DecisionResponse(val answer: DecisionThread.Comment?, val choose: (Decisions.Option) -> Unit) {
    sealed interface State {
        /** No thread to answer in: a description, or the composer's preview. */
        data object ReadOnly : State
        data object Open : State
        data class Chosen(val number: Int) : State
        /** Someone replied without leading with a number or letter. */
        data object AnsweredInWords : State
    }

    fun state(decision: Decisions.Decision): State {
        val answer = answer ?: return State.Open
        return Decisions.chosenNumber(answer.content, decision.options)?.let { State.Chosen(it) } ?: State.AnsweredInWords
    }

    /** Sends only while the question is open. */
    fun tap(option: Decisions.Option, decision: Decisions.Decision) {
        if (state(decision) == State.Open) choose(option)
    }

    companion object {
        fun state(response: DecisionResponse?, decision: Decisions.Decision): State = response?.state(decision) ?: State.ReadOnly
    }
}
