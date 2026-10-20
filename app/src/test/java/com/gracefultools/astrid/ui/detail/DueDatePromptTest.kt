package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DueDatePromptTest {
    @Test
    fun `AATD-54 setting a due date in the detail asks about notifications, clearing it does not`() = runTest {
        val core = FakeCore().apply {
            answer("taskDetail", """{"task":{"id":"t1","title":"Vet"},"descriptionBlocks":[],"comments":[],"subtasks":[]}""")
            answer("dueDateOnDay", """{"dueDateTime":"2026-10-20T00:00:00Z","isAllDay":true}""")
        }
        val vm = TaskDetailViewModel(core, "t1", UnconfinedTestDispatcher())
        var asked = 0
        vm.onDueDateSet = { asked++ }
        vm.setDue(null, false)
        assertEquals(0, asked)
        vm.setDue("2026-10-20T09:00:00Z", false)
        vm.pickDay("2026-10-20")
        assertEquals(2, asked)
    }
}
