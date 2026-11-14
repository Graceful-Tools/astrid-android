package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The trade-off grid's five rating colours and the text on each: astrid-core
 * `design/tokens.json` `palette.tradeoff` / `palette.tradeoff-text`, which are web's `.tradeoff-*`
 * (AATD-103, iOS `TradeOffPalette`). The same on every client and every theme, so a rating reads
 * the same wherever the grid is shown. `rating` is the core's wire name; `null` is an unrated cell.
 */
object TradeoffPalette {
    /** Worst to best, as the core orders a grid's key. */
    val ratings = listOf("veryPoor", "poor", "mixed", "good", "veryGood")

    private val fills = listOf(Color(0xFFCC0000), Color(0xFFE06666), Color(0xFFFFD966), Color(0xFF93C47D), Color(0xFF38761D))
    private val texts = listOf(Color(0xFFFFFFFF), Color(0xFF111827), Color(0xFF111827), Color(0xFF111827), Color(0xFFFFFFFF))

    fun fill(rating: String?): Color? = ratings.indexOf(rating).takeIf { it >= 0 }?.let(fills::get)
    fun text(rating: String?): Color? = ratings.indexOf(rating).takeIf { it >= 0 }?.let(texts::get)
}
