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

    private fun summary(json: String) = CoreLabels.summary(context, CoreJson.parseToJsonElement(json))

    private val nthTuesday = """[{"key":"repeat.every_n_months","count":1},{"key":"repeat.on_nth_weekday","count":3,"values":["tuesday"]}]"""
    private val nthSaturday = """[{"key":"repeat.every_n_months","count":2},{"key":"repeat.on_nth_weekday","count":3,"values":["saturday"]}]"""
    private val monthDay = """[{"key":"repeat.every_n_months","count":1},{"key":"repeat.on_day_of_month","count":15}]"""
    private val march15 = """[{"key":"repeat.every_n_years","count":1},{"key":"repeat.on_month_and_day","count":15,"values":["3"]}]"""
    private val weekly = """[{"key":"repeat.every_n_weeks","count":1},{"key":"repeat.on_weekdays","values":["tuesday","saturday"]}]"""

    @Test
    fun `AATD-70 English says the ordinal, the weekday and the month as iOS does`() {
        assertEquals("Every month on the 3rd Tuesday", summary(nthTuesday))
        assertEquals("Every month on the 15th", summary(monthDay))
        assertEquals("Every year on March 15th", summary(march15))
        assertEquals("Every week on Tuesday, Saturday", summary(weekly))
    }

    @Test
    @Config(qualifiers = "de")
    fun `AATD-70 German fills its fragments with German words`() {
        assertEquals("Jeden Monat am 3. Dienstag", summary(nthTuesday))
        assertEquals("Jeden Monat am 15.", summary(monthDay))
        assertEquals("Jedes Jahr am 15. März", summary(march15))
    }

    @Test
    @Config(qualifiers = "pt")
    fun `AATD-70 Portuguese reads for sabado and domingo too`() {
        assertEquals("A cada 2 meses na 3ª semana, sábado", summary(nthSaturday))
        assertEquals("Todo mês no dia 15", summary(monthDay))
        assertEquals("Todo ano em 15 de março", summary(march15))
    }

    @Test
    @Config(qualifiers = "ja")
    fun `AATD-70 Japanese fragments are sentences, not bare placeholders`() {
        assertEquals("毎月 第3火曜日", summary(nthTuesday))
        assertEquals("毎年 3月15日", summary(march15))
    }

    @Test
    @Config(qualifiers = "ru")
    fun `AATD-70 Russian month and day reads in the genitive`() {
        assertEquals("Каждый год 15 марта", summary(march15))
    }

    @Test
    @Config(qualifiers = "b+zh+Hans")
    fun `AATD-70 Chinese writes the month as a number and lists days with the enumeration comma`() {
        assertEquals("每年 3月15日", summary(march15))
        assertEquals("每月 15日", summary(monthDay))
        assertEquals("每周 在星期二、星期六", summary(weekly))
    }

    @Test
    fun `AATD-70 only the from-due-date qualifier takes a comma`() {
        assertEquals(
            "Every week on Tuesday, Saturday, from due date",
            summary("""[{"key":"repeat.every_n_weeks","count":1},{"key":"repeat.on_weekdays","values":["tuesday","saturday"]},{"key":"repeat.from_due_date"}]"""),
        )
    }
}
