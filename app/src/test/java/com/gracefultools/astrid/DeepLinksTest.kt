package com.gracefultools.astrid

import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {
    @Test
    fun `AATD-16 task links open the task they name`() {
        assertEquals(Arrival.OpenTask("AWTD-1066"), DeepLinks.parse("https://astrid.cc/t/AWTD-1066"))
        assertEquals(Arrival.OpenTask("AATD-7"), DeepLinks.parse("https://www.astrid.cc/t/AATD-7?x=1"))
        assertEquals(Arrival.OpenTask("t-123"), DeepLinks.parse("astrid://task/t-123"))
        assertEquals(Arrival.NewTask(""), DeepLinks.parse("astrid://new"))
        assertNull(DeepLinks.parse("https://evil.example/t/AATD-7"))
        assertNull(DeepLinks.parse("astrid://auth/callback?code=x"))
    }

    @Test
    fun `AATD-16 a share becomes one quick-add line`() {
        assertEquals("Read this https://example.com/a", DeepLinks.shared("Read this", "https://example.com/a"))
        assertEquals("https://example.com/a", DeepLinks.shared(null, " https://example.com/a "))
        assertEquals("Same", DeepLinks.shared("Same", "Same"))
    }

    @Test
    fun `AATD-59 the custom scheme takes iOS's plural hosts`() {
        assertEquals(Arrival.OpenTask("t-1"), DeepLinks.parse("astrid://tasks/t-1"))
        assertEquals(Arrival.OpenList("l_2"), DeepLinks.parse("astrid://lists/l_2"))
        assertEquals(Arrival.OpenProfile("u3"), DeepLinks.parse("astrid://users/u3"))
        assertEquals(Arrival.Shortcode("abc123"), DeepLinks.parse("astrid://s/abc123"))
        assertEquals(Arrival.OpenSettings("agents"), DeepLinks.parse("astrid://settings/agents"))
        assertEquals(Arrival.OpenSettings("settings"), DeepLinks.parse("astrid://settings"))
        // An unknown page still opens Settings, as iOS's openSettings().
        assertEquals(Arrival.OpenSettings("settings"), DeepLinks.parse("astrid://settings/nonsense"))
        // An unknown host is the same path on the site, in the in-app browser.
        assertEquals(Arrival.OpenWeb("https://astrid.cc/help/faq"), DeepLinks.parse("astrid://help/faq"))
        // The reminder screen opens only from a notification's own intent, never from a link.
        assertEquals(Arrival.OpenWeb("https://astrid.cc/reminders/t1"), DeepLinks.parse("astrid://reminders/t1"))
    }

    @Test
    fun `AATD-59 web links open the screen they name, anything else in the browser`() {
        assertEquals(Arrival.OpenTask("t-9"), DeepLinks.parse("https://astrid.cc/tasks/t-9"))
        assertEquals(Arrival.OpenList("l-9"), DeepLinks.parse("https://www.astrid.cc/lists/l-9"))
        assertEquals(Arrival.Shortcode("Xy7"), DeepLinks.parse("https://astrid.cc/s/Xy7"))
        assertEquals(Arrival.OpenSettings("connections"), DeepLinks.parse("https://astrid.cc/settings/api-access"))
        assertEquals(Arrival.OpenSettings("profile"), DeepLinks.parse("https://astrid.cc/settings/profile"))
        assertEquals(Arrival.OpenSettings("settings"), DeepLinks.parse("https://astrid.cc/settings/reminders"))
        assertEquals(Arrival.OpenSettings("settings"), DeepLinks.parse("https://astrid.cc/settings"))
        assertEquals(Arrival.OpenWeb("https://astrid.cc/settings/billing"), DeepLinks.parse("https://astrid.cc/settings/billing"))
        assertEquals(Arrival.OpenWeb("https://astrid.cc/pricing"), DeepLinks.parse("https://astrid.cc/pricing"))
        assertNull(DeepLinks.parse("https://astrid.cc/"))
    }

    @Test
    fun `AATD-59 an id that could walk the API path is refused`() {
        assertNull(DeepLinks.parse("astrid://tasks/..%2Fusers"))
        assertNull(DeepLinks.parse("https://astrid.cc/lists/a%2F..%2Fb"))
        assertNull(DeepLinks.parse("astrid://lists/"))
        assertNull(DeepLinks.parse("astrid://tasks/" + "a".repeat(129)))
    }

    @Test
    fun `AATD-59 a share link resolves through the core, and falls back to the web`() = runTest {
        val core = FakeCore().apply { answer("resolveShortcode", """{"targetType":"list","targetId":"l-1"}""") }
        assertEquals(Arrival.OpenList("l-1"), DeepLinks.resolveShortcode(core, "abc"))
        assertEquals(JsonPrimitive("abc"), core.last("resolveShortcode")!!["code"])

        core.answer("resolveShortcode", """{"targetType":"task","targetId":"t-1"}""")
        assertEquals(Arrival.OpenTask("t-1"), DeepLinks.resolveShortcode(core, "abc"))

        core.answer("resolveShortcode") { throw CoreFailure(FailureKind.NOT_FOUND, "no such code") }
        assertEquals(Arrival.OpenWeb("https://astrid.cc/s/abc"), DeepLinks.resolveShortcode(core, "abc"))
    }
}
