package com.gracefultools.astrid

import androidx.test.core.app.ApplicationProvider
import com.gracefultools.astrid.ui.common.CoreLabels
import java.io.File
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
    fun `AATD-65 strings iOS lacks are translated too, not left in English`() {
        assertEquals("사이드바 열기", context.getString(R.string.navigation_open_sidebar))
    }

    @Test
    fun `AATD-65 every language has every string, so nothing falls back to English`() {
        val res = File("src/main/res")
        fun names(dir: File) = dir.listFiles { f -> f.name.endsWith(".xml") }.orEmpty()
            .flatMap { f -> Regex("""<string name="([^"]+)"(?![^>]*translatable="false")""").findAll(f.readText()).map { it.groupValues[1] } }
            .toSet()
        val english = names(File(res, "values")) - "app_name"
        val locales = res.listFiles { f -> f.name.startsWith("values-") && File(f, "strings.xml").exists() }.orEmpty()
        assertEquals(11, locales.size)
        for (dir in locales) {
            assertEquals("${dir.name} is missing", emptySet<String>(), english - names(dir))
        }
    }
}
