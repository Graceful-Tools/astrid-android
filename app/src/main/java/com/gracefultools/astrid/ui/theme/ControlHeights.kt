package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The minimum heights Android gives its rows and controls (ADEV-196). iOS sets each frame by hand and
 * core's tokens have no control-height scale yet, so they are named here after the views they match;
 * when core adds one, these read it. A task row reads `TouchLayout.rowMinHeight`.
 */
object ControlHeight {
    /** The accent line that shows where a dragged row will drop. */
    val dropLine = 3.dp
    /** The task row's metadata line: identifier, due date and list chips. */
    val rowMetadata = 27.dp
    /** The selected segment's thumb, inside the 2 dp track padding of a 32 dp segment. */
    val segmentThumb = 30.dp
    /** A segment, the list title, a chip or a compact text row (list form, new list, hub form). */
    val sm = 32.dp
    /** A text button in a row: save a key, a connection's actions. */
    val md = 36.dp
    /** A one-line field or dropdown: board quick add, detail pickers, the comment box, hub form. */
    val field = 40.dp
    /**
     * A full-width setting row (filters, agent and detail pickers). This is Android's 48 dp minimum touch
     * height, not iOS's 44 pt (docs/PARITY.md).
     */
    val settingRow = 48.dp
    /** The list colour preview in list settings. */
    val colourPreview = 56.dp
    /** The description box on a new list. */
    val notes = 80.dp
    /** A reminder triage card. */
    val triageCard = 220.dp
}
