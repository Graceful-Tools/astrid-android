package com.gracefultools.astrid.ui.common

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.StatusOptions
import com.gracefultools.astrid.model.TaskRow

/** What tapping a row's leading control asks for — the core's `action`, never the shell's guess. */
sealed interface LeadingOutcome {
    data object Done : LeadingOutcome
    data class PickStatus(val taskId: String, val options: StatusOptions) : LeadingOutcome
    data class Refused(val message: String) : LeadingOutcome
}

class RowActions(private val core: Core) {
    /** `complete` completes or reopens; `openPicker` offers the board's columns; `copy` copies it to you. */
    suspend fun leading(row: TaskRow): LeadingOutcome = try {
        when (row.action) {
            "openPicker" -> LeadingOutcome.PickStatus(
                row.id,
                CoreJson.decodeFromJsonElement(StatusOptions.serializer(), core.run(command("taskStatusOptions", "taskId" to row.id))),
            )
            "copy" -> { core.run(command("copyTask", "taskId" to row.id)); LeadingOutcome.Done }
            else -> {
                core.run(command("completeTask", "taskId" to row.id, "completed" to !row.completed, "source" to "android"))
                LeadingOutcome.Done
            }
        }
    } catch (failure: CoreFailure) {
        if (failure.isStillPending) LeadingOutcome.Done else LeadingOutcome.Refused(failure.message)
    }

    suspend fun setStatus(taskId: String, columnId: String): String? = try {
        core.run(command("setTaskStatus", "taskId" to taskId, "columnId" to columnId)); null
    } catch (failure: CoreFailure) {
        if (failure.isStillPending) null else failure.message
    }
}
