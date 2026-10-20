package com.gracefultools.astrid.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS's FloatingHeader: content padded 16x12 on a card (white on Ocean, glass on Light, headerBg on
 * Dark), radius 12, a soft shadow (black 0.08, radius 4, y 2), inset 8 from the screen's sides. Every
 * screen's header is one of these (AATD-35).
 */
@Composable
fun FloatingHeader(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = AstridTheme.colors
    val shape = RoundedCornerShape(Radius.large)
    Row(
        modifier
            .statusBarsPadding()
            .padding(start = Spacing.s8, end = Spacing.s8, top = Spacing.s8)
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(colors.floating)
            .padding(horizontal = Spacing.s16, vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        content = content,
    )
}

/** iOS's FloatingTextHeader: an optional back chevron and icon, then the title in headline. */
@Composable
fun FloatingTextHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = AstridTheme.colors
    FloatingHeader(modifier) {
        if (onBack != null) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBackIos, stringResource(R.string.common_back), tint = colors.textPrimary,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(Radius.small)).clickable(onClick = onBack).padding(4.dp),
            )
        }
        if (icon != null) Icon(icon, null, tint = iconTint ?: colors.textPrimary, modifier = Modifier.size(20.dp))
        Text(title, style = TextStyles.headline, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
        actions()
    }
}

/** iOS's HamburgerMenuIcon: two 19.2 x 2 lines, 5 apart, in a 24 frame. */
@Composable
fun HamburgerIcon(modifier: Modifier = Modifier) {
    val ink = AstridTheme.colors.textPrimary
    Canvas(modifier.size(24.dp)) {
        val w = 19.2.dp.toPx(); val h = 2.dp.toPx(); val gap = 5.dp.toPx()
        val x = (size.width - w) / 2
        val top = (size.height - (2 * h + gap)) / 2
        val r = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())
        drawRoundRect(ink, Offset(x, top), androidx.compose.ui.geometry.Size(w, h), r)
        drawRoundRect(ink, Offset(x, top + h + gap), androidx.compose.ui.geometry.Size(w, h), r)
    }
}

/**
 * iOS's AstridSpeechBubble: the character at 160 beside a bubble of semibold body text (radius 16,
 * 2pt border, a pointer on its left edge aimed at the character).
 */
@Composable
fun AstridSpeechBubble(message: String, modifier: Modifier = Modifier, characterSize: androidx.compose.ui.unit.Dp = 160.dp) {
    val colors = AstridTheme.colors
    val bubble = colors.bgSecondary
    val border = if (colors.isDark) colors.border else if (colors == com.gracefultools.astrid.ui.theme.OceanColors) colors.border else com.gracefultools.astrid.ui.theme.LightColors.border
    val pointer = when {
        colors == com.gracefultools.astrid.ui.theme.OceanColors -> colors.border
        colors.isDark -> Color(0xFF4B5563)
        else -> Color(0xFFD1D5DB)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        Image(painterResource(R.drawable.astrid_character), null, Modifier.size(characterSize))
        Box(Modifier.weight(1f)) {
            Text(
                message, style = TextStyles.body, fontWeight = FontWeight.SemiBold, color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bubble)
                    .border(2.dp, border, RoundedCornerShape(16.dp)).padding(Spacing.s16),
            )
            Canvas(Modifier.matchParentSize()) {
                val cy = size.height / 2
                val outer = Path().apply {
                    moveTo(-16.dp.toPx(), cy); lineTo(3.dp.toPx(), cy - 12.dp.toPx()); lineTo(3.dp.toPx(), cy + 12.dp.toPx()); close()
                }
                val inner = Path().apply {
                    moveTo(-13.dp.toPx(), cy); lineTo(3.dp.toPx(), cy - 9.5.dp.toPx()); lineTo(3.dp.toPx(), cy + 9.5.dp.toPx()); close()
                }
                drawPath(outer, pointer)
                drawPath(inner, bubble)
            }
        }
    }
}

/** iOS's EmptyStateView: the speech bubble, centred, with an optional accent button under it. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, buttonTitle: String? = null, onButton: (() -> Unit)? = null) {
    val colors = AstridTheme.colors
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AstridSpeechBubble(message, Modifier.padding(horizontal = Spacing.s24).widthIn(max = 560.dp))
        if (buttonTitle != null && onButton != null) {
            Spacer(Modifier.height(Spacing.s24 + Spacing.s8))
            Row(
                Modifier.clip(RoundedCornerShape(Radius.large)).background(colors.accent).clickable(onClick = onButton)
                    .padding(horizontal = Spacing.s24, vertical = Spacing.s12),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AddCircle, null, tint = colors.accentText, modifier = Modifier.size(20.dp))
                Text(buttonTitle, style = TextStyles.headline, color = colors.accentText)
            }
        }
    }
}

/**
 * iOS's AppErrorBanner: a red rounded-10 banner with a warning icon, white text and a close button,
 * at most 520 wide. One per window, at the bottom (AATD-33).
 */
@Composable
fun AppErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = Spacing.s16)
            .shadow(Spacing.s4, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Error)
            .padding(start = Spacing.s12, top = Spacing.s8, bottom = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(message, style = TextStyles.subheadline, color = Color.White, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.common_dismiss), tint = Color.White) }
    }
}

/**
 * iOS's SplashView: while the session is checked the app already looks like an empty list — the
 * floating header, Astrid saying "Loading your tasks...", an empty quick-add bar — so the first real
 * frame changes nothing but the rows (AATD-34).
 */
@Composable
fun SplashLookalike() {
    val colors = AstridTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        FloatingHeader { HamburgerIcon(Modifier.padding(start = 6.dp)); Spacer(Modifier.height(32.dp)) }
        EmptyState(stringResource(R.string.tasks_loading), Modifier.weight(1f))
        Row(
            Modifier.navigationBarsPadding().padding(start = Spacing.s8, end = Spacing.s8, bottom = Spacing.s8).fillMaxWidth()
                .clip(RoundedCornerShape(Radius.large)).background(colors.floatingBar).padding(horizontal = Spacing.s16, vertical = Spacing.s12),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            CheckboxArt(0, repeating = false, checked = false)
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(Radius.medium)).background(colors.field)
                    .border(1.dp, colors.fieldBorder, RoundedCornerShape(Radius.medium)),
            )
            Icon(Icons.Filled.AddCircle, null, tint = colors.textMuted, modifier = Modifier.size(32.dp))
        }
    }
}

