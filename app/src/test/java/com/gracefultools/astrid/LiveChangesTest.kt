package com.gracefultools.astrid

import com.gracefultools.astrid.ui.detail.TaskDetailViewModel
import com.gracefultools.astrid.ui.sidebar.SidebarViewModel
import com.gracefultools.astrid.ui.tasks.TaskListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AATD-30. The screens listen for the words astrid-core actually says (realtime/mod.rs
 * `Change::to_json`). A task added on a device reached the server but its row read "Waiting to send"
 * until the next minute's sync: the list reloaded on `outbox`, which the core never sends, and not on
 * `delivered`, which it does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveChangesTest {
    private val delivered = """{"change":"delivered","taskIds":["t1"],"listIds":[],"commentTaskIds":[],"channelIds":[],"undescribed":false,"pending":0,"running":0,"failed":0}"""

    @Test
    fun `AATD-30 a delivered write redraws the list, so the row stops saying Waiting to send`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", """{"total":0,"offset":0,"rows":[]}""") }
        val vm = TaskListViewModel(core, UnconfinedTestDispatcher(), locale = { "en-US" })
        vm.open("l1")
        val before = core.kinds().count { it == "rowsForList" }
        core.emit("delivered", delivered)
        assertEquals(before + 1, core.kinds().count { it == "rowsForList" })
    }

    @Test
    fun `AATD-30 a delivered write and a task change redraw the sidebar counts`() = runTest {
        val core = FakeCore().apply { answer("lists", "[]") }
        val vm = SidebarViewModel(core, UnconfinedTestDispatcher())
        vm.load()
        val before = core.kinds().count { it == "lists" }
        core.emit("delivered", delivered)
        core.emit("task", """{"change":"task","id":"t1"}""")
        assertEquals(before + 2, core.kinds().count { it == "lists" })
    }

    @Test
    fun `AATD-30 a colleague's comment reaches an open task — the core says comments, not comment`() = runTest {
        val core = FakeCore().apply {
            answer("taskDetail", """{"task":{"id":"t1","title":"Water plants"},"fieldOrder":[],"descriptionBlocks":[],"comments":[],"subtasks":[]}""")
        }
        TaskDetailViewModel(core, "t1", UnconfinedTestDispatcher())
        val before = core.kinds().count { it == "taskDetail" }
        core.emit("comments", """{"change":"comments","taskId":"t1"}""")
        core.emit("comments", """{"change":"comments","taskId":"someone-else"}""")
        core.emit("delivered", delivered)
        assertEquals(before + 2, core.kinds().count { it == "taskDetail" })
    }
}
