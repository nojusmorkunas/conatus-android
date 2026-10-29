package com.useconatus.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.useconatus.app.AppContainer
import com.useconatus.app.data.db.Conflict
import com.useconatus.app.data.db.LabelEntity
import com.useconatus.app.data.db.PendingChange
import com.useconatus.app.data.db.ProjectEntity
import com.useconatus.app.data.db.SubtaskCount
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.data.prefs.Session
import com.useconatus.app.data.sync.Completion
import com.useconatus.app.data.sync.Deletion
import com.useconatus.app.data.sync.TaskEdit
import com.useconatus.app.domain.groupToday
import com.useconatus.app.domain.todayIn
import com.useconatus.app.ui.theme.Hue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** A project as navigation shows it: drawer, tabs and the project shown in a task row. */
data class ProjectItem(val id: String, val name: String, val hue: Hue, val parentId: String?, val isInbox: Boolean, val open: Int, val icon: String? = null)

data class LabelItem(val name: String, val hue: Hue)

/** Where a task stands with the server. Sync is silent: only a problem the user must settle shows. */
sealed interface SyncMark {
    data object Synced : SyncMark
    data class Failed(val reason: String) : SyncMark
    data object Conflict : SyncMark
}

/** Everything a task row in any layout needs, already looked up. */
data class TaskRow(
    val id: String,
    val title: String,
    val description: String,
    val priority: Int,
    val due: LocalDate?,
    val time: String?,
    val recurring: Boolean,
    val deadline: LocalDate?,
    val minutes: Int?,
    val comments: Int,
    val subtasksDone: Int,
    val subtasksTotal: Int,
    val labels: List<LabelItem>,
    val project: ProjectItem?,
    val done: Boolean = false,
    val sync: SyncMark = SyncMark.Synced,
) {
    /** One sentence for TalkBack, so a row is a single stop. */
    fun spoken(dueText: String?, timeText: String?, today: LocalDate): String = buildString {
        append(title)
        if (done) append(", completed")
        if (priority < 4) append(", priority $priority")
        if (due != null && due < today) append(", overdue")
        dueText?.let { append(", due $it") }
        timeText?.let { append(" at $it") }
        if (recurring) append(", repeats")
        project?.takeIf { !it.isInbox }?.let { append(", in ${it.name}") }
        if (labels.isNotEmpty()) append(", labels ${labels.joinToString { it.name }}")
        when (sync) {
            is SyncMark.Failed -> append(", couldn't sync")
            SyncMark.Conflict -> append(", changed on another device too")
            SyncMark.Synced -> {}
        }
    }
}

data class TodayState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val overdue: List<TaskRow> = emptyList(),
    val dueToday: List<TaskRow> = emptyList(),
    val projects: List<ProjectItem> = emptyList(),
    val labels: List<LabelEntity> = emptyList(),
    /** Changes the server rejected, waiting for Retry or Discard. */
    val failed: List<PendingChange> = emptyList(),
    /** Tasks changed here and elsewhere, waiting for the user to keep one side. */
    val conflicts: List<ConflictItem> = emptyList(),
    /** A sync the user pulled for is running. Syncs after each write run without the spinner. */
    val refreshing: Boolean = false,
    /** False only while a newly connected phone waits for its first sync. */
    val ready: Boolean = false,
)

/** A conflict with the task as the phone has it, for showing both versions. */
data class ConflictItem(val conflict: Conflict, val task: TaskEntity?)

/** The problems queued for each task, as the rows show them. */
internal class Queue(changes: List<PendingChange>, conflicts: List<Conflict>) {
    private val failures = changes.filter { it.failure != null }.associate { it.taskId to it.failure!! }
    private val conflicted = conflicts.mapTo(HashSet()) { it.taskId }

    fun mark(taskId: String): SyncMark = when {
        taskId in conflicted -> SyncMark.Conflict
        else -> failures[taskId]?.let(SyncMark::Failed) ?: SyncMark.Synced
    }
}

internal fun ProjectEntity.toItem(open: Int) = ProjectItem(id, name, Hue.of(color), parentId, isInbox, open, icon)

internal fun TaskEntity.toRow(
    projects: Map<String, ProjectItem>,
    labels: Map<String, LabelEntity>,
    subtasks: Map<String, SubtaskCount>,
    queue: Queue,
) = TaskRow(
    id = id,
    title = content,
    description = description,
    priority = priority,
    due = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    time = dueTime,
    recurring = recurrence != null,
    deadline = deadlineDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    minutes = durationMinutes,
    comments = commentCount,
    subtasksDone = subtasks[id]?.done ?: 0,
    subtasksTotal = subtasks[id]?.total ?: 0,
    labels = labelIds.mapNotNull { labels[it] }.map { LabelItem(it.name, Hue.of(it.color)) },
    project = projects[projectId],
    done = isCompleted,
    sync = queue.mark(id),
)

/** Projects with their open counts, shared by Today and task details. Archived ones hide everywhere. */
internal fun AppContainer.projectItems(): Flow<List<ProjectItem>> =
    combine(db.projects().observeAll(), db.tasks().observeOpenCounts()) { projects, counts ->
        val open = counts.associate { it.projectId to it.open }
        projects.filter { !it.isArchived && it.deletedAt == null }.map { it.toItem(open[it.id] ?: 0) }
    }

/** The account's time zone, or null without a server (the phone's applies). */
internal fun AppContainer.timezone(): Flow<String?> = store.session.map { (it as? Session.SignedIn)?.timezone }.distinctUntilChanged()

/** The account's date (the phone's without a server), moving on at midnight even if the app stays open. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun AppContainer.days(): Flow<LocalDate> = timezone().flatMapLatest { tz ->
    flow {
        while (true) {
            emit(todayIn(tz))
            val zone = runCatching { ZoneId.of(tz ?: "") }.getOrDefault(ZoneId.systemDefault())
            val now = ZonedDateTime.now(zone)
            delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).toMillis() + 1_000)
        }
    }
}.distinctUntilChanged()

/** Everything a list needs to turn tasks into rows. */
internal class RowLookups(
    val projects: List<ProjectItem>,
    val labels: List<LabelEntity>,
    private val subtasks: Map<String, SubtaskCount>,
    private val queue: Queue,
) {
    private val projectsById = projects.associateBy { it.id }
    private val labelsById = labels.associateBy { it.id }

    fun row(task: TaskEntity) = task.toRow(projectsById, labelsById, subtasks, queue)
}

internal fun AppContainer.rowLookups(): Flow<RowLookups> = combine(
    projectItems(),
    db.labels().observeAll(),
    db.tasks().observeSubtaskCounts(),
    db.changes().observeAll(),
    db.conflicts().observeAll(),
) { projects, labels, subtasks, changes, conflicts -> RowLookups(projects, labels, subtasks.associateBy { it.parentId }, Queue(changes, conflicts)) }

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val day = container.days()

    /**
     * Tasks completed in this visit that Index keeps in place, struck through. They show as they
     * were, so a repeating task stays put instead of jumping to its next date under your thumb.
     */
    private val kept = MutableStateFlow<Map<String, Completion>>(emptyMap())

    private val tasks = day.flatMapLatest { today ->
        combine(container.db.tasks().observeDueBy(today.toString()), kept) { open, kept ->
            val rows = open.filter { it.id !in kept } + kept.values.map { it.before.copy(isCompleted = true) }
            Triple(today, kept.keys, rows)
        }
    }

    private val lookups = combine(
        container.projectItems(),
        container.db.labels().observeAll(),
        container.db.tasks().observeSubtaskCounts(),
        container.db.changes().observeAll(),
        container.db.conflicts().observeAll().flatMapLatest { conflicts ->
            container.db.tasks().observeByIds(conflicts.map { it.taskId }).map { tasks ->
                conflicts.map { conflict -> ConflictItem(conflict, tasks.firstOrNull { it.id == conflict.taskId }) }
            }
        },
    ) { projects, labels, subtasks, changes, conflicts -> Lookups(projects, labels, subtasks, changes, conflicts) }

    private val pulling = MutableStateFlow(false)

    val state: StateFlow<TodayState> = combine(tasks, lookups, container.store.lastSyncAt, container.store.session, pulling) { (today, kept, rows), look, lastSync, session, pulling ->
        val groups = groupToday(rows, today, kept)
        val projectsById = look.projects.associateBy { it.id }
        val labelsById = look.labels.associateBy { it.id }
        val subtasksById = look.subtasks.associateBy { it.parentId }
        val queue = Queue(look.changes, look.conflicts.map { it.conflict })
        fun row(task: TaskEntity) = task.toRow(projectsById, labelsById, subtasksById, queue)
        TodayState(
            loaded = true,
            today = today,
            overdue = groups.overdue.map(::row),
            dueToday = groups.today.map(::row),
            projects = look.projects,
            labels = look.labels,
            failed = look.changes.filter { it.failure != null },
            conflicts = look.conflicts,
            refreshing = pulling,
            ready = lastSync != null || session !is Session.SignedIn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    private val _added = MutableSharedFlow<TaskEntity>(extraBufferCapacity = 8)

    /** Tasks just added with quick add, so the layout can say where each one went. */
    val added: SharedFlow<TaskEntity> = _added

    init {
        viewModelScope.launch { container.sync.sync() }
    }

    fun refresh() {
        viewModelScope.launch {
            pulling.value = true
            try {
                container.sync.sync()
            } finally {
                pulling.value = false
            }
        }
    }

    /** [keep] leaves the row in place, struck through, until [releaseKept] (Index). */
    suspend fun complete(id: String, keep: Boolean = false): Completion? {
        val task = container.db.tasks().get(id) ?: return null
        return container.writes.complete(task).also { if (keep) kept.value += id to it }
    }

    fun undo(completion: Completion) = viewModelScope.launch {
        kept.value -= completion.taskId
        container.writes.undo(completion)
    }

    suspend fun delete(id: String): Deletion? = container.db.tasks().get(id)?.let { container.writes.delete(it) }

    fun undo(deletion: Deletion) = viewModelScope.launch { container.writes.undo(deletion) }

    /** Unticking a row completed in this visit takes the completion back, date and all. */
    fun reopen(id: String) = viewModelScope.launch {
        val completion = kept.value[id]
        if (completion != null) {
            kept.value -= id
            container.writes.undo(completion)
        } else {
            container.db.tasks().get(id)?.let { container.writes.reopen(it) }
        }
    }

    fun releaseKept() {
        kept.value = emptyMap()
    }

    fun quickAdd(text: String) = viewModelScope.launch { _added.emit(container.writes.quickAdd(text.trim())) }

    fun reschedule(id: String, date: LocalDate?) = reschedule(setOf(id), date)

    fun reschedule(ids: Set<String>, date: LocalDate?) = update(ids, if (date == null) TaskEdit.noDate else TaskEdit(dueDate = TaskEdit.Field(date.toString())))

    fun prioritize(ids: Set<String>, priority: Int) = update(ids, TaskEdit(priority = priority))

    private fun update(ids: Set<String>, edit: TaskEdit) = viewModelScope.launch {
        ids.forEach { id -> container.db.tasks().get(id)?.let { container.writes.update(it, edit) } }
    }

    // A parent's completion, delete or move takes its sub-tasks along, so those act on the parents alone.

    fun move(ids: Set<String>, projectId: String, sectionId: String?) = viewModelScope.launch {
        roots(ids).forEach { container.writes.move(it, projectId, sectionId) }
    }

    suspend fun complete(ids: Set<String>): List<Completion> = roots(ids).filter { !it.isCompleted }.map { container.writes.complete(it) }

    suspend fun delete(ids: Set<String>): List<Deletion> = roots(ids).map { container.writes.delete(it) }

    /** The tasks in [ids], less any with a parent, or a parent's parent, among them. */
    private suspend fun roots(ids: Set<String>): List<TaskEntity> = ids.mapNotNull { container.db.tasks().get(it) }.filterNot { task ->
        var parent = task.parentId
        while (parent != null && parent !in ids) parent = container.db.tasks().get(parent)?.parentId
        parent != null
    }

    fun retry(change: PendingChange) = viewModelScope.launch {
        container.outbox.retry(change)
        container.sync.sync()
    }

    fun discard(change: PendingChange) = viewModelScope.launch { runCatching { container.discard(change) } }

    fun keepMine(conflict: Conflict) = viewModelScope.launch { container.keepMine(conflict) }

    fun keepTheirs(conflict: Conflict) = viewModelScope.launch { container.keepTheirs(conflict) }

    private data class Lookups(
        val projects: List<ProjectItem>,
        val labels: List<LabelEntity>,
        val subtasks: List<SubtaskCount>,
        val changes: List<PendingChange>,
        val conflicts: List<ConflictItem>,
    )
}

/** Projects as a tree: the Inbox first, then each top-level project followed by its sub-projects, with their depth. */
fun List<ProjectItem>.inTreeOrder(): List<Pair<ProjectItem, Int>> {
    val children = groupBy { it.parentId }
    val ids = mapTo(HashSet()) { it.id }
    val out = mutableListOf<Pair<ProjectItem, Int>>()
    fun visit(project: ProjectItem, depth: Int) {
        out += project to depth
        children[project.id].orEmpty().forEach { visit(it, depth + 1) }
    }
    // A project whose parent isn't visible (archived, or shared without it) shows at the top level.
    filter { it.parentId == null || it.parentId !in ids }.sortedByDescending { it.isInbox }.forEach { visit(it, 0) }
    return out
}
