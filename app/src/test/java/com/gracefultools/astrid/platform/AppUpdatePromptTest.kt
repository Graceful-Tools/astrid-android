package com.gracefultools.astrid.platform

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdatePromptTest {
    private class Ledger : UpdateLedger {
        override var dismissedUpdateVersion: Int? = null
    }

    private class Source(var answer: () -> AvailableUpdate?) : UpdateSource {
        var asked = 0
        override suspend fun latest(): AvailableUpdate? { asked++; return answer() }
    }

    @Test
    fun `AATD-67 a newer build on Play shows the card`() = runTest {
        val prompt = AppUpdatePrompt(Ledger(), Source { AvailableUpdate(42) })
        prompt.check()
        assertEquals(AvailableUpdate(42), prompt.available.value)
    }

    @Test
    fun `AATD-67 failure is silence - no Play, offline, or nothing newer shows nothing`() = runTest {
        val nothing = AppUpdatePrompt(Ledger(), Source { null })
        nothing.check()
        assertNull(nothing.available.value)
        val failing = AppUpdatePrompt(Ledger(), Source { throw IllegalStateException("not installed from Play") })
        failing.check()
        assertNull(failing.available.value)
    }

    @Test
    fun `AATD-67 Play is asked once per launch`() = runTest {
        val source = Source { AvailableUpdate(42) }
        val prompt = AppUpdatePrompt(Ledger(), source)
        prompt.check()
        prompt.dismiss()
        prompt.check()
        assertEquals(1, source.asked)
        assertNull(prompt.available.value)
    }

    @Test
    fun `AATD-67 Not now is for that version only`() = runTest {
        val ledger = Ledger()
        val first = AppUpdatePrompt(ledger, Source { AvailableUpdate(42) })
        first.check()
        first.dismiss()
        assertEquals(42, ledger.dismissedUpdateVersion)

        val sameVersion = AppUpdatePrompt(ledger, Source { AvailableUpdate(42) })
        sameVersion.check()
        assertNull(sameVersion.available.value)

        val nextVersion = AppUpdatePrompt(ledger, Source { AvailableUpdate(43) })
        nextVersion.check()
        assertEquals(AvailableUpdate(43), nextVersion.available.value)
    }

    @Test
    fun `AATD-67 going to Play clears the card so it is not waiting on return`() = runTest {
        val ledger = Ledger()
        val prompt = AppUpdatePrompt(ledger, Source { AvailableUpdate(7) })
        prompt.check()
        prompt.acknowledgeUpdateOpened()
        assertNull(prompt.available.value)
        assertEquals(7, ledger.dismissedUpdateVersion)
    }
}
