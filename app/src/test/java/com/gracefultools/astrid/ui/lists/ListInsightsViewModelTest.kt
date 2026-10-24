package com.gracefultools.astrid.ui.lists

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import com.gracefultools.astrid.model.InsightDuration
import com.gracefultools.astrid.model.InsightLane
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListInsightsViewModelTest {
    private fun insights(days: Int) = """{
        "window":{"days":$days,"timeZone":"America/Los_Angeles","start":"2026-09-23","end":"2026-10-22"},
        "series":[{"date":"2026-10-21","created":2,"completed":1},{"date":"2026-10-22","created":0,"completed":3}],
        "totals":{"created":2,"completed":4},
        "createToComplete":{"medianMs":null,"sampleSize":0},
        "lanes":[{"role":"ready","name":"Ready","medianMs":3600000,"sampleSize":4},{"role":"doing","name":"Doing","medianMs":null,"sampleSize":0}],
        "contributors":[{"userId":"u1","name":"Dana","image":null,"isAIAgent":false,"completed":3,"inspired":1,"supported":2,"created":2,"edited":5,"commented":1,"total":8},
                        {"userId":"claude","name":"Claude","isAIAgent":true,"created":1,"edited":0,"commented":4,"total":5}]
    }"""

    @Test
    fun `AATD-77 insights are the core's listInsights for this list, the window and the device's zone`() = runTest {
        val core = FakeCore().apply { answer("listInsights", insights(30)) }
        val vm = ListInsightsViewModel(core, "l1", timeZone = "America/Los_Angeles", dispatcher = UnconfinedTestDispatcher())
        val sent = core.last("listInsights")!!
        assertEquals(JsonPrimitive("l1"), sent["listId"])
        assertEquals(30, sent["days"]!!.jsonPrimitive.intOrNull)
        assertEquals(JsonPrimitive("America/Los_Angeles"), sent["timeZone"])
        val shown = vm.state.value.shown!!
        assertEquals(4, shown.totals.completed)
        assertEquals(listOf("2026-10-21", "2026-10-22"), shown.series.map { it.date })
        assertNull("a null median is \"—\", never 0", shown.createToComplete.medianMs)
        assertEquals(2, shown.contributors.size)
        assertEquals(1, shown.contributors[0].inspired)
        assertTrue(shown.contributors[1].isAIAgent)
        assertEquals("inspired and supported arrived later; a server without them decodes as zero", 0, shown.contributors[1].supported)
    }

    @Test
    fun `AATD-77 choosing a window asks again and shows only that window's answer`() = runTest {
        val core = FakeCore().apply { answer("listInsights") { cmd -> com.gracefultools.astrid.core.CoreJson.parseToJsonElement(insights(cmd["days"]!!.jsonPrimitive.intOrNull!!)) } }
        val vm = ListInsightsViewModel(core, "l1", timeZone = "UTC", dispatcher = UnconfinedTestDispatcher())
        vm.select(7)
        assertEquals(7, core.last("listInsights")!!["days"]!!.jsonPrimitive.intOrNull)
        assertEquals(7, vm.state.value.days)
        assertEquals(7, vm.state.value.shown!!.window.days)
    }

    @Test
    fun `AATD-77 a failed window says so, and another window is a fresh attempt`() = runTest {
        val core = FakeCore().apply { answer("listInsights") { throw CoreFailure(FailureKind.OFFLINE, "offline") } }
        val vm = ListInsightsViewModel(core, "l1", timeZone = "UTC", dispatcher = UnconfinedTestDispatcher())
        assertTrue(vm.state.value.failed)
        assertNull(vm.state.value.shown)
        core.answer("listInsights", insights(90))
        vm.select(90)
        assertFalse(vm.state.value.failed)
        assertEquals(90, vm.state.value.shown!!.window.days)
    }

    @Test
    fun `AATD-77 a board with no samples in any lane has not enough history yet`() {
        assertFalse(ListInsightsFormat.lanesHaveHistory(listOf(InsightLane("ready", "Ready", null, 0), InsightLane("doing", "Doing", null, 0))))
        assertTrue(ListInsightsFormat.lanesHaveHistory(listOf(InsightLane("ready", "Ready", 60_000.0, 1))))
    }

    @Test
    fun `AATD-77 a duration reads at one unit, as web and iOS print it`() {
        assertNull(ListInsightsFormat.duration(InsightDuration(null, 0)))
        assertEquals(InsightUnit.MINUTES to "1", ListInsightsFormat.duration(InsightDuration(5_000.0, 1)))
        assertEquals(InsightUnit.MINUTES to "45", ListInsightsFormat.duration(InsightDuration(45 * 60_000.0, 1)))
        assertEquals(InsightUnit.HOURS to "1.5", ListInsightsFormat.duration(InsightDuration(90 * 60_000.0, 1)))
        assertEquals(InsightUnit.HOURS to "31", ListInsightsFormat.duration(InsightDuration(111_600_000.0, 42)))
        assertEquals(InsightUnit.DAYS to "3", ListInsightsFormat.duration(InsightDuration(3 * 86_400_000.0, 2)))
        assertEquals(InsightUnit.DAYS to "2.5", ListInsightsFormat.duration(InsightDuration(2.5 * 86_400_000.0, 2)))
    }
}
