package com.gracefultools.astrid.ui.lists

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListSettingsViewModelTest {
    private val settings = """{"listId":"l1","name":"Home","color":"#22c55e","colorChoices":["#22c55e","#3b82f6"],
        "privacy":"PRIVATE","canManageMembers":true,"canManageList":true,"canDeleteList":true,"canLeave":false,
        "members":[{"userId":"u-bo","role":"member","user":{"id":"u-bo","name":"Bo"}}]}"""

    private fun vm(core: FakeCore) = ListSettingsViewModel(core, "l1", UnconfinedTestDispatcher())
    private fun TestScope.timedVm(core: FakeCore) = ListSettingsViewModel(core, "l1", UnconfinedTestDispatcher(testScheduler))

    private fun FakeCore.changes(): JsonObject = last("updateList")!!["changes"]!!.jsonObject

    private val owned = """{"listId":"l1","name":"Home","ownerId":"u-me","projectId":null,"color":"#22c55e","colorChoices":["#22c55e","#3b82f6"],
        "privacy":"SHARED","canManageMembers":true,"canManageList":true,"canDeleteList":true,"canLeave":false,"canTransferOwnership":true,
        "currentUserId":"u-me","defaults":{"assigneeId":null,"priority":2,"repeating":"weekly","dueDate":"tomorrow","dueTime":"09:00"},
        "members":[{"userId":"u-me","role":"admin","user":{"id":"u-me","name":"Me","email":"me@example.com"}},
                   {"userId":"u-bo","role":"member","user":{"id":"u-bo","name":"Bo","email":"bo@example.com"}}],
        "description":"Our house","owner":{"id":"u-me","name":"Me","email":"me@example.com"},
        "invitations":[{"id":"i1","email":"cy@example.com","role":"admin"}],
        "recentlyCompletedWindow":{"kind":"duration","amount":7,"unit":"day"}}"""
    // The roster's rules (an invitation already accepted is left out) are the core's: AATD-44 in
    // astrid-core's dispatch tests. listMembers answers them; the app draws what it is given.

    private fun ownedCore() = FakeCore().apply { answer("listMembers", owned) }

    @Test
    fun `AATD-15 the screen opens from the cache and refreshes from the server`() = runTest {
        val core = FakeCore().apply { answer("listMembers", settings) }
        val vm = vm(core)
        assertEquals("Home", vm.state.value.settings?.name)
        assertEquals("Bo", vm.state.value.settings?.members?.first()?.display)
        assertTrue(core.kinds().contains("refreshListMembers"))
    }

    @Test
    fun `AATD-15 a rename is sent trimmed, an unchanged one is not`() = runTest {
        val core = FakeCore().apply { answer("listMembers", settings) }
        val vm = vm(core)
        vm.editName("Home")
        vm.flushEdits()
        assertFalse(core.kinds().contains("updateList"))
        vm.editName("  House ")
        vm.flushEdits()
        assertEquals(JsonPrimitive("House"), (core.last("updateList")!!["changes"] as JsonObject)["name"])
    }

    @Test
    fun `AATD-15 a refused invite says why — membership is online-only (D31)`() = runTest {
        val core = FakeCore().apply {
            answer("listMembers", settings)
            answer("inviteToList") { throw CoreFailure(FailureKind.REFUSED, "already a member") }
        }
        val vm = vm(core)
        vm.invite("not an email")
        assertFalse(core.kinds().contains("inviteToList"))
        vm.invite("bo@example.com")
        assertEquals("already a member", vm.state.value.error)
    }

    @Test
    fun `AATD-15 deleting closes the screen`() = runTest {
        val core = FakeCore().apply { answer("listMembers", settings) }
        val vm = vm(core)
        vm.delete()
        assertTrue(vm.state.value.gone)
        assertEquals(JsonPrimitive("l1"), core.last("deleteList")!!["listId"])
    }

    // ---- AATD-43: List Settings, Admin tab ----

    @Test
    fun `AATD-43 tabs are Filters and Members, plus Admin only for whoever may manage the list`() = runTest {
        assertEquals(listOf(ListSettingsTab.FILTERS, ListSettingsTab.MEMBERS, ListSettingsTab.ADMIN), vm(ownedCore()).state.value.tabs)
        val member = FakeCore().apply { answer("listMembers", owned.replace("\"canManageList\":true", "\"canManageList\":false")) }
        assertEquals(listOf(ListSettingsTab.FILTERS, ListSettingsTab.MEMBERS), vm(member).state.value.tabs)
    }

    @Test
    fun `AATD-43 the description and defaults come from the core`() = runTest {
        val s = vm(ownedCore()).state.value
        assertEquals("Our house", s.detail?.description)
        assertEquals(2, s.settings?.defaults?.priority)
        assertEquals("tomorrow", s.settings?.defaults?.dueDate)
        assertEquals("09:00", s.settings?.defaults?.dueTime)
        assertEquals(RecentlyCompletedPreset.LAST_7_DAYS, s.recentlyCompletedPreset)
    }

    @Test
    fun `AATD-43 name and description are saved as typed, once the typing settles`() = runTest {
        val core = ownedCore()
        val vm = timedVm(core)
        vm.editName("Hou")
        vm.editName("House")
        vm.editDescription("Our new house")
        assertFalse(core.kinds().contains("updateList"))
        advanceTimeBy(ListSettingsViewModel.INFO_DEBOUNCE_MS + 1)
        assertEquals(1, core.kinds().count { it == "updateList" })
        assertEquals(JsonPrimitive("House"), core.changes()["name"])
        assertEquals(JsonPrimitive("Our new house"), core.changes()["description"])
    }

    @Test
    fun `AATD-43 leaving the screen sends an edit still waiting on the debounce, and a blank name is never sent`() = runTest {
        val core = ownedCore()
        val vm = timedVm(core)
        vm.editName("   ")
        vm.flushEdits()
        assertFalse(core.kinds().contains("updateList"))
        vm.editDescription("Mine")
        vm.flushEdits()
        assertEquals(JsonPrimitive("Mine"), core.changes()["description"])
        assertFalse(core.changes().containsKey("name"))
    }

    @Test
    fun `AATD-43 a swatch is saved at once, the current colour is not re-sent, a custom colour waits 400ms`() = runTest {
        val core = ownedCore()
        val vm = timedVm(core)
        vm.pickColor("#22C55E")
        assertFalse(core.kinds().contains("updateList"))
        vm.pickColor("#3b82f6")
        assertEquals(JsonPrimitive("#3b82f6"), core.changes()["color"])
        vm.pickCustomColor("#123456")
        vm.pickCustomColor("#abcdef")
        advanceTimeBy(ListSettingsViewModel.CUSTOM_COLOR_DEBOUNCE_MS - 1)
        assertEquals(JsonPrimitive("#3b82f6"), core.changes()["color"])
        advanceTimeBy(2)
        assertEquals(JsonPrimitive("#abcdef"), core.changes()["color"])
        assertEquals(2, core.kinds().count { it == "updateList" })
    }

    @Test
    fun `AATD-43 the list image is uploaded through setListImage`() = runTest {
        val core = ownedCore().apply { answer("listImage", """{"source":"/cache/l1.png"}""") }
        val vm = vm(core)
        vm.setImage("/data/outgoing/pic.png")
        assertEquals(JsonPrimitive("/data/outgoing/pic.png"), core.last("setListImage")!!["path"])
        assertEquals("/cache/l1.png", vm.state.value.imageSource)
    }

    @Test
    fun `AATD-43 the image row follows the core's showListImages`() = runTest {
        val core = ownedCore().apply { answer("features", """{"showListImages":false}""") }
        assertFalse(vm(core).state.value.showListImages)
    }

    @Test
    fun `AATD-43 default task settings go through updateList's default fields`() = runTest {
        val core = ownedCore()
        val vm = vm(core)
        vm.setDefaultPriority(3)
        assertEquals(JsonPrimitive(3), core.changes()["defaultPriority"])
        vm.setDefaultAssignee("unassigned")
        assertEquals(JsonPrimitive("unassigned"), core.changes()["defaultAssigneeId"])
        vm.setDefaultAssignee(null)
        assertEquals(JsonNull, core.changes()["defaultAssigneeId"])
        vm.setDefaultDueDate("next_week")
        assertEquals(JsonPrimitive("next_week"), core.changes()["defaultDueDate"])
        vm.setDefaultDueTime("17:00")
        assertEquals(JsonPrimitive("17:00"), core.changes()["defaultDueTime"])
        vm.setDefaultDueTime(null)
        assertEquals(JsonNull, core.changes()["defaultDueTime"])
        vm.setDefaultRepeating("monthly")
        assertEquals(JsonPrimitive("monthly"), core.changes()["defaultRepeating"])
    }

    @Test
    fun `AATD-43 Create Board turns the list into a board, and a refusal shows inline`() = runTest {
        val core = ownedCore()
        val vm = vm(core)
        vm.createBoard()
        assertEquals(JsonPrimitive("l1"), core.last("createBoardForList")!!["listId"])
        assertFalse(vm.state.value.boardBusy)
        core.answer("createBoardForList") { throw CoreFailure(FailureKind.OFFLINE, "offline") }
        vm.createBoard()
        assertEquals(BoardFailure(creating = true, message = "offline"), vm.state.value.boardError)
    }

    @Test
    fun `AATD-43 Disable Board deletes the list's project`() = runTest {
        val core = FakeCore().apply { answer("listMembers", owned.replace("\"projectId\":null", "\"projectId\":\"p9\"")) }
        val vm = vm(core)
        vm.disableBoard()
        assertEquals(JsonPrimitive("p9"), core.last("deleteProject")!!["projectId"])
    }

    @Test
    fun `AATD-43 the recently completed window is stored as the core's tagged window`() = runTest {
        val core = ownedCore()
        val vm = vm(core)
        vm.setRecentlyCompleted(RecentlyCompletedPreset.SINCE_LAST_MONDAY)
        assertEquals("""{"kind":"since-weekday","weekday":1}""", core.changes()["recentlyCompletedWindow"].toString())
        vm.setRecentlyCompleted(RecentlyCompletedPreset.DEFAULT_24H)
        assertEquals(JsonNull, core.changes()["recentlyCompletedWindow"])
        vm.setRecentlyCompleted(RecentlyCompletedPreset.SINCE_SPECIFIC_DATE, "2026-10-01")
        assertEquals("""{"kind":"since-date","date":"2026-10-01"}""", core.changes()["recentlyCompletedWindow"].toString())
        assertEquals(RecentlyCompletedPreset.SINCE_SPECIFIC_DATE, RecentlyCompletedPreset.forWindow(core.changes()["recentlyCompletedWindow"]!!.jsonObject))
    }

    @Test
    fun `AATD-43 the coding agent's repository comes from listAgentOptions and is saved on the list`() = runTest {
        val core = ownedCore().apply {
            answer("listAgentOptions", """{"agents":[{"id":"a1","name":"Claude"}],"repositories":[{"fullName":"acme/app","name":"app"}],"githubConnected":true}""")
        }
        val vm = vm(core)
        vm.loadAgentOptions()
        assertTrue(vm.state.value.hasAgents)
        assertEquals("acme/app", vm.state.value.agentOptions?.repositories?.single()?.fullName)
        vm.setGithubRepository("acme/app")
        assertEquals(JsonPrimitive("acme/app"), core.changes()["githubRepositoryId"])
    }

    @Test
    fun `AATD-43 nothing is deleted until the screen confirms, then the list goes and the screen closes`() = runTest {
        val core = ownedCore()
        val vm = vm(core)
        assertFalse(core.kinds().contains("deleteList"))
        vm.delete()
        assertTrue(vm.state.value.gone)
    }

    // ---- AATD-44: Members tab ----

    @Test
    fun `AATD-44 the owner is drawn first, members without them, invitations not yet on the roster`() = runTest {
        val s = vm(ownedCore()).state.value
        assertEquals("u-me", s.owner?.id)
        assertEquals(listOf("u-bo"), s.otherMembers.map { it.who })
        assertEquals(listOf("cy@example.com"), s.pendingInvitations.map { it.email })
    }

    @Test
    fun `AATD-44 Make Public, and Make Private which sets SHARED`() = runTest {
        val core = ownedCore()
        val vm = vm(core)
        vm.togglePublic()
        assertEquals(JsonPrimitive("PUBLIC"), core.changes()["privacy"])
        core.answer("listMembers", owned.replace("\"SHARED\"", "\"PUBLIC\""))
        vm.load()
        vm.togglePublic()
        assertEquals(JsonPrimitive("SHARED"), core.changes()["privacy"])
    }

    @Test
    fun `AATD-44 Add Member invites with the chosen role`() = runTest {
        val core = ownedCore()
        vm(core).invite("dee@example.com", "admin")
        val sent = core.last("inviteToList")!!
        assertEquals(JsonPrimitive("dee@example.com"), sent["email"])
        assertEquals(JsonPrimitive("admin"), sent["role"])
    }

    @Test
    fun `AATD-44 roles, removal and cancelling an invitation are core commands`() = runTest {
        val core = ownedCore()
        val vm = vm(core)
        vm.setRole("u-bo", "admin")
        assertEquals(JsonPrimitive("admin"), core.last("setMemberRole")!!["role"])
        vm.remove("u-bo")
        assertEquals(JsonPrimitive("u-bo"), core.last("removeMember")!!["userId"])
        vm.cancelInvitation("cy@example.com")
        assertEquals(JsonPrimitive("cy@example.com"), core.last("cancelInvitation")!!["email"])
    }

    @Test
    fun `AATD-44 suggestions come from searchUsers, leaving out people already on the list`() = runTest {
        val core = ownedCore().apply {
            answer("searchUsers", """[{"id":"u-bo","name":"Bo","email":"bo@example.com"},{"id":"u-di","name":"Di","email":"di@example.com"}]""")
        }
        val vm = timedVm(core)
        vm.search("d")
        assertFalse(core.kinds().contains("searchUsers"))
        vm.search("di")
        advanceTimeBy(ListSettingsViewModel.SEARCH_DEBOUNCE_MS + 1)
        assertEquals(listOf("di@example.com"), vm.state.value.suggestions.map { it.email })
        assertEquals("""["l1"]""", core.last("searchUsers")!!["listIds"].toString())
    }

    @Test
    fun `AATD-44 leaving happens only when confirmed, and closes the screen`() = runTest {
        val core = FakeCore().apply { answer("listMembers", settings.replace("\"canLeave\":false", "\"canLeave\":true")) }
        val vm = vm(core)
        assertFalse(core.kinds().contains("leaveList"))
        vm.leave()
        assertEquals(JsonPrimitive("l1"), core.last("leaveList")!!["listId"])
        assertTrue(vm.state.value.gone)
    }

    @Test
    fun `AATD-44 the owner's transfer asks eligibleNewOwners, then hands over and leaves in one call`() = runTest {
        val core = ownedCore().apply {
            answer("eligibleNewOwners", """{"availability":"available","owners":[{"id":"u-bo","name":"Bo"}]}""")
        }
        val vm = vm(core)
        vm.loadTransfer()
        assertEquals("available", vm.state.value.transfer?.availability)
        assertEquals("u-bo", vm.state.value.transfer?.owners?.single()?.id)
        vm.transferOwnership("u-bo")
        assertEquals(JsonPrimitive("u-bo"), core.last("transferListOwnership")!!["newOwnerId"])
        assertFalse(core.kinds().contains("leaveList"))
        assertTrue(vm.state.value.gone)
    }

    @Test
    fun `AATD-44 a member who does not own the list is never probed for a transfer`() = runTest {
        val core = FakeCore().apply { answer("listMembers", settings) }
        vm(core).loadTransfer()
        assertFalse(core.kinds().contains("eligibleNewOwners"))
    }

    @Test
    fun `AATD-44 an agent is added by its address and removed by its id`() = runTest {
        val core = ownedCore()
        val vm = vm(core)
        assertTrue(vm.state.value.isMember("u-bo"))
        assertFalse(vm.state.value.isMember("a1"))
        vm.addAgent("claude@astrid.cc")
        assertEquals(JsonPrimitive("claude@astrid.cc"), core.last("inviteToList")!!["email"])
        vm.removeAgent("a1")
        assertEquals(JsonPrimitive("a1"), core.last("removeMember")!!["userId"])
        assertNull(vm.state.value.error)
    }

    @Test
    fun `AATD-44 Share List mints a link with shareList`() = runTest {
        val core = ownedCore().apply { answer("shareList", """{"url":"https://astrid.cc/s/abc"}""") }
        val vm = vm(core)
        vm.requestShareLink()
        assertEquals(JsonPrimitive("l1"), core.last("shareList")!!["listId"])
        assertEquals("https://astrid.cc/s/abc", vm.state.value.shareLink)
        assertFalse(vm.state.value.shareFailed)
    }

    @Test
    fun `AATD-44 a share link that cannot be minted says so`() = runTest {
        val core = ownedCore().apply { answer("shareList") { throw CoreFailure(FailureKind.OFFLINE, "offline") } }
        val vm = vm(core)
        vm.requestShareLink()
        assertTrue(vm.state.value.shareFailed)
        assertNull(vm.state.value.shareLink)
    }
}
