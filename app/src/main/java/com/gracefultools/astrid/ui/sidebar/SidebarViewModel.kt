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
    /** Task counts by list id, the core's `listCounts` — My Tasks included (AITD-460). */
    val counts: Map<String, Int> = emptyMap(),
    /** The signed-in person, for the account card at the foot (iOS SidebarAccountEntry). */
    val user: com.gracefultools.astrid.model.UserSummary? = null,
    /** Where each list's picture comes from (`listImage`), by id. */
    val images: Map<String, String> = emptyMap(),
    /** iOS ListImagesVisibility: pictures, or a `#` in the list's colour (core `features`). */
    val showListImages: Boolean = true,
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
            val raw = core.run(command("lists")) as? kotlinx.serialization.json.JsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            val all = CoreJson.decodeFromJsonElement(ListSerializer(ListSummary.serializer()), raw)
                .filter { it.isPlace() }
            val mineRaw = runCatching { core.run(command("myTasksList")) }.getOrNull()
            val mine = mineRaw?.let { runCatching { CoreJson.decodeFromJsonElement(ListSummary.serializer(), it) }.getOrNull() }
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
            loadDecorations(raw, mineRaw, all)
        } catch (failure: CoreFailure) {
            _state.update { it.copy(error = if (failure.isStillPending) null else failure.message) }
        }
    }

    /** Counts, the reader and list pictures: best effort, each on its own, after the lists draw. */
    private suspend fun loadDecorations(
        raw: kotlinx.serialization.json.JsonArray,
        mine: kotlinx.serialization.json.JsonElement?,
        lists: List<ListSummary>,
    ) {
        val myTasksId = (mine as? kotlinx.serialization.json.JsonObject)?.get("id")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        val counts = runCatching {
            // iOS's ListTaskCount, from the core: open tasks per list, My Tasks the reader's own.
            (core.run(command("listBadges", "lists" to raw, "myTasksId" to myTasksId)) as? kotlinx.serialization.json.JsonObject)
                ?.mapNotNull { (id, n) -> (n as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()?.let { id to it } }
                ?.toMap()
        }.getOrNull().orEmpty()
        val user = runCatching {
            CoreJson.decodeFromJsonElement(com.gracefultools.astrid.model.UserSummary.serializer(), core.run(command("currentUser")))
        }.getOrNull()
        val showImages = runCatching {
            ((core.run(command("features")) as? kotlinx.serialization.json.JsonObject)?.get("showListImages") as? kotlinx.serialization.json.JsonPrimitive)?.content != "false"
        }.getOrDefault(true)
        _state.update { it.copy(counts = counts, user = user ?: it.user, showListImages = showImages) }
        val images = lists.associate { list ->
            list.id to runCatching {
                ((core.run(command("listImage", "listId" to list.id)) as? kotlinx.serialization.json.JsonObject)
                    ?.get("source") as? kotlinx.serialization.json.JsonPrimitive)?.content
            }.getOrNull()
        }.mapNotNull { (id, source) -> source?.let { id to it } }.toMap()
        _state.update { it.copy(images = images) }
    }

    /** iOS's long press on a list row: favourite or unfavourite it. */
    fun toggleFavorite(list: ListSummary) = work {
        try {
            core.run(command("setListFavorite", "listId" to list.id, "favorite" to (list.isFavorite != true)))
        } catch (failure: CoreFailure) {
            if (!failure.isStillPending) _state.update { it.copy(error = failure.message) }
        }
        reload()
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
