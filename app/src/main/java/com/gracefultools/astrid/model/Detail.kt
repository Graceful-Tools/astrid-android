package com.gracefultools.astrid.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The core's `taskDetail` answer (`app::dispatch::tasks::task_detail`). */
@Serializable
data class TaskDetail(
    val task: TaskFields = TaskFields(),
    /** The order to lay the fields out in under the title — shared with web and iOS. */
    val fieldOrder: List<String> = listOf("assignee", "when", "priority", "lists"),
    val descriptionBlocks: List<JsonObject> = emptyList(),
    val due: DueLabel = DueLabel(),
    val isOverdue: Boolean = false,
    val repeatSummary: JsonElement? = null,
    val listChips: List<ListChip> = emptyList(),
    val assignee: UserSummary? = null,
    val isCopyOnly: Boolean = false,
    val showsIdentifier: Boolean = false,
    val link: String? = null,
    val comments: List<CommentRow> = emptyList(),
    val subtasks: List<SubtaskRow> = emptyList(),
)

/** The task itself, in the v1 wire shape. Only what the screen reads; the rest stays in the core. */
@Serializable
data class TaskFields(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val completed: Boolean = false,
    val priority: Int = 0,
    val dueDateTime: String? = null,
    val isAllDay: Boolean = false,
    val repeating: String? = null,
    val identifier: String? = null,
    val listIds: List<String> = emptyList(),
    val isPrivate: Boolean = false,
)

@Serializable
data class CommentRow(
    val id: String = "",
    val content: String = "",
    val createdAt: String? = null,
    val authorName: String? = null,
    val isPending: Boolean = false,
    val showsText: Boolean = true,
    val isMine: Boolean = false,
    val isSystem: Boolean = false,
    val parentId: String? = null,
    val isReply: Boolean = false,
)

@Serializable
data class SubtaskRow(val id: String = "", val title: String = "", val completed: Boolean = false, val isPending: Boolean = false)

/** A quick date or time pick (`dueDateOptions`): a label key and the instant it means. */
@Serializable
data class DuePick(val titleKey: String = "", val dueDateTime: String? = null, val hour: Int? = null, val isSelected: Boolean = false)

@Serializable
data class DueDateOptions(val isAllDay: Boolean = false, val dueDateTime: String? = null, val dates: List<DuePick> = emptyList(), val times: List<DuePick> = emptyList())

@Serializable
data class ListPick(val id: String = "", val name: String = "", val color: String = "#3b82f6", val isSelected: Boolean = false)

@Serializable
data class ListPicks(val selected: List<ListPick> = emptyList(), val options: List<ListPick> = emptyList(), val createName: String? = null)
