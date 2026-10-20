package com.gracefultools.astrid.platform

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RemindersTest {
    @Test
    fun `AATD-14 each due reminder is posted once and the core is told it was shown`() = runTest {
        val core = FakeCore().apply {
            answer("remindersDue", """{"reminders":[{"taskId":"t1","title":"Water plants","reminderTime":"2026-10-18T09:00:00Z"},{"taskId":"t2","title":"Call mum"}]}""")
        }
        val posted = mutableListOf<String>()
        assertEquals(2, ReminderNotifier(core) { posted += it.title }.postDue())
        assertEquals(listOf("Water plants", "Call mum"), posted)
        assertEquals(listOf("remindersDue", "reminderShown", "reminderShown"), core.kinds())
        assertEquals(JsonPrimitive("t2"), core.last("reminderShown")!!["taskId"])
    }

    @Test
    fun `AATD-14 Complete on a notification completes through completeTask, Snooze moves it an hour`() = runTest {
        val core = FakeCore()
        ReminderActions.handle(core, ReminderNotifier.ACTION_COMPLETE, "t1")
        assertEquals(JsonPrimitive(true), core.last("completeTask")!!["completed"])
        assertFalse(core.kinds().contains("updateTask"))
        ReminderActions.handle(core, ReminderNotifier.ACTION_SNOOZE, "t1")
        assertEquals(JsonPrimitive(60), core.last("snoozeReminder")!!["minutes"])
    }

    @Test
    fun `AATD-54 a batch is summarised once after its reminders are posted, and nothing due is no batch`() = runTest {
        val core = FakeCore().apply { answer("remindersDue", """{"reminders":[{"taskId":"t1","title":"A"},{"taskId":"t2","title":"B"}]}""") }
        val batches = mutableListOf<List<String>>()
        ReminderNotifier(core, onBatch = { due -> batches += due.map { it.taskId } }) { }.postDue()
        assertEquals(listOf(listOf("t1", "t2")), batches)

        core.answer("remindersDue", """{"reminders":[]}""")
        ReminderNotifier(core, onBatch = { due -> batches += due.map { it.taskId } }) { }.postDue()
        assertEquals(1, batches.size)
    }
}
