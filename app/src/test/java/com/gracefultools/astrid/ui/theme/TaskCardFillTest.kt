package com.gracefultools.astrid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADEV-196 (iOS ADEV-194): a list row and a board card are both drawn by TaskRowView, and on Dark
 * they filled with `bgPrimary` (#111827), the screen's own colour. They read the tokens'
 * `surface`, and `surface-hover` when the task is the open one, as web and iOS's TaskCardFill do.
 * Ocean keeps its translucent card, now from `surface`. Light is unchanged.
 */
class TaskCardFillTest {
    @Test
    fun `ADEV-196 a dark row and card fill with surface, surface-hover when selected`() {
        assertEquals(DarkColors.tokens.surface, DarkColors.card)
        assertEquals(DarkColors.tokens.surfaceHover, DarkColors.cardSelected)
    }

    @Test
    fun `ADEV-196 an Ocean card is surface at 0_8, solid when selected`() {
        assertEquals(OceanColors.tokens.surface.copy(alpha = 0.8f), OceanColors.card)
        assertEquals(OceanColors.tokens.surface, OceanColors.cardSelected)
    }
}
