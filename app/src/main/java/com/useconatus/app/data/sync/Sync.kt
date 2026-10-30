package com.useconatus.app.data.sync

import androidx.room.withTransaction
import retrofit2.HttpException
import com.useconatus.app.data.net.CommentDto
import com.useconatus.app.data.db.ReminderEntity
import com.useconatus.app.data.db.CommentEntity
import com.useconatus.app.data.db.ChangeKind
import com.useconatus.app.data.db.Conflict
import com.useconatus.app.data.db.ConatusDatabase
import com.useconatus.app.data.db.LOCAL_ID_PREFIX
import com.useconatus.app.data.db.LabelEntity
import com.useconatus.app.data.db.ProjectEntity
import com.useconatus.app.data.db.SectionEntity
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.data.net.ApiProblem
import com.useconatus.app.data.net.ConatusApi
import com.useconatus.app.data.net.LabelDto
import com.useconatus.app.data.net.ProjectDto
import com.useconatus.app.data.net.ReminderDto
import com.useconatus.app.data.net.SectionDto
import com.useconatus.app.data.net.TaskDto
import com.useconatus.app.data.net.TaskPageDto
import com.useconatus.app.data.net.toApiProblem
import com.useconatus.app.data.prefs.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant

/** Every task that changed since the last sync, and the server time to ask from next time. */
data class TaskChanges(val items: List<TaskDto>, val serverTime: String)

/**
 * Pages through GET /api/v1/tasks. The next sync starts from the first page's serverTime: the
 * server reads its clock before querying, so a task edited while we page is newer than that
 * and comes back next time, even if it moved past our cursor.
 */
suspend fun fetchTaskChanges(page: suspend (cursor: String?) -> TaskPageDto): TaskChanges {
    val items = mutableListOf<TaskDto>()
    var cursor: String? = null
    var firstServerTime: String? = null
    do {
        val result = page(cursor)
        if (firstServerTime == null) firstServerTime = result.serverTime
        items += result.items
        cursor = result.nextCursor
    } while (cursor != null)
    return TaskChanges(items, firstServerTime!!)
}

/** What to write locally: the newest version of each task, split into live ones and ones now in Trash. */
data class TaskPlan(val upserts: List<TaskDto>, val deleted: List<TaskDto>) {
    val deletions get() = deleted.map { it.id }
}

fun planTaskChanges(items: List<TaskDto>): TaskPlan {
    val newest = items.groupBy { it.id }.values.map { versions -> versions.maxBy { Instant.parse(it.updatedAt) } }
    val (deleted, live) = newest.partition { it.deletedAt != null }
    return TaskPlan(live, deleted)
}

/**
 * True when a task's reminder notification is out of date because the server's version of the task
 * is completed, deleted or due at another time than the phone's.
 */
fun closedElsewhere(local: TaskEntity?, server: TaskDto): Boolean =
    local != null && !local.isCompleted && local.deletedAt == null &&
        (server.isCompleted || server.deletedAt != null || server.dueDate != local.dueDate || server.dueTime != local.dueTime)

/** What a list of the server's unseen reminders changes on the phone. */
data class ReminderPlan(val upserts: List<ReminderEntity>, val seen: List<String>, val deleted: List<String>)

/**
 * Merges the server's unseen reminders into the phone's [known] ones. New ones are added and moved
 * ones take the new time, ringing again when it's still to come. When [everyUnseen] (the list has
 * them all, not only the due ones), a reminder the phone has from the server but the list leaves
 * out was seen on another device, or deleted there if its time hasn't come. Reminders with a change
 * still [queued] keep the phone's version.
 */
fun planReminders(known: List<ReminderEntity>, listed: List<ReminderDto>, everyUnseen: Boolean, queued: Set<String>, now: Long): ReminderPlan {
    val mine = known.associateBy { it.id }
    val upserts = listed.filter { it.id !in queued }.mapNotNull { dto ->
        val at = instantToMillis(dto.remindAt)
        val local = mine[dto.id]
        if (local != null && local.remindAt == at) null
        else ReminderEntity(dto.id, dto.taskId, at, notified = local != null && local.notified && at <= now)
    }
    if (!everyUnseen) return ReminderPlan(upserts, emptyList(), emptyList())
    val ids = listed.mapTo(HashSet()) { it.id }
    val gone = known.filter { !it.notified && it.id !in ids && it.id !in queued && !it.id.startsWith(LOCAL_ID_PREFIX) }
    val (seen, deleted) = gone.partition { it.remindAt <= now }
    return ReminderPlan(upserts, seen.map { it.id }, deleted.map { it.id })
}

fun TaskDto.toEntity() = TaskEntity(
    id = id, projectId = projectId, sectionId = sectionId, parentId = parentId,
    content = content, description = description.orEmpty(), priority = priority,
    dueDate = dueDate, dueTime = dueTime, deadlineDate = deadlineDate, recurrence = recurrence,
    durationMinutes = durationMinutes, isCompleted = isCompleted, order = order, updatedAt = updatedAt,
    labelIds = labels.map { it.id }, commentCount = commentCount,
    completedAt = completedAt, deletedAt = deletedAt, recurrenceEndDate = recurrenceEndDate,
)

fun ProjectDto.toEntity() = ProjectEntity(id, parentId, name, color, isFavorite, isInbox, order, shared, isArchived, icon = icon)

fun SectionDto.toEntity() = SectionEntity(id, projectId, name, order, isArchived)

fun LabelDto.toEntity() = LabelEntity(id, name, color, isFavorite, order)

fun CommentDto.toEntity() = CommentEntity(id, taskId, projectId, content, createdAt, updatedAt, userId)

/** The server stopped accepting the device token while the queue was going out. */
class SignedOutException : Exception("The server didn't accept this sign-in.")

sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Brings Room and the server together, silently. It pulls first and merges the server's changes
 * with the ones queued on the phone (see [merge]), then sends the queue. Tasks come as deltas;
 * projects and labels have no delta on purpose (losing access to a shared project wouldn't show
 * up in one), so they're fetched whole and replace the local copy.
 */
class SyncRepository(
    private val db: ConatusDatabase,
    private val outbox: Outbox,
    private val store: SettingsStore,
    private val api: suspend () -> ConatusApi,
    /** Clears the reminder notifications of these tasks. */
    private val dismissReminders: (Collection<String>) -> Unit,
    private val onRevoked: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /**
     * Comments and reminders are only listed per task (or project), so details fetch them when
     * they open. Offline, without a server or without access, the phone's copy stays as it is.
     */
    suspend fun refreshNotes(taskId: String? = null, projectId: String? = null) {
        val scope = taskId ?: projectId ?: return
        if (store.token() == null || scope.startsWith(LOCAL_ID_PREFIX)) return
        try {
            val api = api()
            val comments = if (taskId != null) api.comments(taskId = taskId) else api.comments(projectId = projectId)
            val reminders = taskId?.let { api.reminders(it) }
            db.withTransaction {
                val queued = db.changes().all().mapTo(HashSet()) { it.taskId }
                db.comments().upsert(comments.filter { it.id !in queued }.map { it.toEntity() })
                db.comments().deleteOthers(scope, comments.map { it.id })
                if (taskId == null || reminders == null) return@withTransaction
                db.tasks().setCommentCount(taskId, db.comments().countForTask(taskId))
                val known = db.reminders().forTask(taskId).associateBy { it.id }
                val now = System.currentTimeMillis()
                db.reminders().upsert(
                    reminders.filter { it.id !in queued }.map { dto ->
                        val at = instantToMillis(dto.remindAt)
                        // One whose time passed before the phone knew of it isn't shown late.
                        ReminderEntity(dto.id, taskId, at, notified = known[dto.id]?.notified ?: (at <= now))
                    },
                )
                db.reminders().deleteOthers(taskId, reminders.map { it.id })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: HttpException) {
        } catch (_: IOException) {
        }
    }

    /** Null when everything synced, otherwise what went wrong. Concurrent calls wait for each other. */
    suspend fun sync(): ApiProblem? = mutex.withLock {
        // Without a server the phone is the only copy and there's nothing to do.
        if (store.token() == null) return@withLock null
        _state.value = SyncState.Running
        val problem = try {
            run()
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SignedOutException) {
            ApiProblem.Unauthorized("The server didn't accept this sign-in.")
        } catch (error: Throwable) {
            error.toApiProblem()
        }
        if (problem is ApiProblem.Unauthorized) onRevoked()
        _state.value = problem?.let { SyncState.Failed(it.message) } ?: SyncState.Idle
        problem
    }

    private suspend fun run() {
        val api = api()
        val context = api.context()
        store.updateUser(context.user.id, context.user.username, context.user.name, context.user.timezone, context.user.weekStart, context.user.emailReminders)
        val projects = api.projects()
        val sections = api.sections()
        val labels = api.labels()
        // A project the phone hasn't seen (shared with you, or unarchived elsewhere) brings tasks
        // that never showed up in a delta, so those syncs fetch every task again.
        val known = db.projects().all().filter { !it.isArchived && it.deletedAt == null }.mapTo(HashSet()) { it.id }
        val since = store.tasksUpdatedSince()?.takeIf { projects.all { it.id in known || it.isInbox } }
        val changes = fetchTaskChanges { cursor -> api.tasks(since, cursor) }
        val plan = planTaskChanges(changes.items)
        val projectIds = projects.map { it.id }
        val closed = mutableListOf<String>()
        db.withTransaction {
            val serverInbox = projects.firstOrNull { it.isInbox }
            db.projects().inbox()?.takeIf { serverInbox != null && it.id.startsWith(LOCAL_ID_PREFIX) }?.let { outbox.adoptProject(it.id, serverInbox!!.id) }
            // Read inside the transaction, so a write made meanwhile is merged, not overwritten.
            val queued = db.changes().all().groupBy { it.taskId }
            // Projects, sections and labels with changes still queued keep the phone's version until they're through.
            db.projects().upsert(projects.filter { it.id !in queued }.map { it.toEntity() })
            db.projects().deleteOthers(projectIds)
            db.sections().upsert(sections.filter { it.id !in queued }.map { it.toEntity() })
            db.sections().deleteOthers(sections.map { it.id })
            db.labels().upsert(labels.filter { it.id !in queued }.map { it.toEntity() })
            db.labels().deleteOthers(labels.map { it.id })
            for (dto in plan.upserts) {
                val mine = queued[dto.id]
                if (mine == null) {
                    if (closedElsewhere(db.tasks().get(dto.id), dto)) closed += dto.id
                    db.tasks().upsert(listOf(dto.toEntity()))
                    continue
                }
                // Deleted on the phone and not sent yet: the delete goes out below.
                val local = db.tasks().get(dto.id)?.takeIf { it.deletedAt == null } ?: continue
                val merged = merge(local, dto.toEntity(), mine)
                db.tasks().upsert(listOf(merged.task))
                if (merged.conflicts.isEmpty()) db.conflicts().delete(dto.id) else db.conflicts().upsert(Conflict(dto.id, merged.conflicts.toString()))
            }
            // Deleted tasks stay on the phone, in Trash, as on the server.
            for (dto in plan.deleted) {
                val id = dto.id
                val mine = queued[id]
                when {
                    mine == null -> {
                        if (closedElsewhere(db.tasks().get(id), dto)) closed += id
                        db.tasks().upsert(listOf(dto.toEntity()))
                    }
                    // Restored here: the restore goes out below and brings it back there too.
                    mine.any { it.kind == ChangeKind.Restore } -> db.conflicts().delete(id)
                    // Deleted on both sides: nothing left to send.
                    mine.any { it.kind == ChangeKind.Delete } -> {
                        db.changes().deleteFrom(id, 0)
                        db.tasks().upsert(listOf(dto.toEntity()))
                    }
                    // Changed here, deleted there: the user decides.
                    else -> db.conflicts().upsert(Conflict(id, "{}", deleted = true))
                }
            }
            db.tasks().deleteOrphans()
        }
        dismissReminders(closed)
        // Every unseen reminder, so one set on another device rings here at its time. A server
        // without that list only has the due ones, which arrive at the first sync after their time.
        val pending = try {
            api.pendingReminders()
        } catch (_: HttpException) {
            null
        }
        val listed = pending ?: try {
            api.dueReminders()
        } catch (_: HttpException) {
            emptyList()
        }
        db.withTransaction {
            val queued = db.changes().all().mapTo(HashSet()) { it.taskId }
            val plan = planReminders(db.reminders().all(), listed, pending != null, queued, System.currentTimeMillis())
            db.reminders().upsert(plan.upserts.filter { db.tasks().get(it.taskId) != null })
            plan.seen.forEach { db.reminders().setNotified(it) }
            plan.deleted.forEach { db.reminders().delete(it) }
        }
        store.recordSync(changes.serverTime, System.currentTimeMillis())
        when (outbox.push(api)) {
            PushResult.Done -> {}
            PushResult.SignedOut -> throw SignedOutException()
            PushResult.TryLater -> throw IOException("Some changes couldn't be sent yet")
        }
    }
}
