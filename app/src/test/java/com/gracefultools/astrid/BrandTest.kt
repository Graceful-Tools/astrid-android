package com.gracefultools.astrid

import com.gracefultools.astrid.ui.sidebar.defaultListImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** AATD-71: the server is a build setting, and every web link is made from it. */
class BrandTest {
    @Test
    fun `AATD-71 an unconfigured build is hosted Astrid, exactly as before`() {
        assertEquals("https://astrid.cc", BuildConfig.ASTRID_BASE_URL)
        assertEquals("https://astrid.cc", Brand.baseUrl)
        assertEquals("support@astrid.cc", Brand.supportEmail)
        assertEquals("https://astrid.cc/settings/account", Brand.webUrl("/settings/account"))
        assertEquals("https://astrid.cc/settings/account", Brand.webUrl("settings/account"))
        assertEquals("https://astrid.cc/icons/default_list_0.png", defaultListImage(""))
    }

    @Test
    fun `AATD-71 a configured value is trimmed and loses a trailing slash, a blank one falls back`() {
        assertEquals("https://tasks.example.com", Brand.origin("  https://tasks.example.com/ ", "https://astrid.cc"))
        assertEquals("https://astrid.cc", Brand.origin("", "https://astrid.cc"))
        assertEquals("https://astrid.cc", Brand.origin(null, "https://astrid.cc"))
        assertEquals("https://tasks.example.com/s/abc", Brand.webUrl("/s/abc", base = "https://tasks.example.com"))
    }

    @Test
    fun `AATD-71 no screen hardcodes the hosted origin`() {
        val leaks = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Brand.kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if ("https://astrid.cc" in line) "${f.name}:${i + 1}" else null } }
            .toList()
        assertEquals(emptyList<String>(), leaks)
    }
}
