package com.gracefultools.astrid.ui.share

import com.gracefultools.astrid.SharedFile
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ShareTaskState(val saving: Boolean = false, val done: Boolean = false, val error: String? = null)

/**
 * iOS's share extension (TaskQuickCreateView) and how the app then makes the task
 * (AstridApp.processSingleSharedTask): no list — the server's default list — assigned to the
 * reader, then the file attached as a comment. Both through the core, so offline it waits.
 */
class ShareTaskViewModel(
    private val core: Core,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ShareTaskState())
    val state: StateFlow<ShareTaskState> = _state.asStateFlow()

    /** [priority] is the segmented control's 0–3; 0 ("None") sends no priority, as iOS. */
    fun save(title: String, description: String, priority: Int, file: SharedFile?) = work {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || _state.value.saving) return@work
        _state.update { it.copy(saving = true, error = null) }
        val me = runCatching {
            ((core.run(command("currentUser")) as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
        }.getOrNull()
        try {
            val made = core.run(
                command(
                    "createTask", "title" to trimmed, "listIds" to emptyList<String>(),
                    *listOfNotNull(
                        description.takeIf { it.isNotBlank() }?.let { "description" to it },
                        priority.takeIf { it > 0 }?.let { "priority" to it },
                        me?.let { "assigneeId" to it },
                    ).toTypedArray(),
                ),
            )
            val id = ((made as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
            if (file != null && id != null) {
                runCatching {
                    core.run(command("attachFile", "taskId" to id, "path" to file.path, "name" to file.name, "mimeType" to file.mimeType))
                }
            }
            _state.update { it.copy(saving = false, done = true) }
        } catch (failure: CoreFailure) {
            // Offline it is in the Outbox and goes later (ASTRID.md rule 6): that is done too.
            _state.update {
                if (failure.isStillPending) it.copy(saving = false, done = true) else it.copy(saving = false, error = failure.message)
            }
        }
    }

    companion object {
        /** iOS: the file's name without its extension, underscores and dashes as spaces. */
        fun titleFromFileName(name: String): String =
            name.substringBeforeLast('.', name).replace('_', ' ').replace('-', ' ')
    }
}
