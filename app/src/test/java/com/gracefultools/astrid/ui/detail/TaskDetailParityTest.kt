package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The detail actions iOS's TaskDetailViewNew offers (AATD-48/49/52/53), as core commands. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailParityTest {
    private fun detail(extra: String = "") = """{"task":{"id":"t1","title":"Plan","listIds":["l1"]},"fieldOrder":[],"descriptionBlocks":[],
        "comments":[],"subtasks":[{"id":"s1","title":"One","completed":false},{"id":"s2","title":"Two","completed":true}]$extra}"""

    private fun vm(core: FakeCore, id: String = "t1") = TaskDetailViewModel(core, id, UnconfinedTestDispatcher())

    @Test
    fun `AATD-49 Won't do closes as canceled, and Reopen clears it`() = runTest {
        val core = FakeCore().apply { answer("taskDetail", detail()) }
        val vm = vm(core)
        vm.toggleWontDo()
        assertEquals(JsonPrimitive("canceled"), core.last("setClosedReason")!!["closedReason"])
        core.answer("taskDetail", detail(""","isCanceled":true"""))
        vm.load()
        vm.toggleWontDo()
        assertEquals(JsonNull, core.last("setClosedReason")!!["closedReason"])
    }

    @Test
    fun `AATD-52 a sub task is made under this task, in its lists`() = runTest {
        val core = FakeCore().apply { answer("taskDetail", detail()) }
        vm(core).addSubtask("  Buy paint ")
        val sent = core.last("createTask")!!
        assertEquals(JsonPrimitive("Buy paint"), sent["title"])
        assertEquals(JsonPrimitive("t1"), sent["parentTaskId"])
        assertEquals(JsonArray(listOf(JsonPrimitive("l1"))), sent["listIds"])
    }

    @Test
    fun `AATD-52 completing with sub tasks completes the open ones first, through completeTask`() = runTest {
        val core = FakeCore().apply { answer("taskDetail", detail()) }
        vm(core).completeWithSubtasks(alsoSubtasks = true)
        val completed = core.sent.filter { (it["kind"] as JsonPrimitive).content == "completeTask" }.map { (it["taskId"] as JsonPrimitive).content }
        assertEquals(listOf("s1", "t1"), completed)
    }

    @Test
    fun `AATD-37 a task opened by its temporary id follows it to the server's id`() = runTest {
        val core = FakeCore().apply {
            answer("taskDetail") { command ->
                if ((command["taskId"] as JsonPrimitive).content == "temp_9") throw CoreFailure(FailureKind.NOT_FOUND, "gone")
                kotlinx.serialization.json.Json.parseToJsonElement(detail())
            }
            answer("resolveIds", """{"temp_9":"t1"}""")
        }
        val vm = vm(core, "temp_9")
        assertEquals("Plan", vm.state.value.detail?.task?.title)
        assertTrue(core.kinds().contains("resolveIds"))
    }

    @Test
    fun `AATD-53 a reply names the comment it answers`() = runTest {
        val core = FakeCore().apply { answer("taskDetail", detail()) }
        vm(core).postComment("Agreed", replyTo = "c7")
        assertEquals(JsonPrimitive("c7"), core.last("postComment")!!["parentCommentId"])
    }
}
