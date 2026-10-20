package com.gracefultools.astrid.ui.tasks

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {
    private val rows = """{"total":2,"offset":0,"rows":[
        {"id":"t1","title":"Water plants","priority":2,"due":{"key":"today"}},
        {"id":"t2","title":"Call mum","completed":false}]}"""

    private fun vm(core: FakeCore) = TaskListViewModel(core, UnconfinedTestDispatcher(), locale = { "en-US" })

    @Test
    fun `AATD-8 opening a list draws the core's rows`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", rows) }
        val vm = vm(core)
        vm.open("l1")
        assertEquals(listOf("t1", "t2"), vm.state.value.rows.map { it.id })
        assertEquals(JsonPrimitive("l1"), core.last("rowsForList")!!["listId"])
    }

    @Test
    fun `AATD-8 completing goes through completeTask, never updateTask`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", rows) }
        val vm = vm(core)
        vm.open("l1")
        vm.setCompleted("t1", true)
        val sent = core.last("completeTask")!!
        assertEquals(JsonPrimitive("t1"), sent["taskId"])
        assertEquals(JsonPrimitive(true), sent["completed"])
        assertFalse(core.kinds().contains("updateTask"))
    }

    @Test
    fun `AATD-8 a refused completion flips back and says why`() = runTest {
        val core = FakeCore().apply {
            answer("rowsForList", rows)
            answer("completeTask") { throw CoreFailure(FailureKind.REFUSED, "not allowed") }
        }
        val vm = vm(core)
        vm.open("l1")
        // The reload after the refusal answers the core's truth; make it say "not completed".
        vm.setCompleted("t1", true)
        assertFalse(vm.state.value.rows.first { it.id == "t1" }.completed)
        assertEquals("not allowed", vm.state.value.error)
    }

    @Test
    fun `AATD-8 an offline completion is not an error`() = runTest {
        val core = FakeCore().apply {
            answer("rowsForList", rows)
            answer("completeTask") { throw CoreFailure(FailureKind.OFFLINE, "no network") }
        }
        val vm = vm(core)
        vm.open("l1")
        vm.setCompleted("t1", true)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `AATD-8 quick add files into the open list and lets the core parse the words`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", rows) }
        val vm = vm(core)
        vm.open("l1")
        assertNotNull(vm.create("  Call mum tomorrow #home  "))
        val sent = core.last("createTask")!!
        assertEquals(JsonPrimitive("Call mum tomorrow #home"), sent["title"])
        assertEquals(JsonArray(listOf(JsonPrimitive("l1"))), sent["listIds"])
        assertEquals(JsonPrimitive(true), sent["quickAdd"])
        assertEquals(JsonPrimitive("en-US"), sent["locale"])
    }

    @Test
    fun `AATD-8 quick add in My Tasks files into no list, and a blank title makes nothing`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", rows) }
        val vm = vm(core)
        vm.open("my-tasks", isVirtual = true)
        assertNull(vm.create("   "))
        assertFalse(core.kinds().contains("createTask"))
        vm.create("Something")
        assertEquals(JsonArray(emptyList()), core.last("createTask")!!["listIds"])
    }

    @Test
    fun `AATD-10 a change from the core re-reads the open list`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", rows) }
        val vm = vm(core)
        vm.open("l1")
        val before = core.kinds().count { it == "rowsForList" }
        core.emit("synced")
        assertEquals(before + 1, core.kinds().count { it == "rowsForList" })
    }

    @Test
    fun `AATD-8 a task added in My Tasks is assigned as the core's quick-add default says, so it stays in My Tasks`() = runTest {
        val core = FakeCore().apply {
            answer("rowsForList", rows)
            answer("quickAddDefaults", """{"assigneeId":"u-me","priority":0}""")
        }
        val vm = vm(core)
        vm.open("virtual:my-tasks", isVirtual = true)
        vm.create("Buy milk")
        assertEquals(JsonPrimitive("virtual:my-tasks"), core.last("quickAddDefaults")!!["listId"])
        assertEquals(JsonPrimitive("u-me"), core.last("createTask")!!["assigneeId"])
    }

    @Test
    fun `AATD-8 in a real list the core applies the list's own defaults, so nothing is asserted`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", rows) }
        val vm = vm(core)
        vm.open("l1")
        vm.create("Buy milk")
        // quickAddDefaults is asked for the control's face (AATD-37); the create still says nothing.
        assertNull(core.last("createTask")!!["assigneeId"])
        assertNull(core.last("createTask")!!["priority"])
    }

    @Test
    fun `AATD-37 a priority and an assignee picked in quick add are sent as chosen, the words decide otherwise`() = runTest {
        val core = FakeCore().apply {
            answer("rowsForList", rows)
            answer("quickAddDefaults", """{"priority":0,"assigneeId":"u-me","leading":{"kind":"checkbox"},"options":[]}""")
        }
        val vm = vm(core)
        vm.open("l1")
        vm.create("Plain")
        assertNull(core.last("createTask")!!["priority"])
        vm.choosePriority(3)
        vm.chooseAssignee(null)
        assertEquals(JsonPrimitive(3), core.last("quickAddDefaults")!!["priority"])
        vm.create("Urgent")
        val sent = core.last("createTask")!!
        assertEquals(JsonPrimitive(3), sent["priority"])
        assertEquals(JsonPrimitive("unassigned"), sent["assigneeId"])
    }

    @Test
    fun `AATD-37 the plus opens what it made - create answers the new task's id`() = runTest {
        val core = FakeCore().apply {
            answer("rowsForList", rows)
            answer("createTask", """{"id":"temp_1","title":"New"}""")
        }
        val vm = vm(core)
        vm.open("l1")
        assertEquals("temp_1", vm.create("New"))
    }

    @Test
    fun `AATD-42 a drop on a row asks the core to nest, and a line drop in manual sort writes the order`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", """{"total":3,"offset":0,"sortBy":"manual","rows":[{"id":"a"},{"id":"b"},{"id":"c"}]}""") }
        val vm = vm(core)
        vm.open("l1")
        vm.drop("c", "row", targetId = "a")
        assertEquals(JsonPrimitive("a"), core.last("nestTask")!!["targetId"])
        assertFalse(core.kinds().contains("setManualOrder"))
        vm.drop("c", "line", insertAt = 0)
        assertEquals(
            JsonArray(listOf("c", "a", "b").map { JsonPrimitive(it) }),
            core.last("setManualOrder")!!["order"],
        )
    }

    @Test
    fun `AATD-42 outside manual sort a line drop only promotes, it never reorders`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", """{"total":2,"offset":0,"sortBy":"auto","rows":[{"id":"a"},{"id":"b"}]}""") }
        val vm = vm(core)
        vm.open("l1")
        vm.drop("b", "line", insertAt = 0)
        assertTrue(core.kinds().contains("nestTask"))
        assertFalse(core.kinds().contains("setManualOrder"))
    }
}
