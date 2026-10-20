package com.gracefultools.astrid.ui.filters

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FiltersViewModelTest {
    private val options = """{"listId":"virtual:my-tasks","isFiltered":true,"groups":[
        {"field":"filterDueDate","titleKey":"filter.due","picks":[
            {"field":"filterDueDate","value":"all","titleKey":"filter.any","isSelected":false},
            {"field":"filterDueDate","value":"today","titleKey":"filter.due.today","isSelected":true}]},
        {"field":"sortBy","titleKey":"sort","picks":[
            {"field":"sortBy","value":"auto","titleKey":"sort.auto","isSelected":false},
            {"field":"sortBy","value":"priority","titleKey":"sort.priority","isSelected":true}]}]}"""

    @Test
    fun `AATD-41 the sheet is the core's groups, and a pick is the core's setFilter`() = runTest {
        val core = FakeCore().apply { answer("filterOptions", options) }
        val vm = FiltersViewModel(core, "virtual:my-tasks", UnconfinedTestDispatcher())
        assertEquals(listOf("filterDueDate", "sortBy"), vm.state.value.groups.map { it.field })
        vm.pick("filterDueDate", "this_week")
        val sent = core.last("setFilter")!!
        assertEquals(JsonPrimitive("virtual:my-tasks"), sent["listId"])
        assertEquals(JsonPrimitive("filterDueDate"), sent["field"])
        assertEquals(JsonPrimitive("this_week"), sent["value"])
    }

    @Test
    fun `AATD-41 Clear All Filters puts every filter back to its first choice and leaves the sort alone`() = runTest {
        val core = FakeCore().apply { answer("filterOptions", options) }
        val vm = FiltersViewModel(core, "virtual:my-tasks", UnconfinedTestDispatcher())
        vm.clearAll()
        val sets = core.sent.filter { (it["kind"] as JsonPrimitive).content == "setFilter" }
        assertEquals(listOf("filterDueDate" to "all"), sets.map { (it["field"] as JsonPrimitive).content to (it["value"] as JsonPrimitive).content })
        assertTrue(core.kinds().count { it == "filterOptions" } >= 2)
    }
}
