package com.gracefultools.astrid.ui.publiclists

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** Who a public list belongs to (core `PublicListOwner`). */
@Serializable
data class PublicListOwner(val id: String = "", val name: String? = null, val email: String? = null)

/**
 * One list of the core's `publicLists` answer (core `services::list::PublicListSummary`): the
 * catalogue anybody may browse and copy, most copied first.
 *
 * `publicListType` is read when the answer carries it. The pinned core does not send it yet (see
 * the report for AATD-46), so today every list reads as browse-only — iOS's own safe guess.
 */
@Serializable
data class PublicListSummary(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val color: String? = null,
    val imageUrl: String? = null,
    val owner: PublicListOwner? = null,
    val taskCount: Int = 0,
    val memberCount: Int = 0,
    val publicListType: String? = null,
) {
    val isCollaborative: Boolean get() = publicListType == PublicListSections.COLLABORATIVE_TYPE

    /** As the sidebar draws a list: public, with the server's count. */
    fun asListSummary(): ListSummary =
        ListSummary(id = id, name = name, color = color, imageUrl = imageUrl, taskCount = taskCount, privacy = "PUBLIC")

    /**
     * The list in the core's TaskList shape, for `rowsForList`'s `list` input — a public list the
     * reader is not in is not in the cache (core `RowsInputs.list`). iOS's TaskList(publicList:).
     */
    fun asTaskList(): JsonObject = buildJsonObject {
        CoreJson.encodeToJsonElement(serializer(), this@PublicListSummary).jsonObject.forEach { (k, v) -> put(k, v) }
        put("privacy", JsonPrimitive("PUBLIC"))
        owner?.id?.takeIf { it.isNotEmpty() }?.let { put("ownerId", JsonPrimitive(it)) }
    }
}

/**
 * iOS PublicListSections (Task dfb037c7): collaborative lists — which you can contribute to — and
 * the browse-only rest. Anything but "collaborative", including no type at all, is browse-only,
 * because that is the safe direction to guess. A sidebar section shows two, then "See all".
 */
object PublicListSections {
    const val COLLABORATIVE_TYPE = "collaborative"
    const val SIDEBAR_LIMIT = 2

    fun collaborative(lists: List<PublicListSummary>) = lists.filter { it.isCollaborative }
    fun browsable(lists: List<PublicListSummary>) = lists.filterNot { it.isCollaborative }
    fun sidebarRows(section: List<PublicListSummary>) = section.take(SIDEBAR_LIMIT)
    /** Exactly the limit is not "more". */
    fun hasMore(section: List<PublicListSummary>) = section.size > SIDEBAR_LIMIT
}

/** The core's `publicLists`, read. Online-only: a failure is simply no catalogue. */
suspend fun Core.publicLists(): List<PublicListSummary> {
    val raw = run(command("publicLists")) as? JsonArray ?: return emptyList()
    return CoreJson.decodeFromJsonElement(ListSerializer(PublicListSummary.serializer()), raw)
}

/**
 * Where each public list's picture can be drawn from, by id. A public list is not in the store, but
 * its picture is a secure file all the same: the core fetches it by address with the session
 * (core `listImage` with `imageUrl`, AATD-39). One that will not come is simply left out.
 */
suspend fun Core.publicListPictures(lists: List<PublicListSummary>): Map<String, String> = lists.mapNotNull { list ->
    val address = list.imageUrl ?: return@mapNotNull null
    runCatching {
        ((run(command("listImage", "listId" to list.id, "imageUrl" to address)) as? JsonObject)?.get("source") as? JsonPrimitive)?.content
    }.getOrNull()?.let { list.id to it }
}.toMap()

data class PublicListsState(
    val lists: List<PublicListSummary> = emptyList(),
    /** Each list's picture, by id (`publicListPictures`). */
    val images: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** The lists being copied now: their row says "Copying...". */
    val copying: Set<String> = emptySet(),
    /** A copy the server refused: iOS's "Couldn't copy that list. Please try again." */
    val copyFailed: Boolean = false,
)

/** iOS PublicListBrowserView ("Featured Lists", AATD-46): the catalogue, with Copy on each. */
class PublicListsViewModel(
    private val core: Core,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(PublicListsState())
    val state: StateFlow<PublicListsState> = _state.asStateFlow()

    fun load() = work {
        _state.update { it.copy(loading = true) }
        fetch()
        _state.update { it.copy(loading = false) }
    }

    /** Pull to refresh. */
    fun refresh() = work {
        _state.update { it.copy(refreshing = true) }
        fetch()
        _state.update { it.copy(refreshing = false) }
    }

    private suspend fun fetch() {
        val lists = try { core.publicLists() } catch (_: CoreFailure) { null }
        // iOS falls back to what it holds; a failed fetch keeps what was showing.
        if (lists != null) {
            _state.update { it.copy(lists = lists) }
            val images = core.publicListPictures(lists)
            _state.update { it.copy(images = it.images + images) }
        }
    }

    /** Copy a list with its tasks into this account (core `copyList`); the sidebar hears of it. */
    fun copy(listId: String) = work {
        if (listId in _state.value.copying) return@work
        _state.update { it.copy(copying = it.copying + listId, copyFailed = false) }
        val failed = try { core.run(command("copyList", "listId" to listId)); false } catch (_: CoreFailure) { true }
        _state.update { it.copy(copying = it.copying - listId, copyFailed = failed) }
    }

    fun dismissError() = _state.update { it.copy(copyFailed = false) }
}
