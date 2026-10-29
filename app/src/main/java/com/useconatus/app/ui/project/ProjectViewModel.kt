package com.useconatus.app.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.useconatus.app.AppContainer
import com.useconatus.app.data.db.LabelEntity
import com.useconatus.app.data.db.ProjectEntity
import com.useconatus.app.data.db.SectionEntity
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.data.prefs.Session
import com.useconatus.app.domain.keyBetween
import com.useconatus.app.domain.todayIn
import com.useconatus.app.ui.today.ProjectItem
import com.useconatus.app.ui.today.Queue
import com.useconatus.app.ui.today.TaskRow
import com.useconatus.app.ui.today.projectItems
import com.useconatus.app.ui.today.toRow
import com.useconatus.app.data.db.CommentEntity
import com.useconatus.app.ui.today.timezone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.LocalDate

/** What a list page shows: a project, or every open task with a label. */
sealed interface ListSource {
    data class Project(val id: String) : ListSource
    data class Label(val id: String) : ListSource
}

/** A task in a list, at its depth under its parents. */
data class TreeRow(val row: TaskRow, val task: TaskEntity, val depth: Int)

/** One section (null for the tasks above the first section) with its tasks as a tree. */
data class SectionGroup(val section: SectionEntity?, val rows: List<TreeRow>)

/** The list as the screen lays it out: section headings and tasks. The top group has no heading. */
sealed interface ListEntry {
    val key: String

    data class Heading(val section: SectionEntity, val count: Int) : ListEntry {
        override val key get() = "s-" + section.id
    }

    data class Task(val row: TreeRow) : ListEntry {
        override val key get() = row.task.id
    }
}

data class ListState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val project: ProjectEntity? = null,
    val label: LabelEntity? = null,
    val groups: List<SectionGroup> = emptyList(),
    val archivedSections: List<SectionEntity> = emptyList(),
    val board: Boolean = false,
    val projects: List<ProjectItem> = emptyList(),
    val allProjects: List<ProjectEntity> = emptyList(),
    val labels: List<LabelEntity> = emptyList(),
) {
    val title get() = project?.name ?: label?.name ?: ""

    val entries: List<ListEntry>
        get() = groups.flatMap { group ->
            listOfNotNull(group.section?.let { ListEntry.Heading(it, group.rows.size) }) + group.rows.map(ListEntry::Task)
        }
}

/** Tasks as a tree in project order. A sub-task whose parent isn't in [tasks] (completed, say) shows at the top level. */
fun treeOf(tasks: List<TaskEntity>): List<Pair<TaskEntity, Int>> {
    val ids = tasks.mapTo(HashSet()) { it.id }
    val children = tasks.groupBy { task -> task.parentId?.takeIf { it in ids } }
    val out = mutableListOf<Pair<TaskEntity, Int>>()
    fun visit(task: TaskEntity, depth: Int) {
        out += task to depth
        children[task.id].orEmpty().sortedBy { it.order }.forEach { visit(it, depth + 1) }
    }
    children[null].orEmpty().sortedBy { it.order }.forEach { visit(it, 0) }
    return out
}

/** Where a dragged top-level task lands: its section, the top-level task just above it there, and its new order key. */
data class Placement(val sectionId: String?, val after: String?, val order: String)

fun placement(entries: List<ListEntry>, taskId: String): Placement? {
    val index = entries.indexOfFirst { it is ListEntry.Task && it.row.task.id == taskId }
    if (index < 0) return null
    val section = entries.subList(0, index).filterIsInstance<ListEntry.Heading>().lastOrNull()?.section?.id
    fun rootAt(i: Int) = (entries[i] as? ListEntry.Task)?.row?.takeIf { it.depth == 0 }?.task
    var before: TaskEntity? = null
    for (i in index - 1 downTo 0) {
        if (entries[i] is ListEntry.Heading) break
        before = rootAt(i) ?: continue
        break
    }
    var next: TaskEntity? = null
    for (i in index + 1 until entries.size) {
        if (entries[i] is ListEntry.Heading) break
        next = rootAt(i) ?: continue
        break
    }
    return Placement(section, before?.id, keyBetween(before?.order, next?.order))
}

/** A project or label page, in any layout. Writes go through [AppContainer.writes] and [AppContainer.lists]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModel(private val container: AppContainer, private val source: ListSource) : ViewModel() {
    private val db = container.db
    private val sourceId = when (source) {
        is ListSource.Project -> source.id
        is ListSource.Label -> source.id
    }

    /** A list made on this phone takes the server's id once the server has it; the page follows it. */
    private fun currentId() = container.outbox.renamedTo(sourceId) ?: sourceId

    private val projectId get() = if (source is ListSource.Project) currentId() else null

    // The rename lands in the list's own table, so that's when to look the id up again.
    private val ids = (if (source is ListSource.Project) db.projects().observeAll() else db.labels().observeAll()).map { currentId() }.distinctUntilChanged()

    private val tasks = ids.flatMapLatest { id -> if (source is ListSource.Project) db.tasks().observeOpenInProject(id) else db.tasks().observeOpenWithLabel(id) }

    private val sections = if (source is ListSource.Project) ids.flatMapLatest { db.sections().observeInProject(it) } else flowOf(emptyList())

    private val lookups = combine(
        db.tasks().observeSubtaskCounts(),
        db.changes().observeAll(),
        db.conflicts().observeAll(),
        container.store.boardProjects,
        container.store.session.map { (it as? Session.SignedIn)?.timezone },
    ) { subtasks, changes, conflicts, board, timezone -> Lookups(subtasks.associateBy { it.parentId }, Queue(changes, conflicts), board, todayIn(timezone)) }

    val state: StateFlow<ListState> = combine(
        tasks,
        sections,
        combine(container.projectItems(), db.projects().observeAll(), db.labels().observeAll(), ::Triple),
        lookups,
    ) { tasks, sections, (projectItems, projects, labels), look ->
        val projectsById = projectItems.associateBy { it.id }
        val labelsById = labels.associateBy { it.id }
        fun tree(of: List<TaskEntity>) = treeOf(of).map { (task, depth) -> TreeRow(task.toRow(projectsById, labelsById, look.subtasks, look.queue), task, depth) }
        val open = sections.filter { !it.isArchived }
        val groups = if (source is ListSource.Label) {
            // A label gathers tasks from every project: by date, then as they come.
            listOf(SectionGroup(null, tasks.sortedWith(compareBy({ it.dueDate == null }, { it.dueDate }, { it.order })).map { TreeRow(it.toRow(projectsById, labelsById, look.subtasks, look.queue), it, 0) }))
        } else {
            val known = open.mapTo(HashSet()) { it.id }
            val archived = sections.filter { it.isArchived }.mapTo(HashSet()) { it.id }
            // Tasks in an archived section hide with it; those in a section the phone doesn't know go to the top.
            val visible = tasks.filter { it.sectionId !in archived }
            listOf(SectionGroup(null, tree(visible.filter { it.sectionId == null || it.sectionId !in known }))) +
                open.map { section -> SectionGroup(section, tree(visible.filter { it.sectionId == section.id })) }
        }
        ListState(
            loaded = true,
            today = look.today,
            project = projects.firstOrNull { it.id == projectId },
            label = if (source is ListSource.Label) labels.firstOrNull { it.id == currentId() } else null,
            groups = groups,
            archivedSections = sections.filter { it.isArchived },
            board = projectId != null && projectId in look.board,
            projects = projectItems,
            allProjects = projects,
            labels = labels,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState())

    private data class Lookups(
        val subtasks: Map<String, com.useconatus.app.data.db.SubtaskCount>,
        val queue: Queue,
        val board: Set<String>,
        val today: LocalDate,
    )

    fun quickAdd(text: String, sectionId: String? = null) = viewModelScope.launch {
        container.writes.quickAdd(text.trim(), projectId, sectionId)
    }

    fun place(taskId: String, at: Placement) = viewModelScope.launch {
        val task = db.tasks().get(taskId) ?: return@launch
        val siblings = db.tasks().inProject(task.projectId).filter { it.parentId == null && it.sectionId == at.sectionId && it.id != taskId }
        val before = siblings.filter { it.order < task.order }.maxByOrNull { it.order }
        // Dropped back where it was: nothing to send.
        if (task.sectionId == at.sectionId && task.parentId == null && before?.id == at.after) return@launch
        container.writes.place(task, at.sectionId, null, at.after, at.order)
    }

    fun setBoard(board: Boolean) = viewModelScope.launch { projectId?.let { container.store.setBoard(it, board) } }

    // A project's comments, for its Comments sheet. Labels have none.

    val comments: StateFlow<List<CommentEntity>> =
        (if (source is ListSource.Project) ids.flatMapLatest { db.comments().observeForProject(it) } else flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val me: StateFlow<String?> = container.store.userId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val zone: StateFlow<ZoneId> = container.timezone().map { tz -> tz?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZoneId.systemDefault())

    fun refreshComments() = viewModelScope.launch { projectId?.let { container.sync.refreshNotes(projectId = it) } }

    fun addComment(text: String) = viewModelScope.launch { projectId?.let { container.notes.addComment(null, it, text.trim(), me.value) } }

    fun editComment(comment: CommentEntity, text: String) = viewModelScope.launch { container.notes.editComment(comment, text.trim()) }

    fun deleteComment(comment: CommentEntity) = viewModelScope.launch { container.notes.deleteComment(comment) }

    // Project

    fun editProject(name: String, color: String, icon: String?, parentId: String?) = viewModelScope.launch {
        state.value.project?.let { container.lists.editProject(it, name, color, icon, parentId) }
    }

    fun favorite(favorite: Boolean) = viewModelScope.launch { state.value.project?.let { container.lists.favoriteProject(it, favorite) } }

    fun archive() = viewModelScope.launch { state.value.project?.let { container.lists.archiveProject(it, true) } }

    fun deleteProject() = viewModelScope.launch { state.value.project?.let { container.lists.deleteProject(it) } }

    // Sections

    fun addSection(name: String) = viewModelScope.launch { projectId?.let { container.lists.createSection(it, name.trim()) } }

    fun renameSection(section: SectionEntity, name: String) = viewModelScope.launch { container.lists.renameSection(section, name.trim()) }

    fun archiveSection(section: SectionEntity, archived: Boolean) = viewModelScope.launch { container.lists.archiveSection(section, archived) }

    fun deleteSection(section: SectionEntity) = viewModelScope.launch { container.lists.deleteSection(section) }

    /** Swaps a section with the one above or below it. */
    fun moveSection(section: SectionEntity, up: Boolean) = viewModelScope.launch {
        val sections = state.value.groups.mapNotNull { it.section }
        val i = sections.indexOfFirst { it.id == section.id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in sections.indices) return@launch
        // Above the one before the target, or below the target.
        val after = if (up) sections.getOrNull(j - 1) else sections[j]
        val next = if (up) sections[j] else sections.getOrNull(j + 1)
        container.lists.placeSection(section, after?.id, keyBetween(after?.order, next?.order))
    }

    // Label

    fun editLabel(name: String, color: String) = viewModelScope.launch { state.value.label?.let { container.lists.editLabel(it, name.trim(), color) } }

    fun deleteLabel() = viewModelScope.launch { state.value.label?.let { container.lists.deleteLabel(it) } }
}
