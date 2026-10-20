package com.gracefultools.astrid.ui.sidebar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.ListSummary
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.common.initialsOf
import com.gracefultools.astrid.ui.tasks.parseColor
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles

/** iOS SidebarBrandHeader: the logo column every row's icon centres in, and the gap after it. */
private val LogoColumn = 28.8.dp
private val LogoGap = 8.dp

/**
 * iOS's sidebar, as a navigation drawer (AATD-39): the brand header with search, My Tasks, Favorites,
 * Lists (Add List first), and the floating account card at the foot — avatar and name to Profile,
 * the gear to Settings. On the theme's own background, about 85% of a phone's width.
 */
@Composable
fun SidebarDrawer(
    state: SidebarState,
    onSelect: (ListSummary) -> Unit,
    onSettings: () -> Unit,
    onCreate: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onProfile: () -> Unit = {},
    onToggleFavorite: (ListSummary) -> Unit = {},
    onAddList: (() -> Unit)? = null,
    /** A public list from the two featured sections (AATD-46). */
    onSelectPublic: (com.gracefultools.astrid.ui.publiclists.PublicListSummary) -> Unit = {},
    /** "See all … lists →": the Featured Lists browser. */
    onSeeAllPublic: () -> Unit = {},
) {
    val colors = AstridTheme.colors
    val addingFallback = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val width = (LocalConfiguration.current.screenWidthDp * 0.85f).dp.coerceAtMost(360.dp)
    ModalDrawerSheet(
        drawerContainerColor = colors.bgPrimary, drawerShape = RoundedCornerShape(0.dp),
        modifier = Modifier.widthIn(max = width).fillMaxHeight(),
    ) {
        Box(Modifier.fillMaxHeight().statusBarsPadding()) {
            Column(Modifier.fillMaxHeight()) {
                Group(Position.Only, Modifier.padding(top = Spacing.s8)) { BrandHeader(onSearch) }
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    state.myTasks?.let { mine ->
                        item { Group(Position.Only) { MyTasksEntry(mine, state.selectedId == mine.id, state.counts[mine.id] ?: 0) { onSelect(mine) } } }
                    }
                    if (state.favorites.isNotEmpty()) {
                        item { Section(stringResource(R.string.navigation_favorites)) }
                        itemsIndexed(state.favorites, key = { _, it -> "f" + it.id }) { index, list ->
                            Group(Position.of(index, state.favorites.size)) {
                                Entry(list, state.selectedId == list.id, state.counts[list.id], state.images[list.id], state.showListImages, { onSelect(list) }, { onToggleFavorite(list) })
                            }
                        }
                    }
                    item { Section(stringResource(R.string.navigation_lists)) }
                    item { Group(if (state.lists.isEmpty()) Position.Only else Position.First) { AddListEntry { onAddList?.invoke() ?: run { addingFallback.value = true } } } }
                    itemsIndexed(state.lists, key = { _, it -> "l" + it.id }) { index, list ->
                        Group(if (index == state.lists.lastIndex) Position.Last else Position.Middle) {
                            Entry(list, state.selectedId == list.id, state.counts[list.id], state.images[list.id], state.showListImages, { onSelect(list) }, { onToggleFavorite(list) })
                        }
                    }
                    publicSection(
                        "pc", R.string.navigation_public_shared_lists, R.string.navigation_see_all_collaborative,
                        state.collaborativeLists, state, onSelectPublic, onSeeAllPublic,
                    )
                    publicSection(
                        "ps", R.string.navigation_public_lists, R.string.navigation_see_all_suggested,
                        state.suggestedLists, state, onSelectPublic, onSeeAllPublic,
                    )
                }
            }
            AccountCard(state.user, onProfile, onSettings, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (addingFallback.value) CreateListDialog(onDismiss = { addingFallback.value = false }) { addingFallback.value = false; onCreate(it) }
}

/**
 * iOS's featuredCollaborativeSection / featuredSuggestedSection: one card holding an uppercase
 * caption header, at most two lists, then "See all … lists →" in accent when there are more.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.publicSection(
    key: String,
    title: Int,
    seeAll: Int,
    section: List<com.gracefultools.astrid.ui.publiclists.PublicListSummary>,
    state: SidebarState,
    onSelect: (com.gracefultools.astrid.ui.publiclists.PublicListSummary) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (section.isEmpty()) return
    val shown = com.gracefultools.astrid.ui.publiclists.PublicListSections.sidebarRows(section)
    val more = com.gracefultools.astrid.ui.publiclists.PublicListSections.hasMore(section)
    item(key = "$key-header") {
        Group(Position.First) {
            Text(
                stringResource(title).uppercase(), style = TextStyles.caption, color = AstridTheme.colors.textSecondary,
                modifier = Modifier.padding(start = Spacing.s12 + Spacing.s8, end = Spacing.s12, top = Spacing.s4, bottom = Spacing.s4).semantics { heading() },
            )
        }
    }
    itemsIndexed(shown, key = { _, it -> "$key-" + it.id }) { index, list ->
        Group(if (!more && index == shown.lastIndex) Position.Last else Position.Middle) {
            val summary = list.asListSummary()
            Entry(summary, state.selectedId == list.id, list.taskCount, state.images[list.id], state.showListImages, { onSelect(list) }, null)
        }
    }
    if (more) item(key = "$key-more") {
        Group(Position.Last) {
            Text(
                stringResource(seeAll), style = TextStyles.caption, color = AstridTheme.colors.accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s8).clip(RoundedCornerShape(8.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onSeeAll)
                    .testTag("sidebar:$key-see-all").padding(start = Spacing.s12, top = 13.dp, bottom = 13.dp),
            )
        }
    }
}

@Composable
private fun CreateListDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lists_new_list)) },
        text = { androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.lists_new_name)) }, singleLine = true) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.lists_create)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** iOS SidebarBrandHeader: the character (28.8, radius 6), the lowercase wordmark (21.6 semibold), search. */
@Composable
private fun BrandHeader(onSearch: () -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.s16, end = Spacing.s8, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.astrid_character), null, Modifier.size(LogoColumn).clip(RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(LogoGap))
        Text(
            stringResource(R.string.brand_wordmark), fontSize = 21.6.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Icon(
            Icons.Outlined.Search, stringResource(R.string.navigation_search), tint = colors.textPrimary,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onSearch).padding(8.dp).testTag("sidebar:search"),
        )
    }
}

/** Where a row sits in its group: the card's corners round only at its ends, dividers between. */
private enum class Position {
    First, Middle, Last, Only;
    companion object { fun of(index: Int, size: Int) = when { size == 1 -> Only; index == 0 -> First; index == size - 1 -> Last; else -> Middle } }
}

/**
 * iOS 26's sidebar: each group is a rounded card (white on Ocean and Light, headerBg on Dark),
 * inset 12 from the edges, its rows divided by a hairline that starts after the icon column.
 */
@Composable
private fun Group(position: Position, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    val r = 22.dp
    val shape = when (position) {
        Position.Only -> RoundedCornerShape(r)
        Position.First -> RoundedCornerShape(topStart = r, topEnd = r)
        Position.Last -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        Position.Middle -> RoundedCornerShape(0.dp)
    }
    Column(
        modifier.padding(horizontal = Spacing.s12).padding(bottom = if (position == Position.Only || position == Position.Last) Spacing.s12 else 0.dp)
            .fillMaxWidth().clip(shape).background(colors.floating)
            .padding(top = if (position == Position.Only || position == Position.First) 6.dp else 0.dp,
                bottom = if (position == Position.Only || position == Position.Last) 6.dp else 0.dp),
    ) {
        if (position == Position.Middle || position == Position.Last) {
            androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 56.dp, end = Spacing.s16), color = colors.divider)
        }
        content()
    }
}

/** Title-case, as iOS's sidebar list style draws "Favorites" and "Lists". */
@Composable
private fun Section(title: String) {
    Text(
        title, style = TextStyles.headline, color = AstridTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 28.dp, top = Spacing.s8, bottom = Spacing.s8).semantics { heading() },
    )
}

/** A sidebar row: iOS's ListRowButtonStyle — accent at 0.1 when selected, a 0.98 press. */
@Composable
private fun RowShell(
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val colors = AstridTheme.colors
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.s8, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) (if (colors.isDark) colors.bgTertiary else colors.accent.copy(alpha = 0.1f)) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag(tag)
            .semantics { this.selected = selected }
            .padding(start = Spacing.s12, end = Spacing.s4, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LogoGap),
        content = content,
    )
}

/** iOS's My Tasks row: an accent dot, the name, the filter glyph, and the count capsule — always. */
@Composable
private fun MyTasksEntry(list: ListSummary, selected: Boolean, count: Int, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    RowShell(selected, "sidebar:${list.id}", onClick) {
        Box(Modifier.width(LogoColumn), contentAlignment = Alignment.Center) { Box(Modifier.size(12.dp).clip(CircleShape).background(colors.accent)) }
        Text(stringResource(R.string.navigation_my_tasks), style = TextStyles.body, color = colors.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.FilterList, null, tint = colors.textMuted, modifier = Modifier.size(15.dp))
        CountCapsule(count)
    }
}

@Composable
private fun AddListEntry(onClick: () -> Unit) {
    val colors = AstridTheme.colors
    RowShell(false, "sidebar:add-list", onClick) {
        Box(Modifier.width(LogoColumn), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AddCircle, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        Text(stringResource(R.string.navigation_add_list), style = TextStyles.body, color = colors.accent, modifier = Modifier.weight(1f))
    }
}

/** iOS ListRowView: the list's picture (12, radius 2.4) in the logo column, name, privacy, count. Long press favourites. */
@Composable
private fun Entry(list: ListSummary, selected: Boolean, count: Int?, image: String?, showImages: Boolean, onClick: () -> Unit, onFavorite: (() -> Unit)?) {
    val colors = AstridTheme.colors
    val haptics = LocalHapticFeedback.current
    RowShell(selected, "sidebar:${list.id}", onClick, onLongClick = onFavorite?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } }) {
        Box(Modifier.width(LogoColumn), contentAlignment = Alignment.Center) {
            if (showImages) ListPicture(list, image, 12.dp)
            else Icon(Icons.Filled.Tag, null, tint = parseColor(list.color), modifier = Modifier.size(12.dp))
        }
        Text(list.name, style = TextStyles.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        when (list.privacy) {
            "PUBLIC" -> Icon(Icons.Outlined.Public, stringResource(R.string.lists_privacy_public), tint = colors.accent, modifier = Modifier.size(13.dp))
            "SHARED" -> Icon(Icons.Filled.People, stringResource(R.string.lists_privacy_shared), tint = Success, modifier = Modifier.size(13.dp))
        }
        CountCapsule(count ?: list.taskCount ?: 0)
    }
}

/**
 * iOS ListImageView: the list's own picture, else iOS's default icon for it — one of four, chosen by
 * a hash of the id so it never changes (ListImageHelper.getConsistentDefaultImage) — clipped to a
 * rounded square. Below 12 it is a dot of the list's colour.
 */
@Composable
fun ListPicture(list: ListSummary, image: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val tint = parseColor(list.color)
    // A secure file the core fetched with the session is a path on this device: hand Coil a File,
    // which it reads as one (a bare path string is not reliably a file to it).
    val source: Any = image?.let { if (it.startsWith("/")) java.io.File(it) else it } ?: defaultListImage(list.id)
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.2f)).background(tint)) {
        AsyncImage(model = source, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
    }
}

/** iOS ListImageHelper.simpleHash → /icons/default_list_<0-3>.png on the API's host. */
fun defaultListImage(listId: String, base: String = "https://astrid.cc"): String {
    if (listId.isEmpty()) return "$base/icons/default_list_0.png"
    // Swift's Int is 64-bit and its &- / &+ wrap: Kotlin's Long does the same, so the same id picks
    // the same picture on both.
    var hash = 0L
    for (ch in listId) hash = (hash shl 5) - hash + ch.code.toLong()
    return "$base/icons/default_list_${kotlin.math.abs(hash) % 4}.png"
}

/** iOS's count: callout, textSecondary, in a capsule of gray 0.1 (bgTertiary on Dark), shown at 0 too. */
@Composable
private fun CountCapsule(count: Int) {
    val colors = AstridTheme.colors
    Text(
        count.toString(), style = TextStyles.callout, color = colors.textSecondary,
        modifier = Modifier.clip(CircleShape).background(if (colors.isDark) colors.bgTertiary else Color.Gray.copy(alpha = 0.1f))
            .padding(horizontal = Spacing.s4 + 2.dp, vertical = Spacing.s4),
    )
}

/**
 * iOS's floating account card: 40 avatar and the name (to Profile), the gear (to Settings), on a
 * rounded-24 card inset 12 at the sides and 8 at the bottom.
 */
@Composable
private fun AccountCard(user: com.gracefultools.astrid.model.UserSummary?, onProfile: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AstridTheme.colors
    val shape = RoundedCornerShape(24.dp)
    val settings = stringResource(R.string.navigation_settings)
    Row(
        modifier.navigationBarsPadding().padding(start = Spacing.s12, end = Spacing.s12, bottom = Spacing.s8).fillMaxWidth()
            .shadow(6.dp, shape).clip(shape).background(colors.floating).padding(horizontal = Spacing.s8, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onProfile).testTag("sidebar:profile").padding(4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            Avatar(user?.image, initialsOf(user?.name, user?.email), 40.dp)
            Text(user?.display ?: "", style = TextStyles.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.Outlined.Settings, settings, tint = colors.textPrimary,
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onSettings).padding(10.dp).testTag("sidebar:settings"),
        )
    }
}

@Composable
fun SpacerH(h: Int) = Spacer(Modifier.height(h.dp))
