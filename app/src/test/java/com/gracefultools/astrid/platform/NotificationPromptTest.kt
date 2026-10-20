package com.gracefultools.astrid.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPromptTest {
    private class Ledger : PromptLedger {
        override var promptCount = 0
        override var lastPromptMillis: Long? = null
    }

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `AATD-54 asks three times, then once a month`() {
        var now = 1_000_000L
        val ledger = Ledger()
        val prompt = NotificationPrompt(ledger) { now }
        repeat(3) {
            prompt.check(NotificationStatus.NOT_DETERMINED)
            assertEquals(PromptDecision.ASK, prompt.showing.value)
            prompt.dismiss()
            now += day
        }
        assertEquals(3, ledger.promptCount)
        prompt.check(NotificationStatus.NOT_DETERMINED)
        assertEquals(PromptDecision.NONE, prompt.showing.value)
        assertEquals(3, ledger.promptCount)

        now = ledger.lastPromptMillis!! + 30 * day
        prompt.check(NotificationStatus.NOT_DETERMINED)
        assertEquals(PromptDecision.ASK, prompt.showing.value)
        assertEquals(4, ledger.promptCount)
    }

    @Test
    fun `AATD-54 a granted permission is never asked for, a refused one offers Settings`() {
        val prompt = NotificationPrompt(Ledger()) { 0 }
        assertEquals(PromptDecision.NONE, prompt.decide(NotificationStatus.AUTHORIZED))
        prompt.check(NotificationStatus.AUTHORIZED)
        assertEquals(PromptDecision.NONE, prompt.showing.value)
        prompt.check(NotificationStatus.DENIED)
        assertEquals(PromptDecision.OPEN_SETTINGS, prompt.showing.value)
    }

    @Test
    fun `AATD-54 past the three with no date recorded, one more is allowed`() {
        val ledger = Ledger().apply { promptCount = 5 }
        assertEquals(PromptDecision.ASK, NotificationPrompt(ledger) { 0 }.decide(NotificationStatus.NOT_DETERMINED))
    }
}
