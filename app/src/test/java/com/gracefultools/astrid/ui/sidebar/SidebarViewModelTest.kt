package com.gracefultools.astrid.ui.sidebar

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SidebarViewModelTest {
    /** The core's `sidebar` answer (astrid-core task e22621e2): already filtered, ordered and badged. */
    private fun core() = FakeCore().apply {
        answer("sidebar", """{
            "myTasks":{"id":"my-tasks","name":"My Tasks","isVirtual":true,"taskCount":3},
            "favorites":[
                {"id":"f1","name":"Home","isFavorite":true,"favoriteOrder":1,"taskCount":2},
                {"id":"f2","name":"Zed","isFavorite":true,"favoriteOrder":2}
            ],
            "lists":[{"id":"a","name":"Alpha","taskCount":5},{"id":"b","name":"beta"}]
        }""")
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
    fun `AATD-89 the sidebar is the core's sidebar command, drawn as it answers`() = runTest {
        // An order no Kotlin sort would produce, so a re-sort here would show.
        val core = core().apply {
            answer("sidebar", """{
                "myTasks":{"id":"my-tasks","name":"My Tasks","isVirtual":true},
                "favorites":[{"id":"z","name":"Zed","isFavorite":true},{"id":"h","name":"Home","isFavorite":true}],
                "lists":[{"id":"r","name":"Regular"},{"id":"a","name":"Alpha"}]
            }""")
        }
        val vm = SidebarViewModel(core, UnconfinedTestDispatcher())
        vm.load()
        val s = vm.state.value
        assertEquals("my-tasks", s.myTasks?.id)
        assertEquals(listOf("z", "h"), s.favorites.map { it.id })
        assertEquals(listOf("r", "a"), s.lists.map { it.id })
        assertEquals(emptyList<String>(), core.kinds().filter { it in setOf("lists", "myTasksList", "listBadges") })
    }

    @Test
    fun `AATD-89 the sidebar's numbers are the badges the core puts on each entry`() = runTest {
        val vm = SidebarViewModel(core(), UnconfinedTestDispatcher())
        vm.load()
        assertEquals(mapOf("my-tasks" to 3, "f1" to 2, "a" to 5), vm.state.value.counts)
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
