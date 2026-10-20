package com.gracefultools.astrid.ui.connections

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionsViewModelTest {
    private val panel = """{
        "connections":[
          {"id":"c1","kind":"oauthClient","category":"app","owner":"you","name":"My script","scopes":["tasks:read"],"createdAt":"2026-09-01T10:00:00Z","status":"active","revocable":true,"manageIn":"connections","editableClientId":"astrid_client_1"},
          {"id":"tok-1","kind":"accessToken","category":"token","name":"Copilot","actsAs":"copilot@astrid.cc","scopes":["*"],"createdAt":"2026-09-01T10:00:00Z","status":"active","revocable":true,"manageIn":"agents"},
          {"id":"x","kind":"unknown","category":"unknown","name":"Something","createdAt":"2026-09-01T10:00:00Z","status":"active","revocable":false}
        ],
        "sections":[
          {"heading":{"by":"category","category":"app"},"titleKey":"connections.category.app","showsOwnerBadges":true,"rows":[{"id":"c1","kind":"oauthClient","owner":"you","name":"My script","revocable":true,"editableClientId":"astrid_client_1"}]},
          {"heading":{"by":"category","category":"token"},"titleKey":"connections.category.token","showsOwnerBadges":true,"rows":[{"id":"tok-1","kind":"accessToken","name":"Copilot","revocable":true,"manageIn":"agents"}]}
        ],
        "reviewCount":0
    }"""

    private fun core() = FakeCore().apply { answer("connections", panel) }
    private fun vm(core: FakeCore) = ConnectionsViewModel(core, UnconfinedTestDispatcher()).apply { load() }

    @Test
    fun `AATD-57 connections arrive grouped in the core's sections`() = runTest {
        val vm = vm(core())
        val sections = vm.state.value.panel.sections
        assertEquals(listOf("connections.category.app", "connections.category.token"), sections.map { it.titleKey })
        assertTrue(sections.first().showsOwnerBadges)
        assertEquals("astrid_client_1", sections.first().rows.first().editableClientId)
        assertTrue(sections[1].rows.first().managedOnAgentsPage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `AATD-57 Revoke asks first and revokes nothing until confirmed`() = runTest {
        val core = core()
        val vm = vm(core)
        val row = vm.state.value.panel.connections.first { it.id == "tok-1" }
        vm.requestRevoke(row)
        assertEquals(row, vm.state.value.pendingRevoke)
        assertFalse(core.kinds().contains("revokeConnection"))
        vm.cancelRevoke()
        assertNull(vm.state.value.pendingRevoke)
        vm.confirmRevoke()
        assertFalse(core.kinds().contains("revokeConnection"))
    }

    @Test
    fun `AATD-57 a confirmed Revoke sends the row's kind and id, then re-reads`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.requestRevoke(vm.state.value.panel.connections.first { it.id == "tok-1" })
        vm.confirmRevoke()
        val sent = core.last("revokeConnection")!!
        assertEquals(JsonPrimitive("accessToken"), sent["connectionKind"])
        assertEquals(JsonPrimitive("tok-1"), sent["id"])
        assertEquals(2, core.kinds().count { it == "connections" })
        assertNull(vm.state.value.pendingRevoke)
        assertTrue(vm.state.value.revoking.isEmpty())
    }

    @Test
    fun `AATD-57 a row the core says is not revocable cannot be asked to revoke`() = runTest {
        val vm = vm(core())
        vm.requestRevoke(vm.state.value.panel.connections.first { it.id == "x" })
        assertNull(vm.state.value.pendingRevoke)
    }

    private val check = """{"problem":null,"canSend":true,"grantTypes":["client_credentials"],"redirectUris":[],"scopes":["tasks:read","tasks:write"]}"""

    @Test
    fun `AATD-57 a new connection is judged by the core and created with its fields, the secret shown once`() = runTest {
        val core = FakeCore().apply {
            answer("checkOAuthClientDraft", check)
            answer("createOAuthClient", """{"clientId":"astrid_client_9","clientSecret":"secret","name":"Script"}""")
        }
        val vm = OAuthClientEditorViewModel(core, null, UnconfinedTestDispatcher()).apply { load() }
        assertEquals(listOf("tasks:read", "tasks:write"), vm.state.value.check.scopes)
        vm.setName("Script")
        vm.toggleScope("tasks:read")
        vm.save()
        val sent = core.last("createOAuthClient")!!
        assertEquals(JsonPrimitive("Script"), sent["name"])
        assertEquals(listOf("tasks:read"), sent["scopes"]!!.jsonArray.map { (it as JsonPrimitive).content })
        assertEquals(listOf("client_credentials"), sent["grantTypes"]!!.jsonArray.map { (it as JsonPrimitive).content })
        assertEquals("secret", vm.state.value.minted?.clientSecret)
        assertFalse(vm.state.value.finished)
        vm.dismissMinted()
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `AATD-57 a grant toggle is the core's, with its pair`() = runTest {
        val core = FakeCore().apply {
            answer("checkOAuthClientDraft") { cmd ->
                val toggled = (cmd["toggleGrant"] as? JsonPrimitive)?.content
                val grants = if (toggled == "authorization_code") """["client_credentials","authorization_code","refresh_token"]""" else """["client_credentials"]"""
                CoreJson.parseToJsonElement("""{"problem":${if (toggled != null) """{"key":"redirectRequired"}""" else "null"},"canSend":${toggled == null},"grantTypes":$grants,"scopes":[]}""")
            }
        }
        val vm = OAuthClientEditorViewModel(core, null, UnconfinedTestDispatcher()).apply { load() }
        vm.toggleGrant("authorization_code")
        assertEquals(JsonPrimitive("authorization_code"), core.last("checkOAuthClientDraft")!!["toggleGrant"])
        assertEquals(listOf("client_credentials", "authorization_code", "refresh_token"), vm.state.value.draft.grantTypes)
        assertEquals("redirectRequired", vm.state.value.check.problem?.key)
        vm.save()
        assertFalse(core.kinds().contains("createOAuthClient"))
    }

    @Test
    fun `AATD-57 Edit loads the client and writes its redirect URIs by client id`() = runTest {
        val core = FakeCore().apply {
            answer("loadOAuthClient", """{"clientId":"astrid_client_1","name":"My script","redirectUris":["https://a.test/cb"],"grantTypes":["authorization_code","refresh_token"],"scopes":["tasks:read"]}""")
            answer("checkOAuthClientDraft", check)
        }
        val vm = OAuthClientEditorViewModel(core, "astrid_client_1", UnconfinedTestDispatcher()).apply { load() }
        assertEquals(JsonPrimitive("astrid_client_1"), core.last("loadOAuthClient")!!["clientId"])
        assertEquals("https://a.test/cb", vm.state.value.draft.redirectUriText)
        assertTrue(vm.state.value.isEditing)
        vm.setRedirectUris("https://a.test/cb\nhttps://b.test/cb")
        vm.save()
        val sent = core.last("updateOAuthClient")!!
        assertEquals(JsonPrimitive("astrid_client_1"), sent["clientId"])
        assertEquals(JsonPrimitive("https://a.test/cb\nhttps://b.test/cb"), sent["redirectUriText"])
        assertTrue(vm.state.value.finished)
    }
}
