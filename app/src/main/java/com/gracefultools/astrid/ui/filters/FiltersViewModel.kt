package com.gracefultools.astrid.ui.filters

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable
data class FilterPick(val field: String = "", val value: String = "", val titleKey: String = "", val isSelected: Boolean = false)

@Serializable
data class FilterGroup(val field: String = "", val titleKey: String = "", val picks: List<FilterPick> = emptyList())

@Serializable
data class FilterOptions(val listId: String = "", val isFiltered: Boolean = false, val groups: List<FilterGroup> = emptyList())

data class FiltersState(
    val groups: List<FilterGroup> = emptyList(),
    val isFiltered: Boolean = false,
    val error: String? = null,
    /** A list's own switches (not My Tasks'): subtasks inline, and a saved filter. */
    val showSubtasks: Boolean = true,
    val isSavedFilter: Boolean = false,
)

/**
 * iOS's ListSortFiltersTab and MyTasksFilterSheet, one model for both (AATD-41): the core answers
 * the groups and their choices in iOS's order, for a list or for My Tasks (`virtual:my-tasks`), and
 * writes a pick wherever that filter lives (`setFilter`).
 */
class FiltersViewModel(
    private val core: Core,
    private val listId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(FiltersState())
    val state: StateFlow<FiltersState> = _state.asStateFlow()

    init { load() }

    fun load() = work {
        runCatching { CoreJson.decodeFromJsonElement(FilterOptions.serializer(), core.run(command("filterOptions", "listId" to listId))) }
            .onSuccess { options -> _state.update { it.copy(groups = options.groups, isFiltered = options.isFiltered) } }
        if (listId != MY_TASKS) runCatching { core.run(command("list", "listId" to listId)) as? kotlinx.serialization.json.JsonObject }.getOrNull()?.let { list ->
            fun flag(name: String) = (list[name] as? kotlinx.serialization.json.JsonPrimitive)?.content
            _state.update { it.copy(showSubtasks = flag("showSubtasks") != "false", isSavedFilter = flag("isVirtual") == "true") }
        }
    }

    /** iOS's "Show subtasks" for this list (updateList showSubtasks). */
    fun setShowSubtasks(on: Boolean) = change("showSubtasks" to on)

    /** iOS's "Saved Filter": the list shows what its filters keep, not its members (isVirtual). */
    fun setSavedFilter(on: Boolean) = change("isVirtual" to on)

    private fun change(vararg changes: Pair<String, Any?>) = work {
        try {
            core.run(command("updateList", "listId" to listId, "changes" to changes.toMap()))
        } catch (failure: CoreFailure) {
            if (!failure.isStillPending) _state.update { it.copy(error = failure.message) }
        }
        load()
    }

    private companion object { const val MY_TASKS = "virtual:my-tasks" }

    fun pick(field: String, value: String) = work {
        set(field, value)
        load()
    }

    /** iOS's "Clear All Filters": every filter back to its first choice ("any"); the sort stays. */
    fun clearAll() = work {
        for (group in _state.value.groups) {
            if (group.field == "sortBy") continue
            val first = group.picks.firstOrNull() ?: continue
            if (!first.isSelected) set(group.field, first.value)
        }
        load()
    }

    private suspend fun set(field: String, value: String) {
        try {
            core.run(command("setFilter", "listId" to listId, "field" to field, "value" to value))
        } catch (failure: CoreFailure) {
            if (!failure.isStillPending) _state.update { it.copy(error = failure.message) }
        }
    }
}
