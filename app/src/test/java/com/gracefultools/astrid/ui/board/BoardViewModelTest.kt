package com.gracefultools.astrid.ui.board

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {
    private val board = """{"projectId":"p1","listId":"l1","columns":[
        {"id":"todo","name":"To do","kind":"status","total":1,"cards":[{"id":"t1","title":"Draft"}]},
        {"id":"doing","name":"Doing","kind":"status","total":0,"cards":[]}]}"""

    @Test
    fun `AATD-12 a board draws the core's columns and their cards`() = runTest {
        val core = FakeCore().apply { answer("board", board) }
        val vm = BoardViewModel(core, UnconfinedTestDispatcher())
        vm.open("l1")
        assertEquals(listOf("To do", "Doing"), vm.state.value.board!!.columns.map { it.name })
        assertEquals("Draft", vm.state.value.board!!.columns[0].cards[0].title)
    }

    @Test
    fun `AATD-113 a board skips a task change in another list and re-reads one in its own`() = runTest {
        val core = FakeCore().apply { answer("board", board) }
        val vm = BoardViewModel(core, UnconfinedTestDispatcher())
        vm.open("l1")
        fun reads() = core.kinds().count { it == "board" }
        val before = reads()
        core.emit("task", """{"change":"task","id":"t9","listIds":["l2"]}""")
        assertEquals(before, reads())
        core.emit("task", """{"change":"task","id":"t9","listIds":["l1"]}""")
        assertEquals(before + 1, reads())
        core.emit("task", """{"change":"task","id":"t9","listIds":[]}""")
        assertEquals(before + 2, reads())
    }

    @Test
    fun `AATD-12 Move to sends moveTaskToColumn for this board's list`() = runTest {
        val core = FakeCore().apply { answer("board", board) }
        val vm = BoardViewModel(core, UnconfinedTestDispatcher())
        vm.open("l1")
        vm.moveTo("t1", "doing")
        val sent = core.last("moveTaskToColumn")!!
        assertEquals(JsonPrimitive("doing"), sent["columnId"])
        assertEquals(JsonPrimitive("l1"), sent["listId"])
    }

    @Test
    fun `AATD-20 a dropped card sends dropBoardCard with its column and position`() = runTest {
        val core = FakeCore().apply { answer("board", board) }
        val vm = BoardViewModel(core, UnconfinedTestDispatcher())
        vm.open("l1")
        vm.drop("t1", "doing", 0)
        val sent = core.last("dropBoardCard")!!
        assertEquals(JsonPrimitive("doing"), sent["columnId"])
        assertEquals(JsonPrimitive(0), sent["index"])
        assertEquals(JsonPrimitive("l1"), sent["listId"])
    }

    @Test
    fun `AATD-45 a column's quick add makes the card in that column of this board`() = kotlinx.coroutines.test.runTest {
        val core = com.gracefultools.astrid.FakeCore().apply { answer("board", """{"columns":[]}""") }
        val vm = BoardViewModel(core, kotlinx.coroutines.test.UnconfinedTestDispatcher())
        vm.open("l-launch")
        org.junit.Assert.assertTrue(vm.addCard("doing", "  Ship it "))
        val sent = core.last("addBoardCard")!!
        org.junit.Assert.assertEquals(kotlinx.serialization.json.JsonPrimitive("l-launch"), sent["listId"])
        org.junit.Assert.assertEquals(kotlinx.serialization.json.JsonPrimitive("doing"), sent["columnId"])
        org.junit.Assert.assertEquals(kotlinx.serialization.json.JsonPrimitive("Ship it"), sent["title"])
    }

    @Test
    fun `AATD-98 Move to Waiting with nothing to wait on asks, then sends the move again with the answer`() = runTest {
        val core = FakeCore().apply {
            answer("board", board)
            answer("taskDetail", """{"task":{"id":"t1","title":"Draft"},"descriptionBlocks":[],"comments":[],"subtasks":[]}""")
            answer("moveTaskToColumn") { sent ->
                if (sent["waitOnTaskId"] == null || sent["waitOnTaskId"] is kotlinx.serialization.json.JsonNull) throw com.gracefultools.astrid.core.CoreFailure(com.gracefultools.astrid.core.FailureKind.REFUSED, "Waiting needs a date or a task", code = "WAITING_NEEDS_CONDITION")
                kotlinx.serialization.json.JsonNull
            }
        }
        val vm = BoardViewModel(core, UnconfinedTestDispatcher())
        vm.open("l1")
        vm.moveTo("t1", "waiting")
        assertEquals(null, vm.state.value.error)
        assertEquals("t1", vm.waiting.state.value.request?.taskId)
        org.junit.Assert.assertTrue(vm.waiting.confirm(com.gracefultools.astrid.ui.common.WaitCondition.on("t9", "Upstream", keepAssignee = false)))
        val sent = core.last("moveTaskToColumn")!!
        assertEquals(JsonPrimitive("t9"), sent["waitOnTaskId"])
        assertEquals(JsonPrimitive("l1"), sent["listId"])
    }

    @Test
    fun `AATD-98 a card dropped on Waiting asks too, and keeps its drop position`() = runTest {
        val core = FakeCore().apply {
            answer("board", board)
            answer("dueDateOnDay", """{"dueDateTime":"2026-11-20T00:00:00Z","isAllDay":true}""")
            answer("dropBoardCard") { sent ->
                if (sent["dueDateTime"] == null || sent["dueDateTime"] is kotlinx.serialization.json.JsonNull) throw com.gracefultools.astrid.core.CoreFailure(com.gracefultools.astrid.core.FailureKind.REFUSED, "Waiting needs a date or a task", code = "WAITING_NEEDS_CONDITION")
                kotlinx.serialization.json.JsonNull
            }
        }
        val vm = BoardViewModel(core, UnconfinedTestDispatcher())
        vm.open("l1")
        vm.drop("t1", "waiting", 2)
        assertEquals("t1", vm.waiting.state.value.request?.taskId)
        vm.waiting.confirm(com.gracefultools.astrid.ui.common.WaitCondition.until(java.time.LocalDate.of(2026, 11, 20), keepAssignee = true))
        val sent = core.last("dropBoardCard")!!
        assertEquals(JsonPrimitive("2026-11-20T00:00:00Z"), sent["dueDateTime"])
        assertEquals(JsonPrimitive(2), sent["index"])
        assertEquals(JsonPrimitive(true), sent["keepAssignee"])
    }

    @Test
    fun `Done cards decode the core's completedOn day and keep the core's newest-first order`() = runTest {
        val core = FakeCore().apply {
            answer("board", """{"columns":[
                {"id":"todo","name":"To do","kind":"status","total":1,"cards":[{"id":"t1","title":"Draft"}]},
                {"id":"done","name":"Done","kind":"done","total":2,"cards":[
                    {"id":"t3","title":"Newest","completed":true,"completedOn":"2026-11-19"},
                    {"id":"t2","title":"Older","completed":true,"completedOn":"2025-11-19"}]}]}""")
        }
        val vm = BoardViewModel(core, UnconfinedTestDispatcher())
        vm.open("l1")
        val columns = vm.state.value.board!!.columns
        assertEquals(null, columns[0].cards[0].completedOn)
        assertEquals(listOf("t3", "t2"), columns[1].cards.map { it.id })
        assertEquals(listOf("2026-11-19", "2025-11-19"), columns[1].cards.map { it.completedOn })
    }

}
