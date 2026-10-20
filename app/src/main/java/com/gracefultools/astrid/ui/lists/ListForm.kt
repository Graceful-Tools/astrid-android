package com.gracefultools.astrid.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/*
 * iOS's grouped Form, drawn in Compose: a section is an uppercase footnote header, a rounded card of
 * rows split by hairlines, and an optional caption footer. Shared by List Settings and New list.
 */

/** The card colour a Form row sits on: white in light themes, the secondary background in dark. */
@Composable
internal fun formCard(): Color = AstridTheme.colors.let { if (it.isDark) it.bgSecondary else Color.White }

@Composable
internal fun FormSection(
    title: String? = null,
    footer: String? = null,
    header: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AstridTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s16), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null || header != null) {
            Row(
                Modifier.padding(horizontal = Spacing.s16).semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
            ) {
                header?.invoke(this)
                if (title != null) Text(title.uppercase(), style = TextStyles.footnote, color = colors.textSecondary)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.large)).background(formCard()), content = content)
        if (footer != null) Text(footer, style = TextStyles.caption, color = colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.s16))
    }
}

/** A row inside a [FormSection]: 44dp tall at least, 16 in from the card's sides. */
@Composable
internal fun FormRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 44.dp).padding(horizontal = Spacing.s16, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        content = content,
    )
}

@Composable
internal fun FormDivider() = HorizontalDivider(Modifier.padding(start = Spacing.s16), thickness = 0.5.dp, color = AstridTheme.colors.divider)

/** iOS's Picker row in a Form: the label, the current choice in secondary text, and a chevron. */
@Composable
internal fun FormPickerRow(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    FormRow(onClick = if (enabled) onClick else null) {
        Text(label, style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(value, style = TextStyles.body, color = colors.textSecondary, maxLines = 1)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
    }
}

/** A Form button row: an icon and a label, both in [tint]. */
@Composable
internal fun FormButtonRow(icon: ImageVector, label: String, tint: Color, enabled: Boolean = true, trailing: (@Composable RowScope.() -> Unit)? = null, onClick: () -> Unit) {
    FormRow(onClick = if (enabled) onClick else null) {
        Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
        Text(label, style = TextStyles.body, color = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/**
 * iOS's segmented Picker as a Material TabRow: a grey track, the chosen segment a raised white
 * capsule, each segment an icon and its label.
 */
@Composable
internal fun SegmentedTabs(items: List<Pair<ImageVector?, String>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AstridTheme.colors
    val track = if (colors.isDark) colors.bgTertiary else Color(0xFFE9E9EB)
    val thumb = if (colors.isDark) colors.bgActive else Color.White
    TabRow(
        selectedTabIndex = selected,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(track).padding(2.dp),
        containerColor = Color.Transparent,
        contentColor = colors.textPrimary,
        indicator = { positions ->
            if (selected in positions.indices) {
                Box(
                    androidx.compose.material3.TabRowDefaults.run { Modifier.tabIndicatorOffset(positions[selected]) }
                        .padding(1.dp).fillMaxWidth().heightIn(min = 30.dp)
                        .shadow(1.dp, RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp)).background(thumb)
                        .border(0.5.dp, Color.Black.copy(alpha = 0.04f), RoundedCornerShape(7.dp)),
                )
            }
        },
        divider = {},
    ) {
        items.forEachIndexed { index, (icon, label) ->
            Tab(
                selected = index == selected, onClick = { onSelect(index) },
                selectedContentColor = colors.textPrimary, unselectedContentColor = colors.textPrimary,
                modifier = Modifier.heightIn(min = 32.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s4), modifier = Modifier.padding(vertical = 6.dp)) {
                    if (icon != null) Icon(icon, null, modifier = Modifier.size(15.dp))
                    Text(label, style = TextStyles.footnote, fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}
