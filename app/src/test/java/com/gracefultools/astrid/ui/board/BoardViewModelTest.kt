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
}
