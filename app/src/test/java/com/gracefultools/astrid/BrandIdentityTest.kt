package com.gracefultools.astrid

import androidx.test.core.app.ApplicationProvider
import com.gracefultools.astrid.ui.signin.SignInViewModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AATD-72: the app's identity — package, name, wordmark, URL scheme, link hosts and hand-off client —
 * is the brand's (app/build.gradle.kts, `-PastridBrand=<name>`), and an unconfigured build is
 * hosted Astrid exactly as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BrandIdentityTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `AATD-72 an unconfigured build is Astrid's app, exactly as before`() {
        assertEquals("com.gracefultools.astrid", BuildConfig.APPLICATION_ID)
        assertEquals("Astrid", context.getString(R.string.app_name))
        assertEquals("astrid", context.getString(R.string.brand_wordmark))
        assertEquals("astrid", Brand.urlScheme)
        assertEquals(setOf("astrid.cc", "www.astrid.cc"), Brand.linkHosts)
        assertNull(Brand.handoffClientId)
        assertEquals("astrid://auth/callback", Brand.callbackPrefix)
        // Nothing non-default to tell the core, so its config JSON is what it always was.
        assertNull(Brand.coreUrlScheme)
    }

    @Test
    fun `AATD-72 the link hosts always include the server's own`() {
        assertEquals(setOf("tasks.example.com"), Brand.linkHosts("", "https://tasks.example.com"))
        assertEquals(setOf("example.com", "www.example.com"), Brand.linkHosts("example.com, www.example.com", "https://example.com"))
        assertEquals(setOf("tasks.example.com", "go.example.com"), Brand.linkHosts("go.example.com", "https://tasks.example.com/"))
    }

    @Test
    fun `AATD-72 a partner's scheme and hosts are the ones its links use`() {
        val hosts = setOf("tasks.example.com")
        fun parse(link: String) = DeepLinks.parse(link, scheme = "partner", hosts = hosts, base = "https://tasks.example.com")
        assertEquals(Arrival.OpenTask("t-1"), parse("partner://tasks/t-1"))
        assertEquals(Arrival.NewTask(""), parse("partner://new"))
        assertEquals(Arrival.OpenList("l1"), parse("https://tasks.example.com/lists/l1"))
        assertEquals(Arrival.OpenWeb("https://tasks.example.com/help"), parse("partner://help"))
        // Astrid's own links are another app's in a partner build.
        assertNull(parse("astrid://tasks/t-1"))
        assertNull(parse("https://astrid.cc/t/AATD-7"))
        assertNull(parse("partner://auth/callback?code=x"))
    }

    @Test
    fun `AATD-72 a partner build takes the callback on its own scheme only`() {
        assertTrue(SignInViewModel.isCallback("partner://auth/callback?code=a&state=b", scheme = "partner"))
        assertFalse(SignInViewModel.isCallback("astrid://auth/callback?code=a&state=b", scheme = "partner"))
        assertTrue(SignInViewModel.isCallback("astrid://auth/callback?code=a&state=b"))
    }

    @Test
    fun `AATD-72 no code, manifest or resource hardcodes Astrid's scheme, hosts or package`() {
        val code = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Brand.kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if (Regex("\"(astrid://|astrid\\.cc|www\\.astrid\\.cc)").containsMatchIn(line)) "${f.name}:${i + 1}" else null } }
            .toList()
        assertEquals(emptyList<String>(), code)
        val xml = (listOf(File("src/main/AndroidManifest.xml")) + File("src/main/res").walkTopDown().filter { it.isFile && it.extension == "xml" })
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line ->
                if (Regex("""android:(scheme="astrid"|host="(www\.)?astrid\.cc"|data="astrid:|targetPackage="com\.gracefultools)""").containsMatchIn(line)) "${f.name}:${i + 1}" else null
            } }
            .toList()
        assertEquals(emptyList<String>(), xml)
    }
}
