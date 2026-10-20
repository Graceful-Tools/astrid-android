package com.gracefultools.astrid.ui.board

import com.gracefultools.astrid.core.movesTasks
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
        work { core.changes.collect { if ((it.movesTasks || it.kind == ChangeKind.LIST) && _state.value.listId != null) reload() } }
    }

    fun open(listId: String) {
        if (_state.value.listId == listId) return
        _state.value = BoardState(listId = listId)
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
        try {
            core.run(command("moveTaskToColumn", "taskId" to taskId, "columnId" to columnId, "listId" to listId))
        } catch (failure: CoreFailure) {
            if (!failure.isStillPending) _state.update { it.copy(error = failure.message) }
        }
        reload()
    }

    /**
     * A card dropped at `index` in a column — the web's drop (dropBoardCard): the core writes the
     * status, the manual position, and whatever else a move into that column means for this board.
     */
    fun drop(taskId: String, columnId: String, index: Int) = work {
        val listId = _state.value.listId ?: return@work
        try {
            core.run(command("dropBoardCard", "taskId" to taskId, "columnId" to columnId, "listId" to listId, "index" to index.coerceAtLeast(0)))
        } catch (failure: CoreFailure) {
            if (!failure.isStillPending) _state.update { it.copy(error = failure.message) }
        }
        reload()
    }

    fun setCompleted(taskId: String, completed: Boolean) = work {
        runCatching { core.run(command("completeTask", "taskId" to taskId, "completed" to completed, "source" to "android")) }
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

}
