package com.gracefultools.astrid

import androidx.test.core.app.ApplicationProvider
import com.gracefultools.astrid.ui.common.CoreLabels
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AATD-23: the app speaks the languages iOS ships, in iOS's words, and so do the core's labels. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LocalisationTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    @Config(qualifiers = "de")
    fun `AATD-23 German reads as iOS's German`() {
        assertEquals("Meine Aufgaben", context.getString(R.string.navigation_my_tasks))
        assertEquals("Wöchentlich", CoreLabels.resolve(context, "repeating.weekly"))
    }

    @Test
    @Config(qualifiers = "ja")
    fun `AATD-23 Japanese reads as iOS's Japanese`() {
        assertEquals("マイタスク", context.getString(R.string.navigation_my_tasks))
    }

    @Test
    @Config(qualifiers = "ko")
    fun `AATD-23 an untranslated string falls back to English rather than going blank`() {
        assertEquals("Open the sidebar", context.getString(R.string.navigation_open_sidebar))
    }
}
