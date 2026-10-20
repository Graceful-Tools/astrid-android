package com.gracefultools.astrid.ui.chat

import com.gracefultools.astrid.FakeCore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @Test
    fun `AATD-17 a list's chat shows the core's messages`() = runTest {
        val core = FakeCore().apply { answer("chat", """{"channelId":"c1","messages":[{"id":"m1","content":"hi","authorName":"Bo"}]}""") }
        val vm = ChatViewModel(core, "l1", UnconfinedTestDispatcher())
        assertEquals("hi", vm.state.value.chat.messages.single().content)
        vm.send("  hello  ")
        assertEquals(JsonPrimitive("c1"), core.last("sendChatMessage")!!["channelId"])
        assertEquals(JsonPrimitive("hello"), core.last("sendChatMessage")!!["content"])
    }

    @Test
    fun `AATD-17 a list with no channel gets one on the first send`() = runTest {
        val core = FakeCore().apply {
            answer("chat", """{"channelId":null,"messages":[]}""")
            answer("resolveChatChannel", """{"channelId":"c-new"}""")
        }
        val vm = ChatViewModel(core, "l1", UnconfinedTestDispatcher())
        vm.send("   ")
        assertFalse(core.kinds().contains("resolveChatChannel"))
        vm.send("first")
        assertEquals(JsonPrimitive("l1"), core.last("resolveChatChannel")!!["listId"])
        assertEquals(JsonPrimitive("c-new"), core.last("sendChatMessage")!!["channelId"])
    }

    @Test
    fun `AATD-62 an agent thinking in this list's channel shows under the chat, and only this channel's`() = runTest {
        val core = FakeCore().apply { answer("chat", """{"channelId":"c1","messages":[]}""") }
        val vm = ChatViewModel(core, "l1", UnconfinedTestDispatcher())
        core.emit("agentTyping", """{"change":"agentTyping","channelId":"c2","agentName":"Claude","active":true}""")
        assertEquals(null, vm.state.value.typingAgent)
        core.emit("agentTyping", """{"change":"agentTyping","channelId":"c1","agentName":"Claude","active":true}""")
        assertEquals("Claude", vm.state.value.typingAgent?.name)
        core.emit("agentTyping", """{"change":"agentTyping","channelId":"c1","agentName":"Claude","active":false}""")
        assertEquals(null, vm.state.value.typingAgent)
    }
}
