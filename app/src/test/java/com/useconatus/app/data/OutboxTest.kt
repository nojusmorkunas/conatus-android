package com.useconatus.app.data

import com.useconatus.app.data.db.ChangeKind
import com.useconatus.app.data.db.PendingChange
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.data.db.escapeLike
import com.useconatus.app.data.sync.SendOutcome
import com.useconatus.app.data.sync.TaskEdit
import com.useconatus.app.data.sync.baseFor
import com.useconatus.app.data.sync.merge
import com.useconatus.app.data.sync.nextToSend
import com.useconatus.app.data.sync.outcomeFor
import com.useconatus.app.data.sync.uploadOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun change(seq: Long, task: String, kind: ChangeKind = ChangeKind.Update, failure: String? = null) =
    PendingChange(seq = seq, taskId = task, kind = kind, body = "{}", summary = "", failure = failure)

class NextToSendTest {
    @Test fun `sends in order`() =
        assertEquals(1L, nextToSend(listOf(change(2, "b"), change(1, "a"), change(3, "c")))?.seq)

    @Test fun `a rejected change holds back later changes to the same task, not to others`() {
        val queue = listOf(change(1, "a", failure = "Gone"), change(2, "a"), change(3, "b"))
        assertEquals(3L, nextToSend(queue)?.seq)
    }

    @Test fun `a task in conflict sends nothing until the user picks a side`() {
        val queue = listOf(change(1, "a"), change(2, "b"))
        assertEquals(2L, nextToSend(queue, conflicted = setOf("a"))?.seq)
    }

    @Test fun `changes to a task made on the phone wait for its create`() {
        val queue = listOf(change(1, "local:x", ChangeKind.Create), change(2, "local:x", ChangeKind.Complete))
        assertEquals(1L, nextToSend(queue)?.seq)
        // Once the add has failed, nothing for that task goes out.
        assertNull(nextToSend(listOf(change(1, "local:x", ChangeKind.Create, failure = "No project"), change(2, "local:x", ChangeKind.Complete))))
        // And a change can't overtake its add even if the add is still ahead of it.
        assertNull(nextToSend(listOf(change(2, "local:x", ChangeKind.Complete))))
    }

    @Test fun `nothing to send when the queue is empty or everything failed`() {
        assertNull(nextToSend(emptyList()))
        assertNull(nextToSend(listOf(change(1, "a", failure = "No access"))))
    }
}

class UploadOrderTest {
    private fun task(id: String, parent: String? = null, order: String = "a", done: Boolean = true) = TaskEntity(
        id = id, projectId = "p", sectionId = null, parentId = parent, content = id, description = "", priority = 4,
        dueDate = null, dueTime = null, deadlineDate = null, recurrence = null, durationMinutes = null,
        isCompleted = done, order = order, updatedAt = "", labelIds = emptyList(), commentCount = 0,
    )

    @Test fun `every create goes first, parents before sub-tasks, then completions from the bottom up`() {
        val tasks = listOf(task("grandchild", "second", "a"), task("second", "parent", "b"), task("first", "parent", "a"), task("parent"))
        val (creates, completes) = uploadOrder(tasks)
        assertEquals(listOf("parent", "first", "second", "grandchild"), creates.map { it.id })
        assertEquals(listOf("grandchild", "second", "first", "parent"), completes.map { it.id })
    }

    @Test fun `open tasks aren't completed, and a parent the upload doesn't include doesn't count`() {
        val (creates, completes) = uploadOrder(listOf(task("child", "on server", "b"), task("open", order = "a", done = false)))
        assertEquals(listOf("open", "child"), creates.map { it.id })
        assertEquals(listOf("child"), completes.map { it.id })
    }
}

class EscapeLikeTest {
    @Test fun `escapes the wildcards and the escape character`() =
        assertEquals("""50\% off a\_b c\\d""", escapeLike("""50% off a_b c\d"""))
}

class OutcomeTest {
    @Test fun `server and network trouble means try again later`() {
        listOf(408, 429, 500, 502, 503).forEach { assertEquals(SendOutcome.TryLater, outcomeFor(ChangeKind.Update, it, null, false)) }
        assertEquals(SendOutcome.TryLater, outcomeFor(ChangeKind.Create, 409, "still in progress", hasRetryAfter = true))
    }

    @Test fun `a revoked token signs out`() = assertEquals(SendOutcome.SignedOut, outcomeFor(ChangeKind.Complete, 401, null, false))

    @Test fun `deleting a task that's already gone counts as done`() =
        assertEquals(SendOutcome.Sent(), outcomeFor(ChangeKind.Delete, 404, null, false))

    @Test fun `rejections keep a reason the user can read`() {
        val gone = outcomeFor(ChangeKind.Complete, 404, null, false)
        assertTrue(gone is SendOutcome.Rejected && "no longer exists" in gone.reason)
        assertEquals(SendOutcome.Rejected("Project “Nope” was not found"), outcomeFor(ChangeKind.Create, 400, "Project “Nope” was not found", false))
        val noAccess = outcomeFor(ChangeKind.Update, 403, null, false)
        assertTrue(noAccess is SendOutcome.Rejected && "access" in noAccess.reason)
        // A 409 without Retry-After is a key reused for a different body: retrying won't help.
        assertTrue(outcomeFor(ChangeKind.Create, 409, null, false) is SendOutcome.Rejected)
    }
}

class TaskEditTest {
    private val task = TaskEntity(
        id = "t", projectId = "p", sectionId = null, parentId = null, content = "Old", description = "", priority = 4,
        dueDate = "2026-10-26", dueTime = "09:00", deadlineDate = null, recurrence = "every day", durationMinutes = 30,
        isCompleted = false, order = "a", updatedAt = "", labelIds = emptyList(), commentCount = 0,
    )

    @Test fun `only sends the fields that changed`() =
        assertEquals("""{"content":"New","priority":1}""", TaskEdit(content = "New", priority = 1).toJson().toString())

    @Test fun `clearing a field sends an explicit null`() =
        assertEquals("""{"dueDate":null,"dueTime":null,"recurrence":null}""", TaskEdit.noDate.toJson().toString())

    @Test fun `applies set and cleared fields and leaves the rest`() {
        val edited = TaskEdit(content = "New", durationMinutes = TaskEdit.Field(null), deadlineDate = TaskEdit.Field("2026-11-01")).applyTo(task)
        assertEquals("New", edited.content)
        assertNull(edited.durationMinutes)
        assertEquals("2026-11-01", edited.deadlineDate)
        assertEquals("09:00", edited.dueTime)
        assertEquals("every day", edited.recurrence)
        val undated = TaskEdit.noDate.applyTo(task)
        assertNull(undated.dueDate)
        assertNull(undated.dueTime)
        assertNull(undated.recurrence)
    }
}

class MergeTest {
    private val base = TaskEntity(
        id = "t", projectId = "p", sectionId = null, parentId = null, content = "Pay rent", description = "", priority = 4,
        dueDate = "2026-10-26", dueTime = null, deadlineDate = null, recurrence = null, durationMinutes = null,
        isCompleted = false, order = "a", updatedAt = "1", labelIds = listOf("x"), commentCount = 0,
    )

    /** The phone changed [fields] of [base] to make [local]. */
    private fun queued(local: TaskEntity, vararg fields: String, seq: Long = 1) =
        PendingChange(seq = seq, taskId = "t", kind = ChangeKind.Update, body = "{}", summary = "", base = base.baseFor(fields.toList()).toString())

    @Test fun `changes to different fields both survive`() {
        val local = base.copy(content = "Pay the rent")
        val theirs = base.copy(dueDate = "2026-10-30", updatedAt = "2")
        val merged = merge(local, theirs, listOf(queued(local, "content")))
        assertEquals(base.copy(content = "Pay the rent", dueDate = "2026-10-30", updatedAt = "2"), merged.task)
        assertTrue(merged.conflicts.isEmpty())
    }

    @Test fun `the same change on both sides is no conflict`() {
        val local = base.copy(priority = 1)
        assertTrue(merge(local, base.copy(priority = 1), listOf(queued(local, "priority"))).conflicts.isEmpty())
    }

    @Test fun `different changes to the same field are a conflict, showing the phone's meanwhile`() {
        val local = base.copy(dueDate = "2026-10-27")
        val merged = merge(local, base.copy(dueDate = "2026-11-02"), listOf(queued(local, "dueDate")))
        assertEquals("2026-10-27", merged.task.dueDate)
        assertEquals("""{"dueDate":"2026-11-02"}""", merged.conflicts.toString())
    }

    @Test fun `a field changed twice on the phone compares against what it was first`() {
        val local = base.copy(content = "Third")
        val changes = listOf(queued(base.copy(content = "Second"), "content", seq = 1), PendingChange(seq = 2, taskId = "t", kind = ChangeKind.Update, body = "{}", summary = "", base = base.copy(content = "Second").baseFor(listOf("content")).toString()))
        // The server still has the original, so the phone's edits go through.
        assertTrue(merge(local, base, changes).conflicts.isEmpty())
    }

    @Test fun `labels compare as a set`() {
        val local = base.copy(labelIds = listOf("x", "y"))
        assertTrue(merge(local, base.copy(labelIds = listOf("y", "x")), listOf(queued(local, "labelIds"))).conflicts.isEmpty())
    }
}
