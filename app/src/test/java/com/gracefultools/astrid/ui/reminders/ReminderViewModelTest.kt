package com.gracefultools.astrid.ui.reminders

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelTest {
    private fun detail(id: String, title: String, completed: Boolean = false, chips: String = "[]", due: String? = null): JsonElement =
        Json.parseToJsonElement(
            """{"task":{"id":"$id","title":"$title","completed":$completed${due?.let { ",\"dueDateTime\":\"$it\"" } ?: ""}},
               "listChips":$chips,"descriptionBlocks":[],"comments":[],"subtasks":[]}""",
        )

    private fun core(vararg tasks: Pair<String, JsonElement>) = FakeCore().apply {
        val byId = tasks.toMap()
        answer("taskDetail") { command -> byId.getValue((command["taskId"] as JsonPrimitive).content) }
        answer("dueDateOnDay", """{"dueDateTime":"2026-10-20T17:00:00Z","isAllDay":false}""")
    }

    private fun vm(core: FakeCore, vararg ids: String) =
        ReminderViewModel(core, ids.toList(), today = { LocalDate.parse("2026-10-19") }, now = { Instant.parse("2026-10-19T12:00:00Z") }, dispatcher = UnconfinedTestDispatcher())

    @Test
    fun `AATD-54 one reminder opens the reminder screen, Complete goes through completeTask`() = runTest {
        val core = core("t1" to detail("t1", "Water plants"))
        val vm = vm(core, "t1")
        assertFalse(vm.state.value.isTriage)
        assertEquals("Water plants", vm.state.value.current?.task?.title)
        vm.complete("t1")
        assertEquals(JsonPrimitive(true), core.last("completeTask")!!["completed"])
        assertFalse(core.kinds().contains("updateTask"))
        assertTrue(vm.state.value.finished)
        assertEquals(setOf("t1"), vm.handled)
    }

    @Test
    fun `AATD-54 the three snoozes are the core's snoozeReminder for 15 minutes, a day and a week`() = runTest {
        val core = core("t1" to detail("t1", "Water plants"))
        val vm = vm(core, "t1")
        for (minutes in listOf(15L, 1440L, 10080L)) {
            vm.snooze("t1", minutes)
            assertEquals(JsonPrimitive(minutes), core.last("snoozeReminder")!!["minutes"])
            assertEquals(JsonPrimitive("t1"), core.last("snoozeReminder")!!["taskId"])
        }
    }

    @Test
    fun `AATD-54 several reminders deal a triage deck of the open ones`() = runTest {
        val core = core(
            "t1" to detail("t1", "One"),
            "t2" to detail("t2", "Done already", completed = true),
            "t3" to detail("t3", "Three"),
            "t4" to detail("t4", "Four"),
        )
        val vm = vm(core, "t1", "t2", "t3", "t4")
        assertTrue(vm.state.value.isTriage)
        assertEquals(listOf("t1", "t3", "t4"), vm.state.value.deck.map { it.task.id })

        vm.decide(TriageAction.COMPLETE)
        assertEquals(JsonPrimitive("t1"), core.last("completeTask")!!["taskId"])

        vm.decide(TriageAction.POSTPONE)
        // Tomorrow from today, through the core's reading of that day for this task.
        assertEquals(JsonPrimitive("2026-10-20"), core.last("dueDateOnDay")!!["day"])
        val changes = core.last("updateTask")!!["changes"] as JsonObject
        assertEquals(JsonPrimitive("2026-10-20T17:00:00Z"), changes["dueDateTime"])
        assertEquals(JsonPrimitive(false), changes["isAllDay"])

        val writes = core.sent.size
        vm.decide(TriageAction.KEEP)
        assertEquals(writes, core.sent.size)
        assertNull(vm.state.value.current)
        assertEquals(setOf("t1", "t3", "t4"), vm.handled)
    }

    @Test
    fun `AATD-54 the people on a shared list are counting on you, not including you`() = runTest {
        val core = core("t1" to detail("t1", "Rota", chips = """[{"id":"l1","name":"House","glyph":"people"}]""")).apply {
            answer("listMembers", """{"listId":"l1","currentUserId":"me","members":[
                {"userId":"me","user":{"id":"me","name":"Me"}},
                {"userId":"u2","user":{"id":"u2","name":"Bo"}},
                {"email":"x@example.com","isInvitation":true}]}""")
        }
        val vm = vm(core, "t1")
        assertEquals(listOf("u2"), vm.state.value.countingOnYou.map { it.id })
    }

    @Test
    fun `AATD-54 the phrase is a due one once the due time has passed`() = runTest {
        val core = core("t1" to detail("t1", "Past", due = "2026-10-19T08:00:00Z"), "t2" to detail("t2", "Later", due = "2026-10-19T18:00:00Z"))
        val vm = vm(core, "t1", "t2")
        assertTrue(vm.isDue(vm.state.value.deck[0]))
        assertFalse(vm.isDue(vm.state.value.deck[1]))
    }

    @Test
    fun `AATD-54 a swipe decides only when it is long and straight enough`() {
        assertEquals(TriageAction.COMPLETE, ReminderViewModel.swipe(-120f, 10f))
        assertEquals(TriageAction.POSTPONE, ReminderViewModel.swipe(150f, -20f))
        assertEquals(TriageAction.KEEP, ReminderViewModel.swipe(5f, 130f))
        assertNull(ReminderViewModel.swipe(-120f, 100f))
        assertNull(ReminderViewModel.swipe(60f, 0f))
        assertNull(ReminderViewModel.swipe(0f, -150f))
    }
}
