package com.gracefultools.astrid

import androidx.test.core.app.ApplicationProvider
import com.gracefultools.astrid.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A brand's default theme (`astridDefaultTheme`, AATD-90): where a fresh install starts, as web's
 * `NEXT_PUBLIC_BRAND_DEFAULT_THEME` is where a visitor starts. A stored choice still wins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BrandDefaultThemeTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `AATD-90 a fresh install starts on the brand's default theme`() {
        assertEquals(ThemeMode.LIGHT, Preferences(context, defaultTheme = ThemeMode.LIGHT).theme.value)
    }

    @Test
    fun `AATD-90 a theme the user picked outranks the brand's default`() {
        Preferences(context, defaultTheme = ThemeMode.LIGHT).setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, Preferences(context, defaultTheme = ThemeMode.LIGHT).theme.value)
    }

    @Test
    fun `AATD-90 an unknown theme name is the fallback, never a crash`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.of("sepia", fallback = ThemeMode.LIGHT))
        assertEquals(ThemeMode.AUTO, ThemeMode.of(" Auto ", fallback = ThemeMode.LIGHT))
    }

    @Test
    fun `AATD-90 an unconfigured build starts on Ocean and launches on Ocean's cyan`() {
        assertEquals(ThemeMode.OCEAN, Brand.defaultTheme)
        assertEquals(ThemeMode.OCEAN, Preferences(context).theme.value)
        assertEquals(0xFF88DCF8.toInt(), context.getColor(R.color.launch_bg))
    }
}
