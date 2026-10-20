package com.gracefultools.astrid.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.ui.common.FloatingTextHeader
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/*
 * iOS's settings pages are a `Form`/`List` in the inset-grouped style over the theme background:
 * an uppercase caption over each section, rows on a rounded card with hairlines between them, an
 * optional footnote under it. These are those pieces, shared by every settings and account page.
 */

/** A settings page: FloatingTextHeader, then the sections, scrolling (and pulling to refresh, given [onRefresh]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(
    title: String,
    icon: ImageVector?,
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AstridTheme.colors
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            FloatingTextHeader(title, icon = icon, onBack = onBack)
            val body: @Composable () -> Unit = {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.s16).padding(top = Spacing.s16, bottom = Spacing.s32).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s24),
                    content = content,
                )
            }
            if (onRefresh != null) PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { body() } else body()
        }
        overlay()
    }
}

/** The row background of an inset-grouped list: white, or the raised grey in dark. */
internal val formRowBackground: Color @Composable get() = AstridTheme.colors.let { if (it.isDark) it.bgSecondary else Color.White }

/** One section: header, rows on a card (a hairline between each), footer. */
@Composable
internal fun FormSection(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = AstridTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        if (header != null) {
            Text(
                header.uppercase(), style = TextStyles.footnote, color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.s16).semantics { heading() },
            )
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.large)).background(formRowBackground), content = content)
        if (footer != null) {
            Text(footer, style = TextStyles.footnote, color = colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.s16))
        }
    }
}

/** The hairline between two rows, inset past the leading icon as iOS's is. */
@Composable
internal fun FormDivider(inset: Boolean = true) {
    HorizontalDivider(Modifier.padding(start = if (inset) 52.dp else Spacing.s16), thickness = 0.5.dp, color = AstridTheme.colors.divider)
}

/** iOS's Label: the accent SF Symbol in a 24 frame, then the title. */
@Composable
internal fun RowIcon(icon: ImageVector, tint: Color? = null) {
    Icon(icon, null, tint = tint ?: AstridTheme.colors.accent, modifier = Modifier.width(24.dp).size(22.dp))
}

/** A row's frame: 44 tall at least, 16 in from the card's edges. */
@Composable
internal fun FormRowFrame(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = Spacing.s16, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        content = content,
    )
}

/** A NavigationLink row: icon, title (and subtitle), trailing value, chevron. */
@Composable
internal fun NavRow(
    title: String,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    val colors = AstridTheme.colors
    FormRowFrame(Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)) {
        if (icon != null) RowIcon(icon, iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyles.body, color = colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = TextStyles.caption, color = colors.textSecondary)
        }
        if (value != null) Text(value, style = TextStyles.footnote, color = colors.textSecondary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
    }
}

/** A plain button row: accent title (or [color]), optional icon, optional trailing content. */
@Composable
internal fun ButtonRow(
    title: String,
    icon: ImageVector? = null,
    color: Color? = null,
    enabled: Boolean = true,
    centered: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: () -> Unit,
) {
    val colors = AstridTheme.colors
    val ink = (color ?: colors.accent).let { if (enabled) it else it.copy(alpha = 0.4f) }
    FormRowFrame(Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = title, onClick = onClick)) {
        if (centered) Spacer(Modifier.weight(1f))
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Text(title, style = TextStyles.body, color = ink, modifier = if (centered) Modifier else Modifier.weight(1f))
        if (centered) Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** iOS's Toggle in a Form: the whole row toggles, and says so to TalkBack. */
@Composable
internal fun ToggleRow(title: String, checked: Boolean, icon: ImageVector? = null, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    val colors = AstridTheme.colors
    FormRowFrame(Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange)) {
        if (icon != null) RowIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyles.body, color = colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = TextStyles.caption2, color = colors.textSecondary)
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White),
        )
    }
}

/** A label with its value on the right (About's Version, Account Information). */
@Composable
internal fun ValueRow(title: String, value: String, icon: ImageVector? = null) {
    val colors = AstridTheme.colors
    FormRowFrame(Modifier.semantics(mergeDescendants = true) {}) {
        if (icon != null) RowIcon(icon)
        Text(title, style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(value, style = TextStyles.body, color = colors.textSecondary)
    }
}

/** A choice in a list with a check on the chosen one (iOS's inline Picker / Language list). */
@Composable
internal fun CheckRow(title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    FormRowFrame(Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyles.body, color = colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = TextStyles.caption2, color = colors.textSecondary)
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = colors.accent, modifier = Modifier.size(20.dp))
    }
}
