package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.core.agentTyping
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
    /** Every file on the task (`attachments`), for the Attachments strip and its gallery. */
    val attachments: List<com.gracefultools.astrid.model.AttachmentFile> = emptyList(),
    /** A GitHub board's fields for this task (`taskProjectFields`); empty off a GitHub board. */
    val projectFields: List<com.gracefultools.astrid.model.ProjectFieldRow> = emptyList(),
    val projectFieldError: ProjectFieldError? = null,
    /** The Who picker's "Someone else" section. */
    val someoneElse: SomeoneElse = SomeoneElse(),
    /** An agent thinking about this task, from the live stream (`agentTyping`). */
    val typingAgent: TypingAgent? = null,
    /** The comment box's popup: the core's suggestions for the trigger the caret is in. */
    val suggestions: List<com.gracefultools.astrid.model.CommentSuggestion> = emptyList(),
    /** Files picked for the next comment, sent with its text when Send is pressed. */
    val queued: List<QueuedFile> = emptyList(),
)

/** iOS's "Someone else": people found as you type, and what assigning by email came to. */
data class SomeoneElse(
    val suggestions: List<com.gracefultools.astrid.model.UserSummary> = emptyList(),
    /** The task is in no list, so nobody new can be given access to it. */
    val needsList: Boolean = false,
    /** The server's refusal, shown rather than swallowed. */
    val error: String? = null,
    /** Assigned: the sheet can close. */
    val assigned: Boolean = false,
)

/** An agent at work; [name] is null when the stream did not say which. */
data class TypingAgent(val name: String?)

/** A file copied into app storage, waiting to go with the next comment. */
data class QueuedFile(val path: String, val name: String, val mimeType: String?)

/** A project field the server would not take, and iOS's reason for it (ProjectFieldErrors). */
data class ProjectFieldError(val fieldId: String, val reason: Reason) {
    enum class Reason { AUTH_REQUIRED, SSO_REQUIRED, SAVE_FAILED }
}

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
        loadProjectFields()
        work {
            core.changes.collect { change ->
                change.agentTyping?.let { typing ->
                    if (typing.taskId == id) _state.update { it.copy(typingAgent = if (typing.active) TypingAgent(typing.agentName) else null) }
                }
                if (change.concernsTask(id)) load()
            }
        }
    }

    fun load(): kotlinx.coroutines.Job = work {
        try {
            val detail = CoreJson.decodeFromJsonElement(TaskDetail.serializer(), core.run(command("taskDetail", "taskId" to id)))
            id = detail.task.id.ifEmpty { id }
            // An agent's reply ends its "is thinking..." (iOS clears it on the answer too).
            val replied = _state.value.typingAgent != null && detail.comments.lastOrNull()?.authorIsAgent == true &&
                detail.comments.size > (_state.value.detail?.comments?.size ?: 0)
            _state.update { it.copy(detail = detail, error = null, typingAgent = if (replied) null else it.typingAgent) }
            loadAttachments()
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

    /**
     * Told when a due date is set here — iOS asks for notification permission then
     * (TaskDetailViewNew → NotificationPromptManager.checkAndPromptAfterDateSet, AATD-54).
     */
    var onDueDateSet: () -> Unit = {}

    fun setDue(dueDateTime: String?, isAllDay: Boolean) {
        update("dueDateTime" to dueDateTime, "isAllDay" to isAllDay)
        if (dueDateTime != null) onDueDateSet()
    }

    /** A day from the date picker: the core says which instant that day means for this task. */
    fun pickDay(isoDay: String) = work {
        runCommand {
            val answer = core.run(command("dueDateOnDay", "taskId" to id, "day" to isoDay)) as JsonObject
            val due = (answer["dueDateTime"] as? JsonPrimitive)?.contentOrNull
            val allDay = (answer["isAllDay"] as? JsonPrimitive)?.contentOrNull == "true"
            core.run(command("updateTask", "taskId" to id, "changes" to mapOf("dueDateTime" to due, "isAllDay" to allDay)))
        }
        onDueDateSet()
        load()
    }

    /** A time from the time picker: the core puts it on the task's own day (dueDateAtTime). */
    fun pickTime(hour: Int, minute: Int) = work {
        runCommand {
            val answer = core.run(command("dueDateAtTime", "taskId" to id, "hour" to hour, "minute" to minute)) as JsonObject
            val due = (answer["dueDateTime"] as? JsonPrimitive)?.contentOrNull
            core.run(command("updateTask", "taskId" to id, "changes" to mapOf("dueDateTime" to due, "isAllDay" to false)))
        }
        onDueDateSet()
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
    ) = setCustomRepeat(
        com.gracefultools.astrid.model.RepeatPattern(
            unit = unit, interval = interval, weekdays = weekdays, endCondition = endCondition,
            endAfterOccurrences = endAfterOccurrences, endUntilDate = endUntilDate,
        ),
        repeatFrom = null,
    )

    /**
     * iOS's CustomRepeatingPatternEditor saved (AATD-50): the pattern for its unit — weekdays for
     * weeks, a date or a weekday-of-a-week for months, a month and day for years — and the Repeat
     * Mode as the task's `repeatFrom` (`DUE_DATE` / `COMPLETION_DATE`; null leaves it alone).
     * Numbers stay in the editor's 1–99 (a day 1–31, a month 1–12, a week 1–5).
     */
    fun setCustomRepeat(pattern: com.gracefultools.astrid.model.RepeatPattern, repeatFrom: String?) {
        val unit = pattern.unit ?: "days"
        val data = buildMap<String, Any?> {
            put("type", "custom")
            put("unit", unit)
            put("interval", (pattern.interval ?: 1).coerceIn(1, 99))
            when (unit) {
                "weeks" -> pattern.weekdays?.takeIf { it.isNotEmpty() }?.let { put("weekdays", it) }
                "months" -> when (pattern.monthRepeatType) {
                    "same_weekday" -> pattern.monthWeekday?.let { weekday ->
                        put("monthRepeatType", "same_weekday")
                        put("monthWeekday", mapOf("weekday" to weekday.weekday, "weekOfMonth" to weekday.weekOfMonth.coerceIn(1, 5)))
                    }
                    "same_date" -> {
                        put("monthRepeatType", "same_date")
                        put("monthDay", (pattern.monthDay ?: 1).coerceIn(1, 31))
                    }
                }
                "years" -> {
                    put("month", (pattern.month ?: 1).coerceIn(1, 12))
                    put("day", (pattern.day ?: 1).coerceIn(1, 31))
                }
            }
            val end = pattern.endCondition
            if (end != null) {
                put("endCondition", end)
                if (end == "after_occurrences") put("endAfterOccurrences", (pattern.endAfterOccurrences ?: 1).coerceIn(1, 99))
                if (end == "until_date" && pattern.endUntilDate != null) put("endUntilDate", pattern.endUntilDate)
            }
        }
        if (repeatFrom != null) update("repeating" to "custom", "repeatingData" to data, "repeatFrom" to repeatFrom)
        else update("repeating" to "custom", "repeatingData" to data)
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

    /**
     * Send the comment box: the text alone (`postComment`), or with the queued files — each one an
     * `attachFile`, the text riding the first, as iOS sends text and files together (AATD-53).
     */
    fun postComment(text: String, replyTo: String? = null) = work {
        val trimmed = text.trim()
        val files = _state.value.queued
        if (files.isNotEmpty()) {
            _state.update { it.copy(queued = emptyList(), suggestions = emptyList()) }
            files.forEachIndexed { index, file ->
                runCommand {
                    core.run(
                        command(
                            "attachFile", "taskId" to id, "path" to file.path, "name" to file.name, "mimeType" to file.mimeType,
                            *listOfNotNull(
                                trimmed.takeIf { index == 0 && it.isNotEmpty() }?.let { "content" to it },
                                replyTo?.takeIf { index == 0 }?.let { "parentCommentId" to it },
                            ).toTypedArray(),
                        ),
                    )
                }
            }
            load()
            return@work
        }
        if (trimmed.isEmpty()) return@work
        _state.update { it.copy(suggestions = emptyList()) }
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

    // ── Who: someone else (AATD-51) ────────────────────────────────────────────────────────────

    private var peopleSearch: kotlinx.coroutines.Job? = null

    /** "Invite by Email" suggestions: the server's people for this task, from two characters. */
    fun searchPeople(query: String) {
        peopleSearch?.cancel()
        val trimmed = query.trim()
        _state.update { it.copy(someoneElse = it.someoneElse.copy(error = null, needsList = false)) }
        if (trimmed.length < 2 || trimmed.contains('@')) {
            _state.update { it.copy(someoneElse = it.someoneElse.copy(suggestions = emptyList())) }
            return
        }
        peopleSearch = work {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            val found = runCatching {
                CoreJson.decodeFromJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(com.gracefultools.astrid.model.UserSummary.serializer()),
                    core.run(command("searchUsers", "query" to trimmed, "taskId" to id)) as? kotlinx.serialization.json.JsonArray
                        ?: kotlinx.serialization.json.JsonArray(emptyList()),
                )
            }.getOrDefault(emptyList())
            val people = found.filter { !it.email.isNullOrBlank() }.take(5)
            _state.update { it.copy(someoneElse = it.someoneElse.copy(suggestions = people)) }
        }
    }

    /**
     * iOS's assignByEmail: a task cannot be assigned to someone with no access to it, so they join
     * the task's list first (`inviteToList`), then the member is assigned. A task in no list says
     * so; a refusal is shown, because silently not assigning is the worst outcome here.
     */
    fun assignByEmail(email: String) = work {
        val address = email.trim()
        if (address.isEmpty()) return@work
        val listId = _state.value.detail?.listChips?.firstOrNull()?.id
        if (listId == null) {
            _state.update { it.copy(someoneElse = it.someoneElse.copy(needsList = true, error = null)) }
            return@work
        }
        _state.update { it.copy(someoneElse = it.someoneElse.copy(needsList = false, error = null)) }
        try {
            val answer = core.run(command("inviteToList", "listId" to listId, "email" to address, "role" to "member")) as? JsonObject
            val userId = ((answer?.get("member") as? JsonObject)?.get("userId") as? JsonPrimitive)?.contentOrNull
            if (userId != null) {
                runCommand { core.run(command("updateTask", "taskId" to id, "changes" to mapOf("assigneeId" to userId))) }
                load()
            }
            _state.update { it.copy(someoneElse = SomeoneElse(assigned = true)) }
        } catch (failure: CoreFailure) {
            _state.update { it.copy(someoneElse = it.someoneElse.copy(error = failure.message)) }
        }
    }

    /** The Who sheet opened or closed: start "Someone else" afresh. */
    fun resetSomeoneElse() {
        peopleSearch?.cancel()
        _state.update { it.copy(someoneElse = SomeoneElse()) }
    }

    // ── Attachments, project fields, timer (AATD-52) ───────────────────────────────────────────

    /** Every file on the task, from the cache — the Attachments strip. */
    fun loadAttachments() = work {
        runCatching { CoreJson.decodeFromJsonElement(com.gracefultools.astrid.model.Attachments.serializer(), core.run(command("attachments", "taskId" to id))) }
            .onSuccess { found -> _state.update { it.copy(attachments = found.files) } }
    }

    /** A GitHub board's field rows: the server's, or the cache's offline. */
    fun loadProjectFields() = work {
        runCatching {
            CoreJson.decodeFromJsonElement(com.gracefultools.astrid.model.TaskProjectFields.serializer(), core.run(command("taskProjectFields", "taskId" to id)))
        }.onSuccess { fields -> _state.update { it.copy(projectFields = fields.rows) } }
    }

    /** Set one field; `null` clears it. A refusal is kept beside the field, in iOS's words. */
    fun setProjectField(fieldId: String, value: kotlinx.serialization.json.JsonElement?) = work {
        _state.update { it.copy(projectFieldError = null) }
        try {
            val rows = CoreJson.decodeFromJsonElement(
                com.gracefultools.astrid.model.TaskProjectFields.serializer(),
                core.run(command("setTaskProjectField", "taskId" to id, "fieldId" to fieldId, "value" to (value ?: kotlinx.serialization.json.JsonNull))),
            )
            _state.update { it.copy(projectFields = rows.rows) }
        } catch (failure: CoreFailure) {
            if (failure.isStillPending) return@work
            val reason = when {
                failure.message.contains("sso_required") -> ProjectFieldError.Reason.SSO_REQUIRED
                failure.message.contains("auth_required") -> ProjectFieldError.Reason.AUTH_REQUIRED
                else -> ProjectFieldError.Reason.SAVE_FAILED
            }
            _state.update { it.copy(projectFieldError = ProjectFieldError(fieldId, reason)) }
        }
    }

    fun startTimer() = work {
        runCommand { core.run(command("startTimer", "taskId" to id)) }
        load()
    }

    /** Stop timing: the core records the session on the task and leaves its "Last:" caption. */
    fun stopTimer() = work {
        runCommand { core.run(command("stopTimer", "taskId" to id)) }
        load()
    }

    /** The timer's COMPLETE TASK: the session is recorded first, then the task completes. */
    fun completeFromTimer() = work {
        runCommand { core.run(command("stopTimer", "taskId" to id)) }
        runCommand { core.run(command("completeTask", "taskId" to id, "completed" to true, "source" to "android")) }
        load()
    }

    // ── The comment box (AATD-53) ──────────────────────────────────────────────────────────────

    private var suggesting: kotlinx.coroutines.Job? = null

    /** The text or caret moved: ask the core which popup rows the caret's trigger offers. */
    fun commentChanged(text: String, caret: Int) {
        suggesting?.cancel()
        suggesting = work {
            val found = runCatching {
                CoreJson.decodeFromJsonElement(
                    com.gracefultools.astrid.model.CommentSuggestions.serializer(),
                    core.run(command("commentSuggestions", "taskId" to id, "text" to text, "caret" to caret)),
                )
            }.getOrNull()
            _state.update { it.copy(suggestions = if (found?.trigger == null) emptyList() else found.items) }
        }
    }

    /** A row picked: the core writes it into the text the way the server reads it back. */
    fun applySuggestion(text: String, caret: Int, item: com.gracefultools.astrid.model.CommentSuggestion, onApplied: (String, Int) -> Unit) = work {
        suggesting?.cancel()
        _state.update { it.copy(suggestions = emptyList()) }
        runCatching {
            core.run(command("applyCommentSuggestion", "text" to text, "caret" to caret, "triggerKind" to item.kind, "id" to item.id, "label" to item.label)) as JsonObject
        }.getOrNull()?.let { answer ->
            val newText = (answer["text"] as? JsonPrimitive)?.contentOrNull ?: return@let
            val newCaret = (answer["caret"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: newText.length
            onApplied(newText, newCaret)
        }
    }

    fun dismissSuggestions() {
        suggesting?.cancel()
        _state.update { it.copy(suggestions = emptyList()) }
    }

    /** A picked file joins the next comment; the same file twice is one. */
    fun queueFile(path: String, name: String, mimeType: String?) = _state.update { state ->
        if (state.queued.any { it.path == path }) state else state.copy(queued = state.queued + QueuedFile(path, name, mimeType))
    }

    fun unqueueFile(path: String) = _state.update { state -> state.copy(queued = state.queued.filterNot { it.path == path }) }

    fun delete() = work {
        runCommand { core.run(command("deleteTask", "taskId" to id)) }
        _state.update { it.copy(deleted = true) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

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
