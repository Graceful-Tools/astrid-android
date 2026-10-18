package com.gracefultools.astrid.ui.common

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.model.TaskRow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowActionsTest {
    @Test
    fun `AATD-12 a board card's leading control opens the status picker instead of completing`() = runTest {
        val core = FakeCore().apply {
            answer("taskStatusOptions", """{"current":"ready","columns":[{"id":"ready","name":"Ready","isCurrent":true},{"id":"doing","name":"Doing"}]}""")
        }
        val outcome = RowActions(core).leading(TaskRow(id = "t1", action = "openPicker"))
        assertTrue(outcome is LeadingOutcome.PickStatus)
        assertEquals(listOf("Ready", "Doing"), (outcome as LeadingOutcome.PickStatus).options.columns.map { it.name })
        assertFalse(core.kinds().contains("completeTask"))
        RowActions(core).setStatus("t1", "doing")
        assertEquals(JsonPrimitive("doing"), core.last("setTaskStatus")!!["columnId"])
    }

    @Test
    fun `AATD-8 a plain row completes, and a public row copies`() = runTest {
        val core = FakeCore()
        RowActions(core).leading(TaskRow(id = "t1", action = "complete", completed = false))
        assertEquals(JsonPrimitive(true), core.last("completeTask")!!["completed"])
        RowActions(core).leading(TaskRow(id = "t2", action = "copy"))
        assertEquals(JsonPrimitive("t2"), core.last("copyTask")!!["taskId"])
    }
}
