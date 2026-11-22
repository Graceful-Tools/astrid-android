package com.gracefultools.astrid.ui.theme

import com.astrid.design.AstridTokens

/**
 * Android is a touch client, so it lays out in the tokens' comfortable density, as iOS does
 * (`AstridTokens.Layout.Comfortable`). Compact is the desktop density: the Mac, web and Windows.
 */
val TouchLayout = AstridTokens.Layout.Comfortable

/** The comfortable sizes: the 34 pt leading control, the 44 pt hit target, the selection outline. */
val TouchSize = AstridTokens.Size.Comfortable

/** The thinnest line: dividers and borders (`size.hairline`). */
val Hairline = AstridTokens.Size.hairline
