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
import com.gracefultools.astrid.Arrival
import com.gracefultools.astrid.DeepLinks
import com.gracefultools.astrid.ui.tasks.NewTaskSheet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.setValue
import com.gracefultools.astrid.R
import com.gracefultools.astrid.factory
import com.gracefultools.astrid.ui.board.BoardContent
import com.gracefultools.astrid.ui.common.StatusPicker
import com.gracefultools.astrid.ui.board.BoardViewModel
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.gracefultools.astrid.ui.detail.TaskDetailScreen
import com.gracefultools.astrid.ui.detail.TaskDetailViewModel
import com.gracefultools.astrid.ui.lists.ListSettingsScreen
import com.gracefultools.astrid.ui.lists.ListSettingsViewModel
import com.gracefultools.astrid.ui.account.AccountViewModel
import com.gracefultools.astrid.ui.account.AgentsScreen
import com.gracefultools.astrid.ui.account.ConnectionsScreen
import com.gracefultools.astrid.ui.account.ProfileScreen
import com.gracefultools.astrid.ui.chat.ChatScreen
import com.gracefultools.astrid.ui.chat.ChatViewModel
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
    var newTask by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pending) {
        when (val arrival = onDeepLink.take()) {
            is Arrival.OpenTask -> nav.navigate("task/${arrival.ref}")
            is Arrival.NewTask -> newTask = arrival.text
            null -> Unit
        }
    }
    newTask?.let { text ->
        NewTaskSheet(initial = text, listName = side.selected?.name, onDismiss = { newTask = null }) { title ->
            scope.launch { if (tasks.create(title)) newTask = null }
        }
    }

    NavHost(nav, startDestination = "list") {
        composable("list") {
            // iPhone, iPad portrait, iPad landscape — by width, as Android's window size classes cut it.
            val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            val twoPane = width >= 600
            val permanentSidebar = width >= 840
            var paneTaskId by rememberSaveable { mutableStateOf<String?>(null) }
            val openTask: (String) -> Unit = { id -> if (twoPane) paneTaskId = id else nav.navigate("task/$id") }
            LaunchedEffect(side.selectedId) { paneTaskId = null }

            val sidebarContent: @Composable () -> Unit = {
                SidebarDrawer(
                    side,
                    onSelect = { sidebar.select(it.id); scope.launch { drawer.close() } },
                    onSettings = { scope.launch { drawer.close() }; nav.navigate("settings") },
                    onCreate = { sidebar.create(it); scope.launch { drawer.close() } },
                )
            }
            val listContent: @Composable () -> Unit = {
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
                    onListSettings = selected?.takeIf { it.isVirtual != true }?.let { list -> { nav.navigate("list/${list.id}") } },
                    onChat = selected?.takeIf { it.isVirtual != true }?.let { list -> { nav.navigate("chat/${list.id}") } },
                    board = {
                        BoardContent(
                            boardState,
                            onOpenTask = openTask,
                            onMove = boardVm::moveTo,
                            onComplete = boardVm::leading,
                            onDrop = boardVm::drop,
                        )
                    },
                    state = list,
                    // A permanent sidebar has nothing to open: no button that does nothing.
                    onMenu = if (permanentSidebar) null else ({ scope.launch { drawer.open() } }),
                    onSearch = { nav.navigate("search") },
                    onOpenTask = openTask,
                    onComplete = { row -> if (row.action == "complete") tasks.setCompleted(row.id, !row.completed) else tasks.leading(row) },
                    onDelete = { tasks.delete(it.id) },
                    onAdd = tasks::create,
                    onRefresh = tasks::refresh,
                    onLoadMore = tasks::loadMore,
                    onDismissError = tasks::dismissError,
                )
            }
            val detailPane: @Composable () -> Unit = {
                val id = paneTaskId
                if (id == null) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize().background(com.gracefultools.astrid.ui.theme.AstridTheme.colors.bgPrimary),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { Text(stringResource(R.string.detail_select_a_task), color = com.gracefultools.astrid.ui.theme.AstridTheme.colors.textSecondary) }
                } else {
                    val detail: TaskDetailViewModel = viewModel(key = "pane-$id", factory = factory { TaskDetailViewModel(graph.core, id) })
                    TaskDetailScreen(detail, onBack = { paneTaskId = null })
                }
            }
            val panes: @Composable () -> Unit = {
                if (twoPane) {
                    androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxSize()) {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { listContent() }
                        androidx.compose.material3.VerticalDivider(color = com.gracefultools.astrid.ui.theme.AstridTheme.colors.border)
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1.2f).testTag("detailPane")) { detailPane() }
                    }
                } else {
                    listContent()
                }
            }
            if (permanentSidebar) {
                androidx.compose.material3.PermanentNavigationDrawer(drawerContent = sidebarContent) { panes() }
            } else {
                ModalNavigationDrawer(drawerState = drawer, drawerContent = sidebarContent) { panes() }
            }
        }
        composable("task/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val detail: TaskDetailViewModel = viewModel(key = "detail-$id", factory = factory { TaskDetailViewModel(graph.core, id) })
            TaskDetailScreen(detail, onBack = { nav.popBackStack() })
        }
        composable("list/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: ListSettingsViewModel = viewModel(key = "list-$id", factory = factory { ListSettingsViewModel(graph.core, id) })
            ListSettingsScreen(vm, onBack = { nav.popBackStack() }, onGone = {
                nav.popBackStack()
                side.myTasks?.let { sidebar.select(it.id) }
                sidebar.load()
            })
        }
        composable("chat/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: ChatViewModel = viewModel(key = "chat-$id", factory = factory { ChatViewModel(graph.core, id) })
            val name = (listOfNotNull(side.myTasks) + side.favorites + side.lists).firstOrNull { it.id == id }?.name
            ChatScreen(vm, title = name ?: stringResource(R.string.chat_title), onBack = { nav.popBackStack() })
        }
        composable("search") {
            val search: SearchViewModel = viewModel(factory = factory { SearchViewModel(graph.core) })
            SearchScreen(search, onBack = { nav.popBackStack() }, onOpenTask = { nav.navigate("task/$it") })
        }
        composable("settings") {
            val settings: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(graph.core, graph.preferences) })
            SettingsScreen(settings, onBack = { nav.popBackStack() }, onSignedOut = onSignedOut, onOpen = { nav.navigate(it) })
        }
        composable("profile") {
            val vm: AccountViewModel = viewModel(key = "account", factory = factory { AccountViewModel(graph.core) })
            ProfileScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("agents") {
            val vm: AccountViewModel = viewModel(key = "account", factory = factory { AccountViewModel(graph.core) })
            AgentsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("connections") {
            val vm: AccountViewModel = viewModel(key = "account", factory = factory { AccountViewModel(graph.core) })
            ConnectionsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
