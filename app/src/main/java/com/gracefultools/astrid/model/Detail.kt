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
    val offersCopyIdentifier: Boolean = false,
    val link: String? = null,
    val comments: List<CommentRow> = emptyList(),
    val subtasks: List<SubtaskRow> = emptyList(),
    /** Closed as won't-do (setClosedReason). */
    val isCanceled: Boolean = false,
    /** The project state chips, when the task is on a board and the layout shows them. */
    val boardState: BoardState? = null,
    /** "Waiting on", when the task is on a board (or already waits on something). */
    val blockers: BlockerSection? = null,
    val timer: TimerState? = null,
    /** The task this is a subtask of. */
    val parent: ParentLink? = null,
)

@Serializable
data class BoardState(val current: String? = null, val chips: List<BoardChip> = emptyList())

@Serializable
data class BoardChip(val id: String = "", val name: String = "", val kind: String = "", val isCurrent: Boolean = false)

@Serializable
data class BlockerSection(val chips: List<Blocker> = emptyList(), val canEdit: Boolean = false)

@Serializable
data class Blocker(val id: String = "", val title: String? = null, val identifier: String? = null, val completed: Boolean = false, val hidden: Boolean = false)

@Serializable
data class BlockerCandidates(val candidates: List<Blocker> = emptyList())

@Serializable
data class TimerState(val isRunning: Boolean = false, val startedAt: String? = null, val loggedMinutes: Long = 0, val lastValue: String? = null)

@Serializable
data class ParentLink(val id: String = "", val title: String = "")

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
    val creator: UserSummary? = null,
    val parentTaskId: String? = null,
    /** Where it was completed — `google`, `github`, `apple` — when not here. */
    val completedSource: String? = null,
    val lastTimerValue: String? = null,
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
    val files: List<FileRow> = emptyList(),
    val authorId: String? = null,
    val authorImage: String? = null,
    val authorInitials: String? = null,
    /** The text as markdown blocks (mentions, bold), as a description renders. */
    val blocks: List<JsonObject> = emptyList(),
)

/** A file on a comment (`rows::comment::FileRow`); `localPath` once downloaded or attached here. */
@Serializable
data class FileRow(
    val id: String = "",
    val name: String = "",
    val size: Long = 0,
    val mimeType: String = "application/octet-stream",
    val rendersInline: Boolean = false,
    val localPath: String? = null,
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

@Serializable
data class AssigneeOption(
    val userId: String? = null,
    val name: String? = null,
    val initials: String = "",
    val isCurrentUser: Boolean = false,
    val isAgent: Boolean = false,
    val image: String? = null,
)

/** `assigneeOptions`: who the picker offers, in the core's order, first entry "nobody". */
@Serializable
data class AssigneeOptions(val assigneeId: String? = null, val options: List<AssigneeOption> = emptyList())

@Serializable
data class RepeatPreset(val value: String = "", val titleKey: String = "", val isSelected: Boolean = false)

@Serializable
data class RepeatOptions(
    val repeating: String? = null,
    val presets: List<RepeatPreset> = emptyList(),
    /** The task's custom pattern, when it has one — what the custom editor opens on. */
    val pattern: RepeatPattern? = null,
)

@Serializable
data class RepeatPattern(
    val unit: String? = null,
    val interval: Int? = null,
    val weekdays: List<String>? = null,
    val endCondition: String? = null,
    val endAfterOccurrences: Int? = null,
    val endUntilDate: String? = null,
)

@Serializable
data class ReminderPick(val titleKey: String = "", val reminderTime: String? = null, val isSelected: Boolean = false)

@Serializable
data class ReminderOptions(val reminderTime: String? = null, val picks: List<ReminderPick> = emptyList())

@Serializable
data class ReminderSettingsModel(
    val enablePushReminders: Boolean = true,
    val enableEmailReminders: Boolean = false,
    val defaultReminderTime: Int = 0,
    val enableDailyDigest: Boolean = false,
    val dailyDigestTime: String? = null,
    val dailyDigestTimezone: String? = null,
    /** `HH:mm`; both null when quiet hours are off (iOS ReminderSettings.serverChanges). */
    val quietHoursStart: String? = null,
    val quietHoursEnd: String? = null,
)

@Serializable
data class ReminderOffset(val titleKey: String = "", val minutes: Int = 0)

@Serializable
data class SmartTasks(
    val smartTaskCreationEnabled: Boolean = true,
    val emailToTaskEnabled: Boolean = false,
    val taskDisplayMode: String = "list",
    val subtaskDisplay: String = "indented",
    /** The person's own choice; null when they never made one (the experiment's arm applies). */
    val showListImages: Boolean? = null,
)

/** One entry a settings combo offers (`astrid_rules::smart_tasks::Choice`). */
@Serializable
data class SettingChoice(val value: String = "", val titleKey: String = "", val descriptionKey: String? = null)

/** `settings`: the account's settings as the core holds them, with the choices to offer. */
@Serializable
data class AccountSettings(
    val user: UserSummary? = null,
    val reminderSettings: ReminderSettingsModel = ReminderSettingsModel(),
    val offsets: List<ReminderOffset> = emptyList(),
    val smartTasks: SmartTasks = SmartTasks(),
    /** The reader's UTC offset, from the core's clock. */
    val timezone: String? = null,
    val layoutChoices: List<SettingChoice> = emptyList(),
    val subtaskChoices: List<SettingChoice> = emptyList(),
)

@Serializable
data class ProfileStats(val completed: Int = 0, val inspired: Int = 0, val supported: Int = 0)

@Serializable
data class AgentRow(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val email: String? = null,
    val mode: String = "off",
    val locked: Boolean = false,
    val modes: List<String> = listOf("api", "polling", "webhook", "off"),
)

@Serializable
data class AgentHub(val agents: List<AgentRow> = emptyList())

@Serializable
data class ConnectionRow(
    val id: String = "",
    val kind: String = "unknown",
    val name: String = "",
    val owner: String? = null,
    val actsAs: String? = null,
    val scopes: List<String> = emptyList(),
    val lastUsedAt: String? = null,
    val status: String = "unknown",
    val revocable: Boolean = false,
)

@Serializable
data class ConnectionsPanel(val connections: List<ConnectionRow> = emptyList(), val reviewCount: Int = 0)
