package com.gracefultools.astrid.ui.board

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Done column's completed-date pill (Done column date pill, 2026-11-19): label in the reader's locale. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CompletedPillLabelTest {
    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun `a day this year is the short month and day`() {
        assertEquals("Nov 19", completedPillLabel("2026-11-19", today, Locale.US))
        assertEquals("Jan 3", completedPillLabel("2026-01-03", today, Locale.US))
    }

    @Test
    fun `a day in another year adds the year`() {
        assertEquals("Nov 19, 2025", completedPillLabel("2025-11-19", today, Locale.US))
    }

    @Test
    fun `the label follows the locale`() {
        assertEquals("19. Nov.", completedPillLabel("2026-11-19", today, Locale.GERMANY))
    }

    @Test
    fun `no day, or one that does not parse, draws no pill`() {
        assertNull(completedPillLabel(null, today, Locale.US))
        assertNull(completedPillLabel("soon", today, Locale.US))
    }
}
