package com.gracefultools.astrid

import androidx.test.core.app.ApplicationProvider
import com.gracefultools.astrid.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AATD-30, Settings' half: it needs a Context for Preferences, so it runs under Robolectric. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LiveChangesSettingsTest {
    private val delivered = """{"change":"delivered","taskIds":[],"listIds":[],"commentTaskIds":[],"channelIds":[],"undescribed":false,"pending":0,"running":0,"failed":0}"""

    @Test
    fun `AATD-30 Settings' waiting count follows deliveries`() = runTest {
        val core = FakeCore()
        SettingsViewModel(core, Preferences(ApplicationProvider.getApplicationContext()), UnconfinedTestDispatcher())
        val before = core.kinds().count { it == "outboxStats" }
        core.emit("delivered", delivered)
        assertEquals(before + 1, core.kinds().count { it == "outboxStats" })
    }
}
