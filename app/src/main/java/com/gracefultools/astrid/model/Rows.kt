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
    val isOverdue: Boolean = false,
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
)

/** A due date the core has already worded: `key` is a label key, `date`/`time` its arguments. */
@Serializable
data class DueLabel(val key: String = "none", val date: String? = null, val time: String? = null)

/** The leading control's three states: your checkbox, someone else's initial, or unassigned. */
@Serializable
data class LeadingControl(val kind: String = "unassigned", val userId: String? = null, val initial: String? = null)

@Serializable
data class ListChip(val id: String = "", val name: String = "", val color: String = "#3b82f6", val isLabel: Boolean = false)

@Serializable
data class UserSummary(val id: String = "", val name: String? = null, val email: String? = null, val image: String? = null) {
    val display: String get() = name?.takeIf { it.isNotBlank() } ?: email?.takeIf { it.isNotBlank() } ?: id
}

@Serializable
data class RowWindow(val total: Int = 0, val offset: Int = 0, val rows: List<TaskRow> = emptyList(), val sortBy: String? = null)

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
)

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
