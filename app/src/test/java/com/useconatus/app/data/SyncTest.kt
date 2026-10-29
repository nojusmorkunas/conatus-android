package com.useconatus.app.data

import com.useconatus.app.data.net.TaskDto
import com.useconatus.app.data.net.TaskPageDto
import com.useconatus.app.data.sync.fetchTaskChanges
import com.useconatus.app.data.sync.planTaskChanges
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
