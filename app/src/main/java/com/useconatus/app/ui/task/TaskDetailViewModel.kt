package com.useconatus.app.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.useconatus.app.AppContainer
import com.useconatus.app.data.db.CommentEntity
import com.useconatus.app.data.db.Conflict
import com.useconatus.app.data.db.ReminderEntity
import com.useconatus.app.data.db.LabelEntity
import com.useconatus.app.data.db.PendingChange
import com.useconatus.app.data.db.SectionEntity
import com.useconatus.app.data.db.SubtaskCount
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.data.sync.Completion
import com.useconatus.app.data.sync.TaskEdit
import com.useconatus.app.ui.today.ProjectItem
import com.useconatus.app.ui.today.Queue
import com.useconatus.app.ui.today.TaskRow
import com.useconatus.app.ui.today.projectItems
import com.useconatus.app.ui.today.timezone
import com.useconatus.app.ui.today.toRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

data class TaskDetail(
    val loaded: Boolean = false,
    /** Null once the task is gone (deleted, or discarded before the server had it). */
    val task: TaskEntity? = null,
    val row: TaskRow? = null,
    /** Every open section, for the task's own and for moving it. */
    val sections: List<SectionEntity> = emptyList(),
    val subtasks: List<TaskRow> = emptyList(),
    val labels: List<LabelEntity> = emptyList(),
    /** The first change the server rejected, which the details screen leads with. */
    val failure: PendingChange? = null,
    /** Changed here and elsewhere: the details lead with both versions. */
    val conflict: Conflict? = null,
    val projects: List<ProjectItem> = emptyList(),
    /** Oldest first, as on the web. */
    val comments: List<CommentEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    /** The signed-in user, whose comments can be edited and deleted; null without a server. */
    val me: String? = null,
    /** The account's time zone, which reminders and comment times are shown in. */
    val zone: ZoneId = ZoneId.systemDefault(),
)

private data class Notes(val comments: List<CommentEntity>, val reminders: List<ReminderEntity>, val me: String?, val zone: ZoneId)

/** One task, for whichever details presentation the layout uses. Every edit goes through [AppContainer.writes]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModel(private val container: AppContainer, taskId: String) : ViewModel() {
    private val id = MutableStateFlow(taskId)
    private var last = TaskDetail()

    val state: StateFlow<TaskDetail> = id.flatMapLatest { taskId ->
        combine(
            container.db.tasks().observe(taskId),
            container.db.tasks().observeChildren(taskId),
            combine(container.projectItems(), container.db.sections().observeOpen(), ::Pair),
            combine(
                container.db.labels().observeAll(),
                combine(
                    container.db.comments().observeForTask(taskId),
                    container.db.reminders().observeForTask(taskId),
                    container.store.userId,
                    container.timezone().map { tz -> tz?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault() },
                    ::Notes,
                ),
                ::Pair,
            ),
            combine(container.db.tasks().observeSubtaskCounts(), container.db.changes().observeAll(), container.db.conflicts().observeAll(), ::Triple),
        ) { task, children, (projects, sections), (labels, notes), (subtasks, changes, conflicts) ->
            // The server just created it under its own id: follow it, and keep showing it meanwhile.
            if (task == null) container.outbox.renamedTo(taskId)?.let { renamed ->
                id.value = renamed
                return@combine last
            }
            val queue = Queue(changes, conflicts)
            val projectsById = projects.associateBy { it.id }
            val labelsById = labels.associateBy { it.id }
            val subtasksById: Map<String, SubtaskCount> = subtasks.associateBy { it.parentId }
            fun row(entity: TaskEntity) = entity.toRow(projectsById, labelsById, subtasksById, queue)
            TaskDetail(
                loaded = true,
                task = task,
                row = task?.let(::row),
                sections = sections,
                subtasks = children.sortedBy { it.order }.map(::row),
                labels = labels,
                failure = changes.firstOrNull { it.taskId == taskId && it.failure != null },
                conflict = conflicts.firstOrNull { it.taskId == taskId },
                projects = projects,
                comments = notes.comments,
                reminders = notes.reminders,
                me = notes.me,
                zone = notes.zone,
            ).also { last = it }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetail())

    init {
        viewModelScope.launch { container.sync.refreshNotes(taskId = taskId) }
    }

    private fun withTask(block: suspend (TaskEntity) -> Unit) = viewModelScope.launch {
        container.db.tasks().get(id.value)?.let { block(it) }
    }

    fun edit(edit: TaskEdit) = withTask { container.writes.update(it, edit) }

    fun move(projectId: String, sectionId: String?) = withTask { container.writes.move(it, projectId, sectionId) }

    fun addSubtask(text: String) = withTask { container.writes.quickAdd(text.trim(), it.projectId, it.sectionId, parentId = it.id) }

    fun setLabels(ids: List<String>) = withTask { container.writes.setLabels(it, ids) }

    fun reopen() = withTask { container.writes.reopen(it) }

    fun addComment(text: String) = withTask { container.notes.addComment(it.id, null, text.trim(), state.value.me) }

    fun editComment(comment: CommentEntity, text: String) = viewModelScope.launch { container.notes.editComment(comment, text.trim()) }

    fun deleteComment(comment: CommentEntity) = viewModelScope.launch { container.notes.deleteComment(comment) }

    fun addReminder(at: Long) = withTask { container.notes.addReminder(it.id, at) }

    fun deleteReminder(reminder: ReminderEntity) = viewModelScope.launch { container.notes.deleteReminder(reminder) }

    suspend fun complete(): Completion? = container.db.tasks().get(id.value)?.let { container.writes.complete(it) }

    suspend fun completeSubtask(id: String): Completion? = container.db.tasks().get(id)?.let { container.writes.complete(it) }

    fun reopenSubtask(id: String) = viewModelScope.launch { container.db.tasks().get(id)?.let { container.writes.reopen(it) } }

    fun undo(completion: Completion) = viewModelScope.launch { container.writes.undo(completion) }

    fun retry(change: PendingChange) = viewModelScope.launch {
        container.outbox.retry(change)
        container.sync.sync()
    }

    fun discard(change: PendingChange) = viewModelScope.launch { runCatching { container.discard(change) } }

    fun keepMine(conflict: Conflict) = viewModelScope.launch { container.keepMine(conflict) }

    fun keepTheirs(conflict: Conflict) = viewModelScope.launch { container.keepTheirs(conflict) }
}
