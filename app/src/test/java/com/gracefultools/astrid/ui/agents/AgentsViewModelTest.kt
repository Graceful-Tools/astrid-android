package com.gracefultools.astrid.ui.agents

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentsViewModelTest {
    private val hub = """{
        "agents":[
          {"id":"claude","name":"Claude","email":"claude@astrid.cc","mode":"api","locked":false,"modes":["api","polling","webhook","off"]},
          {"id":"copilot","name":"GitHub Copilot","email":"copilot@astrid.cc","mode":"api","locked":false,"modes":["api","polling","webhook","off"]},
          {"id":"codex","name":"Codex","email":"codex@astrid.cc","mode":"polling","locked":true,"modes":["polling","off"]},
          {"id":"gemini","name":"Gemini","email":"gemini@astrid.cc","mode":"webhook","locked":false,"modes":["api","polling","webhook","off"]}
        ],
        "credentials":[{"serviceId":"claude","name":"Claude","configured":false},{"serviceId":"gemini","name":"Gemini","configured":true}],
        "copilot":{"connected":true}
    }"""

    private fun core() = FakeCore().apply { answer("agents", hub) }
    private fun vm(core: FakeCore) = AgentsViewModel(core, UnconfinedTestDispatcher()).apply { load() }
    private fun agent(vm: AgentsViewModel, id: String) = vm.state.value.agents.first { it.id == id }

    @Test
    fun `AATD-57 the hub loads the core's agents, keys and Copilot in one answer`() = runTest {
        val vm = vm(core())
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf("claude", "copilot", "codex", "gemini"), state.agents.map { it.id })
        assertTrue(state.copilotConnected)
        assertEquals(true, state.credentials["gemini"]?.configured)
    }

    @Test
    fun `AATD-57 a load that fails says so and Retry asks again`() = runTest {
        val core = FakeCore().apply { answer("agents") { throw CoreFailure(FailureKind.UNKNOWN, "boom") } }
        val vm = vm(core)
        assertEquals("boom", vm.state.value.loadError)
        core.answer("agents", hub)
        vm.load()
        assertNull(vm.state.value.loadError)
        assertEquals(2, core.kinds().count { it == "agents" })
    }

    @Test
    fun `AATD-57 ownership maps to the mode iOS writes`() = runTest {
        val core = core()
        val vm = vm(core)
        // api → "I run it" writes polling, the web's default transport.
        vm.selectOwnership("claude", Ownership.USER)
        assertEquals(JsonPrimitive("polling"), core.last("setAgentMode")!!["mode"])
        assertEquals(JsonPrimitive("claude"), core.last("setAgentMode")!!["agent"])
        assertEquals("polling", agent(vm, "claude").mode)
        // Re-choosing "I run it" on a webhook agent must not clobber its transport.
        val before = core.kinds().count { it == "setAgentMode" }
        vm.selectOwnership("gemini", Ownership.USER)
        assertEquals(before, core.kinds().count { it == "setAgentMode" })
        vm.selectOwnership("gemini", Ownership.ASTRID)
        assertEquals(JsonPrimitive("api"), core.last("setAgentMode")!!["mode"])
        vm.selectOwnership("gemini", Ownership.OFF)
        assertEquals(JsonPrimitive("off"), core.last("setAgentMode")!!["mode"])
    }

    @Test
    fun `AATD-57 a locked agent offers only I run it and Off, and no transport`() = runTest {
        val core = core()
        val vm = vm(core)
        val codex = agent(vm, "codex")
        assertEquals(listOf(Ownership.USER, Ownership.OFF), vm.offeredOwnerships(codex))
        assertFalse(vm.showsTransport(codex))
        assertTrue(vm.showsTransport(agent(vm, "gemini")))
        assertEquals(listOf(Transport.POLLING, Transport.WEBHOOK, Transport.SSE), vm.offeredTransports(agent(vm, "gemini")))
        vm.selectOwnership("codex", Ownership.ASTRID)
        assertFalse(core.kinds().contains("setAgentMode"))
    }

    @Test
    fun `AATD-57 a refused mode change rolls back and says why`() = runTest {
        val core = core().apply { answer("setAgentMode") { throw CoreFailure(FailureKind.REFUSED, "nope") } }
        val vm = vm(core)
        vm.selectOwnership("claude", Ownership.OFF)
        assertEquals("api", agent(vm, "claude").mode)
        assertEquals("nope", vm.state.value.saveError)
        assertNull(vm.state.value.savingAgentId)
    }

    @Test
    fun `AATD-57 transport writes its mode, Custom Agent hands over without writing`() = runTest {
        val core = FakeCore().apply {
            answer("agents", """{"agents":[{"id":"claude","name":"Claude","mode":"polling","modes":["api","polling","webhook","off"]}]}""")
        }
        val vm = vm(core)
        assertEquals(Transport.POLLING, vm.transport(agent(vm, "claude")))
        assertFalse(vm.selectTransport("claude", Transport.WEBHOOK))
        assertEquals(JsonPrimitive("webhook"), core.last("setAgentMode")!!["mode"])
        val writes = core.kinds().count { it == "setAgentMode" }
        assertTrue(vm.selectTransport("claude", Transport.SSE))
        assertEquals(writes, core.kinds().count { it == "setAgentMode" })
    }

    @Test
    fun `AATD-57 needs setup means Astrid runs it without its key or Copilot grant`() = runTest {
        val core = core()
        val vm = vm(core)
        assertFalse(vm.isConfigured(agent(vm, "claude")))
        assertTrue(vm.isConfigured(agent(vm, "copilot")))
        assertTrue(vm.isConfigured(agent(vm, "gemini")))
        assertTrue(vm.isConfigured(agent(vm, "codex")))
    }

    @Test
    fun `AATD-57 a key is saved trimmed to its service, then the hub re-reads, a blank one is not sent`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.saveKey("claude", "   ")
        assertFalse(core.kinds().contains("saveAgentCredential"))
        vm.saveKey("claude", "  sk-ant-123 ")
        val sent = core.last("saveAgentCredential")!!
        assertEquals(JsonPrimitive("claude"), sent["serviceId"])
        assertEquals(JsonPrimitive("sk-ant-123"), sent["key"])
        assertEquals(2, core.kinds().count { it == "agents" })
        assertNull(vm.state.value.keyBusy)
    }

    @Test
    fun `AATD-57 Test asks the core whether the key works, Delete removes it`() = runTest {
        val core = core().apply { answer("testAgentCredential", """{"works":false}""") }
        val vm = vm(core)
        vm.testKey("gemini")
        assertEquals(JsonPrimitive("gemini"), core.last("testAgentCredential")!!["serviceId"])
        assertEquals(false, vm.state.value.keyWorks["gemini"])
        vm.deleteKey("gemini")
        assertEquals(JsonPrimitive("gemini"), core.last("deleteAgentCredential")!!["serviceId"])
        assertEquals(false, vm.state.value.credentials["gemini"]?.configured)
        assertTrue("gemini" in vm.state.value.editingKeys)
    }

    @Test
    fun `AATD-57 connecting Copilot opens the core's URL and re-reads the status on return`() = runTest {
        val core = core().apply { answer("connectCopilot", """{"authorizeUrl":"https://github.com/login/oauth?x=1"}""") }
        val vm = AgentsViewModel(core, UnconfinedTestDispatcher(), pollInterval = 0).apply { load() }
        vm.connectCopilot()
        assertEquals("https://github.com/login/oauth?x=1", vm.state.value.openUrl)
        vm.urlOpened()
        assertNull(vm.state.value.openUrl)
        val reads = core.kinds().count { it == "agents" }
        vm.resumed()
        assertTrue(core.kinds().count { it == "agents" } > reads)
        assertFalse(vm.state.value.pollingCopilot)
    }

    @Test
    fun `AATD-57 disconnecting Copilot sends disconnectCopilot`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.disconnectCopilot()
        assertTrue(core.kinds().contains("disconnectCopilot"))
        assertFalse(vm.state.value.copilotConnected)
    }

    @Test
    fun `AATD-57 the webhook saves url, enabled, events, agents and regenerate, and shows the secret once`() = runTest {
        val core = FakeCore().apply {
            answer("webhookSettings", """{"configured":false,"events":["task.assigned"],"availableAgents":["claude","openai"]}""")
            answer("saveWebhook", """{"success":true,"webhookSecret":"whsec_1"}""")
        }
        val vm = WebhookViewModel(core, UnconfinedTestDispatcher()).apply { load() }
        assertFalse(vm.state.value.canSave)
        vm.setUrl(" https://example.test/hook ")
        vm.toggleAgent("openai")
        vm.save()
        val sent = core.last("saveWebhook")!!
        assertEquals(JsonPrimitive("https://example.test/hook"), sent["url"])
        assertEquals(JsonPrimitive(true), sent["enabled"])
        assertEquals(listOf("task.assigned"), sent["events"]!!.jsonArray.map { (it as JsonPrimitive).content })
        assertEquals(listOf("openai"), sent["agents"]!!.jsonArray.map { (it as JsonPrimitive).content })
        assertEquals(JsonPrimitive(false), sent["regenerateSecret"])
        assertEquals("whsec_1", vm.state.value.newSecret)
    }

    @Test
    fun `AATD-57 removing the webhook needs a confirmation`() = runTest {
        val core = FakeCore().apply { answer("webhookSettings", """{"configured":true,"webhookUrl":"https://x.test","enabled":true}""") }
        val vm = WebhookViewModel(core, UnconfinedTestDispatcher()).apply { load() }
        vm.requestRemove()
        assertFalse(core.kinds().contains("deleteWebhook"))
        vm.cancelRemove()
        assertFalse(vm.state.value.confirmingRemove)
        vm.requestRemove()
        vm.confirmRemove()
        assertTrue(core.kinds().contains("deleteWebhook"))
        assertFalse(vm.state.value.settings.configured)
    }

    @Test
    fun `AATD-57 the webhook test shows the server's result, and credentials mint from the webhook preset`() = runTest {
        val core = FakeCore().apply {
            answer("webhookSettings", """{"configured":true,"webhookUrl":"https://x.test","enabled":true,"agents":["gemini"]}""")
            answer("testWebhook", """{"success":true,"message":"Delivered","responseTime":120}""")
            answer("mintTransportCredentials", """{"clientId":"c1","clientSecret":"s1","name":"Webhook"}""")
        }
        val vm = WebhookViewModel(core, UnconfinedTestDispatcher()).apply { load() }
        vm.test()
        assertEquals(120, vm.state.value.testResult?.responseTime)
        vm.mintCredentials()
        val sent = core.last("mintTransportCredentials")!!
        assertEquals(JsonPrimitive("webhookServer"), sent["preset"])
        assertEquals(JsonPrimitive("gemini"), sent["agent"])
        assertEquals("s1", vm.state.value.minted?.clientSecret)
    }

    @Test
    fun `AATD-57 a Custom Agent registers by name and shows its credentials once`() = runTest {
        val core = FakeCore().apply {
            answer("customAgents", """[]""")
            answer("registerCustomAgent", """{"agent":{"id":"a1","email":"buddy.oc@astrid.cc","name":"buddy"},"oauth":{"clientId":"c","clientSecret":"s"},"config":{"sseEndpoint":"e","apiBase":"b","tokenEndpoint":"t"}}""")
        }
        val vm = CustomAgentsViewModel(core, UnconfinedTestDispatcher()).apply { load() }
        vm.register(" Buddy ")
        assertEquals(JsonPrimitive("buddy"), core.last("registerCustomAgent")!!["name"])
        assertEquals("s", vm.state.value.registration?.oauth?.clientSecret)
        assertEquals(2, core.kinds().count { it == "customAgents" })
        vm.dismissRegistration()
        assertNull(vm.state.value.registration)
    }

    @Test
    fun `AATD-57 deleting a Custom Agent needs a confirmation`() = runTest {
        val core = FakeCore().apply { answer("customAgents", """[{"id":"a1","email":"buddy.oc@astrid.cc","name":"buddy","agentName":"buddy","status":"active","registeredAt":"2026-09-01T10:00:00Z"}]""") }
        val vm = CustomAgentsViewModel(core, UnconfinedTestDispatcher()).apply { load() }
        vm.requestDelete(vm.state.value.agents.first())
        assertFalse(core.kinds().contains("deleteCustomAgent"))
        vm.confirmDelete()
        assertEquals(JsonPrimitive("a1"), core.last("deleteCustomAgent")!!["agentId"])
        assertTrue(vm.state.value.agents.isEmpty())
    }

    @Test
    fun `AATD-57 Astrid's model is chosen with setAstridModel, and None clears it`() = runTest {
        val core = FakeCore().apply {
            answer("astridModel", """{"options":[{"id":"u1","name":"Claude","service":"claude","serviceLabel":"claude","isSelected":false}],"selected":null}""")
            answer("setAstridModel") { cmd ->
                val id = (cmd["agentId"] as? JsonPrimitive)?.contentOrNull
                com.gracefultools.astrid.core.CoreJson.parseToJsonElement(
                    """{"options":[{"id":"u1","name":"Claude","service":"claude","isSelected":${id == "u1"}}],"selected":${if (id == null) "null" else "\"$id\""}}""",
                )
            }
        }
        val vm = AstridModelViewModel(core, UnconfinedTestDispatcher()).apply { load() }
        vm.select("u1")
        assertEquals(JsonPrimitive("u1"), core.last("setAstridModel")!!["agentId"])
        assertEquals("u1", vm.state.value.model.selected)
        vm.select(null)
        assertEquals(JsonNull, core.last("setAstridModel")!!["agentId"])
        assertNull(vm.state.value.model.selected)
    }
}
