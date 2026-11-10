package com.useconatus.app.data.sync

import androidx.room.withTransaction
import com.useconatus.app.data.db.ChangeKind
import com.useconatus.app.data.db.Conflict
import com.useconatus.app.data.db.ConatusDatabase
import com.useconatus.app.data.db.LOCAL_ID_PREFIX
import com.useconatus.app.data.db.PendingChange
import com.useconatus.app.data.db.ProjectEntity
import com.useconatus.app.data.db.Target
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.data.net.ConatusApi
import com.useconatus.app.data.net.errorMessageFrom
import com.useconatus.app.domain.keyAfter
import com.useconatus.app.domain.nextOccurrenceWithinEnd
import com.useconatus.app.domain.parseQuickAdd
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What happened when a queued change went to the server. */
sealed interface SendOutcome {
    /** Applied. For a create, [createdId] is the id the server gave it. */
    data class Sent(val createdId: String? = null) : SendOutcome

    /** The server won't apply it. It waits with [reason] until the user retries or discards it. */
    data class Rejected(val reason: String) : SendOutcome

    /** Offline, or the server is struggling: stop and try the whole queue again later. */
    data object TryLater : SendOutcome

    /** The device token no longer works. */
    data object SignedOut : SendOutcome
}

/** How the queue treats an HTTP error from the server. */
fun outcomeFor(kind: ChangeKind, status: Int, serverMessage: String?, hasRetryAfter: Boolean): SendOutcome = when {
    status == 401 -> SendOutcome.SignedOut
    // A delete of a task that's already gone did what it set out to do.
    status == 404 && kind == ChangeKind.Delete -> SendOutcome.Sent()
    status == 409 && hasRetryAfter -> SendOutcome.TryLater // the same request is still running on the server
    status == 408 || status == 429 || status >= 500 -> SendOutcome.TryLater
    status == 404 && kind == ChangeKind.Create -> SendOutcome.Rejected("The server couldn't find the project or task this goes in.")
    status == 404 -> SendOutcome.Rejected("It no longer exists on the server. It may have been deleted on another device.")
    status == 403 -> SendOutcome.Rejected(serverMessage ?: "You no longer have access to its project.")
    else -> SendOutcome.Rejected(serverMessage ?: "The server rejected this change (error $status).")
}

/**
 * The next change to send, in order. Anything with a rejected change or a conflict holds back its
 * changes. Changes to something the server hasn't created yet wait for its create, and so does a
 * change that names it, such as a task created in a project made on the phone.
 */
fun nextToSend(changes: List<PendingChange>, conflicted: Set<String> = emptySet()): PendingChange? {
    val held = conflicted.toMutableSet()
    for (change in changes.sortedBy { it.seq }) {
        when {
            change.taskId in held -> {}
            change.failure != null -> held += change.taskId
            change.taskId.startsWith(LOCAL_ID_PREFIX) && change.kind != ChangeKind.Create -> held += change.taskId
            "\"$LOCAL_ID_PREFIX" in change.body -> held += change.taskId
            else -> return change
        }
    }
    return null
}

enum class PushResult { Done, TryLater, SignedOut }

/** Field changes a user can make in task details. Only the fields that are set are sent. */
data class TaskEdit(
    val content: String? = null,
    val description: String? = null,
    val priority: Int? = null,
    val dueDate: Field<String>? = null,
    val dueTime: Field<String>? = null,
    val deadlineDate: Field<String>? = null,
    val durationMinutes: Field<Int>? = null,
    val recurrence: Field<String>? = null,
) {
    /** A field being set to a value or cleared, as opposed to left alone (null). */
    data class Field<T>(val value: T?)

    fun toJson(): JsonObject = buildJsonObject {
        content?.let { put("content", it) }
        description?.let { put("description", it) }
        priority?.let { put("priority", it) }
        dueDate?.let { put("dueDate", it.value?.let(::JsonPrimitive) ?: JsonNull) }
        dueTime?.let { put("dueTime", it.value?.let(::JsonPrimitive) ?: JsonNull) }
        deadlineDate?.let { put("deadlineDate", it.value?.let(::JsonPrimitive) ?: JsonNull) }
        durationMinutes?.let { put("durationMinutes", it.value?.let(::JsonPrimitive) ?: JsonNull) }
        recurrence?.let { put("recurrence", it.value?.let(::JsonPrimitive) ?: JsonNull) }
    }

    fun applyTo(task: TaskEntity): TaskEntity = task.withFields(toJson())

    companion object {
        /** Clearing the date also clears its time and repeat, which the server requires. */
        val noDate = TaskEdit(dueDate = Field(null), dueTime = Field(null), recurrence = Field(null))
    }
}

/**
 * A deleted task that can still be brought back, with its sub-tasks and the queued changes that
 * went with it (those of tasks the server never had).
 */
data class Deletion(val task: TaskEntity, val seq: Long?, val dropped: List<PendingChange> = emptyList(), val descendants: List<TaskEntity> = emptyList())

/** A completed task that can still be undone. [seq] is its queued change; [cascaded] the sub-tasks it completed. */
data class Completion(val taskId: String, val seq: Long?, val before: TaskEntity, val cascaded: List<String> = emptyList())

/** The Inbox, made on the phone when there's none yet (no server, or not synced yet). */
suspend fun ConatusDatabase.inbox(): ProjectEntity = projects().inbox()
    ?: ProjectEntity(LOCAL_ID_PREFIX + "inbox", null, "Inbox", "gray", isFavorite = false, isInbox = true, order = "", shared = false)
        .also { projects().upsert(listOf(it)) }

/**
 * The order tasks made on the phone go to the server: what to create, then what to complete. All
 * the creates go first, parents before their sub-tasks so a sub-task's create can name its parent's
 * server id, and each level in the phone's order, which the server's creates then keep. The server
 * won't add a sub-task under a completed parent, so completions wait, and go sub-tasks first.
 */
fun uploadOrder(tasks: List<TaskEntity>): Pair<List<TaskEntity>, List<TaskEntity>> {
    val byId = tasks.associateBy { it.id }
    fun depth(task: TaskEntity): Int = task.parentId?.let(byId::get)?.let { depth(it) + 1 } ?: 0
    val creates = tasks.sortedWith(compareBy({ depth(it) }, { it.order }))
    return creates to creates.filter { it.isCompleted }.asReversed()
}

/** Every task under [id], however deep. */
suspend fun ConatusDatabase.descendants(id: String): List<TaskEntity> {
    val out = mutableListOf<TaskEntity>()
    var level = tasks().children(id)
    while (level.isNotEmpty()) {
        out += level
        level = level.flatMap { tasks().children(it.id) }
    }
    return out
}

/** The order key after the last task directly under [parentId], or at the top of [sectionId] in [projectId]. */
private suspend fun ConatusDatabase.orderAtEnd(projectId: String, sectionId: String?, parentId: String?): String =
    keyAfter(tasks().inProject(projectId).filter { it.parentId == parentId && (parentId != null || it.sectionId == sectionId) }.map { it.order })

/**
 * Every write: Room changes at once, so the app works the same with or without a server. While a
 * server is [connected], the change also goes into the queue, with the values it replaced, and
 * [requestSync] sends it as soon as there's a network.
 */
class TaskWrites(
    private val db: ConatusDatabase,
    private val connected: suspend () -> Boolean,
    private val today: suspend () -> LocalDate,
    private val requestSync: () -> Unit,
    /** Clears the reminder notifications of these tasks, once the phone completes, reschedules or deletes them. */
    private val dismissReminders: (Collection<String>) -> Unit = {},
    /** Quick add's "remind me …": the new task and the wall-clock time it read. */
    private val remind: suspend (TaskEntity, String) -> Unit = { _, _ -> },
) {
    private suspend fun write(
        task: TaskEntity,
        kind: ChangeKind,
        body: JsonObject,
        summary: String,
        base: JsonObject? = null,
        local: suspend () -> Unit,
    ): Long? {
        val queue = connected()
        val seq = db.withTransaction {
            local()
            if (queue) db.changes().add(PendingChange(taskId = task.id, kind = kind, body = body.toString(), summary = summary, base = base?.toString())) else null
        }
        if (queue) requestSync()
        return seq
    }

    /** A repeating task moves on to its next date here, exactly as the server will move it. */
    suspend fun complete(task: TaskEntity): Completion {
        // Past its end date a repeating task is done for good, as on the server.
        val next = if (task.recurrence != null && task.dueDate != null) {
            runCatching { nextOccurrenceWithinEnd(task.recurrence, task.dueDate, today().toString(), task.recurrenceEndDate) }.getOrNull()
        } else {
            null
        }
        val now = Instant.now().toString()
        val done = if (next != null) task.copy(dueDate = next) else task.copy(isCompleted = true, completedAt = now)
        // Completing a task completes its open sub-tasks too, as on the server.
        val cascaded = if (next == null) db.descendants(task.id).filter { !it.isCompleted } else emptyList()
        val base = task.baseFor(if (next != null) listOf("completed", "dueDate") else listOf("completed"))
        val seq = write(task, ChangeKind.Complete, buildJsonObject { put("completed", true) }, "Complete “${task.content}”", base) {
            db.tasks().upsert(listOf(done) + cascaded.map { it.copy(isCompleted = true, completedAt = now) })
        }
        dismissReminders(cascaded.map { it.id } + task.id)
        return Completion(task.id, seq, task, cascaded.map { it.id })
    }

    suspend fun undo(completion: Completion) {
        val task = db.tasks().get(completion.taskId) ?: return
        val before = completion.before
        val seq = completion.seq
        if (seq != null && db.changes().forTask(task.id).any { it.seq == seq }) {
            db.withTransaction {
                db.changes().delete(seq)
                db.tasks().upsert(listOf(task.copy(isCompleted = before.isCompleted, completedAt = before.completedAt, dueDate = before.dueDate)))
                reopenLocally(completion.cascaded)
            }
        } else if (before.recurrence != null && !task.isCompleted) {
            // It moved on to its next date: move it back.
            update(task, TaskEdit(dueDate = TaskEdit.Field(before.dueDate)))
        } else {
            reopen(task)
        }
    }

    private suspend fun reopenLocally(ids: Collection<String>) =
        db.tasks().upsert(ids.mapNotNull { db.tasks().get(it) }.map { it.copy(isCompleted = false, completedAt = null) })

    suspend fun reopen(task: TaskEntity) {
        // The server reopens the sub-tasks completed with it and any completed parents; so does the phone.
        val alongside = db.descendants(task.id).filter { it.isCompleted }.map { it.id } + completedAncestors(task)
        // A completion that hasn't gone out yet is simply taken back.
        val queued = db.changes().forTask(task.id).lastOrNull { it.kind == ChangeKind.Complete && it.failure == null }
        if (queued != null) {
            db.withTransaction {
                db.changes().delete(queued.seq)
                db.tasks().upsert(listOf(task.withFields(jsonObjectOf(queued.base))))
                reopenLocally(alongside)
            }
            return
        }
        write(task, ChangeKind.Reopen, buildJsonObject { put("completed", false) }, "Reopen “${task.content}”", task.baseFor(listOf("completed"))) {
            db.tasks().upsert(listOf(task.copy(isCompleted = false, completedAt = null)))
            reopenLocally(alongside)
        }
    }

    private suspend fun completedAncestors(task: TaskEntity): List<String> {
        val out = mutableListOf<String>()
        var parentId = task.parentId
        while (parentId != null && parentId !in out) {
            val parent = db.tasks().get(parentId) ?: break
            if (parent.isCompleted) out += parent.id
            parentId = parent.parentId
        }
        return out
    }

    /**
     * Moves a task to another project or section, at the end. As on the server, a sub-task moved
     * on its own becomes a top-level task there, and its own sub-tasks come along.
     */
    suspend fun move(task: TaskEntity, projectId: String, sectionId: String?) {
        if (projectId == task.projectId && sectionId == task.sectionId) return
        val body = buildJsonObject {
            put("projectId", projectId)
            put("sectionId", sectionId)
        }
        val descendants = db.descendants(task.id)
        val order = db.orderAtEnd(projectId, sectionId, null)
        write(task, ChangeKind.Update, body, "Move “${task.content}”", task.baseFor(listOf("projectId", "sectionId", "parentId"))) {
            db.tasks().upsert(
                listOf(task.copy(projectId = projectId, sectionId = sectionId, parentId = null, order = order)) +
                    descendants.map { it.copy(projectId = projectId, sectionId = sectionId) },
            )
        }
    }

    /**
     * Drag and drop: puts a task right after [after] (or first, when null) among the tasks in
     * [sectionId] under [parentId], with [order] the key it takes on the phone. Its sub-tasks follow it.
     */
    suspend fun place(task: TaskEntity, sectionId: String?, parentId: String?, after: String?, order: String) {
        val body = buildJsonObject {
            put("sectionId", sectionId)
            put("parentId", parentId)
            put("afterId", after)
        }
        val descendants = db.descendants(task.id)
        write(task, ChangeKind.Place, body, "Move “${task.content}”", task.baseFor(listOf("sectionId", "parentId"))) {
            db.tasks().upsert(listOf(task.copy(sectionId = sectionId, parentId = parentId, order = order)) + descendants.map { it.copy(sectionId = sectionId) })
        }
    }

    suspend fun update(task: TaskEntity, edit: TaskEdit) {
        val body = edit.toJson()
        write(task, ChangeKind.Update, body, "Edit “${task.content}”", task.baseFor(body.keys)) {
            db.tasks().upsert(listOf(edit.applyTo(task)))
        }
        if (edit.dueDate != null || edit.dueTime != null) dismissReminders(listOf(task.id))
    }

    suspend fun setLabels(task: TaskEntity, labelIds: List<String>) {
        val body = buildJsonObject { put("labelIds", JsonArray(labelIds.map(::JsonPrimitive))) }
        write(task, ChangeKind.SetLabels, body, "Change the labels of “${task.content}”", task.baseFor(listOf("labelIds"))) {
            db.tasks().upsert(listOf(task.copy(labelIds = labelIds)))
        }
    }

    /** Moves a task and its sub-tasks to Trash, as the server does. */
    suspend fun delete(task: TaskEntity): Deletion {
        val descendants = db.descendants(task.id)
        dismissReminders(descendants.map { it.id } + task.id)
        // Queued changes to tasks the server never had are dropped: nothing needs sending.
        val unsent = (listOf(task) + descendants).filter { it.id.startsWith(LOCAL_ID_PREFIX) }
        val now = Instant.now().toString()
        val dropped = mutableListOf<PendingChange>()
        val dropUnsent = suspend {
            unsent.forEach { dropped += db.changes().forTask(it.id); db.changes().deleteFrom(it.id, 0) }
            db.tasks().upsert((listOf(task) + descendants).map { it.copy(deletedAt = now) })
        }
        if (task.id.startsWith(LOCAL_ID_PREFIX)) {
            db.withTransaction { dropUnsent() }
            return Deletion(task, null, dropped, descendants)
        }
        val seq = write(task, ChangeKind.Delete, JsonObject(emptyMap()), "Delete “${task.content}”") { dropUnsent() }
        return Deletion(task, seq, dropped, descendants)
    }

    /** Takes back a delete that's still queued, or restores the task from the server's Trash. */
    suspend fun undo(deletion: Deletion) {
        val (task, seq, dropped, descendants) = deletion
        val putBack = suspend {
            db.tasks().upsert(listOf(task) + descendants)
            dropped.forEach { db.changes().add(it) }
        }
        when {
            seq == null -> db.withTransaction { putBack() }
            db.changes().forTask(task.id).any { it.seq == seq } -> db.withTransaction {
                db.changes().delete(seq)
                putBack()
            }
            else -> write(task, ChangeKind.Restore, JsonObject(emptyMap()), "Restore “${task.content}”") { putBack() }
        }
        if (dropped.isNotEmpty()) requestSync()
    }

    /**
     * Brings a task back from Trash with the sub-tasks that went with it. One the server never had
     * is created again, since its create was dropped when it was deleted.
     */
    suspend fun restore(task: TaskEntity) {
        val subtree = mutableListOf<TaskEntity>()
        var level = db.tasks().deletedChildren(task.id)
        while (level.isNotEmpty()) {
            subtree += level
            level = level.flatMap { db.tasks().deletedChildren(it.id) }
        }
        val back = (listOf(task) + subtree).map { it.copy(deletedAt = null) }
        if (task.id.startsWith(LOCAL_ID_PREFIX)) {
            db.tasks().upsert(back)
            create(back, insert = false)
            return
        }
        write(task, ChangeKind.Restore, JsonObject(emptyMap()), "Restore “${task.content}”") { db.tasks().upsert(back) }
    }

    /**
     * Reads [text] as the server would and adds the task at the end of where it goes: the project
     * it names, else [projectId] and [sectionId] (or under [parentId], for a sub-task), else the Inbox.
     */
    suspend fun quickAdd(text: String, projectId: String? = null, sectionId: String? = null, parentId: String? = null): TaskEntity {
        val projects = db.projects().all()
        val labels = db.labels().all()
        val parsed = parseQuickAdd(text, today().toString(), projects.map { it.name }, labels.map { it.name })
        val named = parsed.projectName?.let { name -> projects.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        val project = named ?: projects.firstOrNull { it.id == projectId } ?: db.inbox()
        // Naming another project takes the task out of the section or parent it was added from.
        val here = project.id == projectId
        val section = sectionId.takeIf { here }
        val parent = parentId.takeIf { here }
        val task = TaskEntity(
            id = LOCAL_ID_PREFIX + UUID.randomUUID(), projectId = project.id, sectionId = section, parentId = parent,
            content = parsed.content, description = "", priority = parsed.priority, dueDate = parsed.dueDate, dueTime = parsed.dueTime,
            deadlineDate = parsed.deadlineDate, recurrence = parsed.recurrence, durationMinutes = parsed.durationMinutes,
            isCompleted = false, order = db.orderAtEnd(project.id, section, parent), updatedAt = "",
            labelIds = parsed.labelNames.mapNotNull { name -> labels.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id },
            commentCount = 0,
        )
        create(listOf(task), insert = true)
        parsed.reminderAt?.let { remind(task, it) }
        return task
    }

    /** Sends the tasks made on this phone before it was connected to the server. */
    suspend fun upload() = create(db.tasks().unsent(), insert = false)

    /** Or removes them from the phone, when they shouldn't go into the account. */
    suspend fun dropUnsent() = db.tasks().delete(db.tasks().unsent().map { it.id })

    private suspend fun create(tasks: List<TaskEntity>, insert: Boolean) {
        val queue = connected()
        db.withTransaction {
            if (insert) db.tasks().upsert(tasks)
            if (!queue) return@withTransaction
            val (creates, completes) = uploadOrder(tasks)
            for (task in creates) {
                val body = buildJsonObject {
                    put("projectId", task.projectId)
                    task.parentId?.let { put("parentId", it) }
                    task.sectionId?.let { put("sectionId", it) }
                    put("content", task.content)
                    if (task.description.isNotEmpty()) put("description", task.description)
                    put("priority", task.priority)
                    task.dueDate?.let { put("dueDate", it) }
                    task.dueTime?.let { put("dueTime", it) }
                    task.recurrence?.let { put("recurrence", it) }
                    task.deadlineDate?.let { put("deadlineDate", it) }
                    task.durationMinutes?.let { put("durationMinutes", it) }
                }
                db.changes().add(PendingChange(taskId = task.id, kind = ChangeKind.Create, body = body.toString(), summary = "Add “${task.content}”", idempotencyKey = UUID.randomUUID().toString()))
                // The server creates a task without labels and open, so those follow as changes of their own.
                if (task.labelIds.isNotEmpty()) {
                    val labels = buildJsonObject { put("labelIds", JsonArray(task.labelIds.map(::JsonPrimitive))) }
                    db.changes().add(PendingChange(taskId = task.id, kind = ChangeKind.SetLabels, body = labels.toString(), summary = "Label “${task.content}”", base = task.copy(labelIds = emptyList()).baseFor(listOf("labelIds")).toString()))
                }
            }
            for (task in completes) {
                db.changes().add(PendingChange(taskId = task.id, kind = ChangeKind.Complete, body = """{"completed":true}""", summary = "Complete “${task.content}”", base = """{"completed":false}"""))
            }
        }
        if (queue) requestSync()
    }
}

/** Sends queued changes in order, handles the server's answers and settles conflicts. */
class Outbox(private val db: ConatusDatabase) {
    private val aliases = ConcurrentHashMap<String, String>()

    /** The server id a task made on this phone took, so an open screen can follow it. */
    fun renamedTo(localId: String): String? = aliases[localId]

    suspend fun push(api: ConatusApi): PushResult {
        while (true) {
            val conflicted = db.conflicts().all().mapTo(HashSet()) { it.taskId }
            val change = nextToSend(db.changes().all(), conflicted) ?: return PushResult.Done
            when (val outcome = send(api, change)) {
                is SendOutcome.Sent -> db.withTransaction {
                    db.changes().delete(change.seq)
                    outcome.createdId?.let { adopt(change.target, change.taskId, it) }
                }
                is SendOutcome.Rejected -> db.changes().setFailure(change.seq, outcome.reason)
                SendOutcome.TryLater -> return PushResult.TryLater
                SendOutcome.SignedOut -> return PushResult.SignedOut
            }
        }
    }

    suspend fun retry(change: PendingChange) = db.changes().setFailure(change.seq, null)

    /**
     * Drops a rejected change and everything queued after it for that task, then puts the
     * server's version of the task back (or removes it, if the server doesn't have it).
     */
    suspend fun discard(change: PendingChange, api: ConatusApi) {
        db.changes().deleteFrom(change.taskId, change.seq)
        val local = change.taskId.startsWith(LOCAL_ID_PREFIX)
        when (change.target) {
            Target.Task -> if (local) {
                db.tasks().delete(listOf(change.taskId))
                return
            }
            // The next sync replaces the whole list, now that nothing queued holds this one back.
            Target.Project -> if (local) db.projects().delete(change.taskId)
            Target.Section -> if (local) db.sections().delete(listOf(change.taskId))
            Target.Label -> if (local) db.labels().delete(change.taskId)
            // Task details fetch a task's comments and reminders again when they open.
            Target.Comment -> if (local) db.comments().delete(change.taskId)
            Target.Reminder -> if (local) db.reminders().delete(change.taskId)
        }
        if (change.target != Target.Task) return
        try {
            db.tasks().upsert(listOf(api.task(change.taskId).toEntity()))
        } catch (error: HttpException) {
            if (error.code() == 404 || error.code() == 403) db.tasks().delete(listOf(change.taskId)) else throw error
        }
    }

    /** Keeps this phone's version: its changes go out and replace the other device's. */
    suspend fun keepMine(conflict: Conflict) = db.withTransaction {
        val mine = db.changes().forTask(conflict.taskId)
        if (conflict.deleted) {
            // Bring the task back from Trash first, then send the phone's changes after it.
            mine.forEach { db.changes().delete(it.seq) }
            db.changes().add(PendingChange(taskId = conflict.taskId, kind = ChangeKind.Restore, body = "{}", summary = "Restore a task"))
            // A restore already queued from Trash is the one above; sending it twice fails the second.
            mine.filter { it.kind != ChangeKind.Restore }.forEach { db.changes().add(it.copy(seq = 0)) }
        } else {
            // The other device's values become the base, so the next sync sees no conflict.
            val theirs = jsonObjectOf(conflict.theirs)
            for (change in mine) {
                val base = jsonObjectOf(change.base)
                if (base.keys.none { it in theirs }) continue
                db.changes().update(change.copy(base = JsonObject(base + theirs.filterKeys { it in base }).toString()))
            }
        }
        db.conflicts().delete(conflict.taskId)
    }

    /** Takes the other device's version of the fields both changed, and drops the phone's changes to them. */
    suspend fun keepTheirs(conflict: Conflict) = db.withTransaction {
        val mine = db.changes().forTask(conflict.taskId)
        if (conflict.deleted) {
            mine.forEach { db.changes().delete(it.seq) }
            db.tasks().delete(listOf(conflict.taskId))
        } else {
            val theirs = jsonObjectOf(conflict.theirs)
            for (change in mine) {
                val body = jsonObjectOf(change.body)
                val base = jsonObjectOf(change.base)
                if ((body.keys + base.keys).none { it in theirs }) continue
                val rest = body - theirs.keys
                // A completion or a labels change is one act: it goes as a whole.
                if (change.kind == ChangeKind.Update && rest.isNotEmpty()) {
                    db.changes().update(change.copy(body = JsonObject(rest).toString(), base = JsonObject(base - theirs.keys).toString()))
                } else {
                    db.changes().delete(change.seq)
                }
            }
            db.tasks().get(conflict.taskId)?.let { db.tasks().upsert(listOf(it.withFields(theirs))) }
        }
        db.conflicts().delete(conflict.taskId)
    }

    /** The Inbox made on the phone before a server was connected becomes the account's Inbox. */
    suspend fun adoptProject(localId: String, serverId: String) {
        db.tasks().moveProject(localId, serverId)
        db.sections().moveProject(localId, serverId)
        db.comments().renameProject(localId, serverId)
        db.changes().replaceInBodies("\"$localId\"", "\"$serverId\"")
        db.projects().delete(localId)
    }

    /**
     * What the phone made now has the server's id: it's renamed everywhere it's named, keeping
     * what the phone shows, and changes queued after the create follow it.
     */
    private suspend fun adopt(target: Target, localId: String, serverId: String) {
        aliases[localId] = serverId
        when (target) {
            Target.Task -> {
                db.tasks().renameId(localId, serverId)
                db.tasks().renameParent(localId, serverId)
                db.comments().renameTask(localId, serverId)
                db.reminders().renameTask(localId, serverId)
            }
            Target.Project -> {
                db.projects().renameId(localId, serverId)
                db.projects().renameParent(localId, serverId)
                db.tasks().moveProject(localId, serverId)
                db.sections().moveProject(localId, serverId)
                db.comments().renameProject(localId, serverId)
            }
            Target.Section -> {
                db.sections().renameId(localId, serverId)
                db.tasks().renameSection(localId, serverId)
            }
            Target.Label -> {
                db.labels().renameId(localId, serverId)
                db.tasks().renameLabel(localId, serverId)
            }
            Target.Comment -> db.comments().renameId(localId, serverId)
            Target.Reminder -> db.reminders().renameId(localId, serverId)
        }
        db.changes().renameTask(localId, serverId)
        db.changes().replaceInBodies("\"$localId\"", "\"$serverId\"")
    }

    private suspend fun send(api: ConatusApi, change: PendingChange): SendOutcome = try {
        val body = change.body.toRequestBody(JSON)
        val id = change.taskId
        when (change.target) {
            Target.Task -> when (change.kind) {
                ChangeKind.Create -> SendOutcome.Sent(api.createTask(change.idempotencyKey!!, body).id)
                ChangeKind.Delete -> SendOutcome.Sent().also { api.deleteTask(id) }
                ChangeKind.Restore -> SendOutcome.Sent().also { api.restoreTask(id) }
                else -> SendOutcome.Sent().also { api.updateTask(id, body) }
            }
            Target.Project -> when (change.kind) {
                ChangeKind.Create -> SendOutcome.Sent(api.createProject(change.idempotencyKey, body).id)
                ChangeKind.Delete -> SendOutcome.Sent().also { api.deleteProject(id) }
                ChangeKind.Restore -> SendOutcome.Sent().also { api.restoreProject(id) }
                else -> SendOutcome.Sent().also { api.updateProject(id, body) }
            }
            Target.Section -> when (change.kind) {
                ChangeKind.Create -> SendOutcome.Sent(api.createSection(change.idempotencyKey, body).id)
                ChangeKind.Delete -> SendOutcome.Sent().also { api.deleteSection(id) }
                else -> SendOutcome.Sent().also { api.updateSection(id, body) }
            }
            Target.Label -> when (change.kind) {
                ChangeKind.Create -> SendOutcome.Sent(api.createLabel(change.idempotencyKey, body).id)
                ChangeKind.Delete -> SendOutcome.Sent().also { api.deleteLabel(id) }
                else -> SendOutcome.Sent().also { api.updateLabel(id, body) }
            }
            Target.Comment -> when (change.kind) {
                ChangeKind.Create -> SendOutcome.Sent(api.createComment(body).id)
                ChangeKind.Delete -> SendOutcome.Sent().also { api.deleteComment(id) }
                else -> SendOutcome.Sent().also { api.updateComment(id, body) }
            }
            Target.Reminder -> when (change.kind) {
                ChangeKind.Create -> SendOutcome.Sent(api.createReminder(body).id)
                ChangeKind.Delete -> SendOutcome.Sent().also { api.deleteReminder(id) }
                // Marking one seen that's gone from the server isn't worth asking about.
                else -> SendOutcome.Sent().also {
                    try {
                        api.updateReminder(id, body)
                    } catch (error: HttpException) {
                        if (error.code() != 404) throw error
                    }
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: HttpException) {
        val response = error.response()
        outcomeFor(change.kind, error.code(), response?.errorBody()?.string()?.let(::errorMessageFrom), response?.headers()?.get("Retry-After") != null)
    } catch (_: IOException) {
        SendOutcome.TryLater
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
