package com.gracefultools.astrid.ui.board

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import com.gracefultools.astrid.ui.lists.GoalRow
import com.gracefultools.astrid.ui.lists.GuardrailRow
import com.gracefultools.astrid.ui.lists.ListSettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADEV-184 (web ADEV-179, iOS ADEV-183): a project's goals and guardrails are the core's
 * `getListGoals` / `getProjectGoals` / `setProjectGoals` (astrid-core ADEV-187). The board shows
 * them; List Settings → Admin edits them for whoever the core says may (`canEdit`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectGoalsViewModelTest {
    private val board = """{"projectId":"p1","listId":"l1","columns":[]}"""
    private val goals = """{"listId":"l1","projectId":"p1","projectName":"Fund",
        "goals":[{"id":"g1","statement":"Maximise long-term returns","measure":"IRR","target":"8%","current":"6%"}],
        "guardrails":[{"id":"r1","statement":"Break no laws","kind":"enforced"}],"canEdit":true}"""

    @Test
    fun `ADEV-184 a board reads its list's goals and guardrails`() = runTest {
        val core = FakeCore().apply { answer("board", board); answer("getListGoals", goals) }
        val vm = BoardViewModel(core, UnconfinedTestDispatcher())
        vm.open("l1")
        assertEquals(JsonPrimitive("l1"), core.last("getListGoals")!!["listId"])
        val read = vm.state.value.goals!!
        assertEquals("IRR", read.goals.single().measure)
        assertTrue(read.guardrails.single().isEnforced)
    }

    @Test
    fun `ADEV-184 a board re-reads its goals when the core says its project's moved, not another's`() = runTest {
        val core = FakeCore().apply { answer("board", board); answer("getListGoals", goals) }
        val vm = BoardViewModel(core, UnconfinedTestDispatcher())
        vm.open("l1")
        fun reads() = core.kinds().count { it == "getListGoals" }
        val before = reads()
        core.emit("projectGoals", """{"change":"projectGoals","id":"p2"}""")
        assertEquals(before, reads())
        core.emit("projectGoals", """{"change":"projectGoals","id":"p1"}""")
        assertEquals(before + 1, reads())
    }

    @Test
    fun `ADEV-184 a guardrail kind this build does not know reads as stated`() = runTest {
        val core = FakeCore().apply {
            answer("board", board)
            answer("getListGoals", """{"goals":[],"guardrails":[{"id":"r1","statement":"x","kind":"audited"}],"canEdit":false}""")
        }
        val vm = BoardViewModel(core, UnconfinedTestDispatcher())
        vm.open("l1")
        assertFalse(vm.state.value.goals!!.guardrails.single().isEnforced)
    }

    private val projectList = """{"listId":"l1","name":"Fund","projectId":"p1","canManageList":true,"members":[]}"""

    @Test
    fun `ADEV-184 List Settings drafts a project's goals for whoever may edit them`() = runTest {
        val core = FakeCore().apply { answer("listMembers", projectList); answer("getProjectGoals", goals) }
        val vm = ListSettingsViewModel(core, "l1", UnconfinedTestDispatcher())
        assertEquals(JsonPrimitive("p1"), core.last("getProjectGoals")!!["projectId"])
        val draft = vm.state.value.goalsDraft!!
        assertEquals("Maximise long-term returns", draft.goals.single().statement)
        assertTrue(draft.guardrails.single().enforced)
        assertFalse(vm.state.value.goalsChanged)
    }

    @Test
    fun `ADEV-184 no editor when the core says this person may not edit the goals`() = runTest {
        val core = FakeCore().apply { answer("listMembers", projectList); answer("getProjectGoals", goals.replace("\"canEdit\":true", "\"canEdit\":false")) }
        val vm = ListSettingsViewModel(core, "l1", UnconfinedTestDispatcher())
        assertNull(vm.state.value.goalsDraft)
    }

    @Test
    fun `ADEV-184 saving PUTs the whole value, a new row with no id and no blank fields`() = runTest {
        val core = FakeCore().apply { answer("listMembers", projectList); answer("getProjectGoals", goals); answer("setProjectGoals", goals) }
        val vm = ListSettingsViewModel(core, "l1", UnconfinedTestDispatcher())
        val draft = vm.state.value.goalsDraft!!
        vm.editGoals(draft.copy(
            goals = draft.goals + GoalRow(statement = "Stay liquid", measure = " "),
            guardrails = draft.guardrails + GuardrailRow(statement = "No leverage"),
        ))
        assertTrue(vm.state.value.goalsChanged)
        vm.saveGoals()
        val sent = core.last("setProjectGoals")!!
        assertEquals(JsonPrimitive("p1"), sent["projectId"])
        val sentGoals = sent["goals"]!!.jsonArray
        assertEquals(JsonPrimitive("g1"), sentGoals[0].jsonObject["id"])
        assertEquals(JsonPrimitive("6%"), sentGoals[0].jsonObject["current"])
        assertEquals(setOf("statement"), sentGoals[1].jsonObject.keys)
        val sentRails = sent["guardrails"] as JsonArray
        assertEquals(JsonPrimitive("enforced"), sentRails[0].jsonObject["kind"])
        assertEquals(setOf("statement", "kind"), sentRails[1].jsonObject.keys)
        assertEquals(JsonPrimitive("stated"), sentRails[1].jsonObject["kind"])
        assertFalse(vm.state.value.goalsSaveFailed)
    }

    @Test
    fun `ADEV-184 a refused save says so and keeps the edit`() = runTest {
        val core = FakeCore().apply {
            answer("listMembers", projectList); answer("getProjectGoals", goals)
            answer("setProjectGoals") { throw CoreFailure(FailureKind.REFUSED, "no", status = 403) }
        }
        val vm = ListSettingsViewModel(core, "l1", UnconfinedTestDispatcher())
        val draft = vm.state.value.goalsDraft!!
        vm.editGoals(draft.copy(goals = draft.goals + GoalRow(statement = "Stay liquid")))
        vm.saveGoals()
        assertTrue(vm.state.value.goalsSaveFailed)
        assertEquals(2, vm.state.value.goalsDraft!!.goals.size)
    }
}
