package com.useconatus.app.data

import com.useconatus.app.data.db.ReminderEntity
import com.useconatus.app.data.net.ReminderDto
import com.useconatus.app.data.net.TaskDto
import com.useconatus.app.data.net.TaskPageDto
import com.useconatus.app.data.sync.ReminderPlan
import com.useconatus.app.data.sync.closedElsewhere
import com.useconatus.app.data.sync.fetchTaskChanges
import com.useconatus.app.data.sync.planReminders
import com.useconatus.app.data.sync.planTaskChanges
import com.useconatus.app.data.sync.toEntity
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun task(id: String, updatedAt: String, deletedAt: String? = null, content: String = id) =
    TaskDto(id = id, projectId = "p", content = content, updatedAt = updatedAt, deletedAt = deletedAt)

class SyncTest {
    @Test fun `follows nextCursor through every page and keeps the first page's serverTime`() = runTest {
        val pages = mapOf(
            null to TaskPageDto(listOf(task("a", "2026-10-26T10:00:00.000Z")), nextCursor = "c1", serverTime = "2026-10-26T12:00:00.000Z"),
            "c1" to TaskPageDto(listOf(task("b", "2026-10-26T09:00:00.000Z")), nextCursor = "c2", serverTime = "2026-10-26T12:00:05.000Z"),
            "c2" to TaskPageDto(listOf(task("c", "2026-10-26T08:00:00.000Z")), nextCursor = null, serverTime = "2026-10-26T12:00:09.000Z"),
        )
        val asked = mutableListOf<String?>()
        val changes = fetchTaskChanges { cursor -> asked += cursor; pages.getValue(cursor) }

        assertEquals(listOf(null, "c1", "c2"), asked)
        assertEquals(listOf("a", "b", "c"), changes.items.map { it.id })
        assertEquals("2026-10-26T12:00:00.000Z", changes.serverTime)
    }

    @Test fun `an empty first page still gives a serverTime to sync from`() = runTest {
        val changes = fetchTaskChanges { TaskPageDto(emptyList(), null, "2026-10-26T12:00:00.000Z") }
        assertEquals(emptyList<TaskDto>(), changes.items)
        assertEquals("2026-10-26T12:00:00.000Z", changes.serverTime)
    }

    @Test fun `deleted tasks become deletions and the rest upserts`() {
        val plan = planTaskChanges(
            listOf(
                task("live", "2026-10-26T10:00:00.000Z"),
                task("gone", "2026-10-26T10:00:00.000Z", deletedAt = "2026-10-26T10:00:00.000Z"),
            ),
        )
        assertEquals(listOf("live"), plan.upserts.map { it.id })
        assertEquals(listOf("gone"), plan.deletions)
    }

    @Test fun `a task seen twice while paging keeps its newest version`() {
        val plan = planTaskChanges(
            listOf(
                task("a", "2026-10-26T10:00:00.000Z", content = "old"),
                task("a", "2026-10-26T10:00:01.500Z", content = "new"),
                task("a", "2026-10-26T09:59:59.000Z", content = "older"),
            ),
        )
        assertEquals(listOf("new"), plan.upserts.map { it.content })
    }

    @Test fun `a task deleted after an edit on another page ends up deleted`() {
        val plan = planTaskChanges(
            listOf(
                task("a", "2026-10-26T10:00:05.000Z", deletedAt = "2026-10-26T10:00:05.000Z"),
                task("a", "2026-10-26T10:00:00.000Z"),
            ),
        )
        assertEquals(emptyList<TaskDto>(), plan.upserts)
        assertEquals(listOf("a"), plan.deletions)
    }

    @Test fun `a reminder notification goes stale when its task is completed, deleted or rescheduled elsewhere`() {
        val open = task("a", "2026-10-26T10:00:00.000Z").copy(dueDate = "2026-10-27", dueTime = "09:00")
        val local = open.toEntity()
        assertFalse(closedElsewhere(local, open.copy(content = "renamed")))
        assertTrue(closedElsewhere(local, open.copy(isCompleted = true)))
        assertTrue(closedElsewhere(local, open.copy(deletedAt = "2026-10-26T11:00:00.000Z")))
        assertTrue(closedElsewhere(local, open.copy(dueTime = "10:00")))
        assertTrue(closedElsewhere(local, open.copy(dueDate = "2026-10-28")))
        assertFalse(closedElsewhere(null, open.copy(isCompleted = true)))
        assertFalse(closedElsewhere(local.copy(isCompleted = true), open.copy(isCompleted = true)))
    }

    @Test fun `pending reminders are added, moved and dropped when seen or deleted elsewhere`() {
        val now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli()
        fun at(time: String) = Instant.parse(time).toEpochMilli()
        val known = listOf(
            ReminderEntity("same", "t", at("2026-10-30T15:00:00Z")),
            ReminderEntity("moved", "t", at("2026-10-30T11:00:00Z"), notified = true),
            ReminderEntity("seen-elsewhere", "t", at("2026-10-30T11:30:00Z")),
            ReminderEntity("deleted-elsewhere", "t", at("2026-10-30T16:00:00Z")),
            ReminderEntity("queued", "t", at("2026-10-30T16:00:00Z")),
            ReminderEntity("local:new", "t", at("2026-10-30T16:00:00Z")),
            ReminderEntity("shown-here", "t", at("2026-10-30T10:00:00Z"), notified = true),
        )
        val listed = listOf(
            ReminderDto("same", "t", "2026-10-30T15:00:00Z"),
            ReminderDto("moved", "t", "2026-10-30T13:00:00Z"),
            ReminderDto("added", "t", "2026-10-30T14:00:00Z"),
        )
        val plan = planReminders(known, listed, everyUnseen = true, queued = setOf("queued"), now = now)

        assertEquals(
            listOf(ReminderEntity("moved", "t", at("2026-10-30T13:00:00Z")), ReminderEntity("added", "t", at("2026-10-30T14:00:00Z"))),
            plan.upserts,
        )
        assertEquals(listOf("seen-elsewhere"), plan.seen)
        assertEquals(listOf("deleted-elsewhere"), plan.deleted)

        // Only the due ones: nothing can be told apart from a reminder that's still to come.
        val due = planReminders(known, emptyList(), everyUnseen = false, queued = emptySet(), now = now)
        assertEquals(ReminderPlan(emptyList(), emptyList(), emptyList()), due)
    }
}
