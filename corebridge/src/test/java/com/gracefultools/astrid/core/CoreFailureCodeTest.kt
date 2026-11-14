package com.gracefultools.astrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * AATD-98. The core names a refusal a shell must act on with a `code` beside its `kind` —
 * `WAITING_NEEDS_CONDITION` is the cue for the Waiting prompt. The bridge used to drop it.
 */
class CoreFailureCodeTest {
    private fun failureOf(envelope: String): CoreFailure = try {
        answerOf(envelope); fail("expected a failure"); throw IllegalStateException()
    } catch (failure: CoreFailure) { failure }

    @Test
    fun `AATD-98 a refusal carries the core's code`() {
        val failure = failureOf("""{"ok":false,"error":{"kind":"refused","message":"Waiting needs a date or a task","code":"WAITING_NEEDS_CONDITION","status":422}}""")
        assertEquals(FailureKind.REFUSED, failure.kind)
        assertEquals("WAITING_NEEDS_CONDITION", failure.code)
        assertEquals(422, failure.status)
    }

    @Test
    fun `AATD-98 a failure with no code has none`() {
        assertNull(failureOf("""{"ok":false,"error":{"kind":"notFound","message":"gone"}}""").code)
    }
}
