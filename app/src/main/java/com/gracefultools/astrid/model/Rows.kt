package com.gracefultools.astrid.model

import kotlinx.serialization.Serializable

/**
 * The core's answers, as the screens read them (astrid-core `rows`, `app::dispatch::row_json`).
 * Every field defaults, because the core may add one and the server is permissive: a shape the
 * app does not fully know degrades to a blank, never to a crash.
 */
@Serializable
data class TaskRow(
    val id: String = "",
    val title: String = "",
    val identifier: String? = null,
    /** Read, never computed: whether this row draws its identifier (board cards do, list rows don't). */
    val showsIdentifier: Boolean = false,
    val completed: Boolean = false,
    val priority: Int = 0,
    val due: DueLabel = DueLabel(),
    /** A timed task's local date and time (core `dueAt`): drawn "Oct 19 6:00 PM" even today, as iOS. */
    val dueAt: DueAt? = null,
    val isOverdue: Boolean = false,
    /** Whether the long-press menu offers "Copy task id". */
    val offersCopyIdentifier: Boolean = false,
    val leading: LeadingControl = LeadingControl(),
    /** What tapping the leading control does: `complete` or `openPicker`. */
    val action: String = "complete",
    val depth: Int = 0,
    /** True while the task exists only on this device — journalled, not yet sent. */
    val isPending: Boolean = false,
    val isPrivate: Boolean = false,
    val isRepeating: Boolean = false,
    val hasDescription: Boolean = false,
    val commentCount: Int = 0,
    val attachmentCount: Int = 0,
    val subtaskCount: Int = 0,
    val listChips: List<ListChip> = emptyList(),
    val assignee: UserSummary? = null,
    val statusRole: String? = null,
    val checkboxLabel: String? = null,
    /** iOS's title chips: closed as won't-do, a GitHub sync mark (`pending`/`failed`), `repo#N`. */
    val isCanceled: Boolean = false,
    val syncMark: String? = null,
    val remote: RemoteChip? = null,
)

@Serializable
data class RemoteChip(val label: String = "", val url: String? = null)

/** A due date the core has already worded: `key` is a label key, `date`/`time` its arguments. */
@Serializable
data class DueLabel(val key: String = "none", val date: String? = null, val time: String? = null)

@Serializable
data class DueAt(val date: String = "", val time: String = "")

/** The leading control's three states: your checkbox, someone else's initial, or unassigned. */
@Serializable
data class LeadingControl(val kind: String = "unassigned", val userId: String? = null, val initial: String? = null)

@Serializable
data class ListChip(
    val id: String = "",
    val name: String = "",
    val color: String = "#3b82f6",
    val isLabel: Boolean = false,
    /** iOS's symbol before the name: `tag`, `globe`, `people` or `hash` (core `ChipGlyph`). */
    val glyph: String = "hash",
)

@Serializable
data class UserSummary(val id: String = "", val name: String? = null, val email: String? = null, val image: String? = null) {
    val display: String get() = name?.takeIf { it.isNotBlank() } ?: email?.takeIf { it.isNotBlank() } ?: id
}

@Serializable
data class RowWindow(
    val total: Int = 0,
    val offset: Int = 0,
    val rows: List<TaskRow> = emptyList(),
    val sortBy: String? = null,
    /** Whether quick add is offered here (core rowsForList, iOS ListPermissions.canAddTasks). */
    val canAddTasks: Boolean = true,
)

/** A list, as the sidebar needs it. */
@Serializable
data class ListSummary(
    val id: String = "",
    val name: String = "",
    val color: String? = null,
    val isFavorite: Boolean? = null,
    val favoriteOrder: Int? = null,
    val listType: String? = null,
    val projectId: String? = null,
    val taskCount: Int? = null,
    val isVirtual: Boolean? = null,
    val imageUrl: String? = null,
    /** PRIVATE, SHARED or PUBLIC; absent from thinner answers, so never read absence as private. */
    val privacy: String? = null,
    /** For a virtual list: `my-tasks`, `today`, … */
    val virtualListType: String? = null,
)

/** One board column — `app::dispatch::board`: its cards are the core's rows for the board surface. */
@Serializable
data class BoardColumn(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val kind: String = "status",
    val total: Int = 0,
    val cards: List<TaskRow> = emptyList(),
)

@Serializable
data class Board(val projectId: String? = null, val listId: String? = null, val columns: List<BoardColumn> = emptyList())

@Serializable
data class StatusChoice(val id: String = "", val name: String = "", val kind: String = "status", val isCurrent: Boolean = false)

/** `taskStatusOptions`: the columns a task may be moved to from its menu (Done is never offered, D46). */
@Serializable
data class StatusOptions(val current: String? = null, val columns: List<StatusChoice> = emptyList())

@Serializable
data class Member(
    val userId: String? = null,
    val id: String? = null,
    val role: String = "member",
    val email: String? = null,
    val name: String? = null,
    val user: UserSummary? = null,
    val isPending: Boolean = false,
    val isInvitation: Boolean = false,
) {
    val who: String? get() = userId ?: user?.id ?: id
    val display: String get() = user?.display ?: name ?: email ?: who.orEmpty()
}

/** `listMembers`: a list's settings and members, with what this account may do to them. */
@Serializable
data class ListSettings(
    val listId: String = "",
    val name: String = "",
    val ownerId: String? = null,
    val projectId: String? = null,
    val color: String = "#3b82f6",
    val colorChoices: List<String> = emptyList(),
    val privacy: String? = null,
    val isFavorite: Boolean = false,
    val canManageMembers: Boolean = false,
    val canManageList: Boolean = false,
    val canDeleteList: Boolean = false,
    val canLeave: Boolean = false,
    val currentUserId: String? = null,
    val members: List<Member> = emptyList(),
    /** The list's picture as stored; where to draw it from is `listImage`. */
    val imageUrl: String? = null,
    /** The coding agent's repository (`owner/repo`), or null. */
    val githubRepositoryId: String? = null,
    /** What a task added to this list starts as (core task c4102c67). */
    val defaults: ListDefaults = ListDefaults(),
    /** The owner is offered a handover instead of Leave (Apple AITD-392). */
    val canTransferOwnership: Boolean = false,
    /** What the list's backend can do; the defaults section offers only what it supports. */
    val supports: ListSupportFlags = ListSupportFlags(),
)

/** The `supports` keys the list settings read. Absent means classic: everything on. */
@Serializable
data class ListSupportFlags(val priority: Boolean = true, val dueDate: Boolean = true)

/**
 * A list's default task settings as the core answers them in `listMembers.defaults`: an absent
 * assignee is the task's creator, `"unassigned"` is nobody, an id is that member.
 */
@Serializable
data class ListDefaults(
    val assigneeId: String? = null,
    val priority: Int = 0,
    val repeating: String = "never",
    val dueDate: String = "none",
    val dueTime: String? = null,
)

/** An invitation nobody has accepted yet (`TaskList.invitations`). */
@Serializable
data class ListInvite(val id: String = "", val email: String = "", val role: String = "member")

/**
 * The parts of the cached list (`list`) the settings screen needs that `listMembers` leaves out:
 * the description, the owner as a person, the invitations and the recently-completed window.
 */
@Serializable
data class ListDetail(
    val id: String = "",
    val description: String? = null,
    val owner: UserSummary? = null,
    val invitations: List<ListInvite> = emptyList(),
    val recentlyCompletedWindow: kotlinx.serialization.json.JsonObject? = null,
)

/** `listAgentOptions`: the agents this account may put on a list, and the repositories it reaches. */
@Serializable
data class ListAgentOptions(
    val githubRepositoryId: String? = null,
    val agents: List<AgentOption> = emptyList(),
    val repositories: List<RepositoryOption> = emptyList(),
    val githubConnected: Boolean = false,
)

/** One agent; `email` is what an invitation needs, `aiAgentType` picks its description. */
@Serializable
data class AgentOption(val id: String = "", val name: String = "", val email: String? = null, val image: String? = null, val aiAgentType: String? = null)

@Serializable
data class RepositoryOption(val fullName: String = "", val name: String = "")

/**
 * `eligibleNewOwners`: one of `unavailable`, `notPermitted`, `noEligibleOwners` or `available`
 * (with [owners]) — `services::list::TransferAvailability`.
 */
@Serializable
data class TransferAvailability(val availability: String = "unavailable", val owners: List<UserSummary> = emptyList())

/** One chat message, projected by the core (`rows::chat::MessageRow`). */
@Serializable
data class ChatMessage(
    val id: String = "",
    val content: String = "",
    val blocks: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    val authorName: String? = null,
    val initials: String = "",
    val isMine: Boolean = false,
    val isPending: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
data class Chat(val channelId: String? = null, val messages: List<ChatMessage> = emptyList())
