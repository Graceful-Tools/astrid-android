package com.gracefultools.astrid.platform

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundSyncTest {
    @Test
    fun `AATD-10 a background pass sends the Outbox, then pulls`() = runTest {
        val core = FakeCore()
        assertEquals(BackgroundSync.Outcome.DONE, BackgroundSync.pass(core))
        assertEquals(listOf("drain", "sync"), core.kinds())
    }

    @Test
    fun `AATD-10 offline retries later, signed out does not`() = runTest {
        val offline = FakeCore().apply { answer("drain") { throw CoreFailure(FailureKind.OFFLINE, "no network") } }
        assertEquals(BackgroundSync.Outcome.RETRY, BackgroundSync.pass(offline))
        val signedOut = FakeCore().apply { answer("drain") { throw CoreFailure(FailureKind.UNAUTHORIZED, "gone") } }
        assertEquals(BackgroundSync.Outcome.GIVE_UP, BackgroundSync.pass(signedOut))
    }
}
