package com.gracefultools.astrid.ui.reminders

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.ListSettings
import com.gracefultools.astrid.model.TaskDetail
import com.gracefultools.astrid.model.UserSummary
import com.gracefultools.astrid.platform.ReminderActions
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate

/** iOS triage's four decisions (ReminderTriage.Action). */
enum class TriageAction { COMPLETE, POSTPONE, KEEP, ASSIGN }

data class ReminderState(
    val loading: Boolean = true,
    /** The tasks reminded about, still open, in the order the notifications named them. */
    val deck: List<TaskDetail> = emptyList(),
    /** The card on top of the triage deck; past the end means "All caught up". */
    val index: Int = 0,
    /** Shared-list collaborators of the first task, for "These people are counting on you!". */
    val countingOnYou: List<UserSummary> = emptyList(),
    /** Set when the screen has done its work and should close. */
    val finished: Boolean = false,
    /** Who an up-swipe hands a task to (the account's assistant), from the core's deck. */
    val assistantId: String? = null,
    val assistantName: String? = null,
) {
    val current: TaskDetail? get() = deck.getOrNull(index)
    /** One task is iOS's ReminderView; several are its ReminderTriageView. */
    val isTriage: Boolean get() = deck.size > 1
}

/**
 * The screen a reminder opens (iOS ReminderPresenter + ReminderView / ReminderTriageView). The
 * deck is the reminders that were tapped — the core decided they were due. Every decision is a
 * core command (ReminderActions).
 */
class ReminderViewModel(
    private val core: Core,
    private val taskIds: List<String>,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> Instant = Instant::now,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ReminderState())
    val state: StateFlow<ReminderState> = _state.asStateFlow()

    /** Tasks the screen has dealt with, so their notifications can be taken down. */
    val handled = mutableSetOf<String>()

    init { load() }

    private fun load() = work {
        // Several reminders: iOS's triage deck, which the core builds (reminderTriageDeck, core
        // 0a3998e): the reminded task, then everything of mine due today or overdue.
        val triage = if (taskIds.distinct().size > 1) {
            runCatching { core.run(command("reminderTriageDeck", "remindedTaskId" to taskIds.first())) as? kotlinx.serialization.json.JsonObject }.getOrNull()
        } else null
        val ids = triage?.get("rows")?.let { rows ->
            (rows as? kotlinx.serialization.json.JsonArray)?.mapNotNull { ((it as? kotlinx.serialization.json.JsonObject)?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }?.takeIf { it.isNotEmpty() } ?: taskIds.distinct()
        val assistant = triage?.get("assistant") as? kotlinx.serialization.json.JsonObject
        val deck = ids.mapNotNull { id ->
            runCatching { CoreJson.decodeFromJsonElement(TaskDetail.serializer(), core.run(command("taskDetail", "taskId" to id))) }.getOrNull()
        }.filterNot { it.task.completed }
        _state.update {
            it.copy(
                loading = false, deck = deck, finished = deck.isEmpty(),
                assistantId = (assistant?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.content,
                assistantName = (assistant?.get("name") as? kotlinx.serialization.json.JsonPrimitive)?.content,
            )
        }
        deck.firstOrNull()?.takeIf { deck.size == 1 }?.let { loadCollaborators(it) }
    }

    /** iOS sharedListMembers: the other people on the task's shared lists, at most four. */
    private suspend fun loadCollaborators(detail: TaskDetail) {
        val people = detail.listChips.filter { it.glyph == "people" }.flatMap { chip ->
            val settings = runCatching {
                CoreJson.decodeFromJsonElement(ListSettings.serializer(), core.run(command("listMembers", "listId" to chip.id)))
            }.getOrNull() ?: return@flatMap emptyList()
            settings.members.filter { !it.isPending && !it.isInvitation && it.who != settings.currentUserId }
                .map { it.user ?: UserSummary(id = it.who.orEmpty(), name = it.name, email = it.email) }
        }.filter { it.id.isNotEmpty() }.distinctBy { it.id }.take(4)
        if (people.isNotEmpty()) _state.update { it.copy(countingOnYou = people) }
    }

    /** iOS ReminderView: the Astrid phrase is a due one once the due time has passed. */
    fun isDue(detail: TaskDetail): Boolean =
        detail.isOverdue || detail.task.dueDateTime?.let { runCatching { !Instant.parse(it).isAfter(now()) }.getOrNull() } == true

    fun complete(taskId: String) = work {
        ReminderActions.complete(core, taskId)
        handled += taskId
        _state.update { it.copy(finished = true) }
    }

    /** 15 minutes, a day (1440) or a week (10080), as iOS's three buttons. */
    fun snooze(taskId: String, minutes: Long) = work {
        ReminderActions.snooze(core, taskId, minutes)
        handled += taskId
        _state.update { it.copy(finished = true) }
    }

    /** One swipe or button on the triage deck, then the next card. */
    fun decide(action: TriageAction) = work {
        val task = _state.value.current ?: return@work
        _state.update { it.copy(index = it.index + 1) }
        when (action) {
            TriageAction.COMPLETE -> ReminderActions.complete(core, task.task.id)
            TriageAction.POSTPONE -> ReminderActions.postpone(core, task.task.id, today())
            TriageAction.KEEP -> Unit
            // iOS's up-swipe: the assistant takes it.
            TriageAction.ASSIGN -> _state.value.assistantId?.let { assistant ->
                runCatching { core.run(command("updateTask", "taskId" to task.task.id, "changes" to mapOf("assigneeId" to assistant))) }
            }
        }
        handled += task.task.id
    }

    fun close() = _state.update { it.copy(finished = true) }

    companion object {
        /** iOS ReminderTriage.commitDistance. */
        const val COMMIT_DISTANCE = 100f

        /**
         * iOS ReminderTriage.action(forDragX:y:): far enough along one axis and clearly more along
         * it than the other. Left completes, right is tomorrow, down keeps, up hands it to the
         * assistant — only when there is one ([hasAssistant]).
         */
        fun swipe(dx: Float, dy: Float, hasAssistant: Boolean = false): TriageAction? {
            val ax = kotlin.math.abs(dx); val ay = kotlin.math.abs(dy)
            if (ax >= COMMIT_DISTANCE && ax > ay * 1.5f) return if (dx < 0) TriageAction.COMPLETE else TriageAction.POSTPONE
            if (ay >= COMMIT_DISTANCE && ay > ax * 1.5f) return if (dy > 0) TriageAction.KEEP else if (hasAssistant) TriageAction.ASSIGN else null
            return null
        }
    }
}
