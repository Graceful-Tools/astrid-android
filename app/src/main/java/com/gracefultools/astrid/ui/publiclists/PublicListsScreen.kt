package com.gracefultools.astrid.ui.publiclists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.common.AppErrorBanner
import com.gracefultools.astrid.ui.common.EmptyState
import com.gracefultools.astrid.ui.common.FloatingTextHeader
import com.gracefultools.astrid.ui.sidebar.ListPicture
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS PublicListBrowserView, "Featured Lists" (AATD-46): the public catalogue, most copied first.
 * Each row has the list's 16 icon, name, a globe (and green people for a collaborative list), two
 * lines of description, "%d tasks", and "View & Add" (collaborative) or "Copy" / "Copying...".
 * Empty: Astrid's bubble, still pull-to-refresh. The large title sits under the floating header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicListsScreen(vm: PublicListsViewModel, onBack: () -> Unit, onView: (PublicListSummary) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    LaunchedEffect(Unit) { vm.load() }
    Box(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            FloatingTextHeader(stringResource(R.string.filters_featured_lists), onBack = onBack)
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("publicLists"),
                    contentPadding = PaddingValues(start = Spacing.s16, end = Spacing.s16, top = Spacing.s8, bottom = Spacing.s24),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s8),
                ) {
                    item {
                        Text(
                            stringResource(R.string.filters_featured_lists), fontSize = 34.sp, fontWeight = FontWeight.Bold,
                            color = colors.textPrimary, modifier = Modifier.padding(vertical = Spacing.s8).semantics { heading() },
                        )
                    }
                    when {
                        state.lists.isEmpty() && state.loading -> item {
                            Box(Modifier.fillMaxWidth().fillParentMaxHeight(0.6f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.accent)
                            }
                        }
                        state.lists.isEmpty() -> item {
                            Box(Modifier.fillParentMaxHeight(0.8f)) { EmptyState(stringResource(R.string.empty_state_public_list)) }
                        }
                        else -> items(state.lists, key = { it.id }) { list ->
                            PublicListRow(list, copying = list.id in state.copying, onView = { onView(list) }, onCopy = { vm.copy(list.id) })
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
        if (state.copyFailed) {
            AppErrorBanner(stringResource(R.string.lists_copy_failed), vm::dismissError, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s24))
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(6_000); vm.dismissError() }
        }
    }
}

/** iOS PublicListRow: one rounded card (radius 8, padding 16), on the theme's card colour so it reads on Ocean. */
@Composable
private fun PublicListRow(list: PublicListSummary, copying: Boolean, onView: () -> Unit, onCopy: () -> Unit) {
    val colors = AstridTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.medium)).background(colors.floating).padding(Spacing.s16)
            .testTag("publicList:${list.id}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            ListPicture(list.asListSummary(), list.imageUrl, 16.dp)
            Text(
                list.name, style = TextStyles.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Public, stringResource(R.string.lists_privacy_public), tint = colors.accent, modifier = Modifier.size(13.dp))
                if (list.isCollaborative) {
                    Icon(Icons.Outlined.People, stringResource(R.string.lists_privacy_shared), tint = Success, modifier = Modifier.size(13.dp))
                }
            }
        }
        list.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = TextStyles.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
                Text(stringResource(R.string.filters_tasks_count, list.taskCount), style = TextStyles.caption, color = colors.textMuted)
            }
            if (list.isCollaborative) {
                ActionText(stringResource(R.string.filters_view_add), colors.accent, enabled = true, onClick = onView)
            } else {
                ActionText(
                    stringResource(if (copying) R.string.lists_copying else R.string.actions_copy),
                    if (copying) colors.textSecondary else colors.accent, enabled = !copying, onClick = onCopy,
                )
            }
        }
    }
}

@Composable
private fun ActionText(text: String, color: androidx.compose.ui.graphics.Color, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text, style = TextStyles.caption, color = color,
        modifier = Modifier.clip(RoundedCornerShape(Radius.small)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.s8, vertical = Spacing.s8),
    )
}
