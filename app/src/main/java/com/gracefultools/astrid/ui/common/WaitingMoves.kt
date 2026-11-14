package com.gracefultools.astrid.ui.common

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.Blocker
import com.gracefultools.astrid.model.BlockerCandidates
import com.gracefultools.astrid.model.TaskDetail
import com.gracefultools.astrid.model.UserSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate

/**
 * What a task in Waiting waits for — a day, or another task — and whether the mover keeps its
 * assignee (iOS `WaitCondition`, AITD-631). Jon, 2026-11-12: Waiting means waiting on a date or on
 * a task, nothing else; astrid-core holds the rule (`astrid_rules::waiting`).
 *
 * A move command carries it in four optional fields (`app::command::WaitOptions`). Every call site
 * names them as literal pairs so the wire-schema gate can read them:
 * `"dueDateTime" to wait?.dueDateTime, "isAllDay" to wait?.isAllDay,
 * "waitOnTaskId" to wait?.waitOnTaskId, "keepAssignee" to (wait?.keepAssignee == true)`.
 */
data class WaitCondition(
    /** The day picked, before the core turns it into an instant ([WaitingMoves.confirm]). */
    val until: LocalDate? = null,
    val waitOnTaskId: String? = null,
    val waitOnTitle: String? = null,
    val keepAssignee: Boolean = false,
    /** Filled by the core's `dueDateOnDay` for [until]: the app never builds the instant itself. */
    val dueDateTime: String? = null,
    val isAllDay: Boolean? = null,
) {
    companion object {
        fun until(day: LocalDate, keepAssignee: Boolean) = WaitCondition(until = day, keepAssignee = keepAssignee)
        fun on(taskId: String, title: String, keepAssignee: Boolean) =
            WaitCondition(waitOnTaskId = taskId, waitOnTitle = title, keepAssignee = keepAssignee)
    }
}

/**
 * Asks "until when, or on what?" when the core refuses a move into Waiting, and sends the same move
 * again with the answer — iOS's `WaitingPromptPresenter` (AITD-631), one per screen that moves tasks.
 *
 * Every move into Waiting runs through [move]. The first attempt carries no condition, so a task
 * that already waits on something, and every move to any other column, simply happens. Only the
 * core's `WAITING_NEEDS_CONDITION` opens the prompt ([State.request]); [confirm] re-sends, [cancel]
 * sends nothing, so the task stays where it was.
 */
class WaitingMoves(private val core: Core, private val afterMove: () -> Unit = {}) {
    /** Whose assignment the "Keep assigned" toggle would keep; no toggle when nobody is assigned. */
    enum class Keep { ME, OTHER }

    /** Why a confirm did not go — iOS's three messages. */
    enum class Problem { CYCLE, ADD_FAILED, OTHER }

    data class Request(
        val taskId: String,
        val title: String,
        val assigneeId: String?,
        val assigneeName: String?,
        val currentUserId: String?,
    ) {
        val keep: Keep? get() = when {
            assigneeId.isNullOrEmpty() -> null
            assigneeId == currentUserId -> Keep.ME
            else -> Keep.OTHER
        }
    }

    data class State(
        val request: Request? = null,
        val candidates: List<Blocker> = emptyList(),
        val sending: Boolean = false,
        val problem: Problem? = null,
        val problemMessage: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var resend: (suspend (WaitCondition?) -> Unit)? = null

    /**
     * Run a move. Answers null when it went (or waits in the Outbox) or when the prompt opened, and
     * the failure's words for anything else — reported exactly as the caller did before.
     */
    suspend fun move(taskId: String, send: suspend (WaitCondition?) -> Unit): String? = try {
        send(null)
        null
    } catch (failure: CoreFailure) {
        when {
            failure.code == NEEDS_CONDITION -> { open(taskId, send); null }
            failure.isStillPending -> null
            else -> failure.message
        }
    }

    private suspend fun open(taskId: String, send: suspend (WaitCondition?) -> Unit) {
        val detail = runCatching {
            CoreJson.decodeFromJsonElement(TaskDetail.serializer(), core.run(command("taskDetail", "taskId" to taskId)))
        }.getOrNull()
        val me = runCatching {
            CoreJson.decodeFromJsonElement(UserSummary.serializer(), core.run(command("currentUser"))).id
        }.getOrNull()
        resend = send
        _state.value = State(
            request = Request(
                taskId = taskId,
                title = detail?.task?.title.orEmpty(),
                assigneeId = detail?.assignee?.id?.takeIf { it.isNotEmpty() },
                assigneeName = detail?.assignee?.display,
                currentUserId = me?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    /** The blocker picker's own search, for "Waiting on…". */
    suspend fun search(query: String) {
        val taskId = _state.value.request?.taskId ?: return
        val found = runCatching {
            CoreJson.decodeFromJsonElement(
                BlockerCandidates.serializer(),
                core.run(command("taskBlockerCandidates", "taskId" to taskId, "query" to query, "limit" to 30)),
            ).candidates.filter { it.id != taskId }
        }.getOrDefault(emptyList())
        _state.update { if (it.request?.taskId == taskId) it.copy(candidates = found) else it }
    }

    /** The prompt's answer: the same move again, carrying it. True when it went and the prompt closed. */
    suspend fun confirm(condition: WaitCondition): Boolean {
        val request = _state.value.request ?: return false
        val send = resend ?: return false
        _state.update { it.copy(sending = true, problem = null, problemMessage = null) }
        return try {
            send(resolve(request.taskId, condition))
            close()
            afterMove()
            true
        } catch (failure: CoreFailure) {
            if (failure.isStillPending) {
                close(); afterMove(); true
            } else {
                // Waiting on a task attaches it as a blocker online, at once: the blocker picker's
                // copy says what went wrong. A date can fail only as any task write can.
                val problem = when {
                    condition.waitOnTaskId != null && failure.status == 409 -> Problem.CYCLE
                    condition.waitOnTaskId != null -> Problem.ADD_FAILED
                    else -> Problem.OTHER
                }
                _state.update { it.copy(sending = false, problem = problem, problemMessage = failure.message) }
                false
            }
        }
    }

    fun cancel() = close()

    private fun close() {
        resend = null
        _state.value = State()
    }

    /** A picked day becomes the core's all-day instant (`dueDateOnDay`), as the When picker does. */
    private suspend fun resolve(taskId: String, condition: WaitCondition): WaitCondition {
        val day = condition.until ?: return condition
        val answer = core.run(command("dueDateOnDay", "taskId" to taskId, "day" to day.toString())) as? JsonObject
        return condition.copy(
            dueDateTime = (answer?.get("dueDateTime") as? JsonPrimitive)?.contentOrNull,
            isAllDay = (answer?.get("isAllDay") as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: true,
        )
    }

    companion object {
        /** The core's refusal code (`astrid_rules::waiting::WAITING_NEEDS_CONDITION`): the cue to ask. */
        const val NEEDS_CONDITION = "WAITING_NEEDS_CONDITION"
    }
}
