package com.gracefultools.astrid.ui.settings

import androidx.test.core.app.ApplicationProvider
import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.JsonNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SettingsViewModelTest {
    private val settings = """{"user":{"id":"u1","name":"Ada"},"reminderSettings":{"enablePushReminders":true,"defaultReminderTime":15},
        "offsets":[{"titleKey":"reminder.at_due_time","minutes":0},{"titleKey":"reminder.15_minutes_before","minutes":15}],
        "smartTasks":{"smartTaskCreationEnabled":true}}"""

    private fun vm(core: FakeCore) =
        SettingsViewModel(core, Preferences(ApplicationProvider.getApplicationContext()), UnconfinedTestDispatcher())

    @Test
    fun `AATD-13 settings open from the cache and refresh from the server`() = runTest {
        val core = FakeCore().apply { answer("settings", settings) }
        val vm = vm(core)
        assertEquals(15, vm.state.value.account?.reminderSettings?.defaultReminderTime)
        assertTrue(core.kinds().contains("refreshSettings"))
    }

    @Test
    fun `AATD-13 a reminder choice and a task setting go through the core's journaled writes`() = runTest {
        val core = FakeCore().apply { answer("settings", settings) }
        val vm = vm(core)
        vm.setReminder("defaultReminderTime", 0)
        assertEquals(JsonPrimitive(0), (core.last("updateReminderSettings")!!["changes"] as JsonObject)["defaultReminderTime"])
        vm.setSmartTasks("smartTaskCreationEnabled", false)
        assertEquals(JsonPrimitive(false), (core.last("updateSmartTaskSettings")!!["changes"] as JsonObject)["smartTaskCreationEnabled"])
    }

    @Test
    fun `AATD-13 sign out asks the core to forget everything`() = runTest {
        val core = FakeCore().apply { answer("settings", settings) }
        val vm = vm(core)
        vm.askSignOut()
        vm.signOut()
        assertTrue(core.kinds().contains("signOut"))
        assertTrue(vm.state.value.signedOut)
    }

    // ── AATD-55: Settings as iOS's SettingsView ──────────────────────────────────────────────

    private fun changes(core: FakeCore, kind: String) = core.last(kind)!!["changes"] as JsonObject

    @Test
    fun `AATD-55 Sync Now runs one core sync with the spinner up, then down`() = runTest {
        val core = FakeCore()
        lateinit var vm: SettingsViewModel
        var spinningDuringSync = false
        core.answer("sync") { spinningDuringSync = vm.state.value.syncing; JsonNull }
        vm = vm(core)
        vm.syncNow()
        assertTrue(core.kinds().contains("sync"))
        assertTrue(spinningDuringSync)
        assertFalse(vm.state.value.syncing)
    }

    @Test
    fun `AATD-55 an offline Sync Now is not an error`() = runTest {
        val core = FakeCore().apply {
            answer("sync") { throw com.gracefultools.astrid.core.CoreFailure(com.gracefultools.astrid.core.FailureKind.OFFLINE, "no network") }
        }
        val vm = vm(core)
        vm.syncNow()
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.syncing)
    }

    @Test
    fun `AATD-55 the Outbox shows counts and each dropped write's reason, and Retry revives them`() = runTest {
        val core = FakeCore().apply {
            answer("outboxStats", """{"pending":1,"running":1,"completed":40,"failed":2,"hasUnsentWork":true,
                "deadLetters":[{"kind":"updateTask","error":"403 forbidden"},{"kind":"postComment","error":null}]}""")
        }
        val vm = vm(core)
        val outbox = vm.state.value.outbox!!
        assertEquals(40, outbox.completed)
        assertEquals(2, outbox.failed)
        assertEquals("403 forbidden", outbox.deadLetters[0].error)
        assertFalse(outbox.isHealthy)
        val before = core.kinds().count { it == "outboxStats" }
        vm.retryDropped()
        assertTrue(core.kinds().contains("retryDeadLetters"))
        assertEquals(before + 1, core.kinds().count { it == "outboxStats" })
    }

    @Test
    fun `AATD-55 sign out needs the confirmation, and Cancel sends nothing`() = runTest {
        val core = FakeCore()
        val vm = vm(core)
        vm.signOut()
        assertFalse(core.kinds().contains("signOut"))
        vm.askSignOut()
        assertTrue(vm.state.value.confirmingSignOut)
        vm.cancelSignOut()
        vm.signOut()
        assertFalse(core.kinds().contains("signOut"))
        assertFalse(vm.state.value.signedOut)
    }

    @Test
    fun `AATD-55 the sub tasks picker and Smart Task Creation write the core's smart-task fields`() = runTest {
        val core = FakeCore().apply { answer("settings", settings) }
        val vm = vm(core)
        vm.setSmartTasks("subtaskDisplay", "under_parent")
        assertEquals(JsonPrimitive("under_parent"), changes(core, "updateSmartTaskSettings")["subtaskDisplay"])
        vm.setSmartTasks("showListImages", false)
        assertEquals(JsonPrimitive(false), changes(core, "updateSmartTaskSettings")["showListImages"])
    }

    @Test
    fun `AATD-55 the task-details layout is not written before the server's settings arrive`() = runTest {
        val core = FakeCore().apply {
            answer("refreshSettings") { throw com.gracefultools.astrid.core.CoreFailure(com.gracefultools.astrid.core.FailureKind.OFFLINE, "no network") }
        }
        val vm = vm(core)
        vm.setTaskDisplayMode("project")
        assertFalse(core.kinds().contains("updateSmartTaskSettings"))

        val online = FakeCore().apply { answer("settings", settings) }
        val ready = vm(online)
        ready.setTaskDisplayMode("project")
        assertEquals(JsonPrimitive("project"), changes(online, "updateSmartTaskSettings")["taskDisplayMode"])
    }

    @Test
    fun `AATD-55 the layout control shows only with projectMode, list images follow the core's answer`() = runTest {
        val core = FakeCore().apply { answer("features", """{"projectMode":true,"googleTasks":null,"showListImages":false}""") }
        val vm = vm(core)
        assertTrue(vm.state.value.showsTaskDisplayMode)
        assertFalse(vm.state.value.features.showListImages)
        val off = vm(FakeCore().apply { answer("features", """{"projectMode":null,"showListImages":true}""") })
        assertFalse(off.state.value.showsTaskDisplayMode)
    }

    @Test
    fun `AATD-55 GitHub Issues and Google Tasks rows appear only when the server offers them`() = runTest {
        val core = FakeCore().apply {
            answer("refreshCapabilities", """{"sync":{"githubIssues":true,"googleTasks":true}}""")
            answer("features", """{"googleTasks":false}""")
        }
        val vm = vm(core)
        assertTrue(vm.state.value.serverOffersGithub)
        assertFalse(vm.state.value.showsGoogleTasks)
        val none = vm(FakeCore())
        assertFalse(none.state.value.serverOffersGithub)
        assertFalse(none.state.value.showsGoogleTasks)
    }

    @Test
    fun `AATD-55 quiet hours on send iOS's defaults, off clears both`() = runTest {
        val core = FakeCore().apply { answer("settings", settings) }
        val vm = vm(core)
        vm.setQuietHours(true)
        assertEquals(JsonPrimitive("22:00"), changes(core, "updateReminderSettings")["quietHoursStart"])
        assertEquals(JsonPrimitive("08:00"), changes(core, "updateReminderSettings")["quietHoursEnd"])
        vm.setQuietHours(false)
        assertEquals(JsonNull, changes(core, "updateReminderSettings")["quietHoursStart"])
        assertEquals(JsonNull, changes(core, "updateReminderSettings")["quietHoursEnd"])
    }

    @Test
    fun `AATD-55 the daily digest goes with the device's zone when none is stored`() = runTest {
        val core = FakeCore().apply { answer("settings", settings) }
        val vm = vm(core)
        vm.setDailyDigest(true, deviceZone = "Europe/Paris")
        assertEquals(JsonPrimitive(true), changes(core, "updateReminderSettings")["enableDailyDigest"])
        assertEquals(JsonPrimitive("Europe/Paris"), changes(core, "updateReminderSettings")["dailyDigestTimezone"])
        vm.setReminders(mapOf("dailyDigestTime" to "07:30"))
        assertEquals(JsonPrimitive("07:30"), changes(core, "updateReminderSettings")["dailyDigestTime"])
    }
}
