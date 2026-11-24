package com.gracefultools.astrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ADEV-257. Answering a decision in the thread: iOS `DecisionThreadTests` (AITD-579, AITD-584,
 * AITD-585, ADEV-232) case for case, plus the ADEV-229 rule that a decision with a grid of the
 * same options is chosen from the grid. The decision itself is read by the real core.
 */
class DecisionThreadTest {
    private val agent = "ai-agent-claude"
    private val jon = "jon"
    private val question = """
        **Decision:** Which way?
        - **A.** Go (recommended)
        - **B.** Wait
    """.trimIndent()

    private fun comment(id: String, content: String, by: String?, minute: Int, system: Boolean = false) =
        DecisionThread.Comment(id, content, by, isSystem = system, createdAt = "2026-11-24T10:%02d:00Z".format(minute))

    private val decision get() = Decisions.parse(question)!!

    private fun state(asked: DecisionThread.Comment, thread: List<DecisionThread.Comment>) =
        DecisionResponse(DecisionThread.answer(asked, thread)) {}.state(decision)

    @Test
    fun `ADEV-257 the core reads the question, its options and the recommended one`() {
        assertEquals("Which way?", decision.prompt)
        assertEquals(listOf("Go", "Wait"), decision.options.map { it.text })
        assertEquals(listOf(true, false), decision.options.map { it.recommended })
        assertEquals(listOf("A", "B"), decision.options.map { it.letter })
        assertNull("plain text is not a decision", Decisions.parse("Just a comment"))
    }

    @Test
    fun `ADEV-257 an unanswered question takes a tap (AITD-579)`() {
        val asked = comment("q", question, agent, 1)
        val thread = listOf(asked, comment("s", "Jon moved this to Waiting", null, 2, system = true), comment("f", "Also, no rush", agent, 3))
        assertEquals(DecisionResponse.State.Open, state(asked, thread))
    }

    @Test
    fun `ADEV-257 a reply starting with a letter shows that option chosen (AITD-579)`() {
        val asked = comment("q", question, agent, 1)
        val thread = listOf(asked, comment("r", "b", jon, 2), comment("x", "A", jon, 3))
        assertEquals(DecisionResponse.State.Chosen(2), state(asked, thread))
    }

    @Test
    fun `ADEV-257 a tapped option replies with its number, which marks it chosen (AITD-585)`() {
        val asked = comment("q", question, agent, 1)
        assertEquals("2 — Wait", Decisions.reply(decision.options[1]))
        val thread = listOf(asked, comment("r", Decisions.reply(decision.options[1]), jon, 2))
        assertEquals(DecisionResponse.State.Chosen(2), state(asked, thread))
    }

    @Test
    fun `ADEV-257 a reply in words closes it without choosing (AITD-579)`() {
        val asked = comment("q", question, agent, 1)
        assertEquals(DecisionResponse.State.AnsweredInWords, state(asked, listOf(asked, comment("r", "Let's talk first", jon, 2))))
    }

    @Test
    fun `ADEV-257 an earlier comment never answers a later question (AITD-579)`() {
        val asked = comment("q", question, agent, 5)
        assertNull(DecisionThread.answer(asked, listOf(comment("r", "A", jon, 1), asked)))
    }

    @Test
    fun `ADEV-257 a description only shows the options (AITD-579)`() {
        assertEquals(DecisionResponse.State.ReadOnly, DecisionResponse.state(null, decision))
    }

    @Test
    fun `ADEV-257 tapping an open option sends it at once (AITD-584)`() {
        val sent = mutableListOf<String>()
        DecisionResponse(null) { sent += Decisions.reply(it) }.tap(decision.options[1], decision)
        assertEquals(listOf("2 — Wait"), sent)
    }

    @Test
    fun `ADEV-257 tapping an answered question sends nothing (AITD-584)`() {
        val sent = mutableListOf<String>()
        val asked = comment("q", question, agent, 1)
        val answer = DecisionThread.answer(asked, listOf(asked, comment("r", "A", jon, 2)))
        DecisionResponse(answer) { sent += Decisions.reply(it) }.tap(decision.options[1], decision)
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `ADEV-257 the asker choosing an option answers their own question (ADEV-232)`() {
        val asked = comment("q", question, jon, 1)
        assertEquals(DecisionResponse.State.Chosen(2), state(asked, listOf(asked, comment("r", "2 — Wait", jon, 2))))
    }

    @Test
    fun `ADEV-257 the asker's follow-up in words leaves it open (ADEV-232)`() {
        val asked = comment("q", question, jon, 1)
        assertNull(DecisionThread.answer(asked, listOf(asked, comment("r", "Leaning towards going, thoughts?", jon, 2))))
    }

    @Test
    fun `ADEV-257 chosen numbers follow the core's rule`() {
        val options = decision.options
        assertEquals(2, Decisions.chosenNumber("2 — Wait", options))
        assertEquals(1, Decisions.chosenNumber("1. go", options))
        assertEquals(2, Decisions.chosenNumber("b", options))
        assertNull(Decisions.chosenNumber("Also, no", options))
        assertNull(Decisions.chosenNumber("2nd thoughts", options))
        assertNull(Decisions.chosenNumber("10 minutes", options))
        assertNull(Decisions.chosenNumber("Béatrice doit vérifier", options))
    }

    @Test
    fun `ADEV-257 a decision with a grid of the same options is chosen from the grid (ADEV-229)`() {
        val withGrid = "$question\n\n| Option | Cost |\n|---|---|\n| 1. Go | ++ |\n| 2. Wait | - |"
        assertEquals(true, Decisions.parse(withGrid)!!.let { Decisions.gridIsTheChoice(it) })
        val mismatched = "$question\n\n| Option | Cost |\n|---|---|\n| 1. Go | ++ |"
        assertEquals(false, Decisions.parse(mismatched)!!.let { Decisions.gridIsTheChoice(it) })
        assertEquals(false, Decisions.gridIsTheChoice(decision))
    }
}
