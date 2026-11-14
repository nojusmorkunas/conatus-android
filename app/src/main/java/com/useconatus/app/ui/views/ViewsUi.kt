package com.useconatus.app.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.useconatus.app.data.db.LabelEntity
import com.useconatus.app.data.db.ProjectEntity
import com.useconatus.app.domain.monthGridStart
import com.useconatus.app.ui.components.ListMessage
import com.useconatus.app.ui.components.projectIcon
import com.useconatus.app.ui.components.SectionHeading
import com.useconatus.app.ui.components.locale
import com.useconatus.app.ui.format.LocalDatePrefs
import com.useconatus.app.ui.format.dueLabel
import com.useconatus.app.ui.format.formatDate
import com.useconatus.app.ui.format.pastDateLabel
import com.useconatus.app.ui.theme.Hue
import com.useconatus.app.ui.theme.LocalConatusColors
import com.useconatus.app.ui.today.ProjectItem
import com.useconatus.app.ui.today.TaskRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

// The pieces of Upcoming, the calendar, search, Completed and Trash that every layout shares.
// Each layout puts them in its own list and frame, with its own task row.

/** One task row in the layout's own style. */
typealias RowSlot = @Composable LazyItemScope.(TaskRow, Modifier) -> Unit

/**
 * A date's heading: "Today", "Tomorrow", a weekday or a date, with the date beside it when that adds anything.
 * [past] days, such as completions, say "Yesterday" and name the weekdays before today.
 */
@Composable
fun DayHeading(date: LocalDate, today: LocalDate, count: Int?, modifier: Modifier = Modifier, rule: Boolean = false, past: Boolean = false) {
    val locale = locale()
    val format = LocalDatePrefs.current.format
    val label = if (past) pastDateLabel(date, today, locale, format) else dueLabel(date, today, locale, format)
    val full = formatDate(date, format)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SectionHeading(label, count, Modifier.weight(1f), rule = rule)
        if (full != label) {
            Text(full, Modifier.padding(end = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A view pushed over the layout with a back button and its own snackbars, which the layout's would be hidden under. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewPage(title: String, snackbar: SnackbarHostState, onBack: () -> Unit, content: @Composable () -> Unit) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    },
    snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = MaterialTheme.shapes.medium) } },
) { padding -> Box(Modifier.padding(padding)) { content() } }

/** Switches Upcoming between its list and the month calendar. */
@Composable
fun CalendarToggle(calendar: Boolean, onToggle: (Boolean) -> Unit) = IconButton({ onToggle(!calendar) }) {
    if (calendar) Icon(Icons.AutoMirrored.Outlined.ViewList, "Show as list") else Icon(Icons.Outlined.CalendarMonth, "Show as calendar")
}

/** Upcoming as a list: a heading per date with tasks, from today on. */
fun LazyListScope.upcomingItems(state: UpcomingState, rule: Boolean = false, row: RowSlot) {
    if (state.loaded && state.days.isEmpty()) item("empty") { ListMessage("Nothing coming up.", Modifier.heightIn(min = 320.dp)) }
    state.days.forEach { day ->
        item("d-" + day.date) { DayHeading(day.date, state.today, day.rows.size, Modifier.animateItem().padding(top = 6.dp), rule) }
        items(day.rows, key = { "${day.date}-${it.id}" }) { row(it, Modifier.animateItem()) }
    }
}

/** Upcoming as a calendar: the month grid, then the tasks on the date picked, repeats included. */
fun LazyListScope.calendarItems(state: UpcomingState, vm: UpcomingViewModel, rule: Boolean = false, row: RowSlot) {
    item("grid") { MonthGrid(state.month, state.weekStart, state.today, state.selected, state.counts, vm::select, vm::showMonth) }
    item("day") { DayHeading(state.selected, state.today, state.selectedRows.size, Modifier.padding(top = 8.dp), rule) }
    if (state.loaded && state.selectedRows.isEmpty()) item("free") { ListMessage("Nothing on this day.", Modifier.heightIn(min = 120.dp)) }
    items(state.selectedRows, key = { "c-" + it.id }) { row(it, Modifier.animateItem()) }
}

/** A month as weeks, with a dot under each date that has tasks: one to three, by how many. */
@Composable
fun MonthGrid(month: YearMonth, weekStart: DayOfWeek, today: LocalDate, picked: LocalDate?, counts: Map<LocalDate, Int>, onPick: (LocalDate) -> Unit, onMonth: (YearMonth) -> Unit) {
    val locale = locale()
    val start = monthGridStart(month, weekStart)
    val colors = LocalConatusColors.current
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month") }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            IconButton({ onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month") }
        }
        Row {
            repeat(7) { i ->
                Text(
                    start.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    Modifier.weight(1f).clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        repeat(6) { week ->
            Row {
                repeat(7) { d ->
                    val date = start.plusDays((week * 7 + d).toLong())
                    val count = counts[date] ?: 0
                    val chosen = date == picked
                    val inMonth = YearMonth.from(date) == month
                    val spoken = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)) + when (count) {
                        0 -> ""
                        1 -> ", 1 task"
                        else -> ", $count tasks"
                    }
                    Column(
                        Modifier.weight(1f).height(52.dp).clickable { onPick(date) }.semantics(mergeDescendants = true) {
                            contentDescription = spoken
                            selected = chosen
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.size(32.dp)
                                .then(if (chosen) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .then(if (date == today && !chosen) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${date.dayOfMonth}",
                                Modifier.clearAndSetSemantics {},
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (date == today) FontWeight.SemiBold else null,
                                color = when {
                                    chosen -> MaterialTheme.colorScheme.onPrimary
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> colors.muted
                                },
                            )
                        }
                        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(count.coerceAtMost(3)) { Box(Modifier.size(4.dp).background(if (inMonth) MaterialTheme.colorScheme.onSurfaceVariant else colors.muted, CircleShape)) }
                        }
                    }
                }
            }
        }
    }
}

/** The search box: focused when it opens, with a clear button once there's something to clear. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        query,
        onQuery,
        modifier.fillMaxWidth().focusRequester(focus),
        placeholder = { Text("Search tasks, projects and labels") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton({ onQuery("") }) { Icon(Icons.Outlined.Close, "Clear") } },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Search results: matching projects and labels, open tasks, then completed ones. */
fun LazyListScope.searchItems(
    state: SearchState,
    onProject: (ProjectItem) -> Unit,
    onLabel: (LabelEntity) -> Unit,
    rule: Boolean = false,
    row: RowSlot,
) {
    if (state.query.isEmpty()) return
    if (state.projects.isEmpty() && state.labels.isEmpty() && state.open.isEmpty() && state.done.isEmpty()) {
        item("none") { ListMessage("Nothing matches “${state.query}”.", Modifier.heightIn(min = 200.dp)) }
        return
    }
    if (state.projects.isNotEmpty()) {
        item("h-projects") { SectionHeading("Projects", state.projects.size, Modifier.padding(top = 6.dp), rule = rule) }
        items(state.projects, key = { "p-" + it.id }) { project -> ResultLine(project.name, projectIcon(project.icon, project.isInbox), project.hue.dot) { onProject(project) } }
    }
    if (state.labels.isNotEmpty()) {
        item("h-labels") { SectionHeading("Labels", state.labels.size, Modifier.padding(top = 6.dp), rule = rule) }
        items(state.labels, key = { "l-" + it.id }) { label -> ResultLine(label.name, Icons.Outlined.Sell, Hue.of(label.color).dot) { onLabel(label) } }
    }
    if (state.open.isNotEmpty()) {
        item("h-open") { SectionHeading("Tasks", state.open.size, Modifier.padding(top = 6.dp), rule = rule) }
        items(state.open, key = { "o-" + it.id }) { row(it, Modifier.animateItem()) }
    }
    if (state.done.isNotEmpty()) {
        item("h-done") { SectionHeading("Completed", state.done.size, Modifier.padding(top = 6.dp), rule = rule) }
        items(state.done, key = { "d-" + it.id }) { row(it, Modifier.animateItem()) }
    }
}

@Composable
private fun ResultLine(name: String, icon: ImageVector, tint: Color, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
) {
    Icon(icon, null, Modifier.size(18.dp), tint = tint)
    Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Completed tasks by the day they were completed, newest first. Unticking one reopens it. */
fun LazyListScope.completedItems(state: CompletedState, rule: Boolean = false, row: RowSlot) {
    if (state.loaded && state.days.isEmpty()) item("empty") { ListMessage("Nothing completed yet.", Modifier.heightIn(min = 320.dp)) }
    state.days.forEach { day ->
        item("d-" + day.date) { DayHeading(day.date, state.today, day.rows.size, Modifier.padding(top = 6.dp), rule, past = true) }
        items(day.rows, key = { "${day.date}-${it.id}" }) { row(it, Modifier.animateItem()) }
    }
}

/** Trash: deleted projects and tasks, each with Restore. As on the web, they stay until restored. */
fun LazyListScope.trashItems(state: TrashState, onRestoreProject: (ProjectEntity) -> Unit, onRestoreTask: (TrashedTask) -> Unit) {
    item("intro") {
        Text(
            "Deleted projects and tasks stay here until you restore them.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.loaded && state.projects.isEmpty() && state.tasks.isEmpty()) item("empty") { ListMessage("Trash is empty.", Modifier.heightIn(min = 240.dp)) }
    if (state.projects.isNotEmpty()) {
        item("h-projects") { SectionHeading("Projects", state.projects.size, Modifier.padding(top = 6.dp)) }
        items(state.projects, key = { "p-" + it.id }) { project ->
            TrashLine(project.name, "Project", projectIcon(project.icon), Hue.of(project.color).dot, Modifier.animateItem()) { onRestoreProject(project) }
        }
    }
    if (state.tasks.isNotEmpty()) {
        item("h-tasks") { SectionHeading("Tasks", state.tasks.size, Modifier.padding(top = 6.dp)) }
        items(state.tasks, key = { "t-" + it.task.id }) { item ->
            TrashLine(item.task.content, item.project, null, Color.Unspecified, Modifier.animateItem()) { onRestoreTask(item) }
        }
    }
}

@Composable
private fun TrashLine(title: String, detail: String?, icon: ImageVector?, tint: Color, modifier: Modifier, onRestore: () -> Unit) = Row(
    modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    icon?.let { Icon(it, null, Modifier.size(20.dp), tint = tint) }
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    TextButton(onRestore) { Text("Restore") }
}
