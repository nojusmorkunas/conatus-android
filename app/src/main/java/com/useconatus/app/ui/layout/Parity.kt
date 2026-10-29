@file:OptIn(ExperimentalMaterial3Api::class)

package com.useconatus.app.ui.layout

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.useconatus.app.AppContainer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import com.useconatus.app.ui.AppLink
import com.useconatus.app.ui.views.COMPLETED
import com.useconatus.app.ui.views.CalendarToggle
import com.useconatus.app.ui.views.CompletedViewModel
import com.useconatus.app.ui.views.RowSlot
import com.useconatus.app.ui.views.SEARCH
import com.useconatus.app.ui.views.SearchField
import com.useconatus.app.ui.views.SearchViewModel
import com.useconatus.app.ui.views.TRASH
import com.useconatus.app.ui.views.TrashViewModel
import com.useconatus.app.ui.views.UpcomingViewModel
import com.useconatus.app.ui.views.calendarItems
import com.useconatus.app.ui.views.completedItems
import com.useconatus.app.ui.views.searchItems
import com.useconatus.app.ui.views.trashItems
import com.useconatus.app.ui.views.upcomingItems
import com.useconatus.app.data.db.SectionEntity
import com.useconatus.app.ui.project.AddTaskLine
import com.useconatus.app.ui.project.ArchivedProjects
import com.useconatus.app.ui.project.ListActions
import com.useconatus.app.ui.project.ListSource
import com.useconatus.app.ui.project.ListState
import com.useconatus.app.ui.project.ListViewModel
import com.useconatus.app.ui.project.NewLabelDialog
import com.useconatus.app.ui.project.NewProjectDialog
import com.useconatus.app.ui.project.ProjectBoard
import com.useconatus.app.ui.project.ProjectList
import com.useconatus.app.ui.project.TreeRow
import com.useconatus.app.ui.theme.Hue
import com.useconatus.app.ui.components.AddedMessages
import com.useconatus.app.ui.components.ListMessage
import com.useconatus.app.ui.components.LocalOpenSettings
import com.useconatus.app.ui.components.ProjectIcon
import com.useconatus.app.ui.components.SectionHeading
import com.useconatus.app.ui.components.SlideOverPage
import com.useconatus.app.ui.components.TaskCheckbox
import com.useconatus.app.ui.components.TaskDetails
import com.useconatus.app.ui.components.UndoSnackbars
import com.useconatus.app.ui.components.dueTexts
import com.useconatus.app.ui.components.rememberUndoSnackbars
import com.useconatus.app.ui.components.SelectionUndo
import com.useconatus.app.ui.components.taskClick
import com.useconatus.app.ui.task.ChangesNotice
import com.useconatus.app.ui.task.ChangesSheet
import com.useconatus.app.ui.task.CommentList
import com.useconatus.app.ui.task.ConflictNotice
import com.useconatus.app.ui.task.Editor
import com.useconatus.app.ui.task.EditorDialog
import com.useconatus.app.ui.task.FailureNotice
import com.useconatus.app.ui.task.PickDateDialog
import com.useconatus.app.ui.task.QuickAddSheet
import com.useconatus.app.ui.task.RescheduleSheet
import com.useconatus.app.ui.task.TaskDetailViewModel
import com.useconatus.app.ui.task.taskProperties
import com.useconatus.app.ui.theme.LocalConatusColors
import com.useconatus.app.ui.today.TaskRow
import com.useconatus.app.ui.today.TodayState
import com.useconatus.app.ui.today.TodayViewModel
import com.useconatus.app.ui.today.inTreeOrder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

// The Parity layout, called Web on screen: the web app on a phone, with a drawer like its sidebar, a +
// button for quick add and task details on a page of their own.

private const val TODAY = "today"
private const val UPCOMING = "upcoming"

@Composable
fun ParityLayout(vm: TodayViewModel, state: TodayState, container: AppContainer) {
    // A drawer destination: Today, Upcoming, search, Completed, Trash, a project id or a label's "l:id".
    var place by rememberSaveable { mutableStateOf(TODAY) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    val searchVm: SearchViewModel = viewModel { SearchViewModel(container) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var rescheduling by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf<String?>(null) }
    // The open task, and the last one, which stays on the page while it slides away.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val undo = rememberUndoSnackbars(vm, snackbar)
    SelectionUndo(undo)
    val project = state.projects.firstOrNull { it.id == place }
    val openTask = { id: String -> open = id; shown = id }
    var newProject by rememberSaveable { mutableStateOf(false) }
    var newLabel by rememberSaveable { mutableStateOf(false) }
    var archived by rememberSaveable { mutableStateOf(false) }
    // Where the + adds: null for Today's Inbox, "" for the project shown, or a section id in it.
    var addingTo by rememberSaveable { mutableStateOf<String?>(null) }
    val listKey = when {
        project != null -> "p:" + project.id
        place.startsWith("l:") -> place
        else -> null
    }
    val listVm: ListViewModel? = listKey?.let { key ->
        viewModel(key = "list-$key") { ListViewModel(container, if (key.startsWith("p:")) ListSource.Project(key.drop(2)) else ListSource.Label(key.drop(2))) }
    }
    val list = listVm?.state?.collectAsStateWithLifecycle()?.value
    // The open place's tasks in order, for the details page's previous and next.
    val order = list?.groups?.flatMap { group -> group.rows.map { it.row } } ?: (state.overdue + state.dueToday)
    BackHandler(enabled = place != TODAY && !drawer.isOpen) { place = TODAY }
    val link by container.links.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        when (val to = link ?: return@LaunchedEffect) {
            is AppLink.Task -> openTask(to.id)
            is AppLink.Project -> place = to.id
            AppLink.Today -> place = TODAY
            AppLink.Upcoming -> place = UPCOMING
            AppLink.Search -> place = SEARCH
        }
        container.links.value = null
    }
    // A project or label made on this phone takes the server's id once the server has it; stay on it.
    LaunchedEffect(state.projects, state.labels) {
        container.outbox.renamedTo(place.removePrefix("l:"))?.let { place = if (place.startsWith("l:")) "l:$it" else it }
    }

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ParityDrawer(state, place, drawer, onNew = { label ->
                    scope.launch { drawer.close() }
                    if (label) newLabel = true else newProject = true
                }, onArchived = {
                    scope.launch { drawer.close() }
                    archived = true
                }, onSearch = { query ->
                    searchVm.search(query)
                    place = SEARCH
                    scope.launch { drawer.close() }
                }) { picked ->
                    place = picked
                    scope.launch { drawer.close() }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                project?.takeIf { !it.isInbox }?.let { ProjectIcon(it, 20.dp); Spacer(Modifier.width(10.dp)) }
                                list?.label?.let { Icon(Icons.Outlined.Sell, null, Modifier.size(18.dp), tint = Hue.of(it.color).dot); Spacer(Modifier.width(10.dp)) }
                                val title = when (place) {
                                    UPCOMING -> "Upcoming"
                                    SEARCH -> "Search"
                                    COMPLETED -> "Completed"
                                    TRASH -> "Trash"
                                    else -> "Today"
                                }
                                Text(list?.title?.takeIf { it.isNotEmpty() } ?: title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        navigationIcon = { IconButton({ scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "Open navigation") } },
                        actions = {
                            if (list != null && listVm != null) ListActions(list, listVm, onGone = { place = TODAY })
                            if (place == UPCOMING) CalendarToggle(calendar) { calendar = it }
                            IconButton(LocalOpenSettings.current) { Icon(Icons.Outlined.Settings, "Settings") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
                    )
                },
                floatingActionButton = {
                    FloatingActionButton({ if (list?.project != null) addingTo = "" else adding = true }, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Outlined.Add, "Add a task")
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = MaterialTheme.shapes.medium) } },
            ) { padding ->
                Column(Modifier.padding(padding)) {
                    when {
                        place == TODAY -> ParityToday(vm, state, undo, openTask, onReschedule = { rescheduling = it }) { reviewing = true }
                        place in setOf(UPCOMING, SEARCH, COMPLETED, TRASH) -> ParityView(container, place, calendar, searchVm, { place = it }) { today ->
                            parityRows(vm, undo, today, openTask) { rescheduling = it }
                        }
                        list != null && listVm != null -> ParityList(list, listVm, vm, undo, openTask, onReschedule = { rescheduling = it }) { addingTo = it?.id ?: "" }
                        else -> ListMessage("This project isn't on this phone any more.")
                    }
                }
            }
        }
        SlideOverPage(open != null, onBack = { open = null }) {
            shown?.let { id ->
                // A task added on this phone takes the server's id once the server has it.
                val current = container.outbox.renamedTo(id) ?: id
                val index = order.indexOfFirst { it.id == current }
                ParityTaskPage(
                    container, id, state.today, undo,
                    previous = order.getOrNull(index - 1)?.takeIf { index > 0 }?.id,
                    next = order.getOrNull(index + 1)?.takeIf { index >= 0 }?.id,
                    onGo = openTask,
                    onClose = { open = null },
                    onDelete = { open = null; undo.delete(current) },
                )
            }
        }
    }

    AddedMessages(vm.added, state, snackbar)
    if (adding) QuickAddSheet(state, onAdd = vm::quickAdd) { adding = false }
    addingTo?.let { section ->
        val into = list?.groups?.firstOrNull { it.section?.id == section }?.section?.name ?: list?.title
        QuickAddSheet(state, into = into, onAdd = { listVm?.quickAdd(it, section.ifEmpty { null }) }) { addingTo = null }
    }
    if (newProject) NewProjectDialog(container, onCreated = { place = it.id }) { newProject = false }
    if (newLabel) NewLabelDialog(container) { newLabel = false }
    if (archived) ArchivedProjects(container) { archived = false }
    if (reviewing) ChangesSheet(state, vm::retry, vm::discard, vm::keepMine, vm::keepTheirs) { reviewing = false }
    rescheduling?.let { id ->
        RescheduleSheet(state.today, onPick = { vm.reschedule(id, it); rescheduling = null }, onPickDate = { pickingDate = id; rescheduling = null }) { rescheduling = null }
    }
    pickingDate?.let { id -> PickDateDialog(null, onDismiss = { pickingDate = null }) { vm.reschedule(id, it); pickingDate = null } }
}

@Composable
private fun ParityDrawer(
    state: TodayState,
    place: String,
    drawerState: DrawerState,
    onNew: (label: Boolean) -> Unit,
    onArchived: () -> Unit,
    onSearch: (String) -> Unit,
    onPick: (String) -> Unit,
) {
    val inbox = state.projects.firstOrNull { it.isInbox }
    var query by rememberSaveable { mutableStateOf("") }
    // Passing the drawer state is what makes back close the drawer, with the predictive animation.
    ModalDrawerSheet(drawerState, drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
            // As in the web sidebar: type, then search opens with the results.
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query); query = "" }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
            )
            DrawerHeading("Navigate")
            inbox?.let { DrawerItem("Inbox", Icons.Outlined.Inbox, it.open, place == it.id) { onPick(it.id) } }
            DrawerItem("Today", Icons.Outlined.Today, state.overdue.count { !it.done } + state.dueToday.count { !it.done }, place == TODAY) { onPick(TODAY) }
            DrawerItem("Upcoming", Icons.Outlined.DateRange, null, place == UPCOMING) { onPick(UPCOMING) }
            DrawerItem("Completed", Icons.Outlined.TaskAlt, null, place == COMPLETED) { onPick(COMPLETED) }
            DrawerItem("Trash", Icons.Outlined.Delete, null, place == TRASH) { onPick(TRASH) }
            DrawerHeading("Projects", "New project") { onNew(false) }
            state.projects.filter { !it.isInbox }.inTreeOrder().forEach { (project, depth) ->
                NavigationDrawerItem(
                    label = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { ProjectIcon(project, 20.dp) },
                    badge = { Text("${project.open}", style = MaterialTheme.typography.labelMedium) },
                    selected = place == project.id,
                    onClick = { onPick(project.id) },
                    shape = MaterialTheme.shapes.medium,
                    colors = drawerColors(),
                    modifier = Modifier.padding(start = (depth * 20).dp),
                )
            }
            DrawerHeading("Labels", "New label") { onNew(true) }
            state.labels.forEach { label ->
                NavigationDrawerItem(
                    label = { Text(label.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Outlined.Sell, null, Modifier.size(20.dp), tint = Hue.of(label.color).dot) },
                    selected = place == "l:" + label.id,
                    onClick = { onPick("l:" + label.id) },
                    shape = MaterialTheme.shapes.medium,
                    colors = drawerColors(),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            DrawerItem("Archived projects", Icons.Outlined.Inventory2, null, false, onArchived)
            val openSettings = LocalOpenSettings.current
            val scope = rememberCoroutineScope()
            // Close the drawer first, so it isn't still open when Settings closes.
            DrawerItem("Settings", Icons.Outlined.Settings, null, false) {
                scope.launch { drawerState.close() }
                openSettings()
            }
        }
    }
}

@Composable
private fun drawerColors() = NavigationDrawerItemDefaults.colors(
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
)

@Composable
private fun DrawerHeading(text: String, addLabel: String? = null, onAdd: () -> Unit = {}) = Row(
    Modifier.fillMaxWidth().padding(start = 12.dp, top = if (addLabel == null) 20.dp else 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        text,
        Modifier.weight(1f).padding(bottom = if (addLabel == null) 6.dp else 0.dp).semantics { heading() },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    addLabel?.let { IconButton(onAdd) { Icon(Icons.Outlined.Add, it, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, count: Int?, selected: Boolean, onClick: () -> Unit) = NavigationDrawerItem(
    label = { Text(label) },
    icon = { Icon(icon, null, Modifier.size(20.dp)) },
    badge = { count?.let { Text("$it", style = MaterialTheme.typography.labelMedium) } },
    selected = selected,
    onClick = onClick,
    shape = MaterialTheme.shapes.medium,
    colors = drawerColors(),
)

@Composable
private fun ParityToday(
    vm: TodayViewModel,
    state: TodayState,
    undo: UndoSnackbars,
    onOpen: (String) -> Unit,
    onReschedule: (String) -> Unit,
    onReview: () -> Unit,
) {
    val row = parityRows(vm, undo, state.today, onOpen, onReschedule)
    // Above the list, so a list that keeps its scroll position can't push it out of view.
    ChangesNotice(state, onReview)
    PullToRefreshBox(state.refreshing, vm::refresh) {
        // Room at the bottom for the + button.
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            when {
                !state.loaded -> {}
                state.overdue.isEmpty() && state.dueToday.isEmpty() -> item("empty") {
                    ListMessage(if (state.ready) "Nothing due today." else "Getting your tasks from the server.", Modifier.heightIn(min = 320.dp))
                }
                else -> {
                    if (state.overdue.isNotEmpty()) {
                        item("h-overdue") { SectionHeading("Overdue", state.overdue.count { !it.done }, Modifier.padding(top = 8.dp)) }
                        items(state.overdue, key = { it.id }) { row(it, Modifier.animateItem()) }
                    }
                    item("h-today") { SectionHeading("Today", state.dueToday.count { !it.done }, Modifier.padding(top = 8.dp)) }
                    items(state.dueToday, key = { it.id }) { row(it, Modifier.animateItem()) }
                }
            }
        }
    }
}

/** Rows like the web's: tap opens the task's page, the menu reschedules or deletes. */
private fun parityRows(vm: TodayViewModel, undo: UndoSnackbars, today: LocalDate, onOpen: (String) -> Unit, onReschedule: (String) -> Unit): RowSlot = { task, modifier ->
    ParityRow(task, today, modifier, onOpen = { onOpen(task.id) }, onReschedule = { onReschedule(task.id) }, onDelete = { undo.delete(task.id) }) { on ->
        if (on) undo.complete(task.id, "Completed “${task.title}”") else vm.reopen(task.id)
    }
}

/** Upcoming, search, Completed or Trash in Parity's main area. */
@Composable
private fun ParityView(container: AppContainer, place: String, calendar: Boolean, searchVm: SearchViewModel, onPlace: (String) -> Unit, rows: (LocalDate) -> RowSlot) {
    // Room at the bottom for the + button.
    val padding = PaddingValues(bottom = 96.dp)
    when (place) {
        UPCOMING -> {
            val vm: UpcomingViewModel = viewModel { UpcomingViewModel(container) }
            val state by vm.state.collectAsStateWithLifecycle()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                if (calendar) calendarItems(state, vm, row = rows(state.today)) else upcomingItems(state, row = rows(state.today))
            }
        }
        SEARCH -> {
            val query by searchVm.query.collectAsStateWithLifecycle()
            val state by searchVm.state.collectAsStateWithLifecycle()
            SearchField(query, searchVm::search, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = padding) {
                searchItems(state, { onPlace(it.id) }, { onPlace("l:" + it.id) }, row = rows(state.today))
            }
        }
        COMPLETED -> {
            val state by viewModel { CompletedViewModel(container) }.state.collectAsStateWithLifecycle()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) { completedItems(state, row = rows(state.today)) }
        }
        TRASH -> {
            val vm: TrashViewModel = viewModel { TrashViewModel(container) }
            val state by vm.state.collectAsStateWithLifecycle()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) { trashItems(state, { vm.restore(it) }, { vm.restore(it.task) }) }
        }
    }
}

@Composable
private fun ParityRow(
    row: TaskRow,
    today: LocalDate,
    modifier: Modifier,
    showProject: Boolean = true,
    onOpen: () -> Unit,
    onReschedule: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalConatusColors.current
    val (dueText, timeText) = dueTexts(row, today)
    // The checkbox pops and the row fades out 500ms later, as on the web.
    var checking by remember(row.id) { mutableStateOf(false) }
    LaunchedEffect(checking) {
        if (checking) {
            delay(500)
            onToggle(true)
            checking = false
        }
    }
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().taskClick(row.id, selectable = !row.done, onOpen = onOpen).padding(start = 4.dp, top = 2.dp, bottom = 6.dp)) {
        TaskCheckbox(row.priority, checked = row.done || checking, taskTitle = row.title) { on -> if (on) checking = true else onToggle(false) }
        Column(Modifier.weight(1f).padding(top = 12.dp).clearAndSetSemantics { contentDescription = row.spoken(dueText, timeText, today) }) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, color = if (row.done) colors.muted else Color.Unspecified)
            if (row.description.isNotBlank()) {
                Text(row.description, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
            TaskDetails(row, today, showProject = showProject)
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "More for ${row.title}", tint = colors.muted) }
            DropdownMenu(menu, { menu = false }, shape = MaterialTheme.shapes.medium) {
                DropdownMenuItem({ Text("Reschedule") }, { menu = false; onReschedule() })
                DropdownMenuItem({ Text("Delete", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() })
            }
        }
    }
}

/** Parity details: the web's task modal as a page, with the properties stacked as label and value. */
@Composable
private fun ParityTaskPage(
    container: AppContainer,
    taskId: String,
    today: LocalDate,
    undo: UndoSnackbars,
    previous: String?,
    next: String?,
    onGo: (String) -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    val vm: TaskDetailViewModel = viewModel(key = "task-$taskId") { TaskDetailViewModel(container, taskId) }
    val detail by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editor by rememberSaveable { mutableStateOf<Editor?>(null) }
    var menu by remember { mutableStateOf(false) }
    val colors = LocalConatusColors.current
    LaunchedEffect(detail.loaded, detail.task) { if (detail.loaded && detail.task == null) onClose() }
    val row = detail.row
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    row?.project?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProjectIcon(it, 16.dp)
                            Text(it.name, style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = { IconButton(onClose) { Icon(Icons.Outlined.Close, "Close") } },
                actions = {
                    IconButton({ previous?.let(onGo) }, enabled = previous != null) { Icon(Icons.Outlined.KeyboardArrowUp, "Previous task") }
                    IconButton({ next?.let(onGo) }, enabled = next != null) { Icon(Icons.Outlined.KeyboardArrowDown, "Next task") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "More") }
                        DropdownMenu(menu, { menu = false }, shape = MaterialTheme.shapes.medium) {
                            DropdownMenuItem({ Text("Delete", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (row == null) return@Scaffold
        val properties = taskProperties(detail, today)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            detail.failure?.let { change -> item { FailureNotice(change, { vm.retry(change) }, { vm.discard(change) }, Modifier.padding(16.dp)) } }
            detail.conflict?.let { c -> item { ConflictNotice(c, detail.task, detail.labels, today, { vm.keepMine(c) }, { vm.keepTheirs(c) }, Modifier.padding(16.dp)) } }
            item {
                Row(Modifier.padding(start = 4.dp, end = 16.dp)) {
                    TaskCheckbox(row.priority, row.done, row.title) { on ->
                        if (on) scope.launch { vm.complete()?.let { undo.completed(it, "Completed “${row.title}”") } } else vm.reopen()
                    }
                    Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.title, Modifier.fillMaxWidth().clickable(onClickLabel = "Edit name") { editor = Editor.Title }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        Text(
                            row.description.ifBlank { "Description" },
                            Modifier.fillMaxWidth().clickable(onClickLabel = "Edit description") { editor = Editor.Description }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                }
            }
            if (detail.subtasks.isNotEmpty()) {
                item { SectionHeading("Sub-tasks", detail.subtasks.size, Modifier.padding(top = 12.dp)) }
                items(detail.subtasks, key = { it.id }) { sub ->
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        TaskCheckbox(sub.priority, sub.done, sub.title) { on ->
                            if (on) scope.launch { vm.completeSubtask(sub.id)?.let { undo.completed(it) } } else vm.reopenSubtask(sub.id)
                        }
                        Text(sub.title, style = MaterialTheme.typography.bodyLarge, color = if (sub.done) colors.muted else Color.Unspecified)
                    }
                }
            }
            item { AddTaskLine { editor = Editor.Subtask } }
            item { CommentList(detail.comments, detail.me, detail.zone, today, vm::addComment, vm::editComment, vm::deleteComment) }
            item { HorizontalDivider(Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outlineVariant) }
            items(properties) { property ->
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = property.editor != null) { editor = property.editor }.heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(property.label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(property.icon, null, Modifier.size(16.dp), tint = property.color ?: colors.muted)
                        Text(property.value, style = MaterialTheme.typography.bodyMedium, color = property.color ?: if (property.set) MaterialTheme.colorScheme.onSurface else colors.muted)
                    }
                }
            }
        }
    }
    editor?.let { EditorDialog(it, detail, today, vm::edit, vm::setLabels, vm::move, vm::addSubtask, vm::addReminder, vm::deleteReminder) { editor = null } }
}

/** A project or label in Parity's main area: rows like the web's, with "Add task" under each section. */
@Composable
private fun ParityList(
    list: ListState,
    vm: ListViewModel,
    todayVm: TodayViewModel,
    undo: UndoSnackbars,
    onOpen: (String) -> Unit,
    onReschedule: (String) -> Unit,
    onAdd: (SectionEntity?) -> Unit,
) {
    val isProject = list.project != null
    val toggle = { tree: TreeRow, on: Boolean -> if (on) undo.complete(tree.task.id, "Completed “${tree.row.title}”") else todayVm.reopen(tree.task.id); Unit }
    if (list.board) {
        ProjectBoard(list, vm, onOpen, toggle, onAdd)
    } else {
        ProjectList(list, vm, contentPadding = PaddingValues(bottom = 96.dp), addInSections = if (isProject) onAdd else null) { tree, modifier ->
            ParityRow(tree.row, list.today, modifier, showProject = !isProject, onOpen = { onOpen(tree.task.id) }, onReschedule = { onReschedule(tree.task.id) }, onDelete = { undo.delete(tree.task.id) }) { toggle(tree, it) }
        }
    }
}
