package com.gracefultools.astrid.ui.common

import androidx.test.core.app.ApplicationProvider
import com.gracefultools.astrid.core.CoreJson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CoreLabelsTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `AATD-9 a core key reads as the shared words`() {
        assertEquals("Weekly", CoreLabels.resolve(context, "repeating.weekly"))
        assertEquals("15 minutes before", CoreLabels.resolve(context, "reminder.15_minutes_before"))
        assertEquals("Next week", CoreLabels.resolve(context, "picker.next_week"))
    }

    @Test
    fun `AATD-9 a key with a count fills it in, and an unknown key still reads`() {
        assertEquals("Every 3 days", CoreLabels.resolve(context, "repeat.every_n_days", "3"))
        assertEquals("Brand new thing", CoreLabels.resolve(context, "repeat.brand_new_thing"))
    }

    @Test
    fun `AATD-9 a repeat summary joins its parts`() {
        val parts = CoreJson.parseToJsonElement("""[{"key":"repeat.every_n_weeks","count":2},{"key":"repeat.on_weekdays","values":["monday","wednesday"]}]""")
        assertEquals("Every 2 weeks on Monday, Wednesday", CoreLabels.summary(context, parts))
    }
}
