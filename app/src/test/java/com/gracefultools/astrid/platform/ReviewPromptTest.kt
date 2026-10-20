package com.gracefultools.astrid.platform

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptTest {
    private class Ledger : ReviewLedger {
        override var reviewPromptCount = 0
        override var lastReviewPromptMillis: Long? = null
        override var hasReviewed = false
    }

    private val day = 24L * 60 * 60 * 1000

    private fun core(completed: Int) = FakeCore().apply { answer("profileStats", """{"completed":$completed,"inspired":0,"supported":0}""") }

    @Test
    fun `AATD-63 Loving Astrid is asked once five tasks are completed, not before`() = runTest {
        assertFalse(ReviewPrompt(Ledger(), core(4)) { 0 }.isDue())
        val ledger = Ledger()
        val prompt = ReviewPrompt(ledger, core(5)) { 1_000L }
        prompt.check()
        assertEquals(ReviewDialog.LOVE, prompt.showing.value)
        assertEquals(1, ledger.reviewPromptCount)
        assertEquals(1_000L, ledger.lastReviewPromptMillis)
    }

    @Test
    fun `AATD-63 at most every 30 days`() = runTest {
        var now = 10 * day
        val ledger = Ledger()
        val prompt = ReviewPrompt(ledger, core(20)) { now }
        prompt.check()
        prompt.dismiss()
        now += 29 * day
        prompt.check()
        assertEquals(ReviewDialog.NONE, prompt.showing.value)
        now += day
        prompt.check()
        assertEquals(ReviewDialog.LOVE, prompt.showing.value)
        assertEquals(2, ledger.reviewPromptCount)
    }

    @Test
    fun `AATD-63 yes is never asked again, not really offers feedback`() = runTest {
        val ledger = Ledger()
        val prompt = ReviewPrompt(ledger, core(9)) { 0 }
        prompt.check()
        prompt.notReally()
        assertEquals(ReviewDialog.FEEDBACK, prompt.showing.value)
        prompt.dismiss()
        prompt.love()
        assertTrue(ledger.hasReviewed)
        assertFalse(ReviewPrompt(ledger, core(99)) { 365 * day }.isDue())
    }

    @Test
    fun `AATD-63 without the core's count there is no ask`() = runTest {
        val core = FakeCore().apply { answer("profileStats") { throw CoreFailure(FailureKind.OFFLINE, "no network") } }
        val prompt = ReviewPrompt(Ledger(), core) { 0 }
        prompt.check()
        assertEquals(ReviewDialog.NONE, prompt.showing.value)
    }

    @Test
    fun `AATD-63 Send Feedback mails support with iOS's subject`() {
        assertEquals("mailto:support@astrid.cc?subject=Astrid%20App%20Feedback", ReviewPrompt.feedbackMailto())
    }
}
