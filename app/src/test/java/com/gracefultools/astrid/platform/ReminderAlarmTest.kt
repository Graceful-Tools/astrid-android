package com.gracefultools.astrid.platform

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReminderAlarmTest {
    private class Recorded : Alarms {
        var at: Long? = null; var cancelled = false
        override fun set(atMillis: Long) { at = atMillis; cancelled = false }
        override fun cancel() { at = null; cancelled = true }
    }

    @Test
    fun `AATD-21 the alarm is set for the core's next reminder`() = runTest {
        val core = FakeCore().apply { answer("nextReminder", """{"reminder":{"taskId":"t1","title":"Vet","reminderTime":"2026-10-18T13:00:00Z"}}""") }
        val alarms = Recorded()
        val set = ReminderAlarm(core, alarms).reschedule()
        assertEquals(Instant.parse("2026-10-18T13:00:00Z").toEpochMilli(), set)
        assertEquals(set, alarms.at)
    }

    @Test
    fun `AATD-21 with nothing to come the alarm is cleared`() = runTest {
        val core = FakeCore().apply { answer("nextReminder", """{"reminder":null}""") }
        val alarms = Recorded().apply { at = 5 }
        assertNull(ReminderAlarm(core, alarms).reschedule())
        assertTrue(alarms.cancelled)
    }
}
