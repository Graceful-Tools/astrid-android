package com.gracefultools.astrid.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.ListSummary
import com.gracefultools.astrid.ui.tasks.parseColor
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing

/** The iOS overlay sidebar, as a navigation drawer: My Tasks, Favorites, Lists, then Settings. */
@Composable
fun SidebarDrawer(state: SidebarState, onSelect: (ListSummary) -> Unit, onSettings: () -> Unit) {
    val colors = AstridTheme.colors
    ModalDrawerSheet(drawerContainerColor = colors.bgSecondary, modifier = Modifier.width(300.dp)) {
        Column(Modifier.fillMaxHeight().statusBarsPadding()) {
            Text(
                stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = colors.textPrimary, modifier = Modifier.padding(Spacing.s16),
            )
            LazyColumn(Modifier.weight(1f)) {
                state.myTasks?.let { mine ->
                    item { Entry(mine, stringResource(R.string.navigation_my_tasks), state.selectedId == mine.id, Icons.Outlined.Person) { onSelect(mine) } }
                }
                if (state.favorites.isNotEmpty()) {
                    item { Section(stringResource(R.string.navigation_favorites)) }
                    items(state.favorites, key = { "f" + it.id }) { list -> Entry(list, list.name, state.selectedId == list.id) { onSelect(list) } }
                }
                if (state.lists.isNotEmpty()) {
                    item { Section(stringResource(R.string.navigation_lists)) }
                    items(state.lists, key = { "l" + it.id }) { list -> Entry(list, list.name, state.selectedId == list.id) { onSelect(list) } }
                }
            }
            HorizontalDivider(color = colors.border)
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(Spacing.s16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Settings, null, tint = colors.textSecondary)
                Spacer(Modifier.width(Spacing.s12))
                Text(stringResource(R.string.navigation_settings), color = colors.textPrimary, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AstridTheme.colors.textMuted,
        modifier = Modifier.padding(start = Spacing.s16, top = Spacing.s16, bottom = Spacing.s4),
    )
}

@Composable
private fun Entry(
    list: ListSummary,
    title: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.s8, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.bgSelected else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .testTag("sidebar:${list.id}")
            .semantics { this.selected = selected }
            .padding(horizontal = Spacing.s12, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        } else {
            Box(Modifier.size(12.dp).clip(CircleShape).background(parseColor(list.color)))
            Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(Spacing.s12))
        Text(title, color = colors.textPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        list.taskCount?.takeIf { it > 0 }?.let { Text(it.toString(), color = colors.textMuted, fontSize = 15.sp) }
    }
}

@Composable
fun SpacerH(h: Int) = Spacer(Modifier.height(h.dp))
