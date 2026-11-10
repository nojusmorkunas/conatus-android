@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.useconatus.app.ui.layout

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.useconatus.app.AppContainer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.TaskAlt
import com.useconatus.app.data.db.LabelEntity
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
import com.useconatus.app.ui.views.ViewPage
import com.useconatus.app.ui.views.calendarItems
import com.useconatus.app.ui.views.completedItems
import com.useconatus.app.ui.views.searchItems
import com.useconatus.app.ui.views.trashItems
import com.useconatus.app.ui.views.upcomingItems
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.useconatus.app.ui.task.QuickAddSheet
import com.useconatus.app.ui.theme.Hue
import com.useconatus.app.ui.components.AddedMessages
import com.useconatus.app.ui.components.ListMessage
import com.useconatus.app.ui.components.LocalOpenSettings
import com.useconatus.app.ui.components.ProjectIcon
import com.useconatus.app.ui.components.SectionHeading
import com.useconatus.app.ui.components.SyncDetail
import com.useconatus.app.ui.components.TaskCheckbox
import com.useconatus.app.ui.components.UndoSnackbars
import com.useconatus.app.ui.components.dueTexts
import com.useconatus.app.ui.components.locale
import com.useconatus.app.ui.components.rememberUndoSnackbars
import com.useconatus.app.ui.components.LocalTaskSelection
import com.useconatus.app.ui.components.SelectionUndo
import com.useconatus.app.ui.components.taskClick
import com.useconatus.app.ui.format.dueColor
import com.useconatus.app.ui.format.shortDueLabel
import com.useconatus.app.ui.task.ChangesNotice
import com.useconatus.app.ui.task.ChangesSheet
import com.useconatus.app.ui.task.CommentsSheet
import com.useconatus.app.ui.task.ConflictNotice
import com.useconatus.app.ui.task.Editor
import com.useconatus.app.ui.task.EditorDialog
import com.useconatus.app.ui.task.FailureNotice
import com.useconatus.app.ui.task.TaskDetailViewModel
import com.useconatus.app.ui.task.taskProperties
import com.useconatus.app.ui.theme.LocalConatusColors
import com.useconatus.app.ui.theme.SheetShape
import com.useconatus.app.ui.theme.sheetColor
import com.useconatus.app.ui.today.ProjectItem
import com.useconatus.app.ui.today.TaskRow
import com.useconatus.app.ui.today.TodayState
import com.useconatus.app.ui.today.TodayViewModel
import com.useconatus.app.ui.today.inTreeOrder
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.activity.compose.BackHandler
import com.useconatus.app.ui.components.ClearSelectionOn
import com.useconatus.app.ui.components.PullToSync

// The Index layout, called Compact on screen: every list is a tab at the bottom,
// every task one 48dp line, a + button for adding, and details that open in place.

private sealed interface IndexPage {
    val title: String
    data object Today : IndexPage { override val title = "Today" }
    data object Upcoming : IndexPage { override val title = "Upcoming" }
    data class Project(val project: ProjectItem, val depth: Int) : IndexPage { override val title get() = project.name }
}

/** text-xs with tabular figures, so times and dates line up down the right edge. */
@Composable
private fun figures(): TextStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")

@Composable
fun IndexLayout(vm: TodayViewModel, state: TodayState, container: AppContainer) {
    val pages = listOf(IndexPage.Today, IndexPage.Upcoming) + state.projects.inTreeOrder().map { (project, depth) -> IndexPage.Project(project, depth) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var jumping by rememberSaveable { mutableStateOf(false) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val undo = rememberUndoSnackbars(vm, snackbar)
    var newProject by rememberSaveable { mutableStateOf(false) }
    var newLabel by rememberSaveable { mutableStateOf(false) }
    var archived by rememberSaveable { mutableStateOf(false) }
    // The page pushed over the tabs (a label's "l:id", search, Completed or Trash), and the last one, which stays while it slides away.
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var shownPage by rememberSaveable { mutableStateOf<String?>(null) }
    val openPage = { key: String -> page = key; shownPage = key }
    val pageSnackbar = remember { SnackbarHostState() }
    val pageUndo = rememberUndoSnackbars(vm, pageSnackbar)
    SelectionUndo(if (page != null) pageUndo else undo)
    // Completed rows stay in place, struck through, until you leave the tab.
    LaunchedEffect(pager.settledPage) { vm.releaseKept() }
    // A new project's tab appears once the project list updates, under the server's id if it has one by then; then go there.
    var goTo by rememberSaveable { mutableStateOf<String?>(null) }
    // A task opened from a notification or the widget isn't in a row on screen, so it opens in a sheet.
    var linkedTask by rememberSaveable { mutableStateOf<String?>(null) }
    val link by container.links.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        when (val to = link ?: return@LaunchedEffect) {
            is AppLink.Task -> linkedTask = to.id
            is AppLink.Project -> { page = null; goTo = to.id }
            AppLink.Today -> { page = null; pager.scrollToPage(0) }
            AppLink.Upcoming -> { page = null; pager.scrollToPage(1) }
            AppLink.Search -> openPage(SEARCH)
        }
        container.links.value = null
    }
    LaunchedEffect(goTo, pages) {
        val id = goTo?.let { container.outbox.renamedTo(it) ?: it } ?: return@LaunchedEffect
        val i = pages.indexOfFirst { it is IndexPage.Project && it.project.id == id }
        if (i >= 0) {
            goTo = null
            pager.scrollToPage(i)
        }
    }

    val selecting = LocalTaskSelection.current.active
    ClearSelectionOn(page, pager.currentPage)
    // Composed before the pages and sheets, so their back handlers go first.
    BackHandler(enabled = pager.currentPage != 0 && page == null) { scope.launch { pager.scrollToPage(0) } }
    var adding by rememberSaveable { mutableStateOf(false) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    val current = pages.getOrNull(pager.currentPage)
    // The project tab's own view model, the one its page uses, for the options menu and the + button.
    val project = (current as? IndexPage.Project)?.project
    val listVm: ListViewModel? = project?.let { viewModel(key = "list-p:" + it.id) { ListViewModel(container, ListSource.Project(it.id)) } }
    val list = listVm?.state?.collectAsStateWithLifecycle()?.value
    val count = { page: IndexPage ->
        when (page) {
            IndexPage.Today -> state.overdue.count { !it.done } + state.dueToday.count { !it.done }
            IndexPage.Upcoming -> null
            is IndexPage.Project -> page.project.open
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        project?.let { ProjectIcon(it, 20.dp) }
                        Text(current?.title.orEmpty(), Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        current?.let(count)?.let { Text("$it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                actions = {
                    IconButton({ openPage(SEARCH) }) { Icon(Icons.Outlined.Search, "Search") }
                    if (current == IndexPage.Upcoming) CalendarToggle(calendar) { calendar = it }
                    if (list != null && listVm != null) ListActions(list, listVm, onGone = { scope.launch { pager.scrollToPage(0) } })
                    IconButton(LocalOpenSettings.current) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        // Lists and adding sit at the bottom, in reach of a thumb. The selection's own bar takes their place while tasks are selected.
        bottomBar = {
            if (!selecting) Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Lifted off the bottom edge, where a thumb has to bend to reach.
                Row(Modifier.navigationBarsPadding().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryScrollableTabRow(pager.currentPage.coerceAtMost(pages.lastIndex), Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.surface, edgePadding = 4.dp, divider = {}) {
                        pages.forEachIndexed { i, page ->
                            Tab(
                                selected = pager.currentPage == i,
                                onClick = { scope.launch { pager.scrollToPage(i) } },
                                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (page is IndexPage.Project) ProjectIcon(page.project, 14.dp)
                                        Text(page.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                        count(page)?.let { Text("$it", style = figures(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                },
                            )
                        }
                    }
                    IconButton({ jumping = true }) { Icon(Icons.AutoMirrored.Outlined.List, "All lists") }
                }
            }
        },
        floatingActionButton = {
            if (!selecting) FloatingActionButton({ adding = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Icon(Icons.Outlined.Add, "Add a task")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = MaterialTheme.shapes.medium) } },
    ) { padding ->
        HorizontalPager(pager, Modifier.padding(padding).fillMaxSize(), beyondViewportPageCount = 1) { i ->
            when (val page = pages[i]) {
                IndexPage.Today -> IndexToday(vm, state, container, undo) { reviewing = true }
                IndexPage.Upcoming -> IndexUpcoming(container, vm, undo, calendar)
                is IndexPage.Project -> IndexList(container, "p:" + page.project.id, state, vm, undo)
            }
        }
    }

    SlideOverPage(page != null, onBack = { page = null }) {
        shownPage?.let { key ->
            val close = { page = null }
            when (key) {
                SEARCH -> ViewPage("Search", pageSnackbar, close) {
                    IndexSearch(container, vm, pageUndo, onLabel = { openPage("l:" + it.id) }) { project ->
                        // A project is a tab: close search and go there.
                        page = null
                        pages.indexOfFirst { it is IndexPage.Project && it.project.id == project.id }.takeIf { it >= 0 }?.let { scope.launch { pager.scrollToPage(it) } }
                    }
                }
                COMPLETED -> ViewPage("Completed", pageSnackbar, close) {
                    val done by viewModel { CompletedViewModel(container) }.state.collectAsStateWithLifecycle()
                    val row = indexRows(container, done.today, pageUndo, vm)
                    PullToSync(vm) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) { completedItems(done, rule = true, row = row) }
                    }
                }
                TRASH -> ViewPage("Trash", pageSnackbar, close) {
                    val trashVm: TrashViewModel = viewModel { TrashViewModel(container) }
                    val trash by trashVm.state.collectAsStateWithLifecycle()
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) { trashItems(trash, { trashVm.restore(it) }, { trashVm.restore(it.task) }) }
                }
                else -> IndexLabelPage(container, key, state, vm, pageUndo, pageSnackbar, close)
            }
        }
    }
    if (newProject) NewProjectDialog(container, onCreated = { goTo = it.id }) { newProject = false }
    if (newLabel) NewLabelDialog(container) { newLabel = false }
    linkedTask?.let { id ->
        ModalBottomSheet({ linkedTask = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = sheetColor(), shape = SheetShape) {
            IndexDetails(container, id, state.today, undo, inSheet = true, onDelete = { linkedTask = null; undo.delete(id) })
        }
    }
    if (archived) ArchivedProjects(container) { archived = false }
    AddedMessages(vm.added, state, snackbar)
    if (adding) QuickAddSheet(state, into = project?.name, onAdd = { text -> listVm?.quickAdd(text) ?: vm.quickAdd(text) }) { adding = false }
    if (reviewing) ChangesSheet(state, vm::retry, vm::discard, vm::keepMine, vm::keepTheirs) { reviewing = false }
    if (jumping) {
        ModalBottomSheet({ jumping = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = sheetColor(), shape = SheetShape) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Jump to", Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                pages.forEachIndexed { i, page ->
                    val project = (page as? IndexPage.Project)
                    Row(
                        Modifier.fillMaxWidth().clickable { scope.launch { pager.scrollToPage(i) }; jumping = false }
                            .heightIn(min = 48.dp).padding(start = (20 + (project?.depth ?: 0) * 20).dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (page) {
                            IndexPage.Today -> Icon(Icons.Outlined.Today, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            IndexPage.Upcoming -> Icon(Icons.Outlined.DateRange, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            is IndexPage.Project -> ProjectIcon(page.project)
                        }
                        Text(page.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (pager.currentPage == i) Text("Open", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                JumpAction(Icons.Outlined.Add, "New project") { jumping = false; newProject = true }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Labels", Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                state.labels.forEach { l ->
                    JumpAction(Icons.Outlined.Sell, l.name, Hue.of(l.color).dot) { jumping = false; openPage("l:" + l.id) }
                }
                JumpAction(Icons.Outlined.Add, "New label") { jumping = false; newLabel = true }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                JumpAction(Icons.Outlined.TaskAlt, "Completed") { jumping = false; openPage(COMPLETED) }
                JumpAction(Icons.Outlined.Delete, "Trash") { jumping = false; openPage(TRASH) }
                JumpAction(Icons.Outlined.Inventory2, "Archived projects") { jumping = false; archived = true }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/** Rows that open their details in place, one at a time. [keep] leaves completed rows struck through until you leave the tab. */
@Composable
private fun indexRows(container: AppContainer, today: LocalDate, undo: UndoSnackbars, vm: TodayViewModel, keep: Boolean = false): RowSlot {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // A task added on this phone takes the server's id once the server has it; stay open on it.
    val openId = open?.let { container.outbox.renamedTo(it) ?: it }
    return { task, modifier ->
        IndexRow(
            task, today, modifier,
            open = openId == task.id,
            onOpen = { open = if (openId == task.id) null else task.id },
            onToggle = { on -> if (on) undo.complete(task.id, keep = keep) else vm.reopen(task.id) },
        ) {
            IndexDetails(container, task.id, today, undo, onDelete = { open = null; undo.delete(task.id) })
        }
    }
}

@Composable
private fun IndexToday(vm: TodayViewModel, state: TodayState, container: AppContainer, undo: UndoSnackbars, onReview: () -> Unit) {
    val row = indexRows(container, state.today, undo, vm, keep = true)
    // Above the list, as in the other layouts, rather than as an item that's empty most of the time.
    Column {
        ChangesNotice(state, onReview)
        PullToRefreshBox(state.refreshing, { vm.releaseKept(); vm.refresh() }) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 72.dp)) {
                when {
                    !state.loaded -> {}
                    state.overdue.isEmpty() && state.dueToday.isEmpty() -> item("empty") {
                        ListMessage(if (state.ready) "Nothing due today." else "Getting your tasks from the server.", Modifier.heightIn(min = 320.dp))
                    }
                    else -> {
                        if (state.overdue.isNotEmpty()) {
                            item("h-overdue") { SectionHeading("Overdue", state.overdue.count { !it.done }, Modifier.padding(top = 6.dp), rule = true) }
                            items(state.overdue, key = { it.id }) { row(it, Modifier.animateItem()) }
                        }
                        item("h-today") { SectionHeading("Today", state.dueToday.count { !it.done }, Modifier.padding(top = 6.dp), rule = true) }
                        items(state.dueToday, key = { it.id }) { row(it, Modifier.animateItem()) }
                    }
                }
            }
        }
    }
}

/** Upcoming: tasks by date from today on, or the month calendar when [calendar] is on. */
@Composable
private fun IndexUpcoming(container: AppContainer, todayVm: TodayViewModel, undo: UndoSnackbars, calendar: Boolean) {
    val vm: UpcomingViewModel = viewModel { UpcomingViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val row = indexRows(container, state.today, undo, todayVm)
    PullToSync(todayVm) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 72.dp)) {
            if (calendar) calendarItems(state, vm, rule = true, row = row) else upcomingItems(state, rule = true, row = row)
        }
    }
}

@Composable
private fun IndexSearch(container: AppContainer, todayVm: TodayViewModel, undo: UndoSnackbars, onLabel: (LabelEntity) -> Unit, onProject: (ProjectItem) -> Unit) {
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val row = indexRows(container, state.today, undo, todayVm)
    Column {
        SearchField(query, vm::search, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) { searchItems(state, onProject, onLabel, rule = true, row = row) }
    }
}

@Composable
private fun IndexRow(
    row: TaskRow,
    today: LocalDate,
    modifier: Modifier,
    open: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    details: @Composable () -> Unit,
) {
    val colors = LocalConatusColors.current
    val (dueText, timeText) = dueTexts(row, today)
    Column(modifier.background(if (open) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent)) {
        Row(
            Modifier.fillMaxWidth().taskClick(row.id, selectable = !row.done, if (open) "Close details" else "Open details", onOpen).heightIn(min = 48.dp).padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskCheckbox(row.priority, checked = row.done, taskTitle = row.title, onToggle = onToggle)
            Text(
                row.title,
                Modifier.weight(1f).clearAndSetSemantics { contentDescription = row.spoken(dueText, timeText, today) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.done) colors.muted else Color.Unspecified,
                textDecoration = if (row.done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncDetail(row.sync)
                if (row.subtasksTotal > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Outlined.Checklist, null, Modifier.size(14.dp), tint = colors.muted)
                        Text("${row.subtasksDone}/${row.subtasksTotal}", style = figures(), color = colors.muted)
                    }
                }
                row.due?.let { due ->
                    val color = dueColor(due, today, colors)
                    if (row.recurring) Icon(Icons.Outlined.Repeat, null, Modifier.size(14.dp), tint = color)
                    // Today's tasks show only their time; anything else shows its date, with a time if it has one.
                    val text = if (due == today) timeText ?: "Today" else listOfNotNull(shortDueLabel(due, today, locale()), timeText).joinToString(" ")
                    Text(text, style = figures(), color = color)
                }
            }
        }
        AnimatedVisibility(open, enter = expandVertically(tween(120)), exit = shrinkVertically(tween(120))) { details() }
    }
}

/** Index details: labelled fields in place, under the row, with sub-tasks and the actions. */
@Composable
private fun IndexDetails(container: AppContainer, taskId: String, today: LocalDate, undo: UndoSnackbars, inSheet: Boolean = false, onDelete: () -> Unit) {
    val vm: TaskDetailViewModel = viewModel(key = "task-$taskId") { TaskDetailViewModel(container, taskId) }
    val detail by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editor by rememberSaveable { mutableStateOf<Editor?>(null) }
    var commenting by rememberSaveable { mutableStateOf(false) }
    val colors = LocalConatusColors.current
    val row = detail.row ?: return
    // In a sheet there's no row above, so the task's checkbox and name lead.
    if (inSheet) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TaskCheckbox(row.priority, row.done, row.title) { on -> if (on) scope.launch { vm.complete()?.let { undo.completed(it) } } else vm.reopen() }
            Text(row.title, Modifier.clickable(onClickLabel = "Edit name") { editor = Editor.Title }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        }
    }
    Column(Modifier.fillMaxWidth().padding(start = 48.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        detail.failure?.let { FailureNotice(it, { vm.retry(it) }, { vm.discard(it) }, Modifier.padding(bottom = 8.dp)) }
        detail.conflict?.let { c -> ConflictNotice(c, detail.task, detail.labels, today, { vm.keepMine(c) }, { vm.keepTheirs(c) }, Modifier.padding(bottom = 8.dp)) }
        Text(
            row.description.ifBlank { "Add a description" },
            Modifier.fillMaxWidth().clickable(onClickLabel = "Edit description") { editor = Editor.Description }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
        )
        // The labels' column grows with the font size, so "Reminders" still fits at 200%.
        val labelWidth = 96.dp * LocalDensity.current.fontScale
        taskProperties(detail, today).forEach { property ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = property.editor != null) { editor = property.editor }.heightIn(min = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(property.label, Modifier.width(labelWidth), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                Text(property.value, style = figures(), color = property.color ?: if (property.set) MaterialTheme.colorScheme.onSurface else colors.muted)
            }
        }
        if (detail.subtasks.isNotEmpty()) {
            Text("Sub-tasks", Modifier.padding(top = 8.dp).semantics { heading() }, style = MaterialTheme.typography.labelSmall, color = colors.muted)
            detail.subtasks.forEach { sub ->
                Row(Modifier.fillMaxWidth().padding(start = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    TaskCheckbox(sub.priority, sub.done, sub.title) { on ->
                        if (on) scope.launch { vm.completeSubtask(sub.id)?.let { undo.completed(it) } } else vm.reopenSubtask(sub.id)
                    }
                    Text(
                        sub.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sub.done) colors.muted else Color.Unspecified,
                        textDecoration = if (sub.done) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
        detail.comments.lastOrNull()?.let { latest ->
            Text("Latest comment", Modifier.padding(top = 8.dp).semantics { heading() }, style = MaterialTheme.typography.labelSmall, color = colors.muted)
            Text(latest.content, Modifier.clickable(onClickLabel = "Show comments") { commenting = true }, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IndexButton("Edit") { editor = Editor.Title }
            IndexButton("Reschedule") { editor = Editor.Date }
            IndexButton("Move") { editor = Editor.Move }
            IndexButton(if (detail.comments.isEmpty()) "Comment" else "Comments (${detail.comments.size})") { commenting = true }
            IndexButton("Add sub-task") { editor = Editor.Subtask }
            IndexButton("Delete", MaterialTheme.colorScheme.error, onDelete)
        }
    }
    editor?.let { EditorDialog(it, detail, today, vm::edit, vm::setLabels, vm::move, vm::addSubtask, vm::addReminder, vm::deleteReminder) { editor = null } }
    if (commenting) CommentsSheet("Comments", detail.comments, detail.me, detail.zone, today, vm::addComment, vm::editComment, vm::deleteComment) { commenting = false }
}

@Composable
private fun JumpAction(icon: ImageVector, text: String, tint: Color? = null, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Icon(icon, null, Modifier.size(18.dp), tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

/**
 * A project tab, or a label's tasks: one-line rows that open in place, the command line adding
 * to the project, and the project's options beside it. [key] is "p:" or "l:" and an id.
 */
@Composable
private fun IndexList(container: AppContainer, key: String, todayState: TodayState, todayVm: TodayViewModel, undo: UndoSnackbars) {
    val source = if (key.startsWith("p:")) ListSource.Project(key.drop(2)) else ListSource.Label(key.drop(2))
    val vm: ListViewModel = viewModel(key = "list-$key") { ListViewModel(container, source) }
    val list by vm.state.collectAsStateWithLifecycle()
    var open by rememberSaveable(key) { mutableStateOf<String?>(null) }
    val openId = open?.let { container.outbox.renamedTo(it) ?: it }
    var addingTo by rememberSaveable(key) { mutableStateOf<String?>(null) }
    val row = @Composable { tree: TreeRow, modifier: Modifier ->
        IndexRow(
            tree.row, list.today, modifier,
            open = openId == tree.task.id,
            onOpen = { open = if (openId == tree.task.id) null else tree.task.id },
            onToggle = { on -> if (on) undo.complete(tree.task.id) else todayVm.reopen(tree.task.id) },
        ) {
            IndexDetails(container, tree.task.id, list.today, undo, onDelete = { open = null; undo.delete(tree.task.id) })
        }
    }
    PullToSync(todayVm) {
        if (list.board) {
            Column {
                ProjectBoard(list, vm, onOpen = { open = it }, onToggle = { tree, on -> if (on) undo.complete(tree.task.id) else todayVm.reopen(tree.task.id) }, onAdd = { addingTo = it?.id ?: "" })
            }
            // A board has no rows to open in place, so a card's details open in a sheet.
            open?.let { id -> ModalBottomSheet({ open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = sheetColor(), shape = SheetShape) { IndexDetails(container, id, list.today, undo, inSheet = true, onDelete = { open = null; undo.delete(id) }) } }
        } else {
            ProjectList(list, vm, contentPadding = PaddingValues(bottom = 72.dp), rule = true, addInSections = { addingTo = it?.id ?: "" }, row = row)
        }
    }
    addingTo?.let { section ->
        val into = list.groups.firstOrNull { it.section?.id == section }?.section?.name ?: list.title
        QuickAddSheet(todayState, into = into, onAdd = { vm.quickAdd(it, section.ifEmpty { null }) }) { addingTo = null }
    }
}

/** A label's tasks, pushed over the tabs from the jump sheet. */
@Composable
private fun IndexLabelPage(container: AppContainer, key: String, todayState: TodayState, todayVm: TodayViewModel, undo: UndoSnackbars, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val vm: ListViewModel = viewModel(key = "list-$key") { ListViewModel(container, ListSource.Label(key.drop(2))) }
    val list by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Sell, null, Modifier.size(18.dp), tint = Hue.of(list.label?.color).dot)
                        Text(list.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { ListActions(list, vm, onGone = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = MaterialTheme.shapes.medium) } },
    ) { padding ->
        Box(Modifier.padding(padding)) { IndexList(container, key, todayState, todayVm, undo) }
    }
}

@Composable
private fun IndexButton(text: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) = OutlinedButton(
    onClick,
    shape = MaterialTheme.shapes.small,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    contentPadding = PaddingValues(horizontal = 14.dp),
) { Text(text, style = MaterialTheme.typography.labelLarge, color = color) }
