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
import org.junit.Assert.assertTrue
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
        vm.signOut()
        assertTrue(core.kinds().contains("signOut"))
        assertTrue(vm.state.value.signedOut)
    }
}
