package com.gracefultools.astrid.ui.detail

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
                val about = (change.raw["id"] as? JsonPrimitive)?.contentOrNull
                if (change.kind == "synced" || about == id || change.kind == "comment") load()
            }
        }
    }

    fun load() = work {
        try {
            val detail = CoreJson.decodeFromJsonElement(TaskDetail.serializer(), core.run(command("taskDetail", "taskId" to id)))
            id = detail.task.id.ifEmpty { id }
            _state.update { it.copy(detail = detail, error = null) }
        } catch (failure: CoreFailure) {
            _state.update { it.copy(error = if (failure.isStillPending) null else failure.message) }
        }
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

    fun postComment(text: String) = work {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@work
        runCommand { core.run(command("postComment", "taskId" to id, "content" to trimmed)) }
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
