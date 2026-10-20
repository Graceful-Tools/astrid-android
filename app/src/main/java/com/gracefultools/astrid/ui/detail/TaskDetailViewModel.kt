package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.core.concernsTask
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.AssigneeOptions
import com.gracefultools.astrid.model.DueDateOptions
import com.gracefultools.astrid.model.ReminderOptions
import com.gracefultools.astrid.model.RepeatOptions
import com.gracefultools.astrid.model.ListPicks
import com.gracefultools.astrid.model.TaskDetail
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class TaskDetailState(
    val detail: TaskDetail? = null,
    val assignees: AssigneeOptions? = null,
    val repeats: RepeatOptions? = null,
    val reminders: ReminderOptions? = null,
    val dueOptions: DueDateOptions? = null,
    val listPicks: ListPicks? = null,
    val deleted: Boolean = false,
    val error: String? = null,
    val blockerCandidates: List<com.gracefultools.astrid.model.Blocker> = emptyList(),
    /** A link minted by shareTask, for the share sheet. */
    val shareLink: String? = null,
    val shareFailed: Boolean = false,
    /** A copy made by copyTask: open it. */
    val copiedId: String? = null,
)

/**
 * One task's detail. Every edit is a core command (`updateTask`, `completeTask`, `setTaskLists`,
 * `postComment`); the core journals it, so an edit made offline is kept and sent later.
 */
class TaskDetailViewModel(
    private val core: Core,
    private val taskId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(TaskDetailState())
    val state: StateFlow<TaskDetailState> = _state.asStateFlow()
    /** The task's real id, once read — the screen may have been opened by an identifier like AATD-7. */
    private var id = taskId

    init {
        load()
        work {
            core.changes.collect { change ->
                if (change.concernsTask(id)) load()
            }
        }
    }

    fun load(): kotlinx.coroutines.Job = work {
        try {
            val detail = CoreJson.decodeFromJsonElement(TaskDetail.serializer(), core.run(command("taskDetail", "taskId" to id)))
            id = detail.task.id.ifEmpty { id }
            _state.update { it.copy(detail = detail, error = null) }
        } catch (failure: CoreFailure) {
            // A task opened by the id it had before the server named it: follow it to its real one.
            if (failure.kind == com.gracefultools.astrid.core.FailureKind.NOT_FOUND && id.startsWith("temp")) {
                val real = runCatching {
                    ((core.run(command("resolveIds", "ids" to listOf(id))) as? JsonObject)?.get(id) as? JsonPrimitive)?.contentOrNull
                }.getOrNull()
                if (real != null) { id = real; load(); return@work }
            }
            _state.update { it.copy(error = if (failure.isStillPending) null else failure.message) }
        }
    }

    /** iOS's menu: Won't do, or Reopen a task closed that way (setClosedReason). */
    fun toggleWontDo() = work {
        val canceled = _state.value.detail?.isCanceled == true
        runCommand { core.run(command("setClosedReason", "taskId" to id, "closedReason" to if (canceled) null else "canceled")) }
        load()
    }

    /** iOS's Copy sheet: into a list (null = My Tasks only), with or without the comments. */
    fun copy(targetListId: String?, includeComments: Boolean) = work {
        runCommand {
            val made = core.run(command("copyTask", "taskId" to id, "targetListId" to targetListId, "includeComments" to includeComments))
            val copyId = ((made as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
            _state.update { it.copy(copiedId = copyId) }
        }
    }

    fun copyOpened() = _state.update { it.copy(copiedId = null) }

    /** iOS's Share sheet: a link minted by the server (online only). */
    fun share() = work {
        _state.update { it.copy(shareLink = null, shareFailed = false) }
        try {
            val answer = core.run(command("shareTask", "taskId" to id)) as JsonObject
            _state.update { it.copy(shareLink = (answer["url"] as? JsonPrimitive)?.contentOrNull) }
        } catch (failure: CoreFailure) {
            _state.update { it.copy(shareFailed = true) }
        }
    }

    /** The project state chips: the core moves the task to that column. */
    fun setStatus(columnId: String) = work {
        runCommand { core.run(command("setTaskStatus", "taskId" to id, "columnId" to columnId)) }
        load()
    }

    fun searchBlockers(query: String) = work {
        runCatching {
            CoreJson.decodeFromJsonElement(
                com.gracefultools.astrid.model.BlockerCandidates.serializer(),
                core.run(command("taskBlockerCandidates", "taskId" to id, "query" to query, "limit" to 30)),
            )
        }.onSuccess { found -> _state.update { it.copy(blockerCandidates = found.candidates) } }
    }

    fun addBlocker(blockingTaskId: String) = work {
        runCommand { core.run(command("addTaskBlocker", "taskId" to id, "blockingTaskId" to blockingTaskId)) }
        load()
    }

    fun removeBlocker(blockingTaskId: String) = work {
        runCommand { core.run(command("removeTaskBlocker", "taskId" to id, "blockingTaskId" to blockingTaskId)) }
        load()
    }

    /** iOS's "Add a sub task": a task under this one, in this one's lists. */
    fun addSubtask(title: String) = work {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return@work
        val lists = _state.value.detail?.task?.listIds.orEmpty()
        runCommand { core.run(command("createTask", "title" to trimmed, "parentTaskId" to id, "listIds" to lists)) }
        load()
    }

    fun setSubtaskCompleted(subtaskId: String, completed: Boolean) = work {
        runCommand { core.run(command("completeTask", "taskId" to subtaskId, "completed" to completed, "source" to "android")) }
        load()
    }

    /** iOS's "Complete sub-tasks?": the open subtasks first, then this task. */
    fun completeWithSubtasks(alsoSubtasks: Boolean) = work {
        val open = _state.value.detail?.subtasks?.filterNot { it.completed }.orEmpty()
        if (alsoSubtasks) for (sub in open) runCommand { core.run(command("completeTask", "taskId" to sub.id, "completed" to true, "source" to "android")) }
        runCommand { core.run(command("completeTask", "taskId" to id, "completed" to true, "source" to "android")) }
        load()
    }

    fun setTitle(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || trimmed == _state.value.detail?.task?.title) return
        update("title" to trimmed)
    }

    fun setDescription(text: String) {
        if (text == _state.value.detail?.task?.description) return
        update("description" to text)
    }

    fun setPriority(priority: Int) = update("priority" to priority)

    fun setDue(dueDateTime: String?, isAllDay: Boolean) = update("dueDateTime" to dueDateTime, "isAllDay" to isAllDay)

    /** A day from the date picker: the core says which instant that day means for this task. */
    fun pickDay(isoDay: String) = work {
        runCommand {
            val answer = core.run(command("dueDateOnDay", "taskId" to id, "day" to isoDay)) as JsonObject
            val due = (answer["dueDateTime"] as? JsonPrimitive)?.contentOrNull
            val allDay = (answer["isAllDay"] as? JsonPrimitive)?.contentOrNull == "true"
            core.run(command("updateTask", "taskId" to id, "changes" to mapOf("dueDateTime" to due, "isAllDay" to allDay)))
        }
        load()
    }

    /** A time from the time picker: the core puts it on the task's own day (dueDateAtTime). */
    fun pickTime(hour: Int, minute: Int) = work {
        runCommand {
            val answer = core.run(command("dueDateAtTime", "taskId" to id, "hour" to hour, "minute" to minute)) as JsonObject
            val due = (answer["dueDateTime"] as? JsonPrimitive)?.contentOrNull
            core.run(command("updateTask", "taskId" to id, "changes" to mapOf("dueDateTime" to due, "isAllDay" to false)))
        }
        load()
    }

    fun loadDueOptions() = work {
        runCatching { CoreJson.decodeFromJsonElement(DueDateOptions.serializer(), core.run(command("dueDateOptions", "taskId" to id))) }
            .onSuccess { options -> _state.update { it.copy(dueOptions = options) } }
    }

    fun loadListPicks(query: String = "") = work {
        runCatching {
            CoreJson.decodeFromJsonElement(ListPicks.serializer(), core.run(command("listPicks", "taskId" to id, "query" to query, "asToggles" to true)))
        }.onSuccess { picks -> _state.update { it.copy(listPicks = picks) } }
    }

    fun loadAssignees() = work {
        runCatching { CoreJson.decodeFromJsonElement(AssigneeOptions.serializer(), core.run(command("assigneeOptions", "taskId" to id))) }
            .onSuccess { options -> _state.update { it.copy(assignees = options) } }
    }

    /** `null` is nobody: the core's first option, and an explicit unassign on the wire. */
    fun setAssignee(userId: String?) = update("assigneeId" to userId)

    fun loadRepeats() = work {
        runCatching { CoreJson.decodeFromJsonElement(RepeatOptions.serializer(), core.run(command("repeatOptions", "taskId" to id))) }
            .onSuccess { options -> _state.update { it.copy(repeats = options) } }
    }

    /**
     * A preset's wire value (`never`, `daily`, …). `custom` alone writes nothing — there is no rule
     * to store until the editor has built one — and a preset clears any old pattern, or the task
     * would repeat one way and read another (as astrid-windows does).
     */
    fun setRepeating(value: String) {
        if (value == "custom") return
        update("repeating" to value, "repeatingData" to null)
    }

    /**
     * A custom pattern (`CustomRepeatingPattern`), sending only the fields this rule needs: a weekly
     * rule carrying a stale month day means something else to the next client that reads it.
     */
    fun setCustomRepeat(
        unit: String,
        interval: Int,
        weekdays: List<String> = emptyList(),
        endCondition: String? = null,
        endAfterOccurrences: Int? = null,
        endUntilDate: String? = null,
    ) {
        val pattern = buildMap<String, Any?> {
            put("type", "custom")
            put("unit", unit)
            put("interval", interval.coerceAtLeast(1))
            if (unit == "weeks" && weekdays.isNotEmpty()) put("weekdays", weekdays)
            if (endCondition != null) {
                put("endCondition", endCondition)
                if (endCondition == "after_occurrences" && (endAfterOccurrences ?: 0) > 0) put("endAfterOccurrences", endAfterOccurrences)
                if (endCondition == "until_date" && endUntilDate != null) put("endUntilDate", endUntilDate)
            }
        }
        update("repeating" to "custom", "repeatingData" to pattern)
    }

    fun loadReminders() = work {
        runCatching { CoreJson.decodeFromJsonElement(ReminderOptions.serializer(), core.run(command("reminderOptions", "taskId" to id))) }
            .onSuccess { options -> _state.update { it.copy(reminders = options) } }
    }

    fun setReminder(reminderTime: String?) = update("reminderTime" to reminderTime)

    fun setLists(listIds: List<String>) = work {
        runCommand { core.run(command("setTaskLists", "taskId" to id, "listIds" to listIds)) }
        load()
    }

    /** Commit nothing first is not a risk here: every field above was already sent as it changed. */
    fun setCompleted(completed: Boolean) = work {
        runCommand { core.run(command("completeTask", "taskId" to id, "completed" to completed, "source" to "android")) }
        load()
    }

    fun postComment(text: String, replyTo: String? = null) = work {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@work
        runCommand {
            core.run(command("postComment", "taskId" to id, "content" to trimmed, *listOfNotNull(replyTo?.let { "parentCommentId" to it }).toTypedArray()))
        }
        load()
    }

    fun editComment(commentId: String, text: String) = work {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@work
        runCommand { core.run(command("editComment", "commentId" to commentId, "content" to trimmed)) }
        load()
    }

    fun deleteComment(commentId: String) = work {
        runCommand { core.run(command("deleteComment", "commentId" to commentId)) }
        load()
    }

    /** iOS's pull to refresh on detail: send what is waiting, then fetch this task's thread. */
    fun refresh() = work {
        runCatching { core.run(command("drain")) }
        runCatching { core.run(command("refreshComments", "taskId" to id)) }
        load()
    }

    /** A file the person picked, already copied to app storage: the core uploads it with a comment. */
    fun attach(path: String, name: String, mimeType: String?) = work {
        runCommand { core.run(command("attachFile", "taskId" to id, "path" to path, "name" to name, "mimeType" to mimeType)) }
        load()
    }

    /** The file on this device — downloaded by the core if it is not already — for [onReady] to open. */
    fun open(fileId: String, onReady: (String) -> Unit) = work {
        try {
            val answer = core.run(command("downloadAttachment", "taskId" to id, "fileId" to fileId)) as JsonObject
            (answer["path"] as? JsonPrimitive)?.contentOrNull?.let(onReady)
        } catch (failure: CoreFailure) {
            _state.update { it.copy(error = failure.message) }
        }
    }

    fun delete() = work {
        runCommand { core.run(command("deleteTask", "taskId" to id)) }
        _state.update { it.copy(deleted = true) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun update(vararg changes: Pair<String, Any?>) = work {
        runCommand { core.run(command("updateTask", "taskId" to id, "changes" to changes.toMap())) }
        load()
    }

    private suspend fun runCommand(block: suspend () -> Unit) {
        try {
            block()
        } catch (failure: CoreFailure) {
            if (!failure.isStillPending) _state.update { it.copy(error = failure.message) }
        }
    }
}
