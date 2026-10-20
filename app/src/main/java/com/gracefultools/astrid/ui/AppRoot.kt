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
import com.gracefultools.astrid.ui.lists.NewListScreen
import com.gracefultools.astrid.ui.lists.NewListViewModel
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

/** iOS's getEmptyStateMessage: what an empty list says, by what kind of list it is. */
@Composable
private fun emptyMessage(list: com.gracefultools.astrid.model.ListSummary?, isMyTasks: Boolean): String = when {
    list == null -> stringResource(R.string.empty_state_default)
    isMyTasks || list.virtualListType == "my-tasks" -> stringResource(R.string.empty_state_my_tasks, stringResource(R.string.app_name))
    list.isVirtual == true -> if (list.virtualListType == "today") stringResource(R.string.empty_state_today) else stringResource(R.string.empty_state_default)
    list.privacy == "PUBLIC" -> stringResource(R.string.empty_state_public_list)
    list.privacy == "SHARED" -> stringResource(R.string.empty_state_shared_list)
    else -> stringResource(R.string.empty_state_list_with_name, list.name)
}

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
    // List, messages or board, per list, on this device. A list with a board opens on it, as iOS's
    // does when a list with a project is selected (TaskListView 742-746).
    val viewModes = remember { mutableStateMapOf<String, com.gracefultools.astrid.ui.tasks.ViewMode>() }
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
            scope.launch { if (tasks.create(title) != null) newTask = null }
        }
    }

    NavHost(nav, startDestination = "list") {
        composable("list") {
            // iPhone, iPad portrait, iPad landscape — by width, as Android's window size classes cut it.
            val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            val twoPane = width >= 600
            val permanentSidebar = width >= 840
            var paneTaskId by rememberSaveable { mutableStateOf<String?>(null) }
            // iOS's search is a mode of the main pane, entered from the sidebar's magnifier.
            var searching by rememberSaveable { mutableStateOf(false) }
            val openTask: (String) -> Unit = { id -> if (twoPane) paneTaskId = id else nav.navigate("task/$id") }
            LaunchedEffect(side.selectedId) { paneTaskId = null }

            val sidebarContent: @Composable () -> Unit = {
                SidebarDrawer(
                    side,
                    onSelect = { searching = false; sidebar.select(it.id); scope.launch { drawer.close() } },
                    onSettings = { scope.launch { drawer.close() }; nav.navigate("settings") },
                    onSearch = { scope.launch { drawer.close() }; searching = true },
                    onProfile = { scope.launch { drawer.close() }; nav.navigate("profile") },
                    onToggleFavorite = sidebar::toggleFavorite,
                    onCreate = { sidebar.create(it); scope.launch { drawer.close() } },
                    onAddList = { scope.launch { drawer.close() }; nav.navigate("new-list") },
                )
            }
            val listContent: @Composable () -> Unit = if (searching) ({
                val search: SearchViewModel = viewModel(factory = factory { SearchViewModel(graph.core) })
                SearchScreen(
                    search, onBack = { searching = false }, onOpenTask = openTask,
                    onMenu = if (permanentSidebar) null else ({ scope.launch { drawer.open() } }),
                )
            }) else ({
                val selected = side.selected
                val isMyTasks = selected != null && selected.id == side.myTasks?.id
                val title = selected?.let { if (isMyTasks) stringResource(R.string.navigation_my_tasks) else it.name } ?: ""
                val hasBoard = selected?.projectId != null
                // Messages for a real list; My Tasks' virtual channel is AATD-62.
                val includesMessages = selected != null && selected.isVirtual != true
                val mode = selected?.let { viewModes[it.id] }
                    ?: if (hasBoard) com.gracefultools.astrid.ui.tasks.ViewMode.BOARD else com.gracefultools.astrid.ui.tasks.ViewMode.LIST
                if (mode == com.gracefultools.astrid.ui.tasks.ViewMode.BOARD) LaunchedEffect(selected!!.id) { boardVm.open(selected.id) }
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
                    viewMode = mode,
                    hasBoard = hasBoard,
                    includesMessages = includesMessages,
                    onViewMode = { next -> selected?.let { viewModes[it.id] = next } },
                    onListSettings = selected?.takeIf { it.isVirtual != true }?.let { list -> { nav.navigate("list/${list.id}") } },
                    board = {
                        BoardContent(
                            boardState,
                            onOpenTask = openTask,
                            onMove = boardVm::moveTo,
                            onComplete = boardVm::leading,
                            onDrop = boardVm::drop,
                            onAddCard = boardVm::addCard,
                            onDismissError = boardVm::dismissError,
                            selectedTaskId = paneTaskId,
                        )
                    },
                    messages = {
                        selected?.let { list ->
                            val vm: ChatViewModel = viewModel(key = "chat-${list.id}", factory = factory { ChatViewModel(graph.core, list.id) })
                            com.gracefultools.astrid.ui.chat.ChatPanel(vm)
                        }
                    },
                    emptyMessage = emptyMessage(selected, isMyTasks),
                    state = list,
                    // A permanent sidebar has nothing to open: no button that does nothing.
                    onMenu = if (permanentSidebar) null else ({ scope.launch { drawer.open() } }),
                    onOpenTask = openTask,
                    onComplete = { row -> if (row.action == "complete") tasks.setCompleted(row.id, !row.completed) else tasks.leading(row) },
                    onDelete = { tasks.delete(it.id) },
                    onAdd = tasks::create,
                    onRefresh = tasks::refresh,
                    onLoadMore = tasks::loadMore,
                    onDismissError = tasks::dismissError,
                    onPickPriority = tasks::choosePriority,
                    onPickAssignee = tasks::chooseAssignee,
                    selectedTaskId = paneTaskId,
                )
            })
            val detailPane: @Composable () -> Unit = {
                val id = paneTaskId
                if (id == null) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize().background(com.gracefultools.astrid.ui.theme.AstridTheme.colors.bgPrimary),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { Text(stringResource(R.string.detail_select_a_task), color = com.gracefultools.astrid.ui.theme.AstridTheme.colors.textSecondary) }
                } else {
                    val detail: TaskDetailViewModel = viewModel(key = "pane-$id", factory = factory { TaskDetailViewModel(graph.core, id) })
                    TaskDetailScreen(detail, onBack = { paneTaskId = null }, onOpenTask = { paneTaskId = it }, onOpenList = { sidebar.select(it) })
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
            TaskDetailScreen(
                detail, onBack = { nav.popBackStack() },
                onOpenTask = { nav.navigate("task/$it") },
                onOpenList = { sidebar.select(it); nav.popBackStack("list", inclusive = false) },
            )
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
        composable("new-list") {
            val vm: NewListViewModel = viewModel(key = "new-list", factory = factory { NewListViewModel(graph.core) })
            // Android opens the new list once it is made; iOS's ListEditView only dismisses (PARITY).
            NewListScreen(vm, onCancel = { nav.popBackStack() }, onCreated = { id ->
                nav.popBackStack()
                sidebar.select(id)
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
