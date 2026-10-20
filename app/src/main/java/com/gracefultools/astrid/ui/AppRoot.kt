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
import androidx.compose.foundation.layout.padding
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
import com.gracefultools.astrid.ui.account.ProfileScreen
import com.gracefultools.astrid.ui.account.ProfileViewModel
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

/** The core's id for My Tasks' filters (astrid_rules::filters::my_tasks::VIRTUAL_ID). */
private const val MY_TASKS_FILTER_ID = "virtual:my-tasks"

/** iOS's My Tasks title: " - Today !! !" for the due filter and priorities chosen, marks in their colours. */
@Composable
private fun myTasksSuffix(state: com.gracefultools.astrid.ui.filters.FiltersState): androidx.compose.ui.text.AnnotatedString? {
    val due = state.groups.firstOrNull { it.field == "filterDueDate" }?.picks?.firstOrNull { it.isSelected && it.value != "all" }
    val priority = state.groups.firstOrNull { it.field == "filterPriority" }?.picks?.firstOrNull { it.isSelected && it.value != "all" }
    if (due == null && priority == null) return null
    val dueWord = due?.let { com.gracefultools.astrid.ui.common.coreLabel(it.titleKey) }
    return androidx.compose.ui.text.buildAnnotatedString {
        append(" - ")
        if (dueWord != null) append(dueWord)
        priority?.value?.toIntOrNull()?.let { p ->
            if (dueWord != null) append(" ")
            pushStyle(androidx.compose.ui.text.SpanStyle(color = com.gracefultools.astrid.ui.theme.PriorityColors[p.coerceIn(0, 3)]))
            append(if (p == 0) "○" else "!".repeat(p))
            pop()
        }
    }
}

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
    val context = androidx.compose.ui.platform.LocalContext.current
    var newTask by remember { mutableStateOf<Arrival.NewTask?>(null) }
    var reminder by remember { mutableStateOf<Arrival.Reminder?>(null) }
    // Each arrival gets its own view model, even one identical to the last.
    var serial by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // A link, a share or a reminder: the screen it names (iOS DeepLinkManager / presenters, AATD-59).
    suspend fun arrive(arrival: Arrival) {
        when (arrival) {
            is Arrival.OpenTask -> nav.navigate("task/${arrival.ref}")
            is Arrival.OpenList -> { nav.popBackStack("list", inclusive = false); sidebar.select(arrival.id) }
            // Only the reader's own profile has a screen yet; another person's opens it too.
            is Arrival.OpenProfile -> nav.navigate("profile")
            is Arrival.OpenSettings -> nav.navigate(arrival.route)
            is Arrival.Shortcode -> arrive(DeepLinks.resolveShortcode(graph.core, arrival.code))
            is Arrival.OpenWeb -> runCatching {
                androidx.browser.customtabs.CustomTabsIntent.Builder().setShowTitle(true).build()
                    .launchUrl(context, android.net.Uri.parse(arrival.url))
            }
            is Arrival.NewTask -> { serial++; newTask = arrival }
            is Arrival.Reminder -> { serial++; reminder = arrival }
        }
    }
    LaunchedEffect(pending) { onDeepLink.take()?.let { arrive(it) } }
    newTask?.let { arrival ->
        val share: com.gracefultools.astrid.ui.share.ShareTaskViewModel =
            viewModel(key = "share-$serial", factory = factory { com.gracefultools.astrid.ui.share.ShareTaskViewModel(graph.core) })
        val shareState by share.state.collectAsStateWithLifecycle()
        LaunchedEffect(shareState.done) { if (shareState.done) { newTask = null; tasks.refresh() } }
        NewTaskSheet(
            initial = arrival.text, file = arrival.file, saving = shareState.saving, error = shareState.error,
            onDismiss = { newTask = null },
            onSave = { title, description, priority -> share.save(title, description, priority, arrival.file) },
        )
    }
    reminder?.let { arrival ->
        val vm: com.gracefultools.astrid.ui.reminders.ReminderViewModel = viewModel(
            key = "reminder-$serial",
            factory = factory { com.gracefultools.astrid.ui.reminders.ReminderViewModel(graph.core, arrival.taskIds) },
        )
        com.gracefultools.astrid.ui.reminders.ReminderOverlay(
            vm,
            onOpenTask = { nav.navigate("task/$it") },
            onFinished = { handled ->
                com.gracefultools.astrid.platform.ReminderNotifier.dismiss(context, handled)
                reminder = null
            },
        )
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
                // Messages for a list, and for My Tasks through the reader's virtual channel (AATD-62);
                // a saved filter has none.
                val includesMessages = selected != null && (selected.isVirtual != true || isMyTasks)
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
                var filtering by remember { mutableStateOf(false) }
                val myFilters: com.gracefultools.astrid.ui.filters.FiltersViewModel? = if (isMyTasks) viewModel(
                    key = "filters-my-tasks", factory = factory { com.gracefultools.astrid.ui.filters.FiltersViewModel(graph.core, MY_TASKS_FILTER_ID) },
                ) else null
                val myFilterState = myFilters?.state?.collectAsStateWithLifecycle()?.value
                if (filtering && myFilters != null) {
                    com.gracefultools.astrid.ui.filters.MyTasksFilterSheet(myFilters, onDismiss = { filtering = false; tasks.reload() })
                }
                TaskListScreen(
                    title = title,
                    titleSuffix = myFilterState?.let { myTasksSuffix(it) },
                    onFilter = if (isMyTasks) ({ myFilters?.load(); filtering = true }) else null,
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
                    detail.onDueDateSet = graph.notificationPrompt::request
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
            detail.onDueDateSet = graph.notificationPrompt::request
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
            },
                filters = {
                    val filters: com.gracefultools.astrid.ui.filters.FiltersViewModel = viewModel(key = "filters-$id", factory = factory { com.gracefultools.astrid.ui.filters.FiltersViewModel(graph.core, id) })
                    val fs by filters.state.collectAsStateWithLifecycle()
                    com.gracefultools.astrid.ui.filters.FiltersPanel(filters, androidx.compose.ui.Modifier.padding(horizontal = com.gracefultools.astrid.ui.theme.Spacing.s16)) {
                        com.gracefultools.astrid.ui.filters.ListSwitches(fs, filters)
                    }
                },
                integrations = { com.gracefultools.astrid.ui.sync.ListIntegrationsSections(graph.core, id) },
            )
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
            SettingsScreen(
                settings, onBack = { nav.popBackStack() }, onSignedOut = onSignedOut, onOpen = { nav.navigate(it) },
                canOpen = { route -> nav.graph.findNode(route) != null },
            )
        }
        composable("settings/reminders") {
            val settings: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(graph.core, graph.preferences) })
            com.gracefultools.astrid.ui.settings.ReminderSettingsScreen(settings, onBack = { nav.popBackStack() })
        }
        composable("settings/appearance") {
            val settings: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(graph.core, graph.preferences) })
            com.gracefultools.astrid.ui.settings.AppearanceSettingsScreen(settings, onBack = { nav.popBackStack() })
        }
        composable("settings/language") {
            com.gracefultools.astrid.ui.settings.LanguageSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("profile") {
            val vm: ProfileViewModel = viewModel(key = "profile", factory = factory { ProfileViewModel(graph.core) })
            ProfileScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("account") {
            val vm: ProfileViewModel = viewModel(key = "profile", factory = factory { ProfileViewModel(graph.core) })
            com.gracefultools.astrid.ui.account.AccountAccessScreen(vm, onBack = { nav.popBackStack() }, onDelete = { nav.navigate("account/delete") })
        }
        composable("account/delete") {
            val vm: ProfileViewModel = viewModel(key = "profile", factory = factory { ProfileViewModel(graph.core) })
            com.gracefultools.astrid.ui.account.DeleteAccountScreen(vm, onBack = { nav.popBackStack() }, onSignedOut = onSignedOut)
        }
        // AI Agents, Connections, and GitHub Issues / Google Tasks sync (AATD-57).
        composable(com.gracefultools.astrid.ui.agents.AgentRoutes.HUB) {
            val vm: com.gracefultools.astrid.ui.agents.AgentsViewModel = viewModel(factory = factory { com.gracefultools.astrid.ui.agents.AgentsViewModel(graph.core) })
            com.gracefultools.astrid.ui.agents.AgentsScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) })
        }
        composable(com.gracefultools.astrid.ui.agents.AgentRoutes.WEBHOOK) {
            val vm: com.gracefultools.astrid.ui.agents.WebhookViewModel = viewModel(factory = factory { com.gracefultools.astrid.ui.agents.WebhookViewModel(graph.core) })
            com.gracefultools.astrid.ui.agents.WebhookSettingsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(com.gracefultools.astrid.ui.agents.AgentRoutes.CUSTOM) {
            val vm: com.gracefultools.astrid.ui.agents.CustomAgentsViewModel = viewModel(factory = factory { com.gracefultools.astrid.ui.agents.CustomAgentsViewModel(graph.core) })
            com.gracefultools.astrid.ui.agents.CustomAgentsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(com.gracefultools.astrid.ui.agents.AgentRoutes.MODEL) {
            val vm: com.gracefultools.astrid.ui.agents.AstridModelViewModel = viewModel(factory = factory { com.gracefultools.astrid.ui.agents.AstridModelViewModel(graph.core) })
            com.gracefultools.astrid.ui.agents.AstridModelScreen(vm, onBack = { nav.popBackStack() }, onAddKey = { nav.popBackStack() })
        }
        composable(com.gracefultools.astrid.ui.agents.AgentRoutes.CONNECTIONS) {
            val vm: com.gracefultools.astrid.ui.connections.ConnectionsViewModel = viewModel(factory = factory { com.gracefultools.astrid.ui.connections.ConnectionsViewModel(graph.core) })
            com.gracefultools.astrid.ui.connections.ConnectionsScreen(vm, graph.core, onBack = { nav.popBackStack() })
        }
        composable(com.gracefultools.astrid.ui.sync.SyncRoutes.GITHUB) {
            val vm: com.gracefultools.astrid.ui.sync.ExternalSyncViewModel = viewModel(factory = factory { com.gracefultools.astrid.ui.sync.ExternalSyncViewModel(graph.core, com.gracefultools.astrid.ui.sync.SyncProvider.GITHUB) })
            com.gracefultools.astrid.ui.sync.ExternalSyncScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(com.gracefultools.astrid.ui.sync.SyncRoutes.GOOGLE) {
            val vm: com.gracefultools.astrid.ui.sync.ExternalSyncViewModel = viewModel(factory = factory { com.gracefultools.astrid.ui.sync.ExternalSyncViewModel(graph.core, com.gracefultools.astrid.ui.sync.SyncProvider.GOOGLE) })
            com.gracefultools.astrid.ui.sync.ExternalSyncScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
