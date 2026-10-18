package com.gracefultools.astrid.ui.tasks

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
        work { core.changes.collect { if (it.kind in RELOAD_ON && _state.value.listId != null) reload() } }
    }

    fun open(listId: String, isVirtual: Boolean = false) {
        if (_state.value.listId == listId) return
        generation++
        _state.value = TaskListState(listId = listId, isVirtual = isVirtual, loading = true)
        reload()
    }

    fun reload() = work {
        val listId = _state.value.listId ?: return@work
        val mine = generation
        val wanted = maxOf(PAGE, _state.value.rows.size)
        try {
            val window = CoreJson.decodeFromJsonElement(
                RowWindow.serializer(),
                core.run(command("rowsForList", "listId" to listId, "offset" to 0, "limit" to wanted)),
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
                core.run(command("rowsForList", "listId" to listId, "offset" to state.rows.size, "limit" to PAGE)),
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
     * words those are. Answers whether a task was made.
     */
    suspend fun create(title: String): Boolean {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return false
        val state = _state.value
        val listIds = listOfNotNull(state.listId?.takeUnless { state.isVirtual })
        // In a real list the core applies that list's defaults itself. A virtual list has no row
        // for it to read them from, so the core's own quick-add answer for it is forwarded — in
        // My Tasks that is the reader, without whom the new task would leave the list it was
        // added to (D25: My Tasks is only what is assigned to you).
        val assigneeId = if (state.isVirtual && state.listId != null) {
            runCatching {
                ((core.run(command("quickAddDefaults", "listId" to state.listId)) as? JsonObject)?.get("assigneeId") as? JsonPrimitive)?.contentOrNull
            }.getOrNull()
        } else null
        return try {
            core.run(
                command(
                    "createTask", "title" to trimmed, "listIds" to listIds, "quickAdd" to true, "locale" to locale(),
                    *listOfNotNull(assigneeId?.let { "assigneeId" to it }).toTypedArray(),
                ),
            )
            reload()
            true
        } catch (failure: CoreFailure) {
            if (failure.isStillPending) { reload(); return true }
            _state.update { it.copy(error = failure.message) }
            false
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

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun flip(taskId: String, completed: Boolean) =
        _state.update { s -> s.copy(rows = s.rows.map { if (it.id == taskId) it.copy(completed = completed) else it }) }

    private fun CoreFailure.shown(): String? = if (isStillPending) null else message

    companion object {
        const val PAGE = 200
        private val RELOAD_ON = setOf("task", "tasks", "synced", "list", "taskDeleted", "outbox")
    }
}

/** The title the core answered a created task with — what the parser left after reading tags. */
internal fun createdTitle(answer: kotlinx.serialization.json.JsonElement): String? =
    ((answer as? JsonObject)?.get("title") as? JsonPrimitive)?.contentOrNull
