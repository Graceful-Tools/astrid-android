package com.gracefultools.astrid.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.TaskRow
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import kotlinx.coroutines.launch

/**
 * A list: header with the sidebar and search buttons, the rows, and the quick-add bar at the bottom
 * — iOS's TaskListView on iPhone. Pull to refresh; swipe right to complete, left to delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    title: String,
    state: TaskListState,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onOpenTask: (String) -> Unit,
    onComplete: (TaskRow) -> Unit,
    onDelete: (TaskRow) -> Unit,
    onAdd: suspend (String) -> Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDismissError: () -> Unit,
) {
    val colors = AstridTheme.colors
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); onDismissError() }
    }
    Box(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            Header(title, onMenu, onSearch)
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
                val listState = rememberLazyListState()
                LaunchedEffect(listState, state.rows.size) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        .collect { last -> if (last >= state.rows.size - 20) onLoadMore() }
                }
                if (state.rows.isEmpty() && !state.loading) {
                    Text(
                        stringResource(R.string.tasks_empty), color = colors.textSecondary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = Spacing.s12, vertical = Spacing.s8),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s8),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        SwipeRow(row, onComplete = { onComplete(row) }, onDelete = { onDelete(row) }) {
                            TaskRowView(row, onLeading = { onComplete(row) }, onOpen = { onOpenTask(row.id) })
                        }
                    }
                }
            }
            QuickAddBar(onAdd)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
}

@Composable
private fun Header(title: String, onMenu: () -> Unit, onSearch: () -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.headerBg).padding(top = 36.dp, start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, stringResource(R.string.navigation_open_sidebar), tint = colors.textPrimary) }
        Text(
            title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary,
            modifier = Modifier.weight(1f).testTag("listTitle").semantics { heading() }, maxLines = 1,
        )
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.navigation_search), tint = colors.textPrimary) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(row: TaskRow, onComplete: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onComplete(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().background(if (toEnd) Success else Error, RoundedCornerShape(8.dp)).padding(horizontal = 20.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    if (toEnd) Icons.Filled.Check else Icons.Filled.Delete,
                    stringResource(if (toEnd) R.string.tasks_complete else R.string.tasks_delete),
                    tint = Color.White,
                )
            }
        },
    ) { content() }
}

@Composable
private fun QuickAddBar(onAdd: suspend (String) -> Boolean) {
    val colors = AstridTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val submit = {
        val title = text
        scope.launch { if (onAdd(title)) text = "" }
        Unit
    }
    Row(
        Modifier.fillMaxWidth().background(colors.headerBg).navigationBarsPadding().imePadding()
            .padding(horizontal = Spacing.s12, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.tasks_add_task_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.inputBg, unfocusedContainerColor = colors.inputBg,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
                unfocusedPlaceholderColor = colors.inputPlaceholder, focusedPlaceholderColor = colors.inputPlaceholder,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = submit, enabled = text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.tasks_add_task), tint = colors.accent)
        }
    }
}
