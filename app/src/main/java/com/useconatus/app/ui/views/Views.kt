package com.useconatus.app.ui.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.useconatus.app.AppContainer
import com.useconatus.app.data.db.LabelEntity
import com.useconatus.app.data.db.ProjectEntity
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.domain.calendarOccurrences
import com.useconatus.app.domain.monthGridStart
import com.useconatus.app.domain.weekStartOf
import com.useconatus.app.ui.today.ProjectItem
import com.useconatus.app.ui.today.TaskRow
import com.useconatus.app.ui.today.days
import com.useconatus.app.ui.today.rowLookups
import com.useconatus.app.ui.today.timezone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import com.useconatus.app.data.db.escapeLike

// The views across projects: Upcoming with its calendar, search, Completed and Trash. All of them
// read only the phone's database, so they work the same offline and without a server.

/** The views' keys as pushed pages or drawer places, beside a project's and a label's. */
const val SEARCH = "search"
const val COMPLETED = "completed"
const val TRASH = "trash"

/** The account's first day of the week, or the phone's locale's without a server. */
private fun AppContainer.weekStart() = store.weekStart.map { it?.let(::weekStartOf) ?: WeekFields.of(Locale.getDefault()).firstDayOfWeek }

data class DayGroup(val date: LocalDate, val rows: List<TaskRow>)

data class UpcomingState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    /** Open tasks from today on, by date, as on the web: each repeating task once, on its next date. */
    val days: List<DayGroup> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val selected: LocalDate = LocalDate.now(),
    /** Tasks on each date of the month grid, with repeats on every date they fall on. */
    val counts: Map<LocalDate, Int> = emptyMap(),
    /** The selected date's tasks, repeats included. */
    val selectedRows: List<TaskRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingViewModel(private val container: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow<LocalDate?>(null)

    private data class Period(val today: LocalDate, val weekStart: DayOfWeek, val month: YearMonth, val selected: LocalDate?)

    val state: StateFlow<UpcomingState> = combine(container.days(), container.weekStart(), month, selected, ::Period).flatMapLatest { p ->
        val start = monthGridStart(p.month, p.weekStart)
        val end = start.plusDays(41)
        combine(
            container.db.tasks().observeUpcoming(p.today.toString()),
            container.db.tasks().observeScheduledBy(end.toString()),
            container.rowLookups(),
        ) { upcoming, scheduled, look ->
            val day = p.selected ?: p.today
            val occurrences = calendarOccurrences(scheduled, start.toString(), end.toString())
            UpcomingState(
                loaded = true,
                today = p.today,
                days = upcoming.groupBy { it.dueDate!! }.map { (date, tasks) -> DayGroup(LocalDate.parse(date), tasks.map(look::row)) },
                month = p.month,
                weekStart = p.weekStart,
                selected = day,
                counts = occurrences.groupingBy { LocalDate.parse(it.date) }.eachCount(),
                // A repeat shows on the day picked, as a preview of the same task.
                selectedRows = occurrences.filter { it.date == day.toString() }.map { look.row(it.task).copy(due = day) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpcomingState())

    fun showMonth(value: YearMonth) {
        month.value = value
    }

    fun select(date: LocalDate) {
        selected.value = date
        month.value = YearMonth.from(date)
    }
}

data class SearchState(
    val query: String = "",
    val today: LocalDate = LocalDate.now(),
    val projects: List<ProjectItem> = emptyList(),
    val labels: List<LabelEntity> = emptyList(),
    val open: List<TaskRow> = emptyList(),
    val done: List<TaskRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")

    /** What's typed, right away; the results follow a moment later. */
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<SearchState> = _query.debounce(150).map { it.trim() }.flatMapLatest { q ->
        if (q.isEmpty()) return@flatMapLatest container.days().map { SearchState(today = it) }
        combine(container.db.tasks().observeSearch(escapeLike(q)), container.rowLookups(), container.days()) { tasks, look, today ->
            val (done, open) = tasks.partition { it.isCompleted }
            SearchState(
                query = q,
                today = today,
                projects = look.projects.filter { it.name.contains(q, ignoreCase = true) },
                labels = look.labels.filter { it.name.contains(q, ignoreCase = true) },
                open = open.map(look::row),
                done = done.map(look::row),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    fun search(text: String) {
        _query.value = text
    }
}

data class CompletedState(val loaded: Boolean = false, val today: LocalDate = LocalDate.now(), val days: List<DayGroup> = emptyList())

class CompletedViewModel(container: AppContainer) : ViewModel() {
    /** The last completions, by the day they were completed in the account's time zone. */
    val state: StateFlow<CompletedState> = combine(container.db.tasks().observeCompleted(), container.rowLookups(), container.days(), container.timezone()) { tasks, look, today, tz ->
        val zone = runCatching { ZoneId.of(tz ?: "") }.getOrDefault(ZoneId.systemDefault())
        fun day(task: TaskEntity) = task.completedAt?.let { runCatching { Instant.parse(it).atZone(zone).toLocalDate() }.getOrNull() } ?: today
        CompletedState(true, today, tasks.groupBy(::day).map { (date, done) -> DayGroup(date, done.map(look::row)) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompletedState())
}

/** A task in Trash, with the name of its project. */
data class TrashedTask(val task: TaskEntity, val project: String?)

data class TrashState(val loaded: Boolean = false, val projects: List<ProjectEntity> = emptyList(), val tasks: List<TrashedTask> = emptyList())

class TrashViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<TrashState> = combine(container.db.projects().observeTrash(), container.db.tasks().observeTrash(), container.db.projects().observeAll()) { projects, tasks, all ->
        val names = all.associate { it.id to it.name }
        TrashState(true, projects, tasks.map { TrashedTask(it, names[it.projectId]) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashState())

    fun restore(task: TaskEntity) = viewModelScope.launch { container.writes.restore(task) }

    fun restore(project: ProjectEntity) = viewModelScope.launch { container.lists.restoreProject(project) }
}
