package com.gracefultools.astrid.ui.sidebar

import org.junit.Assert.assertEquals
import org.junit.Test

/** AATD-39: the same list gets the same default picture as on iOS (ListImageHelper.simpleHash, 64-bit). */
class DefaultListImageTest {
    /** iOS's own arithmetic, spelled out: hash = ((hash << 5) &- hash) &+ scalar, wrapping at 64 bits. */
    private fun swift(id: String): Long {
        var hash = 0L
        for (ch in id) hash = (hash shl 5) - hash + ch.code
        return kotlin.math.abs(hash) % 4
    }

    @Test
    fun `AATD-39 a long id wraps as Swift's 64-bit Int does, not as a 32-bit one`() {
        // uitest@astrid.cc's "L", drawn green (default_list_1) by iOS; a 32-bit hash picked pink.
        assertEquals("https://astrid.cc/icons/default_list_1.png", defaultListImage("1ebe3e2d-06bf-4c75-9a40-c1f9c4c1f05b"))
        assertEquals("https://astrid.cc/icons/default_list_2.png", defaultListImage("fc895bae-ad01-490d-9b28-2d303161a9fc"))
        val id = "cmgx9z0k20001ld04abcdefgh"
        assertEquals("https://astrid.cc/icons/default_list_${swift(id)}.png", defaultListImage(id))
        assertEquals("https://astrid.cc/icons/default_list_0.png", defaultListImage(""))
    }
}
