package com.gracefultools.astrid.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The words astrid-core says when its cache moves — `astrid_core::realtime::Change::to_json`, all of
 * them. Screens ask the questions below instead of spelling kinds: they spelled `outbox`,
 * `taskDeleted` and `comment`, which the core never sends, and missed `delivered`, so a sent task
 * kept saying "Waiting to send" for up to a minute (AATD-30).
 */
object ChangeKind {
    const val TASK = "task"
    const val LIST = "list"
    const val COMMENTS = "comments"
    const val CHAT = "chat"
    const val AGENT_TYPING = "agentTyping"
    const val SETTINGS = "settings"
    const val REMINDERS_DUE = "remindersDue"
    const val NEEDS_SYNC = "needsSync"
    const val SYNCED = "synced"
    const val DELIVERED = "delivered"
    const val NOTIFICATIONS = "notifications"
    const val STREAM = "stream"

    val ALL = setOf(TASK, LIST, COMMENTS, CHAT, AGENT_TYPING, SETTINGS, REMINDERS_DUE, NEEDS_SYNC, SYNCED, DELIVERED, NOTIFICATIONS, STREAM)
}

private fun CoreChange.string(key: String) = (raw[key] as? JsonPrimitive)?.contentOrNull
private fun CoreChange.ids(key: String) = (raw[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
private val CoreChange.undescribed get() = (raw["undescribed"] as? JsonPrimitive)?.contentOrNull == "true"

/** Any task's rows may have moved: a list, a board, search, the reminder alarm. */
val CoreChange.movesTasks: Boolean
    get() = kind == ChangeKind.TASK || kind == ChangeKind.SYNCED || kind == ChangeKind.DELIVERED

/** Lists or their counts may have moved: the sidebar. A task's change moves a count. */
val CoreChange.movesLists: Boolean
    get() = movesTasks || kind == ChangeKind.LIST

/** This task, or its comments, may have moved. */
fun CoreChange.concernsTask(id: String): Boolean = when (kind) {
    ChangeKind.SYNCED -> true
    ChangeKind.TASK -> string("id") == id
    ChangeKind.COMMENTS -> string("taskId") == id
    ChangeKind.DELIVERED -> undescribed || id in ids("taskIds") || id in ids("commentTaskIds")
    else -> false
}

/** A chat channel may have moved; [channelId] null means "any". */
fun CoreChange.concernsChat(channelId: String? = null): Boolean = when (kind) {
    ChangeKind.SYNCED -> true
    ChangeKind.CHAT -> channelId == null || string("channelId") == channelId
    ChangeKind.DELIVERED -> undescribed || (if (channelId == null) ids("channelIds").isNotEmpty() else channelId in ids("channelIds"))
    else -> false
}

/** The Outbox's counts moved. */
val CoreChange.movesOutbox: Boolean get() = kind == ChangeKind.DELIVERED

/** Account settings moved. */
val CoreChange.movesSettings: Boolean get() = kind == ChangeKind.SETTINGS

/** `agentTyping`: an agent started (`active`) or stopped thinking about a task or in a channel. */
data class AgentTyping(val channelId: String?, val taskId: String?, val agentName: String?, val active: Boolean)

/** This change as an agent's typing, or null when it is something else. */
val CoreChange.agentTyping: AgentTyping?
    get() = if (kind != ChangeKind.AGENT_TYPING) null else AgentTyping(
        channelId = string("channelId"),
        taskId = string("taskId"),
        agentName = string("agentName")?.takeIf { it.isNotBlank() },
        active = string("active") == "true",
    )
