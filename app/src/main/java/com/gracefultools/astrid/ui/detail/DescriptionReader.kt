package com.gracefultools.astrid.ui.detail

/**
 * The full-screen description: when it is offered, how it opens, what closing it saves.
 * iOS `DescriptionReader` (AITD-577; opening it to write, AITD-649 → AATD-102).
 *
 * These are presentation decisions — which screen opens in which mode — not task rules, so they
 * sit beside the view as they do on iOS. Saving still goes through the core (`updateTask`).
 */
object DescriptionReader {
    enum class Mode { READING, EDITING }

    /** How the reader was reached: the expand button beside the label, or a tap on the text. */
    enum class Entry { EXPAND, WRITE }

    /** Is the expand button shown? Always when you can edit; read-only, only with something to read. */
    fun offersFullScreen(text: String, isReadOnly: Boolean): Boolean = !isReadOnly || text.isNotBlank()

    /**
     * An empty page you cannot type on is a dead end, and a written description opened straight
     * into an editor puts a keyboard over what you came to read. A tap on the text is asking to
     * write, so it opens the editor whatever it holds. A read-only task never opens one.
     */
    fun initialMode(text: String, isReadOnly: Boolean, entry: Entry = Entry.EXPAND): Mode {
        if (isReadOnly) return Mode.READING
        return if (entry == Entry.WRITE || text.isBlank()) Mode.EDITING else Mode.READING
    }

    /** The text closing the reader writes, or null when nothing changed. Clearing it is a change. */
    fun textToSave(edited: String, original: String): String? = if (edited == original) null else edited
}
