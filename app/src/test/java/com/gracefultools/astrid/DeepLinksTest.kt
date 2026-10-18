package com.gracefultools.astrid

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
}
