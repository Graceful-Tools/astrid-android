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
    fun `AATD-71 a configured value is trimmed and loses a trailing slash`() {
        // A blank one never gets here: Gradle's brand() makes it Astrid's (AATD-90).
        assertEquals("https://tasks.example.com", Brand.origin("  https://tasks.example.com/ "))
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

    @Test
    fun `AATD-90 Brand holds no Astrid value of its own, so a partner's dex carries none`() {
        // Gradle gives every brand field a value (Astrid's when unset); a fallback here is dead code
        // that still ships "Astrid" and astrid.cc in every partner's APK (check-brand-literals --apk).
        val brand = File("src/main/java/com/gracefultools/astrid/Brand.kt").readLines()
            .filterNot { line -> listOf("/*", "*", "//").any { line.trimStart().startsWith(it) } }
        val leaks = brand.filter { "astrid.cc" in it || "\"Astrid\"" in it }
        assertEquals(emptyList<String>(), leaks)
    }
}
