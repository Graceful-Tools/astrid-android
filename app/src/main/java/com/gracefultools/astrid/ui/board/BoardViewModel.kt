package com.gracefultools.astrid.ui.board

import com.gracefultools.astrid.core.touchesList
import com.gracefultools.astrid.core.ChangeKind
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.Board
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BoardState(
    val listId: String? = null,
    /** A filter, not a stored list: it is in no task's `listIds`, so every task change re-reads it. */
    val isVirtual: Boolean = false,
    val board: Board? = null,
    val error: String? = null,
    val statusPicker: com.gracefultools.astrid.ui.common.LeadingOutcome.PickStatus? = null,
)

/**
 * A project list as a board. Which card is in which column, in what order, and what a move writes
 * are the core's board contract (fixture-locked to web); this draws and dispatches.
 */
class BoardViewModel(private val core: Core, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(BoardState())
    val state: StateFlow<BoardState> = _state.asStateFlow()

    init {
        // A task change that names only other lists skips the read (AATD-113).
        work {
            core.changes.collect { change ->
                val open = _state.value
                val listId = open.listId ?: return@collect
                if (change.kind == ChangeKind.LIST || change.touchesList(listId, open.isVirtual)) reload()
            }
        }
    }

    fun open(listId: String, isVirtual: Boolean = false) {
        if (_state.value.listId == listId) return
        _state.value = BoardState(listId = listId, isVirtual = isVirtual)
        reload()
    }

    fun reload() = work {
        val listId = _state.value.listId ?: return@work
        try {
            val board = CoreJson.decodeFromJsonElement(Board.serializer(), core.run(command("board", "listId" to listId)))
            _state.update { it.copy(board = board) }
        } catch (failure: CoreFailure) {
            if (!failure.isStillPending) _state.update { it.copy(error = failure.message) }
        }
    }

    /** "Move to…": the core writes what a move to that column means for this board. */
    fun moveTo(taskId: String, columnId: String) = work {
        val listId = _state.value.listId ?: return@work
        // Into Waiting with nothing to wait on, the core refuses and `waiting` asks (AATD-98).
        waiting.move(taskId) { wait ->
            core.run(command("moveTaskToColumn", "taskId" to taskId, "columnId" to columnId, "listId" to listId, "dueDateTime" to wait?.dueDateTime, "isAllDay" to wait?.isAllDay, "waitOnTaskId" to wait?.waitOnTaskId, "keepAssignee" to (wait?.keepAssignee == true)))
        }?.let { message -> _state.update { it.copy(error = message) } }
        reload()
    }

    /**
     * A card dropped at `index` in a column — the web's drop (dropBoardCard): the core writes the
     * status, the manual position, and whatever else a move into that column means for this board.
     */
    fun drop(taskId: String, columnId: String, index: Int) = work {
        val listId = _state.value.listId ?: return@work
        waiting.move(taskId) { wait ->
            core.run(
                command(
                    "dropBoardCard", "taskId" to taskId, "columnId" to columnId, "listId" to listId, "index" to index.coerceAtLeast(0),
                    "dueDateTime" to wait?.dueDateTime, "isAllDay" to wait?.isAllDay, "waitOnTaskId" to wait?.waitOnTaskId, "keepAssignee" to (wait?.keepAssignee == true),
                ),
            )
        }?.let { message -> _state.update { it.copy(error = message) } }
        reload()
    }

    fun setCompleted(taskId: String, completed: Boolean) = work {
        runCatching { core.run(command("completeTask", "taskId" to taskId, "completed" to completed, "source" to "android")) }
        reload()
    }

    /** AATD-98: asks what a task moved into Waiting waits for. */
    val waiting = com.gracefultools.astrid.ui.common.WaitingMoves(core) { reload() }

    private val rowActions = com.gracefultools.astrid.ui.common.RowActions(core, waiting)

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

    /** The picker's "Mark as done" / "Mark as to-do" row (ADEV-150): completes or reopens the row it opened from. */
    fun completeFromPicker() = work {
        val picker = _state.value.statusPicker ?: return@work
        _state.update { it.copy(statusPicker = null) }
        (rowActions.complete(picker.row) as? com.gracefultools.astrid.ui.common.LeadingOutcome.Refused)
            ?.let { refused -> _state.update { it.copy(error = refused.message) } }
        reload()
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** iOS's quick add at the foot of a column: a card made straight into that column (addBoardCard). */
    suspend fun addCard(columnId: String, title: String): Boolean {
        val listId = _state.value.listId ?: return false
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return false
        return try {
            core.run(command("addBoardCard", "listId" to listId, "columnId" to columnId, "title" to trimmed))
            reload()
            true
        } catch (failure: com.gracefultools.astrid.core.CoreFailure) {
            if (failure.isStillPending) { reload(); true } else { _state.update { it.copy(error = failure.message) }; false }
        }
    }

}
