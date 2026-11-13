package com.gracefultools.astrid.ui.sidebar

import com.gracefultools.astrid.core.movesLists
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.ListSummary
import com.gracefultools.astrid.ui.common.CoreViewModel
import com.gracefultools.astrid.ui.publiclists.publicListPictures
import com.gracefultools.astrid.ui.publiclists.publicLists
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

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
    /** The core's `publicLists`: the sidebar's two public sections (iOS ListSidebarView 349-429). */
    val publicLists: List<com.gracefultools.astrid.ui.publiclists.PublicListSummary> = emptyList(),
    /** A public list being viewed from the featured sections or the browser (iOS isViewingFromFeatured). */
    val featured: com.gracefultools.astrid.ui.publiclists.PublicListSummary? = null,
) {
    val selected: ListSummary? get() = (listOfNotNull(myTasks) + favorites + lists).firstOrNull { it.id == selectedId }
        ?: featured?.takeIf { it.id == selectedId }?.asListSummary()

    /** Whether the open list is a featured public one: read-only rows, Copy List, no rotator. */
    val isViewingFeatured: Boolean get() = featured != null && featured.id == selectedId

    val collaborativeLists get() = com.gracefultools.astrid.ui.publiclists.PublicListSections.collaborative(publicLists)
    val suggestedLists get() = com.gracefultools.astrid.ui.publiclists.PublicListSections.browsable(publicLists)
}

/**
 * The sidebar: My Tasks first and selected by default, then favourites, then every other place —
 * iOS's sidebar. Which lists are places, their order and their badges are the core's `sidebar`
 * command (astrid-rules `rows::sidebar`), never decided here (AATD-89).
 */
/** The core's `sidebar` answer (astrid-rules `rows::sidebar::Sections`). */
@Serializable
data class SidebarSections(
    val myTasks: ListSummary? = null,
    val favorites: List<ListSummary> = emptyList(),
    val lists: List<ListSummary> = emptyList(),
)

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
            val sections = CoreJson.decodeFromJsonElement(SidebarSections.serializer(), core.run(command("sidebar")))
            val mine = sections.myTasks
            val favorites = sections.favorites
            val others = sections.lists
            _state.update { old ->
                val everything = listOfNotNull(mine) + favorites + others
                SidebarState(
                    myTasks = mine, favorites = favorites, lists = others,
                    selectedId = old.selectedId?.takeIf { id -> everything.any { it.id == id } || old.featured?.id == id }
                        ?: everything.firstOrNull()?.id,
                    publicLists = old.publicLists,
                    featured = old.featured,
                    // iOS's ListTaskCount: the badge the core put on each entry, My Tasks the reader's own.
                    counts = (listOfNotNull(mine) + favorites + others)
                        .mapNotNull { list -> list.taskCount?.let { list.id to it } }.toMap(),
                    user = old.user, images = old.images, showListImages = old.showListImages,
                )
            }
            loadDecorations(favorites + others)
        } catch (failure: CoreFailure) {
            _state.update { it.copy(error = if (failure.isStillPending) null else failure.message) }
        }
    }

    /** The reader and list pictures: best effort, each on its own, after the lists draw. */
    private suspend fun loadDecorations(lists: List<ListSummary>) {
        val user = runCatching {
            CoreJson.decodeFromJsonElement(com.gracefultools.astrid.model.UserSummary.serializer(), core.run(command("currentUser")))
        }.getOrNull()
        val showImages = runCatching {
            ((core.run(command("features")) as? kotlinx.serialization.json.JsonObject)?.get("showListImages") as? kotlinx.serialization.json.JsonPrimitive)?.content != "false"
        }.getOrDefault(true)
        _state.update { it.copy(user = user ?: it.user, showListImages = showImages) }
        val images = lists.associate { list ->
            list.id to runCatching {
                ((core.run(command("listImage", "listId" to list.id)) as? kotlinx.serialization.json.JsonObject)
                    ?.get("source") as? kotlinx.serialization.json.JsonPrimitive)?.content
            }.getOrNull()
        }.mapNotNull { (id, source) -> source?.let { id to it } }.toMap()
        _state.update { it.copy(images = images) }
        // Online-only: offline, the public sections stay as they were (iOS shows none).
        val public = runCatching { core.publicLists() }.getOrNull() ?: return
        _state.update { it.copy(publicLists = public) }
        val publicImages = core.publicListPictures(public)
        _state.update { it.copy(images = it.images + publicImages) }
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

    fun select(id: String) = _state.update { it.copy(selectedId = id, featured = it.featured?.takeIf { f -> f.id == id }) }

    /** Open a public list from the featured sections or the browser (iOS isViewingFromFeatured). */
    fun selectFeatured(list: com.gracefultools.astrid.ui.publiclists.PublicListSummary) =
        _state.update { it.copy(selectedId = list.id, featured = list) }

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
