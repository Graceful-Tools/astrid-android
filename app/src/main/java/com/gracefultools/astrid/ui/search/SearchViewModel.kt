package com.gracefultools.astrid.ui.search

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class SearchState(val query: String = "", val rows: List<TaskRow> = emptyList(), val searched: Boolean = false)

/**
 * Search over the cache with the web's grammar (`assignee:me priority:high due:week AST-142`) — the
 * core parses and matches; there is no server search.
 */
class SearchViewModel(
    private val core: Core,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val debounceMs: Long = 150,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var pending: Job? = null

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
        pending?.cancel()
        if (query.isBlank()) { _state.value = SearchState(query = query); return }
        pending = work {
            if (debounceMs > 0) delay(debounceMs)
            val answer = runCatching { core.run(command("searchTasks", "query" to query.trim(), "limit" to 100)) }.getOrNull()
            if (_state.value.query == query) _state.value = _state.value.copy(rows = rowsOf(answer), searched = true)
        }
    }

    companion object {
        /** Search answers a window of rows, or a bare array of them. */
        fun rowsOf(answer: JsonElement?): List<TaskRow> {
            val array = when (answer) {
                is JsonArray -> answer
                is JsonObject -> answer["rows"] as? JsonArray
                else -> null
            } ?: return emptyList()
            return runCatching { CoreJson.decodeFromJsonElement(ListSerializer(TaskRow.serializer()), array) }.getOrDefault(emptyList())
        }
    }
}
