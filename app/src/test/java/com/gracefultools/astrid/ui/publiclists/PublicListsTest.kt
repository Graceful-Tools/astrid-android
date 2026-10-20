package com.gracefultools.astrid.ui.publiclists

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import com.gracefultools.astrid.ui.sidebar.SidebarViewModel
import com.gracefultools.astrid.ui.tasks.TaskListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PublicListsTest {
    /** The core's `publicLists` answer, as services::list::PublicListSummary serialises it. */
    private val catalogue = """[
        {"id":"p1","name":"Reading","description":"Books","taskCount":12,"memberCount":3,"owner":{"id":"o1","name":"Ann"},"publicListType":"collaborative"},
        {"id":"p2","name":"Recipes","taskCount":4,"memberCount":1},
        {"id":"p3","name":"Gardening","taskCount":7,"memberCount":1,"publicListType":"copy_only"},
        {"id":"p4","name":"Packing","taskCount":2,"memberCount":1},
        {"id":"p5","name":"Hikes","taskCount":9,"memberCount":5,"publicListType":"collaborative"}
    ]"""

    private fun core() = FakeCore().apply {
        answer("lists", """[{"id":"l1","name":"Home"}]""")
        answer("myTasksList", """{"id":"my-tasks","name":"My Tasks","isVirtual":true}""")
        answer("publicLists", catalogue)
    }

    private fun summary(id: String, type: String? = null) = PublicListSummary(id = id, name = id, publicListType = type)

    @Test
    fun `AATD-46 collaborative lists are their own section and anything else is browse-only`() {
        val lists = listOf(summary("a", "collaborative"), summary("b"), summary("c", "copy_only"), summary("d", "someday"))
        assertEquals(listOf("a"), PublicListSections.collaborative(lists).map { it.id })
        assertEquals(listOf("b", "c", "d"), PublicListSections.browsable(lists).map { it.id })
    }

    @Test
    fun `AATD-46 a sidebar section shows two, and See all only when more are hidden`() {
        val three = listOf(summary("a"), summary("b"), summary("c"))
        assertEquals(listOf("a", "b"), PublicListSections.sidebarRows(three).map { it.id })
        assertTrue(PublicListSections.hasMore(three))
        assertFalse(PublicListSections.hasMore(three.take(2)))
    }

    @Test
    fun `AATD-46 the sidebar reads the core's publicLists into its two sections`() = runTest {
        val vm = SidebarViewModel(core(), UnconfinedTestDispatcher())
        vm.load()
        val s = vm.state.value
        assertEquals(listOf("p1", "p5"), s.collaborativeLists.map { it.id })
        assertEquals(listOf("p2", "p3", "p4"), s.suggestedLists.map { it.id })
    }

    @Test
    fun `AATD-46 opening a featured list selects it, and a reload keeps it open`() = runTest {
        val core = core()
        val vm = SidebarViewModel(core, UnconfinedTestDispatcher())
        vm.load()
        vm.selectFeatured(vm.state.value.publicLists.first { it.id == "p2" })
        assertEquals("p2", vm.state.value.selected?.id)
        assertEquals("PUBLIC", vm.state.value.selected?.privacy)
        assertTrue(vm.state.value.isViewingFeatured)
        core.emit("synced")
        assertEquals("p2", vm.state.value.selectedId)
        vm.select("l1")
        assertFalse(vm.state.value.isViewingFeatured)
    }

    @Test
    fun `AATD-46 a featured list's rows come from featuredRows, with the list itself, which the cache does not hold`() = runTest {
        val core = FakeCore().apply { answer("featuredRows", """{"total":1,"rows":[{"id":"t9","title":"Dune","action":"copy","leading":{"kind":"copy"}}]}""") }
        val vm = TaskListViewModel(core, UnconfinedTestDispatcher(), locale = { "en-US" })
        vm.open("p1", featured = PublicListSummary(id = "p1", name = "Reading", owner = PublicListOwner("o1"), taskCount = 12))
        val list = core.last("featuredRows")!!["list"] as JsonObject
        assertEquals(JsonPrimitive("p1"), list["id"])
        assertEquals(JsonPrimitive("PUBLIC"), list["privacy"])
        assertEquals(JsonPrimitive("o1"), list["ownerId"])
        assertTrue(vm.state.value.featured)
        assertEquals("copy", vm.state.value.rows.single().action)
    }

    @Test
    fun `AATD-46 an ordinary list sends no list input`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", """{"total":0,"rows":[]}""") }
        val vm = TaskListViewModel(core, UnconfinedTestDispatcher(), locale = { "en-US" })
        vm.open("l1")
        assertNull(core.last("rowsForList")!!["list"])
        assertFalse(vm.state.value.featured)
    }

    @Test
    fun `AATD-46 Copy List copies the featured list through copyList`() = runTest {
        val core = FakeCore().apply {
            answer("rowsForList", """{"total":0,"rows":[]}""")
            answer("featuredRows", """{"total":0,"rows":[]}""")
            answer("copyList", """{"id":"mine","name":"Reading"}""")
        }
        val vm = TaskListViewModel(core, UnconfinedTestDispatcher(), locale = { "en-US" })
        vm.open("p1", featured = PublicListSummary(id = "p1", name = "Reading"))
        assertTrue(vm.copyList())
        assertEquals(JsonPrimitive("p1"), core.last("copyList")!!["listId"])
        assertFalse(vm.state.value.copyingList)
        assertFalse(vm.state.value.copyListFailed)
    }

    @Test
    fun `AATD-46 a refused Copy List says it could not copy`() = runTest {
        val core = FakeCore().apply {
            answer("rowsForList", """{"total":0,"rows":[]}""")
            answer("featuredRows", """{"total":0,"rows":[]}""")
            answer("copyList") { throw CoreFailure(FailureKind.OFFLINE, "no network") }
        }
        val vm = TaskListViewModel(core, UnconfinedTestDispatcher(), locale = { "en-US" })
        vm.open("p1", featured = PublicListSummary(id = "p1", name = "Reading"))
        assertFalse(vm.copyList())
        assertTrue(vm.state.value.copyListFailed)
        vm.dismissError()
        assertFalse(vm.state.value.copyListFailed)
    }

    @Test
    fun `AATD-46 Copy List is only for a featured list`() = runTest {
        val core = FakeCore().apply { answer("rowsForList", """{"total":0,"rows":[]}""") }
        val vm = TaskListViewModel(core, UnconfinedTestDispatcher(), locale = { "en-US" })
        vm.open("l1")
        assertFalse(vm.copyList())
        assertFalse(core.kinds().contains("copyList"))
    }

    @Test
    fun `AATD-46 the Featured Lists browser loads the catalogue and copies a list`() = runTest {
        val core = core().apply { answer("copyList", """{"id":"mine","name":"Recipes"}""") }
        val vm = PublicListsViewModel(core, UnconfinedTestDispatcher())
        vm.load()
        assertEquals(5, vm.state.value.lists.size)
        assertEquals("Books", vm.state.value.lists.first().description)
        assertTrue(vm.state.value.lists.first().isCollaborative)
        vm.copy("p2")
        assertEquals(JsonPrimitive("p2"), core.last("copyList")!!["listId"])
        assertTrue(vm.state.value.copying.isEmpty())
        assertFalse(vm.state.value.copyFailed)
    }

    @Test
    fun `AATD-46 a failed copy in the browser is reported, and a failed refresh keeps the lists`() = runTest {
        val core = core()
        val vm = PublicListsViewModel(core, UnconfinedTestDispatcher())
        vm.load()
        core.answer("copyList") { throw CoreFailure(FailureKind.REFUSED, "nope") }
        vm.copy("p3")
        assertTrue(vm.state.value.copyFailed)
        core.answer("publicLists") { throw CoreFailure(FailureKind.OFFLINE, "no network") }
        vm.refresh()
        assertEquals(5, vm.state.value.lists.size)
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `AATD-46 the TaskList shape carries what rowsForList needs`() {
        val shape = PublicListSummary(id = "p1", name = "Reading", color = "#ff0000", owner = PublicListOwner("o1"), publicListType = "collaborative").asTaskList()
        assertEquals("Reading", (shape["name"] as JsonPrimitive).content)
        assertEquals("collaborative", (shape["publicListType"] as JsonPrimitive).content)
        assertEquals("o1", (shape["owner"]!!.jsonObject["id"] as JsonPrimitive).content)
    }
}
