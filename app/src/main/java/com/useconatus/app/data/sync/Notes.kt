package com.useconatus.app.data.sync

import androidx.room.withTransaction
import com.useconatus.app.data.db.ChangeKind
import com.useconatus.app.data.db.CommentEntity
import com.useconatus.app.data.db.ConatusDatabase
import com.useconatus.app.data.db.LOCAL_ID_PREFIX
import com.useconatus.app.data.db.PendingChange
import com.useconatus.app.data.db.ReminderEntity
import com.useconatus.app.data.db.Target
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** A wall-clock time like "2026-10-28T09:00" (what quick add reads) in [zone], as epoch milliseconds. */
fun localTimeToMillis(value: String, zone: ZoneId): Long? = runCatching { LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli() }.getOrNull()

/** The server's ISO instant as epoch milliseconds. */
fun instantToMillis(value: String): Long = Instant.parse(value).toEpochMilli()

/**
 * Comments and reminders, written like everything else: Room first, then (with a server) the
 * queue. They have no conflict check; the server keeps the last edit.
 */
class NoteWrites(
    private val db: ConatusDatabase,
    private val connected: suspend () -> Boolean,
    private val requestSync: () -> Unit,
) {
    private suspend fun write(target: Target, id: String, kind: ChangeKind, body: JsonObject, summary: String, local: suspend () -> Unit) {
        val queue = connected()
        db.withTransaction {
            local()
            if (queue) db.changes().add(PendingChange(taskId = id, target = target, kind = kind, body = body.toString(), summary = summary))
        }
        if (queue) requestSync()
    }

    private fun newId() = LOCAL_ID_PREFIX + UUID.randomUUID()

    /** "on “Buy milk”", or the project's name, for the list of changes that couldn't sync. */
    private suspend fun where(taskId: String?, projectId: String?): String =
        taskId?.let { db.tasks().get(it)?.content }?.let { "on “$it”" } ?: projectId?.let { db.projects().get(it)?.name }?.let { "on “$it”" } ?: ""

    private fun commentBody(comment: CommentEntity) = buildJsonObject {
        comment.taskId?.let { put("taskId", it) }
        comment.projectId?.let { put("projectId", it) }
        put("content", comment.content)
    }

    private fun reminderBody(reminder: ReminderEntity) = buildJsonObject {
        put("taskId", reminder.taskId)
        put("remindAt", Instant.ofEpochMilli(reminder.remindAt).toString())
    }

    /** A comment on a task, or on a project when [taskId] is null. [userId] marks it as the user's own. */
    suspend fun addComment(taskId: String?, projectId: String?, content: String, userId: String?) {
        val now = Instant.now().toString()
        val comment = CommentEntity(newId(), taskId, projectId, content, now, now, userId)
        write(Target.Comment, comment.id, ChangeKind.Create, commentBody(comment), "Comment ${where(taskId, projectId)}".trim()) {
            db.comments().upsert(listOf(comment))
            taskId?.let { db.tasks().countComment(it, 1) }
        }
    }

    suspend fun editComment(comment: CommentEntity, content: String) {
        if (content == comment.content) return
        write(Target.Comment, comment.id, ChangeKind.Update, buildJsonObject { put("content", content) }, "Edit a comment ${where(comment.taskId, comment.projectId)}".trim()) {
            db.comments().upsert(listOf(comment.copy(content = content, updatedAt = Instant.now().toString())))
        }
    }

    suspend fun deleteComment(comment: CommentEntity) {
        val local: suspend () -> Unit = {
            db.comments().delete(comment.id)
            comment.taskId?.let { db.tasks().countComment(it, -1) }
        }
        // Never sent: dropping its queued changes is all it takes.
        if (comment.id.startsWith(LOCAL_ID_PREFIX)) return db.withTransaction {
            db.changes().deleteFrom(comment.id, 0)
            local()
        }
        write(Target.Comment, comment.id, ChangeKind.Delete, JsonObject(emptyMap()), "Delete a comment ${where(comment.taskId, comment.projectId)}".trim(), local)
    }

    suspend fun addReminder(taskId: String, at: Long) {
        val reminder = ReminderEntity(newId(), taskId, at)
        write(Target.Reminder, reminder.id, ChangeKind.Create, reminderBody(reminder), "Add a reminder ${where(taskId, null)}".trim()) {
            db.reminders().upsert(listOf(reminder))
        }
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        if (reminder.id.startsWith(LOCAL_ID_PREFIX)) return db.withTransaction {
            db.changes().deleteFrom(reminder.id, 0)
            db.reminders().delete(reminder.id)
        }
        write(Target.Reminder, reminder.id, ChangeKind.Delete, JsonObject(emptyMap()), "Remove a reminder ${where(reminder.taskId, null)}".trim()) {
            db.reminders().delete(reminder.id)
        }
    }

    /** The phone showed it. The server marks it seen, so the web's reminder bell doesn't ring for it too. */
    suspend fun shown(reminder: ReminderEntity) {
        if (reminder.id.startsWith(LOCAL_ID_PREFIX)) return db.reminders().setNotified(reminder.id)
        write(Target.Reminder, reminder.id, ChangeKind.Update, buildJsonObject { put("seen", true) }, "Mark a reminder seen") {
            db.reminders().setNotified(reminder.id)
        }
    }

    /** Sends the comments and reminders made before a server was connected, after the tasks they belong to. */
    suspend fun upload() {
        db.withTransaction {
            db.comments().unsent().sortedBy { it.createdAt }.forEach {
                db.changes().add(PendingChange(taskId = it.id, target = Target.Comment, kind = ChangeKind.Create, body = commentBody(it).toString(), summary = "Comment ${where(it.taskId, it.projectId)}".trim()))
            }
            db.reminders().unsent().forEach {
                db.changes().add(PendingChange(taskId = it.id, target = Target.Reminder, kind = ChangeKind.Create, body = reminderBody(it).toString(), summary = "Add a reminder ${where(it.taskId, null)}".trim()))
            }
        }
        requestSync()
    }

    /** Or removes them from the phone. */
    suspend fun dropUnsent() = db.withTransaction {
        db.comments().unsent().forEach { db.comments().delete(it.id) }
        db.reminders().unsent().forEach { db.reminders().delete(it.id) }
    }
}
