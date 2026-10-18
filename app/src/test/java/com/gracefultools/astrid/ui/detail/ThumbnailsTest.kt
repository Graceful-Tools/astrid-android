package com.gracefultools.astrid.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailsTest {
    @Test
    fun `AATD-29 a large photo decodes at a fraction of its size, a small one whole`() {
        assertEquals(4, Thumbnails.sampleSize(4032, 3024, 600))
        assertEquals(1, Thumbnails.sampleSize(500, 400, 600))
        assertEquals(2, Thumbnails.sampleSize(1300, 1300, 600))
    }
}
