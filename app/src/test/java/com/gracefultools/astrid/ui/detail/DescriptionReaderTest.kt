package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.ui.detail.DescriptionReader.Entry
import com.gracefultools.astrid.ui.detail.DescriptionReader.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS DescriptionReaderTests (AITD-577, AITD-649), for Android's reader (AATD-102). */
class DescriptionReaderTest {
    @Test
    fun `AATD-102 tapping a written description to change it opens the editor`() {
        assertEquals(Mode.EDITING, DescriptionReader.initialMode("Plan **this**", isReadOnly = false, entry = Entry.WRITE))
    }

    @Test
    fun `AATD-102 tapping an empty description to add one opens the editor`() {
        assertEquals(Mode.EDITING, DescriptionReader.initialMode("", isReadOnly = false, entry = Entry.WRITE))
    }

    @Test
    fun `AATD-102 the expand button opens a written description for reading, an empty one for editing`() {
        assertEquals(Mode.READING, DescriptionReader.initialMode("Plan", isReadOnly = false, entry = Entry.EXPAND))
        assertEquals(Mode.EDITING, DescriptionReader.initialMode("  \n", isReadOnly = false, entry = Entry.EXPAND))
    }

    @Test
    fun `AATD-102 a read-only task never opens an editor`() {
        assertEquals(Mode.READING, DescriptionReader.initialMode("", isReadOnly = true, entry = Entry.WRITE))
        assertEquals(Mode.READING, DescriptionReader.initialMode("Plan", isReadOnly = true, entry = Entry.WRITE))
    }

    @Test
    fun `AATD-102 the expand button shows when editable, or read-only with something to read`() {
        assertTrue(DescriptionReader.offersFullScreen("", isReadOnly = false))
        assertTrue(DescriptionReader.offersFullScreen("Plan", isReadOnly = true))
        assertFalse(DescriptionReader.offersFullScreen(" ", isReadOnly = true))
    }

    @Test
    fun `AATD-102 Done saves only a change, and clearing it is a change`() {
        assertNull(DescriptionReader.textToSave("Plan", "Plan"))
        assertEquals("Plan v2", DescriptionReader.textToSave("Plan v2", "Plan"))
        assertEquals("", DescriptionReader.textToSave("", "Plan"))
    }
}
