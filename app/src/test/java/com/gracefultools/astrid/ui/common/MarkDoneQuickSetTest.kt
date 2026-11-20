package com.gracefultools.astrid.ui.common

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.LeadingControl
import com.gracefultools.astrid.model.TaskRow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADEV-150 (ADEV-146/148): the quick changer's completion row, as iOS MarkDoneQuickSetRow. */
class MarkDoneQuickSetTest {
    private val mine = LeadingControl(kind = "checkbox")
    private val theirs = LeadingControl(kind = "avatar", userId = "u-other")
    private val nobodys = LeadingControl(kind = "unassigned")

    @Test
    fun `ADEV-150 the row says what a tap does from where the task is now`() {
        assertEquals(R.string.tasks_mark_as_done, MarkDoneQuickSet.label(completed = false))
        assertEquals(R.string.tasks_mark_as_todo, MarkDoneQuickSet.label(completed = true))
    }

    @Test
    fun `ADEV-150 completing a task that is not yours asks first, yours and reopening never do`() {
        assertTrue(MarkDoneQuickSet.needsConfirmation(TaskRow(leading = theirs)))
        assertTrue(MarkDoneQuickSet.needsConfirmation(TaskRow(leading = nobodys)))
        assertFalse(MarkDoneQuickSet.needsConfirmation(TaskRow(leading = mine)))
        // A finished task of someone else's wears the checkbox now (ADEV-147); reopening never asks.
        assertFalse(MarkDoneQuickSet.needsConfirmation(TaskRow(leading = mine, completed = true)))
        assertFalse(MarkDoneQuickSet.needsConfirmation(TaskRow(leading = theirs, completed = true)))
    }

    @Test
    fun `ADEV-150 the row completes and reopens through completeTask`() = runTest {
        val core = FakeCore()
        RowActions(core).complete(TaskRow(id = "t1", completed = false))
        assertEquals(JsonPrimitive(true), core.last("completeTask")!!["completed"])
        assertEquals(JsonPrimitive("t1"), core.last("completeTask")!!["taskId"])
        RowActions(core).complete(TaskRow(id = "t2", completed = true))
        assertEquals(JsonPrimitive(false), core.last("completeTask")!!["completed"])
    }

    @Test
    fun `ADEV-150 the picker carries the row it was opened from`() = runTest {
        val core = FakeCore().apply { answer("taskStatusOptions", """{"current":"ready","columns":[]}""") }
        val row = TaskRow(id = "t1", action = "openPicker", priority = 3, isRepeating = true, leading = theirs)
        val outcome = RowActions(core).leading(row) as LeadingOutcome.PickStatus
        assertEquals(row, outcome.row)
    }
}
