package com.gracefultools.astrid.ui.lists

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.ListAgentOptions
import com.gracefultools.astrid.model.ListDetail
import com.gracefultools.astrid.model.ListInvite
import com.gracefultools.astrid.model.ListSettings
import com.gracefultools.astrid.model.Member
import com.gracefultools.astrid.model.TransferAvailability
import com.gracefultools.astrid.model.UserSummary
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A board operation that failed, and why — the screen words it. */
data class BoardFailure(val creating: Boolean, val message: String)

/**
 * iOS ListSettingsModal's tabs. Insights is for everyone who can see the list (saved filters never
 * reach this screen); Admin only for those who may manage it.
 */
enum class ListSettingsTab { FILTERS, MEMBERS, INSIGHTS, ADMIN }

data class ListSettingsState(
    val settings: ListSettings? = null,
    val detail: ListDetail? = null,
    /** iOS ListImagesVisibility (core `features.showListImages`): no image row while hidden. */
    val showListImages: Boolean = true,
    /** Where the list's picture can be drawn from (`listImage`). */
    val imageSource: String? = null,
    val imageUploading: Boolean = false,
    val agentOptions: ListAgentOptions? = null,
    val transfer: TransferAvailability? = null,
    val transferring: Boolean = false,
    val boardBusy: Boolean = false,
    /** iOS's inline "Couldn't create board: …" / "Couldn't disable board: …". */
    val boardError: BoardFailure? = null,
    /** The Share List sheet's minted link (`shareList`), and whether minting it failed. */
    val shareLink: String? = null,
    val shareFailed: Boolean = false,
    /** People found for the Add Member sheet (`searchUsers`). */
    val suggestions: List<UserSummary> = emptyList(),
    val searching: Boolean = false,
    val gone: Boolean = false,
    val error: String? = null,
) {
    /** The owner first, as a person: the list's own record, else their member row. */
    val owner: UserSummary?
        get() = detail?.owner ?: settings?.let { s -> s.members.firstOrNull { it.who == s.ownerId }?.user }

    /** Member rows without the owner, who is drawn first (iOS filters the same way). */
    val otherMembers: List<Member>
        get() = settings?.let { s -> s.members.filter { it.who != null && it.who != (s.ownerId ?: detail?.owner?.id) } }.orEmpty()

    /** Invitations nobody has accepted yet — the core's roster leaves out anyone already a member. */
    val pendingInvitations: List<ListInvite>
        get() = detail?.invitations.orEmpty()

    val tabs: List<ListSettingsTab>
        get() = listOf(ListSettingsTab.FILTERS, ListSettingsTab.MEMBERS, ListSettingsTab.INSIGHTS) + if (settings?.canManageList == true) listOf(ListSettingsTab.ADMIN) else emptyList()

    val recentlyCompletedPreset: RecentlyCompletedPreset get() = RecentlyCompletedPreset.forWindow(detail?.recentlyCompletedWindow)

    /** The `since-date` window's date (`yyyy-MM-dd`), when that is the window. */
    val recentlyCompletedSince: String?
        get() = detail?.recentlyCompletedWindow?.takeIf { (it["kind"] as? JsonPrimitive)?.content == "since-date" }
            ?.let { (it["date"] as? JsonPrimitive)?.content }

    /** Whether this account has an agent at all — iOS shows the coding-agent repository only then. */
    val hasAgents: Boolean get() = agentOptions?.agents?.isNotEmpty() == true

    fun isMember(userId: String): Boolean =
        settings?.ownerId == userId || settings?.members?.any { it.who == userId } == true
}

/**
 * The list's settings, members and admin (iOS ListSettingsModal: ListSortFiltersTab,
 * ListMembershipTab, ListAdminTab). What this account may change is the core's answer
 * (`canManageList`, `canManageMembers`, `canDeleteList`, `canLeave`, `canTransferOwnership`) —
 * never a role comparison here (ASTRID.md rule 9). Membership is online-only on purpose (D31): a
 * refusal shows. Destructive actions are confirmed by the screen before they reach here.
 */
class ListSettingsViewModel(
    private val core: Core,
    private val listId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ListSettingsState())
    val state: StateFlow<ListSettingsState> = _state.asStateFlow()

    private var pendingInfo: Job? = null
    private var pendingName: String? = null
    private var pendingDescription: String? = null
    private var pendingColor: Job? = null
    private var pendingColorValue: String? = null
    private var searchJob: Job? = null

    init {
        load()
        work {
            _state.update { it.copy(showListImages = showListImages()) }
            loadImage()
        }
        // The cache opens the screen; the network corrects it.
        work { runCatching { core.run(command("refreshListMembers", "listId" to listId)) }; load() }
    }

    /** One answer, the core's `listMembers`: settings, roster, description and window (AATD-44). */
    fun load() = work {
        val answer = runCatching { core.run(command("listMembers", "listId" to listId)) }.getOrNull() ?: return@work
        runCatching { CoreJson.decodeFromJsonElement(ListSettings.serializer(), answer) }
            .onSuccess { settings -> _state.update { it.copy(settings = settings) } }
        runCatching { CoreJson.decodeFromJsonElement(ListDetail.serializer(), answer) }
            .onSuccess { detail -> _state.update { it.copy(detail = detail) } }
    }

    /** The Admin tab's network-backed choices: the coding-agent options. Best effort. */
    fun loadAgentOptions() = work {
        runCatching { CoreJson.decodeFromJsonElement(ListAgentOptions.serializer(), core.run(command("listAgentOptions", "listId" to listId))) }
            .onSuccess { options -> _state.update { it.copy(agentOptions = options) } }
    }

    /**
     * The owner's Transfer control asks the server who it may go to — only when the core says this
     * account is the owner, since the endpoint refuses everybody else (iOS ListMembershipTab).
     */
    fun loadTransfer() = work {
        if (_state.value.settings?.canTransferOwnership != true) return@work
        runCatching { CoreJson.decodeFromJsonElement(TransferAvailability.serializer(), core.run(command("eligibleNewOwners", "listId" to listId))) }
            .onSuccess { transfer -> _state.update { it.copy(transfer = transfer) } }
            .onFailure { _state.update { it.copy(transfer = TransferAvailability("unavailable")) } }
    }

    // ---- Filters tab (the Favorite toggle until the lead's filters land) ----

    fun setFavorite(favorite: Boolean) = act { core.run(command("setListFavorite", "listId" to listId, "favorite" to favorite)) }

    // ---- Admin: List Information (saved as typed, debounced) ----

    fun editName(name: String) {
        pendingName = name
        scheduleInfo()
    }

    fun editDescription(description: String) {
        pendingDescription = description
        scheduleInfo()
    }

    /** Send whatever is still waiting on the debounce — the screen calls this as it closes. */
    fun flushEdits() {
        if (pendingInfo?.isActive == true) {
            pendingInfo?.cancel()
            saveInfo()
        }
        if (pendingColor?.isActive == true) {
            pendingColor?.cancel()
            pendingColorValue?.let { saveColor(it) }
        }
    }

    private fun scheduleInfo() {
        pendingInfo?.cancel()
        pendingInfo = work {
            delay(INFO_DEBOUNCE_MS)
            saveInfo()
        }
    }

    private fun saveInfo() {
        val changes = mutableMapOf<String, Any?>()
        pendingName?.trim()?.let { name ->
            if (name.isNotEmpty() && name != _state.value.settings?.name) changes["name"] = name
        }
        pendingDescription?.let { description ->
            if (description != _state.value.detail?.description.orEmpty()) changes["description"] = description
        }
        pendingName = null
        pendingDescription = null
        if (changes.isNotEmpty()) change(changes)
    }

    // ---- Admin: List Appearance ----

    /** A palette swatch: sent at once, unless it is the colour the list already has. */
    fun pickColor(color: String) {
        pendingColor?.cancel()
        saveColor(color)
    }

    /** iOS's custom ColorPicker fires on every drag step, so it saves once the drag settles (400ms). */
    fun pickCustomColor(color: String) {
        pendingColorValue = color
        pendingColor?.cancel()
        pendingColor = work {
            delay(CUSTOM_COLOR_DEBOUNCE_MS)
            saveColor(color)
        }
    }

    private fun saveColor(color: String) {
        if (color.equals(_state.value.settings?.color, ignoreCase = true)) return
        change(mapOf("color" to color.lowercase()))
    }

    /** A picture chosen with the photo picker, already copied to [path] in app storage. */
    fun setImage(path: String) = work {
        _state.update { it.copy(imageUploading = true) }
        try {
            core.run(command("setListImage", "listId" to listId, "path" to path))
            loadImage()
            load()
        } catch (failure: CoreFailure) {
            _state.update { it.copy(error = failure.message) }
        } finally {
            _state.update { it.copy(imageUploading = false) }
        }
    }

    // ---- Admin: Default Task Settings (each choice through updateList's default* fields) ----

    fun setDefaultPriority(priority: Int) = change(mapOf("defaultPriority" to priority))

    /** null = Task Creator, `"unassigned"` = Unassigned, else a member's id. */
    fun setDefaultAssignee(assigneeId: String?) = change(mapOf("defaultAssigneeId" to assigneeId))

    /** `none`, `today`, `tomorrow`, `next_week`, `next_month`. */
    fun setDefaultDueDate(dueDate: String) = change(mapOf("defaultDueDate" to dueDate))

    /** null = All day, else `HH:mm`. */
    fun setDefaultDueTime(dueTime: String?) = change(mapOf("defaultDueTime" to dueTime))

    /** `never`, `daily`, `weekly`, `monthly`. */
    fun setDefaultRepeating(repeating: String) = change(mapOf("defaultRepeating" to repeating))

    // ---- Admin: AI coding agent ----

    fun setGithubRepository(fullName: String?) = change(mapOf("githubRepositoryId" to fullName))

    // ---- Admin: Project board ----

    fun createBoard() = board(creating = true) {
        core.run(command("createBoardForList", "listId" to listId))
    }

    /** Only after "Disable board?" has been confirmed. */
    fun disableBoard() {
        val projectId = _state.value.settings?.projectId ?: return
        board(creating = false) { core.run(command("deleteProject", "projectId" to projectId)) }
    }

    private fun board(creating: Boolean, block: suspend () -> Unit) = work {
        _state.update { it.copy(boardBusy = true, boardError = null) }
        try {
            block()
            load()
            _state.update { it.copy(boardBusy = false) }
        } catch (failure: CoreFailure) {
            _state.update { it.copy(boardBusy = false, boardError = BoardFailure(creating, failure.message.orEmpty())) }
        }
    }

    // ---- Admin: Advanced — recently completed window ----

    /** A preset; [sinceDate] (`yyyy-MM-dd`) for "Since a specific date…". */
    fun setRecentlyCompleted(preset: RecentlyCompletedPreset, sinceDate: String? = null) {
        val window: JsonObject? = when (preset) {
            RecentlyCompletedPreset.SINCE_SPECIFIC_DATE -> buildJsonObject {
                put("kind", "since-date"); put("date", sinceDate ?: java.time.LocalDate.now().toString())
            }
            else -> preset.window
        }
        change(mapOf("recentlyCompletedWindow" to window))
    }

    // ---- Danger Zone ----

    /** Only after the "Delete list" alert has been confirmed. */
    fun delete() = act(goneAfter = true) { core.run(command("deleteList", "listId" to listId)) }

    // ---- Members ----

    /** iOS's single privacy button: Make Public, or Make Private — which sets SHARED. */
    fun togglePublic() {
        val isPublic = _state.value.settings?.privacy.equals("PUBLIC", ignoreCase = true)
        change(mapOf("privacy" to if (isPublic) "SHARED" else "PUBLIC"))
    }

    fun invite(email: String, role: String = "member") {
        val trimmed = email.trim()
        if (!trimmed.contains('@')) return
        act { core.run(command("inviteToList", "listId" to listId, "email" to trimmed, "role" to role)) }
    }

    fun setRole(userId: String, role: String) = act { core.run(command("setMemberRole", "listId" to listId, "userId" to userId, "role" to role)) }
    fun remove(userId: String) = act { core.run(command("removeMember", "listId" to listId, "userId" to userId)) }
    fun cancelInvitation(email: String) = act { core.run(command("cancelInvitation", "listId" to listId, "email" to email)) }

    /** The Add Member sheet's suggestions: people the server knows, as you type (from 2 characters). */
    fun search(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(suggestions = emptyList(), searching = false) }
            return
        }
        searchJob = work {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(searching = true) }
            val found = runCatching {
                CoreJson.decodeFromJsonElement(
                    ListSerializer(UserSummary.serializer()),
                    core.run(command("searchUsers", "query" to trimmed, "listIds" to listOf(listId))) as? JsonArray ?: JsonArray(emptyList()),
                )
            }.getOrDefault(emptyList())
            val s = _state.value
            val taken = buildSet {
                s.settings?.members?.forEach { m -> (m.user?.email ?: m.email)?.let { add(it.lowercase()) } }
                s.owner?.email?.let { add(it.lowercase()) }
                s.pendingInvitations.forEach { add(it.email.lowercase()) }
            }
            _state.update { it.copy(suggestions = found.filter { u -> !u.email.isNullOrBlank() && u.email.lowercase() !in taken }, searching = false) }
        }
    }

    fun clearSuggestions() {
        searchJob?.cancel()
        _state.update { it.copy(suggestions = emptyList(), searching = false) }
    }

    // ---- Members: Share List ----

    /** Mint a share link for the list, as iOS's ShareTargetView does. Online-only. */
    fun requestShareLink() = work {
        _state.update { it.copy(shareLink = null, shareFailed = false) }
        try {
            val url = ((core.run(command("shareList", "listId" to listId)) as? JsonObject)?.get("url") as? JsonPrimitive)?.content
            _state.update { it.copy(shareLink = url, shareFailed = url == null) }
        } catch (failure: CoreFailure) {
            _state.update { it.copy(shareFailed = true) }
        }
    }

    // ---- Members: AI agents ----

    fun addAgent(email: String) = invite(email, "member")
    fun removeAgent(userId: String) = remove(userId)

    // ---- Leaving ----

    /** Only after "Leave List" has been confirmed. */
    fun leave() = act(goneAfter = true) { core.run(command("leaveList", "listId" to listId)) }

    /** Only after the Transfer ownership confirmation: hands the list over and leaves, in one call. */
    fun transferOwnership(newOwnerId: String) {
        if (newOwnerId.isBlank()) return
        _state.update { it.copy(transferring = true) }
        act(goneAfter = true) {
            try {
                core.run(command("transferListOwnership", "listId" to listId, "newOwnerId" to newOwnerId))
            } finally {
                _state.update { it.copy(transferring = false) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun change(changes: Map<String, Any?>) = act { core.run(command("updateList", "listId" to listId, "changes" to changes)) }

    private suspend fun showListImages(): Boolean = runCatching {
        ((core.run(command("features")) as? JsonObject)?.get("showListImages") as? JsonPrimitive)?.content != "false"
    }.getOrDefault(true)

    private suspend fun loadImage() {
        val source = runCatching { ((core.run(command("listImage", "listId" to listId)) as? JsonObject)?.get("source") as? JsonPrimitive)?.content }.getOrNull()
        _state.update { it.copy(imageSource = source) }
    }

    private fun act(goneAfter: Boolean = false, block: suspend () -> Unit) = work {
        try {
            block()
            if (goneAfter) _state.update { it.copy(gone = true) } else load()
        } catch (failure: CoreFailure) {
            if (failure.isStillPending) {
                if (goneAfter) _state.update { it.copy(gone = true) } else load()
            } else {
                _state.update { it.copy(error = failure.message) }
            }
        }
    }

    companion object {
        const val INFO_DEBOUNCE_MS = 500L
        const val CUSTOM_COLOR_DEBOUNCE_MS = 400L
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
