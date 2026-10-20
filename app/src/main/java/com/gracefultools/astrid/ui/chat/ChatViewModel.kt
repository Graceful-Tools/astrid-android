package com.gracefultools.astrid.ui.chat

import com.gracefultools.astrid.core.concernsChat
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.Chat
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

data class ChatState(val chat: Chat = Chat(), val error: String? = null)

/**
 * A list's chat (iOS's ChatView): the core holds the channel, the messages and their order, and
 * journals a send so one written offline goes later. A list with no channel yet gets one on the
 * first send (resolveChatChannel).
 */
class ChatViewModel(private val core: Core, private val listId: String, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) :
    CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        load()
        work { runCatching { core.run(command("refreshChat", "listId" to listId)) }; load() }
        work { core.changes.collect { if (it.concernsChat()) load() } }
    }

    fun load() = work {
        runCatching { CoreJson.decodeFromJsonElement(Chat.serializer(), core.run(command("chat", "listId" to listId))) }
            .onSuccess { chat -> _state.update { it.copy(chat = chat) } }
    }

    fun send(text: String) = work {
        val content = text.trim()
        if (content.isEmpty()) return@work
        try {
            val channel = _state.value.chat.channelId ?: (
                (core.run(command("resolveChatChannel", "listId" to listId)) as? JsonObject)
                    ?.let { (it["channelId"] ?: it["id"]) as? JsonPrimitive }?.contentOrNull
                ) ?: return@work
            core.run(command("sendChatMessage", "channelId" to channel, "content" to content))
        } catch (failure: CoreFailure) {
            if (!failure.isStillPending) _state.update { it.copy(error = failure.message) }
        }
        load()
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
