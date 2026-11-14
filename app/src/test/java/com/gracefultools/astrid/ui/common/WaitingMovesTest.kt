package com.gracefultools.astrid.ui.common

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import com.gracefultools.astrid.core.command
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * AATD-98: a move into Waiting that names no condition is refused by the core with
 * `WAITING_NEEDS_CONDITION`; the app asks "until when, or on what?" and sends the same move again
 * with the answer (iOS's WaitingPromptPresenter, AITD-631).
 */
class WaitingMovesTest {
    private val detail = """{"task":{"id":"t1","title":"Ship it","assigneeId":"u2"},
        "assignee":{"id":"u2","name":"Sam"},"descriptionBlocks":[],"comments":[],"subtasks":[]}"""

    /** A core whose `setTaskStatus` into Waiting needs a condition, as astrid_rules::waiting R2 says. */
    private fun waitingCore() = FakeCore().apply {
        answer("taskDetail", detail)
        answer("currentUser", """{"id":"u1","name":"Me"}""")
        answer("dueDateOnDay", """{"dueDateTime":"2026-11-20T00:00:00Z","isAllDay":true}""")
        answer("taskBlockerCandidates", """{"candidates":[{"id":"t9","title":"Upstream"}]}""")
        answer("setTaskStatus") { sent -> refuseWithoutCondition(sent) }
    }

    private fun refuseWithoutCondition(sent: JsonObject): JsonElement {
        val named = listOf("dueDateTime", "waitOnTaskId").any { sent[it] != null && sent[it] != JsonNull }
        if (!named) throw CoreFailure(FailureKind.REFUSED, "Waiting needs a date or a task", code = "WAITING_NEEDS_CONDITION")
        return JsonNull
    }

    private suspend fun move(core: FakeCore, moves: WaitingMoves): String? = moves.move("t1") { wait ->
        core.run(
            command(
                "setTaskStatus", "taskId" to "t1", "columnId" to "waiting",
                "dueDateTime" to wait?.dueDateTime, "isAllDay" to wait?.isAllDay,
                "waitOnTaskId" to wait?.waitOnTaskId, "keepAssignee" to (wait?.keepAssignee == true),
            ),
        )
    }

    @Test
    fun `AATD-98 a refused move into Waiting opens the prompt for that task`() = runTest {
        val core = waitingCore()
        val moves = WaitingMoves(core)
        assertNull(move(core, moves))
        val request = moves.state.value.request!!
        assertEquals("t1", request.taskId)
        assertEquals("Ship it", request.title)
        assertEquals("u2", request.assigneeId)
        assertEquals("Sam", request.assigneeName)
        assertEquals("u1", request.currentUserId)
    }

    @Test
    fun `AATD-98 a move the core allows opens no prompt`() = runTest {
        val core = waitingCore().apply { answer("setTaskStatus", "null") }
        val moves = WaitingMoves(core)
        assertNull(move(core, moves))
        assertNull(moves.state.value.request)
        assertEquals(1, core.kinds().count { it == "setTaskStatus" })
    }

    @Test
    fun `AATD-98 any other refusal is reported as before, with no prompt`() = runTest {
        val core = waitingCore().apply { answer("setTaskStatus") { throw CoreFailure(FailureKind.REFUSED, "No access") } }
        val moves = WaitingMoves(core)
        assertEquals("No access", move(core, moves))
        assertNull(moves.state.value.request)
    }

    @Test
    fun `AATD-98 waiting until a day re-sends the move with the core's all-day date and unassigns`() = runTest {
        val core = waitingCore()
        var moved = 0
        val moves = WaitingMoves(core) { moved++ }
        move(core, moves)
        assertTrue(moves.confirm(WaitCondition.until(LocalDate.of(2026, 11, 20), keepAssignee = false)))
        assertEquals(JsonPrimitive("2026-11-20"), core.last("dueDateOnDay")!!["day"])
        val sent = core.last("setTaskStatus")!!
        assertEquals(JsonPrimitive("2026-11-20T00:00:00Z"), sent["dueDateTime"])
        assertEquals(JsonPrimitive(true), sent["isAllDay"])
        assertEquals(JsonPrimitive(false), sent["keepAssignee"])
        assertNull(moves.state.value.request)
        assertEquals(1, moved)
    }

    @Test
    fun `AATD-98 waiting on a task re-sends the move with it, keeping the assignee when asked`() = runTest {
        val core = waitingCore()
        val moves = WaitingMoves(core)
        move(core, moves)
        moves.search("Upst")
        assertEquals("t1", core.last("taskBlockerCandidates")!!["taskId"]!!.let { (it as JsonPrimitive).content })
        val hit = moves.state.value.candidates.single()
        assertTrue(moves.confirm(WaitCondition.on(hit.id, hit.title.orEmpty(), keepAssignee = true)))
        val sent = core.last("setTaskStatus")!!
        assertEquals(JsonPrimitive("t9"), sent["waitOnTaskId"])
        assertEquals(JsonPrimitive(true), sent["keepAssignee"])
        assertNull(moves.state.value.request)
    }

    @Test
    fun `AATD-98 cancel sends nothing and leaves the task where it was`() = runTest {
        val core = waitingCore()
        val moves = WaitingMoves(core)
        move(core, moves)
        moves.cancel()
        assertNull(moves.state.value.request)
        assertEquals(1, core.kinds().count { it == "setTaskStatus" })
    }

    @Test
    fun `AATD-98 a failed confirm keeps the prompt open and says why`() = runTest {
        val core = waitingCore()
        val moves = WaitingMoves(core)
        move(core, moves)
        core.answer("setTaskStatus") { throw CoreFailure(FailureKind.REFUSED, "server said no", status = 400) }
        assertFalse(moves.confirm(WaitCondition.on("t9", "Upstream", keepAssignee = false)))
        assertEquals("t1", moves.state.value.request?.taskId)
        assertEquals(WaitingMoves.Problem.ADD_FAILED, moves.state.value.problem)
        // The server answers a blocker that would close a loop with 409 (iOS TaskBlockers.isCycleRefusal).
        core.answer("setTaskStatus") { throw CoreFailure(FailureKind.REFUSED, "dependency_cycle", status = 409) }
        assertFalse(moves.confirm(WaitCondition.on("t9", "Upstream", keepAssignee = false)))
        assertEquals(WaitingMoves.Problem.CYCLE, moves.state.value.problem)
        // A date fails only as any write can: its own words.
        core.answer("setTaskStatus") { throw CoreFailure(FailureKind.REFUSED, "No access") }
        assertFalse(moves.confirm(WaitCondition.until(LocalDate.of(2026, 11, 20), keepAssignee = false)))
        assertEquals(WaitingMoves.Problem.OTHER, moves.state.value.problem)
        assertEquals("No access", moves.state.value.problemMessage)
    }

    @Test
    fun `AATD-98 the keep toggle names whose assignment it keeps, and hides when nobody is assigned`() {
        assertEquals(WaitingMoves.Keep.ME, WaitingMoves.Request("t1", "", "u1", "Me", "u1").keep)
        assertEquals(WaitingMoves.Keep.OTHER, WaitingMoves.Request("t1", "", "u2", "Sam", "u1").keep)
        assertNull(WaitingMoves.Request("t1", "", null, null, "u1").keep)
    }
}
