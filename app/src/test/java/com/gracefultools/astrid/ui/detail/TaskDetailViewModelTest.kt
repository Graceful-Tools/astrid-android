package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelTest {
    private val detail = """{"task":{"id":"t1","title":"Water plants","priority":1},
        "fieldOrder":["assignee","when","priority","lists"],"descriptionBlocks":[],"comments":[],"subtasks":[]}"""

    private fun vm(core: FakeCore, ref: String = "t1") = TaskDetailViewModel(core, ref, UnconfinedTestDispatcher())

    @Test
    fun `AATD-9 opening by identifier reads the task and then edits it by its id`() = runTest {
        val core = FakeCore().apply { answer("taskDetail", detail) }
        val vm = vm(core, "AATD-9")
        assertEquals("Water plants", vm.state.value.detail?.task?.title)
        vm.setPriority(3)
        val sent = core.last("updateTask")!!
        assertEquals(JsonPrimitive("t1"), sent["taskId"])
        assertEquals(JsonPrimitive(3), (sent["changes"] as JsonObject)["priority"])
    }

    @Test
    fun `AATD-9 an unchanged or blank title is not sent`() = runTest {
        val core = FakeCore().apply { answer("taskDetail", detail) }
        val vm = vm(core)
        vm.setTitle("Water plants")
        vm.setTitle("   ")
        assertFalse(core.kinds().contains("updateTask"))
        vm.setTitle("Water the plants")
        assertTrue(core.kinds().contains("updateTask"))
    }

    @Test
    fun `AATD-9 a picked day is turned into an instant by the core, not the app`() = runTest {
        val core = FakeCore().apply {
            answer("taskDetail", detail)
            answer("dueDateOnDay", """{"dueDateTime":"2026-10-20T00:00:00Z","isAllDay":true}""")
        }
        val vm = vm(core)
        vm.pickDay("2026-10-20")
        assertEquals(JsonPrimitive("2026-10-20"), core.last("dueDateOnDay")!!["day"])
        val changes = core.last("updateTask")!!["changes"] as JsonObject
        assertEquals(JsonPrimitive("2026-10-20T00:00:00Z"), changes["dueDateTime"])
        assertEquals(JsonPrimitive(true), changes["isAllDay"])
    }

    @Test
    fun `AATD-9 completing from detail goes through completeTask`() = runTest {
        val core = FakeCore().apply { answer("taskDetail", detail) }
        vm(core).setCompleted(true)
        assertTrue(core.kinds().contains("completeTask"))
        assertFalse(core.kinds().contains("updateTask"))
    }

    @Test
    fun `AATD-9 a comment is posted trimmed, and an empty one is not posted`() = runTest {
        val core = FakeCore().apply { answer("taskDetail", detail) }
        val vm = vm(core)
        vm.postComment("   ")
        assertFalse(core.kinds().contains("postComment"))
        vm.postComment("  done soon ")
        assertEquals(JsonPrimitive("done soon"), core.last("postComment")!!["content"])
    }

    @Test
    fun `AATD-9 the pickers send the core's own values`() = runTest {
        val core = FakeCore().apply {
            answer("taskDetail", detail)
            answer("assigneeOptions", """{"assigneeId":null,"options":[{"userId":null},{"userId":"u-bo","name":"Bo","initials":"B"}]}""")
            answer("repeatOptions", """{"repeating":"never","presets":[{"value":"never","titleKey":"repeating.one_time_only","isSelected":true},{"value":"weekly","titleKey":"repeating.weekly"}]}""")
        }
        val vm = vm(core)
        vm.loadAssignees(); vm.loadRepeats()
        assertEquals("u-bo", vm.state.value.assignees!!.options[1].userId)
        vm.setAssignee("u-bo")
        assertEquals(JsonPrimitive("u-bo"), (core.last("updateTask")!!["changes"] as JsonObject)["assigneeId"])
        vm.setRepeating("weekly")
        assertEquals(JsonPrimitive("weekly"), (core.last("updateTask")!!["changes"] as JsonObject)["repeating"])
        vm.setAssignee(null)
        assertEquals(kotlinx.serialization.json.JsonNull, (core.last("updateTask")!!["changes"] as JsonObject)["assigneeId"])
    }
}
