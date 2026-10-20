package com.gracefultools.astrid.ui.sidebar

import com.gracefultools.astrid.core.movesLists
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.ListSummary
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer

data class SidebarState(
    val myTasks: ListSummary? = null,
    val favorites: List<ListSummary> = emptyList(),
    val lists: List<ListSummary> = emptyList(),
    val selectedId: String? = null,
    val error: String? = null,
) {
    val selected: ListSummary? get() = (listOfNotNull(myTasks) + favorites + lists).firstOrNull { it.id == selectedId }
}

/** A board column is a state and a label is a tag: neither is somewhere to go (web's list flavours). */
fun ListSummary.isPlace(): Boolean = listType != "status" && listType != "label"

/**
 * The sidebar: My Tasks first and selected by default, then favourites in their saved order, then
 * every other list by name — iOS's sidebar and astrid-windows' SidebarViewModel.
 */
class SidebarViewModel(
    private val core: Core,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(SidebarState())
    val state: StateFlow<SidebarState> = _state.asStateFlow()

    init {
        work { core.changes.collect { if (it.movesLists) load() } }
    }

    fun load() = work { reload() }

    private suspend fun reload() {
        try {
            val all = CoreJson.decodeFromJsonElement(ListSerializer(ListSummary.serializer()), core.run(command("lists")))
                .filter { it.isPlace() }
            val mine = runCatching {
                CoreJson.decodeFromJsonElement(ListSummary.serializer(), core.run(command("myTasksList")))
            }.getOrNull()
            val byName = compareBy<ListSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            val favorites = all.filter { it.isFavorite == true }
                .sortedWith(compareBy<ListSummary> { it.favoriteOrder ?: Int.MAX_VALUE }.then(byName))
            val others = all.filter { it.isFavorite != true }.sortedWith(byName)
            _state.update { old ->
                val everything = listOfNotNull(mine) + favorites + others
                SidebarState(
                    myTasks = mine, favorites = favorites, lists = others,
                    selectedId = old.selectedId?.takeIf { id -> everything.any { it.id == id } }
                        ?: everything.firstOrNull()?.id,
                )
            }
        } catch (failure: CoreFailure) {
            _state.update { it.copy(error = if (failure.isStillPending) null else failure.message) }
        }
    }

    fun select(id: String) = _state.update { it.copy(selectedId = id) }

    /** A new private list, opened once made — iOS's "Add List". */
    fun create(name: String) = work {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@work
        try {
            val made = CoreJson.decodeFromJsonElement(ListSummary.serializer(), core.run(command("createList", "name" to trimmed)))
            reload()
            _state.update { it.copy(selectedId = made.id.ifEmpty { it.selectedId }) }
        } catch (failure: CoreFailure) {
            _state.update { it.copy(error = if (failure.isStillPending) null else failure.message) }
        }
    }
}
