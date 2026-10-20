package com.gracefultools.astrid.ui.tasks

import com.gracefultools.astrid.core.movesTasks
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.RowWindow
import com.gracefultools.astrid.model.TaskRow
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
import java.util.Locale

data class TaskListState(
    val listId: String? = null,
    /** A filter (My Tasks, Today), not a place: a task made there goes into no list. */
    val isVirtual: Boolean = false,
    val rows: List<TaskRow> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** A refusal to show. Never set for an offline write — that is still going to happen. */
    val error: String? = null,
    /** Open when a row's leading control asked for the status picker. */
    val statusPicker: com.gracefultools.astrid.ui.common.LeadingOutcome.PickStatus? = null,
    /** What the quick-add bar's leading control shows and its picker offers (`quickAddDefaults`). */
    val quickAdd: QuickAddDefaults = QuickAddDefaults(),
    /** A featured public list the reader is not in (AATD-46): Copy List in the header, no rotator. */
    val featured: Boolean = false,
    /** iOS isCopyingList: the Copy List button's spinner. */
    val copyingList: Boolean = false,
    /** iOS lists.copy_failed: "Couldn't copy that list. Please try again." */
    val copyListFailed: Boolean = false,
)

/** The core's answer for a new task's starting priority and assignee, and who may be chosen. */
@kotlinx.serialization.Serializable
data class QuickAddDefaults(
    val priority: Int = 0,
    val assigneeId: String? = null,
    val leading: com.gracefultools.astrid.model.LeadingControl = com.gracefultools.astrid.model.LeadingControl("checkbox"),
    val options: List<com.gracefultools.astrid.model.AssigneeOption> = emptyList(),
)

/**
 * One list's rows. The core filters, sorts, splices subtasks and projects each row; this holds
 * the window and dispatches what the person did.
 */
class TaskListViewModel(
    private val core: Core,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val locale: () -> String = { Locale.getDefault().toLanguageTag() },
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(TaskListState())
    val state: StateFlow<TaskListState> = _state.asStateFlow()
    private var generation = 0

    init {
        // A colleague's edit, a sync pass, a write landing: re-read what is on screen.
        work { core.changes.collect { if (it.movesTasks && _state.value.listId != null) reload() } }
    }

    /**
     * Open a list. [featured] is a public list the reader is not in, which the cache does not hold:
     * it travels with every `rowsForList` as the core's `list` input (iOS's featured mode).
     */
    fun open(listId: String, isVirtual: Boolean = false, featured: com.gracefultools.astrid.ui.publiclists.PublicListSummary? = null) {
        if (_state.value.listId == listId && (featured != null) == _state.value.featured) return
        generation++
        chosenPriority = null
        chosenAssignee = null
        featuredList = featured?.asTaskList()
        _state.value = TaskListState(listId = listId, isVirtual = isVirtual, loading = true, featured = featured != null)
        reload()
        loadQuickAdd()
    }

    private var featuredList: JsonObject? = null

    /** `rowsForList` for the open list, a featured one carrying its own shape. */
    private fun rowsCommand(listId: String, offset: Int, limit: Int) = command(
        "rowsForList", "listId" to listId, "offset" to offset, "limit" to limit,
        *listOfNotNull(featuredList?.let { "list" to it }).toTypedArray(),
    )

    // What the person picked in the quick-add picker; null = not said, so the list's default stands.
    private var chosenPriority: Int? = null
    private var chosenAssignee: String? = null

    /** The quick-add control's face and options, from the core: the list's defaults under what was chosen. */
    fun loadQuickAdd() = work {
        val listId = _state.value.listId
        val answer = runCatching {
            CoreJson.decodeFromJsonElement(
                QuickAddDefaults.serializer(),
                core.run(
                    command(
                        "quickAddDefaults",
                        *listOfNotNull(
                            listId?.let { "listId" to it },
                            chosenAssignee?.let { "assigneeId" to it },
                            chosenPriority?.let { "priority" to it },
                        ).toTypedArray(),
                    ),
                ),
            )
        }.getOrNull() ?: return@work
        _state.update { it.copy(quickAdd = answer) }
    }

    /** iOS's QuickAddPickerSheet: a priority for the next task. */
    fun choosePriority(priority: Int) { chosenPriority = priority; loadQuickAdd() }

    /** …and an assignee; null is "Unassigned", a choice the core spells `unassigned`. */
    fun chooseAssignee(userId: String?) { chosenAssignee = userId ?: "unassigned"; loadQuickAdd() }

    fun reload() = work {
        val listId = _state.value.listId ?: return@work
        val mine = generation
        val wanted = maxOf(PAGE, _state.value.rows.size)
        try {
            val window = CoreJson.decodeFromJsonElement(
                RowWindow.serializer(),
                core.run(rowsCommand(listId, 0, wanted)),
            )
            if (mine != generation) return@work
            _state.update { it.copy(rows = window.rows, total = window.total, loading = false) }
        } catch (failure: CoreFailure) {
            if (mine == generation) _state.update { it.copy(loading = false, error = failure.shown()) }
        }
    }

    /** The next page, when the list is scrolled near its end. */
    fun loadMore() = work {
        val state = _state.value
        val listId = state.listId ?: return@work
        if (state.rows.size >= state.total) return@work
        val mine = generation
        runCatching {
            CoreJson.decodeFromJsonElement(
                RowWindow.serializer(),
                core.run(rowsCommand(listId, state.rows.size, PAGE)),
            )
        }.onSuccess { window ->
            if (mine == generation) _state.update { it.copy(rows = it.rows + window.rows, total = window.total) }
        }
    }

    /** Pull to refresh: a sync pass, then the rows again. */
    fun refresh() = work {
        _state.update { it.copy(refreshing = true) }
        runCatching { core.run(command("sync")) }
        _state.update { it.copy(refreshing = false) }
        reload()
    }

    /**
     * Complete or reopen a row — ONLY through `completeTask`, which owns repeat rollover (ASTRID.md
     * rule 3). The row flips on the tap and flips back only if the core refuses.
     */
    fun setCompleted(taskId: String, completed: Boolean) = work {
        flip(taskId, completed)
        try {
            core.run(command("completeTask", "taskId" to taskId, "completed" to completed, "source" to "android"))
        } catch (failure: CoreFailure) {
            if (failure.isStillPending) return@work
            flip(taskId, !completed)
            _state.update { it.copy(error = failure.message) }
        }
        reload()
    }

    /**
     * Add a task from the quick-add bar. `quickAdd` lets the core read `#list`, dates and priority
     * out of the words, as the account's smart parsing says; the reader's language decides which
     * words those are. Answers the new task's id, or null when none was made.
     */
    suspend fun create(title: String): String? {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return null
        val state = _state.value
        val listIds = listOfNotNull(state.listId?.takeUnless { state.isVirtual })
        // In a real list the core applies that list's defaults itself. A virtual list has no row
        // for it to read them from, so the core's own quick-add answer for it is forwarded — in
        // My Tasks that is the reader, without whom the new task would leave the list it was
        // added to (D25: My Tasks is only what is assigned to you). A choice made in the picker
        // is sent as said; an unchosen priority is left to the words ("!!") and the list.
        val assigneeId = chosenAssignee ?: if (state.isVirtual && state.listId != null) {
            runCatching {
                ((core.run(command("quickAddDefaults", "listId" to state.listId)) as? JsonObject)?.get("assigneeId") as? JsonPrimitive)?.contentOrNull
            }.getOrNull()
        } else null
        return try {
            val made = core.run(
                command(
                    "createTask", "title" to trimmed, "listIds" to listIds, "quickAdd" to true, "locale" to locale(),
                    *listOfNotNull(assigneeId?.let { "assigneeId" to it }, chosenPriority?.let { "priority" to it }).toTypedArray(),
                ),
            )
            reload()
            ((made as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull ?: ""
        } catch (failure: CoreFailure) {
            if (failure.isStillPending) { reload(); return "" }
            _state.update { it.copy(error = failure.message) }
            null
        }
    }

    fun delete(taskId: String) = work {
        _state.update { s -> s.copy(rows = s.rows.filterNot { it.id == taskId }, total = maxOf(0, s.total - 1)) }
        try {
            core.run(command("deleteTask", "taskId" to taskId))
        } catch (failure: CoreFailure) {
            if (!failure.isStillPending) _state.update { it.copy(error = failure.message) }
        }
        reload()
    }

    private val rowActions = com.gracefultools.astrid.ui.common.RowActions(core)

    /** A tap on a row's leading control — whatever the core says that control does. */
    fun leading(row: com.gracefultools.astrid.model.TaskRow) = work {
        when (val outcome = rowActions.leading(row)) {
            is com.gracefultools.astrid.ui.common.LeadingOutcome.PickStatus -> _state.update { it.copy(statusPicker = outcome) }
            is com.gracefultools.astrid.ui.common.LeadingOutcome.Refused -> _state.update { it.copy(error = outcome.message) }
            com.gracefultools.astrid.ui.common.LeadingOutcome.Done -> reload()
        }
    }

    fun pickStatus(columnId: String) = work {
        val picker = _state.value.statusPicker ?: return@work
        _state.update { it.copy(statusPicker = null) }
        rowActions.setStatus(picker.taskId, columnId)?.let { message -> _state.update { it.copy(error = message) } }
        reload()
    }

    fun dismissStatusPicker() = _state.update { it.copy(statusPicker = null) }

    fun dismissError() = _state.update { it.copy(error = null, copyListFailed = false) }

    /**
     * iOS's Copy List (TaskListView.copyList): the featured list, with its tasks, into this account
     * through the core's `copyList`. Answers whether it was copied, so the sidebar can re-read.
     */
    suspend fun copyList(): Boolean {
        val state = _state.value
        val listId = state.listId?.takeIf { state.featured } ?: return false
        if (state.copyingList) return false
        _state.update { it.copy(copyingList = true, copyListFailed = false) }
        val copied = try { core.run(command("copyList", "listId" to listId)); true } catch (_: CoreFailure) { false }
        _state.update { it.copy(copyingList = false, copyListFailed = !copied) }
        return copied
    }

    private fun flip(taskId: String, completed: Boolean) =
        _state.update { s -> s.copy(rows = s.rows.map { if (it.id == taskId) it.copy(completed = completed) else it }) }

    private fun CoreFailure.shown(): String? = if (isStillPending) null else message

    companion object {
        const val PAGE = 200
    }
}

/** The title the core answered a created task with — what the parser left after reading tags. */
internal fun createdTitle(answer: kotlinx.serialization.json.JsonElement): String? =
    ((answer as? JsonObject)?.get("title") as? JsonPrimitive)?.contentOrNull
