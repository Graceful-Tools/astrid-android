package com.gracefultools.astrid.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gracefultools.astrid.ui.theme.AstridTheme

/**
 * Material's [TextButton] with its label in the accent **ink** (AATD-101). Material takes the label
 * from `colorScheme.primary`, which is the accent fill: it stays the fill because pickers, switches
 * and sliders draw white on it, and as text on Dark it is 3.7:1. iOS's alert and toolbar buttons
 * take the system tint, which lightens on Dark for the same reason. A label that sets its own
 * colour (`errorInk` for a destructive action) still wins.
 */
@Composable
fun AstridTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = TextButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    colors = ButtonDefaults.textButtonColors(contentColor = AstridTheme.colors.accentInk),
    content = content,
)
