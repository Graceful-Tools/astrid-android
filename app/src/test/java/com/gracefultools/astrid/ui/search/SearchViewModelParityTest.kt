package com.gracefultools.astrid.ui.search

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelParityTest {
    private val rows = """{"rows":[{"id":"t1","title":"Water plants","action":"complete"}]}"""

    @Test
    fun `AATD-31 a search result's checkbox completes the task, as in the list - it does not open it`() = runTest {
        val core = FakeCore().apply { answer("searchTasks", rows) }
        val vm = SearchViewModel(core, UnconfinedTestDispatcher(), debounceMs = 0)
        vm.setQuery("water")
        vm.leading(vm.state.value.rows.first())
        val sent = core.last("completeTask")!!
        assertEquals(JsonPrimitive("t1"), sent["taskId"])
        assertEquals(JsonPrimitive(true), sent["completed"])
    }

    @Test
    fun `AATD-47 results splice their subtasks under them, as iOS draws search`() = runTest {
        val core = FakeCore().apply { answer("searchTasks", rows) }
        val vm = SearchViewModel(core, UnconfinedTestDispatcher(), debounceMs = 0)
        vm.setQuery("water")
        assertEquals(JsonPrimitive(true), core.last("searchTasks")!!["showSubtasks"])
    }
}
