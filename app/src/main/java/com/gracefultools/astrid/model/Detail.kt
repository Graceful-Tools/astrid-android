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
    /** Runs of completion notes folded into one line (core `streaks`, iOS CompletionStreak). */
    val streaks: List<CompletionStreak> = emptyList(),
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
data class CompletionStreak(val ids: List<String> = emptyList(), val count: Int = 0, val spanDays: Int? = null, val dates: List<String> = emptyList())

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
    /** `DUE_DATE` or `COMPLETION_DATE`: what a repeat counts from. */
    val repeatFrom: String? = null,
    /** The occurrence this one followed, and the one that followed it (iOS's Occurrences row). */
    val previousOccurrenceId: String? = null,
    val nextOccurrenceId: String? = null,
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
    /** An AI agent wrote it: its reply ends that agent's typing indicator. */
    val authorIsAgent: Boolean = false,
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
    /** `DUE_DATE` or `COMPLETION_DATE`; absent is the core's default, from completion. */
    val repeatFrom: String? = null,
    val presets: List<RepeatPreset> = emptyList(),
    /** The task's custom pattern, when it has one — what the custom editor opens on. */
    val pattern: RepeatPattern? = null,
)

/** The wire's `CustomRepeatingPattern` (astrid-rules `model::task`): every field the core reads. */
@Serializable
data class RepeatPattern(
    val unit: String? = null,
    val interval: Int? = null,
    val weekdays: List<String>? = null,
    val endCondition: String? = null,
    val endAfterOccurrences: Int? = null,
    val endUntilDate: String? = null,
    /** Monthly: `same_date` (on [monthDay]) or `same_weekday` (on [monthWeekday]). */
    val monthRepeatType: String? = null,
    val monthDay: Int? = null,
    val monthWeekday: MonthWeekday? = null,
    /** Yearly: the month (1-12) and the day. */
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
data class MonthWeekday(val weekday: String = "monday", val weekOfMonth: Int = 1)

/** A file on the task (`attachments`): its own and its comments', with where it is on this device. */
@Serializable
data class AttachmentFile(
    val id: String = "",
    val name: String = "",
    val size: Long = 0,
    val mimeType: String = "application/octet-stream",
    val isCached: Boolean = false,
    val path: String? = null,
) {
    /** Drawn as a picture rather than an icon. */
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

@Serializable
data class Attachments(val files: List<AttachmentFile> = emptyList())

/** `taskProjectFields`: a GitHub board's fields for this task; no rows off a GitHub board. */
@Serializable
data class TaskProjectFields(val projectId: String? = null, val rows: List<ProjectFieldRow> = emptyList())

@Serializable
data class ProjectFieldRow(
    val id: String = "",
    val name: String = "",
    /** `text`, `number`, `date`, `single_select` or `iteration`. */
    val dataType: String = "text",
    val options: List<ProjectFieldOption> = emptyList(),
    val iterations: List<ProjectFieldIteration> = emptyList(),
    val value: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class ProjectFieldOption(val id: String = "", val name: String = "", val color: String? = null)

@Serializable
data class ProjectFieldIteration(val id: String = "", val title: String = "", val startDate: String = "", val completed: Boolean = false)

/** One row of the comment box's popup (`commentSuggestions`): a person, a list or a task. */
@Serializable
data class CommentSuggestion(
    /** `mention`, `list` or `task`. */
    val kind: String = "mention",
    val id: String = "",
    val label: String = "",
    val secondary: String? = null,
    val isAgent: Boolean = false,
    val completed: Boolean = false,
)

@Serializable
data class CommentTrigger(val kind: String = "mention", val start: Int = 0, val query: String = "")

@Serializable
data class CommentSuggestions(val trigger: CommentTrigger? = null, val items: List<CommentSuggestion> = emptyList())

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
