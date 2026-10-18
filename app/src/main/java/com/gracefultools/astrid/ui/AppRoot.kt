package com.gracefultools.astrid.ui

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gracefultools.astrid.AppGraph
import com.gracefultools.astrid.DeepLinks
import com.gracefultools.astrid.R
import com.gracefultools.astrid.factory
import com.gracefultools.astrid.ui.board.BoardContent
import com.gracefultools.astrid.ui.common.StatusPicker
import com.gracefultools.astrid.ui.board.BoardViewModel
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.gracefultools.astrid.ui.detail.TaskDetailScreen
import com.gracefultools.astrid.ui.detail.TaskDetailViewModel
import com.gracefultools.astrid.ui.search.SearchScreen
import com.gracefultools.astrid.ui.search.SearchViewModel
import com.gracefultools.astrid.ui.settings.SettingsScreen
import com.gracefultools.astrid.ui.settings.SettingsViewModel
import com.gracefultools.astrid.ui.sidebar.SidebarDrawer
import com.gracefultools.astrid.ui.sidebar.SidebarViewModel
import com.gracefultools.astrid.ui.tasks.TaskListScreen
import com.gracefultools.astrid.ui.tasks.TaskListViewModel
import kotlinx.coroutines.launch

/** The signed-in app: the sidebar drawer over a list, with detail, search and settings pushed on top. */
@Composable
fun AppRoot(graph: AppGraph, onSignedOut: () -> Unit, onDeepLink: DeepLinks) {
    val nav = rememberNavController()
    val sidebar: SidebarViewModel = viewModel(factory = factory { SidebarViewModel(graph.core) })
    val tasks: TaskListViewModel = viewModel(factory = factory { TaskListViewModel(graph.core) })
    val side by sidebar.state.collectAsStateWithLifecycle()
    val list by tasks.state.collectAsStateWithLifecycle()
    val boardVm: BoardViewModel = viewModel(factory = factory { BoardViewModel(graph.core) })
    val boardState by boardVm.state.collectAsStateWithLifecycle()
    // Board or list, per list, on this device — iOS keeps the choice per list too.
    val boardLists = remember { mutableStateMapOf<String, Boolean>() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { sidebar.load() }
    LaunchedEffect(side.selectedId) {
        side.selected?.let { tasks.open(it.id, isVirtual = it.isVirtual == true) }
    }
    val pending by onDeepLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val link = onDeepLink.take() ?: return@LaunchedEffect
        taskRefFrom(link)?.let { nav.navigate("task/$it") }
    }

    NavHost(nav, startDestination = "list") {
        composable("list") {
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    SidebarDrawer(
                        side,
                        onSelect = { sidebar.select(it.id); scope.launch { drawer.close() } },
                        onSettings = { scope.launch { drawer.close() }; nav.navigate("settings") },
                    )
                },
            ) {
                val title = side.selected?.let { if (it.id == side.myTasks?.id) stringResource(R.string.navigation_my_tasks) else it.name } ?: ""
                val selected = side.selected
                val onBoard = selected?.projectId != null
                val showingBoard = onBoard && boardLists[selected!!.id] == true
                if (showingBoard) LaunchedEffect(selected!!.id) { boardVm.open(selected.id) }
                (list.statusPicker ?: boardState.statusPicker)?.let { picker ->
                    val fromBoard = boardState.statusPicker != null
                    StatusPicker(
                        picker.options,
                        onPick = { if (fromBoard) boardVm.pickStatus(it) else tasks.pickStatus(it) },
                        onDismiss = { if (fromBoard) boardVm.dismissStatusPicker() else tasks.dismissStatusPicker() },
                    )
                }
                TaskListScreen(
                    title = title,
                    onToggleBoard = if (onBoard) ({ boardLists[selected!!.id] = !showingBoard }) else null,
                    showingBoard = showingBoard,
                    board = {
                        BoardContent(
                            boardState,
                            onOpenTask = { nav.navigate("task/$it") },
                            onMove = boardVm::moveTo,
                            onComplete = boardVm::leading,
                        )
                    },
                    state = list,
                    onMenu = { scope.launch { drawer.open() } },
                    onSearch = { nav.navigate("search") },
                    onOpenTask = { nav.navigate("task/$it") },
                    onComplete = { row -> if (row.action == "complete") tasks.setCompleted(row.id, !row.completed) else tasks.leading(row) },
                    onDelete = { tasks.delete(it.id) },
                    onAdd = tasks::create,
                    onRefresh = tasks::refresh,
                    onLoadMore = tasks::loadMore,
                    onDismissError = tasks::dismissError,
                )
            }
        }
        composable("task/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val detail: TaskDetailViewModel = viewModel(key = "detail-$id", factory = factory { TaskDetailViewModel(graph.core, id) })
            TaskDetailScreen(detail, onBack = { nav.popBackStack() })
        }
        composable("search") {
            val search: SearchViewModel = viewModel(factory = factory { SearchViewModel(graph.core) })
            SearchScreen(search, onBack = { nav.popBackStack() }, onOpenTask = { nav.navigate("task/$it") })
        }
        composable("settings") {
            val settings: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(graph.core, graph.preferences) })
            SettingsScreen(settings, onBack = { nav.popBackStack() }, onSignedOut = onSignedOut)
        }
    }
}

/** `astrid.cc/t/AWTD-1066` or `astrid://task/<id>` → the task reference the core resolves. */
fun taskRefFrom(link: String): String? {
    val web = Regex("""^https://(?:www\.)?astrid\.cc/t/([A-Za-z0-9-]+)""").find(link)
    if (web != null) return web.groupValues[1]
    val app = Regex("""^astrid://task/([A-Za-z0-9-]+)""").find(link)
    return app?.groupValues?.get(1)
}
