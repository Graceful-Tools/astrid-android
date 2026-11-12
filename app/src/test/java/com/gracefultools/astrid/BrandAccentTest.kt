package com.gracefultools.astrid

import androidx.compose.ui.graphics.Color
import com.gracefultools.astrid.ui.theme.Accent
import com.gracefultools.astrid.ui.theme.AccentHover
import com.gracefultools.astrid.ui.theme.AccentText
import com.gracefultools.astrid.ui.theme.LightColors
import com.gracefultools.astrid.ui.theme.ThemeMode
import com.gracefultools.astrid.ui.theme.brandColor
import com.gracefultools.astrid.ui.theme.colorsFor
import com.gracefultools.astrid.ui.theme.materialScheme
import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * AATD-75: the in-app accent is the brand's (iOS `Brand.accentColor`, `accentHoverColor`,
 * `accentTextColor`), set from `brand.properties` like the rest of the brand, and an unconfigured
 * build keeps Astrid's exact colours.
 */
class BrandAccentTest {
    @Test
    fun `AATD-75 an unconfigured build keeps Astrid's exact accent colours`() {
        assertEquals("#2563eb", Brand.accentColorHex)
        assertEquals("#1d4ed8", Brand.accentHoverColorHex)
        assertEquals("#ffffff", Brand.accentTextColorHex)
        assertEquals(Color(37, 99, 235), Accent)
        assertEquals(Color(29, 78, 216), AccentHover)
        assertEquals(Color.White, AccentText)
    }

    @Test
    fun `AATD-75 every palette and the Material scheme draw the brand's accent`() {
        val accent = brandColor(Brand.accentColorHex)
        assertEquals(accent, LightColors.priority[1])
                for (mode in ThemeMode.entries) for (systemDark in listOf(false, true)) {
            val c = colorsFor(mode, systemDark)
            assertEquals(accent, c.accent)
            assertEquals(brandColor(Brand.accentHoverColorHex), c.accentHover)
            assertEquals(brandColor(Brand.accentTextColorHex), c.accentText)
            // AWTD-1598: Dark draws focus in the accent's light ink, a light theme in the accent.
            assertEquals(if (c.isDark) c.accentInk else accent, c.borderFocus)
            assertEquals(accent, materialScheme(c).primary)
        }
    }

    @Test
    fun `AATD-75 a malformed accent falls back to Astrid's, as on iOS`() {
        assertEquals("#2563eb", Brand.resolveAccentHex(null))
        assertEquals("#2563eb", Brand.resolveAccentHex(""))
        assertEquals("#2563eb", Brand.resolveAccentHex("blue"))
        assertEquals("#2563eb", Brand.resolveAccentHex("#12345"))
        assertEquals("#1d4ed8", Brand.resolveAccentHex("#zzzzzz", fallback = "#1d4ed8"))
        assertEquals("#0d9488", Brand.resolveAccentHex(" #0D9488 "))
        assertEquals("#0d9488", Brand.resolveAccentHex("0d9488"))
        assertEquals("#0d948880", Brand.resolveAccentHex("#0d948880"))
        assertEquals(Color(13, 148, 136), brandColor("#0d9488"))
        assertEquals(Color(13, 148, 136, 128), brandColor("#0d948880"))
    }

    @Test
    fun `AATD-75 the example brand's accent reaches the theme as its own colour`() {
        val profile = Properties().apply { File("../brands/example/brand.properties").reader().use(::load) }
        fun accent(key: String, fallback: String) = brandColor(Brand.resolveAccentHex(profile.getProperty(key), fallback))
        val brandAccent = accent("astridAccentColor", Brand.DEFAULT_ACCENT_HEX)
        assertEquals(Color(13, 148, 136), brandAccent)
        assertNotEquals(Accent, brandAccent)
        assertEquals(Color(15, 118, 110), accent("astridAccentHoverColor", Brand.DEFAULT_ACCENT_HOVER_HEX))
        assertEquals(Color.White, accent("astridAccentTextColor", "#ffffff"))
    }
}
