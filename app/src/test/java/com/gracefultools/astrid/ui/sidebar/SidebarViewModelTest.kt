package com.gracefultools.astrid.ui.sidebar

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SidebarViewModelTest {
    private fun core() = FakeCore().apply {
        answer("lists", """[
            {"id":"b","name":"beta"},
            {"id":"col","name":"Doing","listType":"status"},
            {"id":"lab","name":"bug","listType":"label"},
            {"id":"f2","name":"Zed","isFavorite":true,"favoriteOrder":2},
            {"id":"f1","name":"Home","isFavorite":true,"favoriteOrder":1},
            {"id":"a","name":"Alpha"}
        ]""")
        answer("myTasksList", """{"id":"my-tasks","name":"My Tasks","isVirtual":true}""")
    }

    @Test
    fun `AATD-7 the sidebar is My Tasks, then favourites in order, then places by name`() = runTest {
        val vm = SidebarViewModel(core(), UnconfinedTestDispatcher())
        vm.load()
        val s = vm.state.value
        assertEquals("my-tasks", s.myTasks?.id)
        assertEquals(listOf("f1", "f2"), s.favorites.map { it.id })
        assertEquals(listOf("a", "b"), s.lists.map { it.id })
    }

    @Test
    fun `AATD-7 My Tasks is open on launch and a choice survives a reload`() = runTest {
        val core = core()
        val vm = SidebarViewModel(core, UnconfinedTestDispatcher())
        vm.load()
        assertEquals("my-tasks", vm.state.value.selectedId)
        vm.select("b")
        core.emit("synced")
        assertEquals("b", vm.state.value.selectedId)
    }

    @Test
    fun `AATD-15 Add List makes the list and opens it`() = runTest {
        val core = core().apply { answer("createList", """{"id":"l-new","name":"Garden"}""") }
        val vm = SidebarViewModel(core, UnconfinedTestDispatcher())
        vm.load()
        vm.create("  Garden ")
        assertEquals(kotlinx.serialization.json.JsonPrimitive("Garden"), core.last("createList")!!["name"])
        assertEquals("l-new", vm.state.value.selectedId)
    }
}
