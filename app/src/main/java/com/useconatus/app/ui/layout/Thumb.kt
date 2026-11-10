@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.useconatus.app.ui.layout

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.useconatus.app.AppContainer
import com.useconatus.app.data.sync.Completion
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.useconatus.app.ui.components.AddedMessages
import com.useconatus.app.ui.components.SlideOverPage
import com.useconatus.app.ui.project.ArchivedProjects
import com.useconatus.app.ui.project.ListActions
import com.useconatus.app.ui.project.ListSource
import com.useconatus.app.ui.project.ListViewModel
import com.useconatus.app.ui.project.NewLabelDialog
import com.useconatus.app.ui.project.NewProjectDialog
import com.useconatus.app.ui.project.ProjectBoard
import com.useconatus.app.ui.project.ProjectList
import com.useconatus.app.ui.project.TreeRow
import com.useconatus.app.ui.theme.Hue
import com.useconatus.app.ui.today.inTreeOrder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.TaskAlt
import com.useconatus.app.ui.AppLink
import com.useconatus.app.ui.views.COMPLETED
import com.useconatus.app.ui.views.CalendarToggle
import com.useconatus.app.ui.views.CompletedViewModel
import com.useconatus.app.ui.views.RowSlot
import com.useconatus.app.ui.views.SearchField
import com.useconatus.app.ui.views.SearchViewModel
import com.useconatus.app.ui.views.TRASH
import com.useconatus.app.ui.views.TrashViewModel
import com.useconatus.app.ui.views.ViewPage
import com.useconatus.app.ui.views.UpcomingViewModel
import com.useconatus.app.ui.views.calendarItems
import com.useconatus.app.ui.views.completedItems
import com.useconatus.app.ui.views.searchItems
import com.useconatus.app.ui.views.trashItems
import com.useconatus.app.ui.views.upcomingItems
import com.useconatus.app.ui.components.ListMessage
import com.useconatus.app.ui.components.LocalOpenSettings
import com.useconatus.app.ui.components.ProjectIcon
import com.useconatus.app.ui.components.projectIcon
import com.useconatus.app.ui.components.SectionHeading
import com.useconatus.app.ui.components.TaskCheckbox
import com.useconatus.app.ui.components.TaskDetails
import com.useconatus.app.ui.components.dueTexts
import com.useconatus.app.ui.components.locale
import com.useconatus.app.ui.components.rememberUndoSnackbars
import com.useconatus.app.ui.components.LocalTaskSelection
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
import com.useconatus.app.ui.theme.SheetShape
import com.useconatus.app.ui.theme.sheetColor
import com.useconatus.app.ui.today.TaskRow
import com.useconatus.app.ui.today.TodayState
import com.useconatus.app.ui.today.TodayViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// The Thumb layout, called Comfortable on screen: bottom tabs, a + button above them, a large
// collapsing title, roomy rows, swipe right to complete and left to reschedule, details in a sheet.

private enum class ThumbTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Today("Today", Icons.Outlined.Today, Icons.Filled.Today),
    Upcoming("Upcoming", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarToday),
    Projects("Projects", Icons.Outlined.Folder, Icons.Filled.Folder),
    Search("Search", Icons.Outlined.Search, Icons.Filled.Search),
}

/** What a task row can ask the layout to do. */
private class RowActions(
    val open: (String) -> Unit,
    val complete: (TaskRow) -> Unit,
    val reopen: (TaskRow) -> Unit,
    val reschedule: (String) -> Unit,
)

@Composable
fun ThumbLayout(vm: TodayViewModel, state: TodayState, container: AppContainer) {
    var tab by rememberSaveable { mutableStateOf(ThumbTab.Today) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var rescheduling by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    // The project or label page pushed over the tabs ("p:id" or "l:id"), and the last one, which stays while it slides away.
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var shownPage by rememberSaveable { mutableStateOf<String?>(null) }
    var newProject by rememberSaveable { mutableStateOf(false) }
    var newLabel by rememberSaveable { mutableStateOf(false) }
    var archived by rememberSaveable { mutableStateOf(false) }
    val openPage = { key: String -> page = key; shownPage = key }
    val snackbar = remember { SnackbarHostState() }
    // A pushed page covers the tabs' snackbars, so it shows its own.
    val pageSnackbar = remember { SnackbarHostState() }
    val tabUndo = rememberUndoSnackbars(vm, snackbar)
    val pageUndo = rememberUndoSnackbars(vm, pageSnackbar)
    val undo = if (page != null) pageUndo else tabUndo
    SelectionUndo(undo)
    val selecting = LocalTaskSelection.current.active
    val link by container.links.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        when (val to = link ?: return@LaunchedEffect) {
            is AppLink.Task -> detail = to.id
            is AppLink.Project -> openPage("p:" + to.id)
            AppLink.Today -> { page = null; tab = ThumbTab.Today }
            AppLink.Upcoming -> { page = null; tab = ThumbTab.Upcoming }
            AppLink.Search -> { page = null; tab = ThumbTab.Search }
        }
        container.links.value = null
    }
    val actions = RowActions(
        open = { detail = it },
        complete = { undo.complete(it.id) },
        reopen = { vm.reopen(it.id) },
        reschedule = { rescheduling = it },
    )

    Scaffold(
        bottomBar = {
            // The selection's own bar takes this place while tasks are selected.
            if (!selecting) Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    ThumbTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(if (tab == item) item.selectedIcon else item.icon, null) },
                            // One line even at 200% font size, where "Upcoming" would otherwise wrap.
                            label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.surfaceContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = { if (!selecting) AddButton { adding = true } },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = MaterialTheme.shapes.medium) } },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            // Fade-through between tabs, as Material recommends for sibling destinations.
            AnimatedContent(tab, transitionSpec = { (fadeIn(tween(210, 90)) + scaleIn(tween(210, 90), 0.94f)) togetherWith fadeOut(tween(90)) }, label = "tab") { current ->
                when (current) {
                    ThumbTab.Today -> ThumbToday(state, vm::refresh, actions) { reviewing = true }
                    ThumbTab.Projects -> ThumbProjects(state, openPage, { newProject = true }, { newLabel = true }) { archived = true }
                    ThumbTab.Upcoming -> ThumbUpcoming(container, actions)
                    ThumbTab.Search -> ThumbSearch(container, actions, openPage)
                }
            }
        }
    }
    SlideOverPage(page != null, onBack = { page = null }) {
        shownPage?.let { key ->
            when (key) {
                COMPLETED -> ViewPage("Completed", pageSnackbar, { page = null }) {
                    val vm: CompletedViewModel = viewModel { CompletedViewModel(container) }
                    val done by vm.state.collectAsStateWithLifecycle()
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        completedItems(done) { row, modifier -> ThumbRow(row, done.today, actions, modifier) }
                    }
                }
                TRASH -> ViewPage("Trash", pageSnackbar, { page = null }) {
                    val vm: TrashViewModel = viewModel { TrashViewModel(container) }
                    val trash by vm.state.collectAsStateWithLifecycle()
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        trashItems(trash, { vm.restore(it) }, { vm.restore(it.task) })
                    }
                }
                else -> ThumbListPage(container, key, state, actions, pageSnackbar) { page = null }
            }
        }
    }

    if (newProject) NewProjectDialog(container, onCreated = { openPage("p:" + it.id) }) { newProject = false }
    if (newLabel) NewLabelDialog(container) { newLabel = false }
    if (archived) ArchivedProjects(container) { archived = false }
    AddedMessages(vm.added, state, snackbar)
    if (adding) QuickAddSheet(state, onAdd = vm::quickAdd) { adding = false }
    detail?.let { id ->
        ThumbDetailSheet(container, id, state.today, onDismiss = { detail = null }, onCompleted = { undo.completed(it) }, onDelete = {
            detail = null
            undo.delete(id)
        })
    }
    rescheduling?.let { id ->
        RescheduleSheet(state.today, onPick = { vm.reschedule(id, it); rescheduling = null }, onPickDate = { pickingDate = id; rescheduling = null }) { rescheduling = null }
    }
    pickingDate?.let { id -> PickDateDialog(null, onDismiss = { pickingDate = null }) { vm.reschedule(id, it); pickingDate = null } }
    if (reviewing) ChangesSheet(state, vm::retry, vm::discard, vm::keepMine, vm::keepTheirs) { reviewing = false }
}

/** Quick add's + at the bottom right, above the tabs: round and primary-coloured, as in Parity. */
@Composable
private fun AddButton(onClick: () -> Unit) = FloatingActionButton(onClick, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary) {
    Icon(Icons.Outlined.Add, "Add a task")
}

@Composable
private fun ThumbPage(
    title: String,
    subtitle: String?,
    refreshing: Boolean? = null,
    onRefresh: () -> Unit = {},
    header: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                actions = {
                    actions()
                    val openSettings = LocalOpenSettings.current
                    IconButton(openSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
                scrollBehavior = scroll,
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        // Room at the bottom for the + button.
        val list = @Composable { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), content = content) }
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            // Above the list, so a list that keeps its scroll position can't push it out of view.
            header()
            if (refreshing != null) PullToRefreshBox(refreshing, onRefresh) { list() } else list()
        }
    }
}

@Composable
private fun ThumbToday(state: TodayState, onRefresh: () -> Unit, actions: RowActions, onReview: () -> Unit) {
    val date = state.today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale()))
    ThumbPage("Today", date, refreshing = state.refreshing, onRefresh = onRefresh, header = { ChangesNotice(state, onReview) }) {
        when {
            !state.loaded -> {}
            state.overdue.isEmpty() && state.dueToday.isEmpty() -> item("empty") {
                ListMessage(if (state.ready) "Nothing due today." else "Getting your tasks from the server.", Modifier.heightIn(min = 320.dp))
            }
            else -> {
                if (state.overdue.isNotEmpty()) {
                    stickyHeader("h-overdue") { SectionHeading("Overdue", state.overdue.count { !it.done }) }
                    items(state.overdue, key = { it.id }) { SwipeRow(it, state.today, actions, Modifier.animateItem()) }
                }
                stickyHeader("h-today") { SectionHeading("Today", state.dueToday.count { !it.done }) }
                items(state.dueToday, key = { it.id }) { SwipeRow(it, state.today, actions, Modifier.animateItem()) }
            }
        }
    }
}

/** Swipe right to complete, left to reschedule. Both are also TalkBack actions on the row. */
@Composable
private fun SwipeRow(row: TaskRow, today: LocalDate, actions: RowActions, modifier: Modifier = Modifier, showProject: Boolean = true) {
    // Not rememberSwipeToDismissBoxState: it saves its value, so a row brought back by Undo would
    // return dismissed and fire onDismiss again.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val swipe = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // While tasks are selected, a stray swipe shouldn't act on one of them alone.
    val selecting = LocalTaskSelection.current.active
    SwipeToDismissBox(
        swipe,
        modifier = modifier,
        enableDismissFromStartToEnd = !row.done && !selecting,
        enableDismissFromEndToStart = !selecting,
        backgroundContent = {
            val toEnd = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().background(if (toEnd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                if (toEnd) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary) else Icon(Icons.Outlined.Event, null)
            }
        },
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                actions.complete(row)
            } else {
                actions.reschedule(row.id)
            }
            scope.launch { swipe.reset() }
        },
    ) { ThumbRow(row, today, actions, showProject = showProject) }
}

@Composable
private fun ThumbRow(row: TaskRow, today: LocalDate, actions: RowActions, modifier: Modifier = Modifier, showProject: Boolean = true) {
    val colors = LocalConatusColors.current
    val (dueText, timeText) = dueTexts(row, today)
    // The checkbox pops first and the row leaves 420ms later.
    var checking by remember(row.id) { mutableStateOf(false) }
    LaunchedEffect(checking) {
        if (checking) {
            delay(420)
            actions.complete(row)
            checking = false
        }
    }
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .taskClick(row.id, selectable = !row.done) { actions.open(row.id) }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (row.done) "Reopen" else "Complete") { if (row.done) actions.reopen(row) else actions.complete(row); true },
                    CustomAccessibilityAction("Reschedule") { actions.reschedule(row.id); true },
                )
            }
            .heightIn(min = 64.dp).padding(start = 4.dp, end = 16.dp, bottom = 10.dp),
    ) {
        TaskCheckbox(row.priority, checked = row.done || checking, taskTitle = row.title) { on -> if (on) checking = true else actions.reopen(row) }
        Column(Modifier.weight(1f).padding(top = 12.dp).clearAndSetSemantics { contentDescription = row.spoken(dueText, timeText, today) }) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (row.done) colors.muted else Color.Unspecified)
            if (row.description.isNotBlank()) {
                Text(row.description, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TaskDetails(row, today, showProject = false)
        }
        row.project?.takeIf { showProject && !it.isInbox }?.let { project ->
            Row(Modifier.padding(start = 8.dp, top = 15.dp).width(96.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(project.name, Modifier.weight(1f, fill = false).padding(end = 6.dp), style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProjectIcon(project, 14.dp)
            }
        }
    }
}

/** Thumb details: a sheet that opens half-height, with chips for every property and the main actions at the bottom. */
@Composable
private fun ThumbDetailSheet(
    container: AppContainer,
    taskId: String,
    today: LocalDate,
    onDismiss: () -> Unit,
    onCompleted: (Completion) -> Unit,
    onDelete: () -> Unit,
) {
    val vm: TaskDetailViewModel = viewModel(key = "task-$taskId") { TaskDetailViewModel(container, taskId) }
    val detail by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editor by rememberSaveable { mutableStateOf<Editor?>(null) }
    val colors = LocalConatusColors.current
    // The task can disappear underneath the sheet: deleted elsewhere, or discarded before the server had it.
    LaunchedEffect(detail.loaded, detail.task) { if (detail.loaded && detail.task == null) onDismiss() }
    val row = detail.row ?: return
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = sheetColor(), shape = SheetShape) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            detail.failure?.let { change -> item { FailureNotice(change, { vm.retry(change) }, { vm.discard(change) }, Modifier.padding(16.dp)) } }
            detail.conflict?.let { c -> item { ConflictNotice(c, detail.task, detail.labels, today, { vm.keepMine(c) }, { vm.keepTheirs(c) }, Modifier.padding(16.dp)) } }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.project?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProjectIcon(it, 16.dp)
                            Text(it.name, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        }
                    }
                    Text(row.title, Modifier.fillMaxWidth().clickable(onClickLabel = "Edit name") { editor = Editor.Title }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        row.description.ifBlank { "Add a description" },
                        Modifier.fillMaxWidth().clickable(onClickLabel = "Edit description") { editor = Editor.Description }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
            }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    taskProperties(detail, today).forEach { property ->
                        DetailChip(property.icon, if (property.set) property.value else property.label, property.color, enabled = property.editor != null) {
                            property.editor?.let { editor = it }
                        }
                    }
                }
            }
            item { SectionHeading("Sub-tasks", detail.subtasks.size.takeIf { it > 0 }, Modifier.background(sheetColor())) }
            items(detail.subtasks, key = { it.id }) { sub ->
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TaskCheckbox(sub.priority, sub.done, sub.title) { on ->
                        if (on) scope.launch { vm.completeSubtask(sub.id)?.let(onCompleted) } else vm.reopenSubtask(sub.id)
                    }
                    Text(sub.title, style = MaterialTheme.typography.bodyLarge, color = if (sub.done) colors.muted else Color.Unspecified)
                }
            }
            item {
                TextButton({ editor = Editor.Subtask }, Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add sub-task")
                }
            }
            item {
                CommentList(detail.comments, detail.me, detail.zone, today, vm::addComment, vm::editComment, vm::deleteComment, Modifier.background(sheetColor()))
            }
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onDelete,
                        Modifier.height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                    OutlinedButton(
                        { editor = Editor.Date },
                        Modifier.weight(1f).height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) { Text("Reschedule", color = MaterialTheme.colorScheme.onSurface) }
                    Button(
                        { if (row.done) vm.reopen() else scope.launch { vm.complete()?.let(onCompleted); onDismiss() } },
                        Modifier.weight(1f).height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (row.done) "Reopen" else "Complete")
                    }
                }
            }
        }
    }
    editor?.let { EditorDialog(it, detail, today, vm::edit, vm::setLabels, vm::move, vm::addSubtask, vm::addReminder, vm::deleteReminder) { editor = null } }
}

/** The Projects tab: every project as a tree, then labels, then the archived projects. */
@Composable
private fun ThumbProjects(state: TodayState, onOpen: (String) -> Unit, onNewProject: () -> Unit, onNewLabel: () -> Unit, onArchived: () -> Unit) {
    ThumbPage("Projects", null) {
        item("h-projects") { DirectoryHeading("Projects", "New project", onNewProject) }
        items(state.projects.inTreeOrder(), key = { "p-" + it.first.id }) { (project, depth) ->
            DirectoryLine(project.name, project.hue.dot, project.open, depth, projectIcon(project.icon, project.isInbox)) { onOpen("p:" + project.id) }
        }
        item("h-labels") { DirectoryHeading("Labels", "New label", onNewLabel) }
        if (state.labels.isEmpty()) item("no-labels") { Text("No labels yet.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.labels, key = { "l-" + it.id }) { label ->
            DirectoryLine(label.name, Hue.of(label.color).dot, null, 0, Icons.Outlined.Sell) { onOpen("l:" + label.id) }
        }
        item("more") {
            Column(Modifier.padding(top = 8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DirectoryLine("Completed", MaterialTheme.colorScheme.onSurfaceVariant, null, 0, Icons.Outlined.TaskAlt) { onOpen(COMPLETED) }
                DirectoryLine("Trash", MaterialTheme.colorScheme.onSurfaceVariant, null, 0, Icons.Outlined.Delete) { onOpen(TRASH) }
                DirectoryLine("Archived projects", MaterialTheme.colorScheme.onSurfaceVariant, null, 0, Icons.Outlined.Inventory2, onClick = onArchived)
            }
        }
    }
}

/** Upcoming: tasks by date from today on, or the month calendar with repeats on every date they fall on. */
@Composable
private fun ThumbUpcoming(container: AppContainer, actions: RowActions) {
    val vm: UpcomingViewModel = viewModel { UpcomingViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var calendar by rememberSaveable { mutableStateOf(false) }
    ThumbPage("Upcoming", null, actions = { CalendarToggle(calendar) { calendar = it } }) {
        val row: RowSlot = { task, modifier -> SwipeRow(task, state.today, actions, modifier) }
        if (calendar) calendarItems(state, vm, row = row) else upcomingItems(state, row = row)
    }
}

@Composable
private fun ThumbSearch(container: AppContainer, actions: RowActions, onOpen: (String) -> Unit) {
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    ThumbPage("Search", null, header = { SearchField(query, vm::search, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }) {
        searchItems(state, { onOpen("p:" + it.id) }, { onOpen("l:" + it.id) }) { task, modifier -> SwipeRow(task, state.today, actions, modifier) }
    }
}

@Composable
private fun DirectoryHeading(title: String, addLabel: String, onAdd: () -> Unit) = Row(
    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
    IconButton(onAdd) { Icon(Icons.Outlined.Add, addLabel) }
}

@Composable
private fun DirectoryLine(name: String, tint: Color, count: Int?, depth: Int, icon: ImageVector, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(start = (16 + depth * 20).dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
) {
    Icon(icon, null, Modifier.size(18.dp), tint = tint)
    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    count?.let { Text("$it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/**
 * A project or label page, pushed over the tabs: the list (or board) with its sections, an add
 * bar that adds to this project, and the project's options. [key] is "p:" or "l:" and an id.
 */
@Composable
private fun ThumbListPage(
    container: AppContainer,
    key: String,
    today: TodayState,
    actions: RowActions,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val source = if (key.startsWith("p:")) ListSource.Project(key.drop(2)) else ListSource.Label(key.drop(2))
    val vm: ListViewModel = viewModel(key = "list-$key") { ListViewModel(container, source) }
    val list by vm.state.collectAsStateWithLifecycle()
    // Where quick add puts tasks: "" for the project itself, or a section id.
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    val isProject = source is ListSource.Project
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val hue = Hue.of(list.project?.color ?: list.label?.color)
                        Icon(if (isProject) projectIcon(list.project?.icon, list.project?.isInbox == true) else Icons.Outlined.Sell, null, Modifier.size(20.dp), tint = hue.dot)
                        Text(list.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { ListActions(list, vm, onGone = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = { if (isProject && !LocalTaskSelection.current.active) AddButton { adding = "" } },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = MaterialTheme.shapes.medium) } },
    ) { padding ->
        val toggle = { tree: TreeRow, on: Boolean -> if (on) actions.complete(tree.row) else actions.reopen(tree.row) }
        if (list.board) {
            ProjectBoard(list, vm, onOpen = actions.open, onToggle = toggle, onAdd = { adding = it?.id ?: "" }, modifier = Modifier.padding(padding))
        } else {
            ProjectList(list, vm, Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) { tree, modifier -> SwipeRow(tree.row, list.today, actions, modifier, showProject = !isProject) }
        }
    }
    adding?.let { section ->
        val into = list.groups.firstOrNull { it.section?.id == section }?.section?.name ?: list.title
        QuickAddSheet(today, into = into, onAdd = { vm.quickAdd(it, section.ifEmpty { null }) }) { adding = null }
    }
}

@Composable
private fun DetailChip(icon: ImageVector, text: String, color: Color?, enabled: Boolean, onClick: () -> Unit) = Surface(
    onClick = onClick,
    enabled = enabled,
    shape = MaterialTheme.shapes.small,
    color = Color.Transparent,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
) {
    Row(Modifier.height(36.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = color ?: MaterialTheme.colorScheme.onSurface)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}
