package com.gracefultools.astrid.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.common.EmptyState
import com.gracefultools.astrid.ui.common.FloatingHeader
import com.gracefultools.astrid.ui.common.HamburgerIcon
import com.gracefultools.astrid.ui.common.StatusPicker
import com.gracefultools.astrid.ui.tasks.TaskRowView
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS's search mode of the main pane (AATD-47): the floating header keeps the menu and becomes a
 * magnifier, a "Search tasks..." field and a clear button, focused a moment after it opens. Results
 * are list rows whose leading control completes, as in the list, with subtasks spliced under them;
 * the empty states are Astrid's.
 */
@Composable
fun SearchScreen(vm: SearchViewModel, onBack: () -> Unit, onOpenTask: (String) -> Unit, onMenu: (() -> Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); runCatching { focus.requestFocus() } }
    state.statusPicker?.let { picker -> StatusPicker(picker.options, onPick = vm::pickStatus, onDismiss = vm::dismissStatusPicker) }
    Column(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        val openSidebar = stringResource(R.string.navigation_open_sidebar)
        FloatingHeader {
            if (onMenu != null) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(Radius.small)).clickable(onClick = onMenu).semantics { contentDescription = openSidebar }, contentAlignment = Alignment.Center) {
                    HamburgerIcon()
                }
            }
            Icon(Icons.Filled.Search, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
            val placeholder = stringResource(R.string.tasks_search_tasks_placeholder)
            Box(Modifier.weight(1f)) {
                if (state.query.isEmpty()) Text(placeholder, style = TextStyles.headline, color = colors.textMuted)
                BasicTextField(
                    value = state.query, onValueChange = vm::setQuery, singleLine = true,
                    textStyle = TextStyles.headline.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search:field").semantics { contentDescription = placeholder },
                )
            }
            if (state.query.isNotEmpty()) Icon(
                Icons.Filled.Cancel, stringResource(R.string.search_clear), tint = colors.textMuted,
                modifier = Modifier.size(22.dp).clip(CircleShape).clickable { vm.setQuery("") },
            )
        }
        Box(Modifier.weight(1f)) {
            when {
                state.query.isBlank() -> EmptyState(stringResource(R.string.tasks_search_tasks_placeholder))
                state.searched && state.rows.isEmpty() -> EmptyState(stringResource(R.string.empty_state_default))
            }
            LazyColumn(
                contentPadding = PaddingValues(Spacing.s8),
                verticalArrangement = Arrangement.spacedBy(Spacing.s8),
                modifier = Modifier.fillMaxSize().testTag("searchResults"),
            ) {
                items(state.rows, key = { it.id }) { row -> TaskRowView(row, onLeading = { vm.leading(row) }, onOpen = { onOpenTask(row.id) }) }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
