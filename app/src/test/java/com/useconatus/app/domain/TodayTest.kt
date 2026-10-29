package com.useconatus.app.domain

import com.useconatus.app.data.db.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

private fun task(id: String, due: String?, time: String? = null, priority: Int = 4, order: String = "a", done: Boolean = false) = TaskEntity(
    id = id, projectId = "p", sectionId = null, parentId = null, content = id, description = "", priority = priority,
    dueDate = due, dueTime = time, deadlineDate = null, recurrence = null, durationMinutes = null, isCompleted = done,
    order = order, updatedAt = "2026-10-26T10:00:00.000Z", labelIds = emptyList(), commentCount = 0,
)

class TodayTest {
    private val today = LocalDate.of(2026, 10, 26)

    @Test fun `splits overdue from today and leaves out later, undated and completed tasks`() {
        val groups = groupToday(
            listOf(
                task("late", "2026-10-24"),
                task("now", "2026-10-26"),
                task("later", "2026-10-27"),
                task("undated", null),
                task("done", "2026-10-26", done = true),
                task("broken", "26/10/2026"),
            ),
            today,
        )
        assertEquals(listOf("late"), groups.overdue.map { it.id })
        assertEquals(listOf("now"), groups.today.map { it.id })
    }

    @Test fun `overdue runs oldest first`() {
        val groups = groupToday(listOf(task("b", "2026-10-25"), task("a", "2026-10-20"), task("c", "2026-10-22")), today)
        assertEquals(listOf("a", "c", "b"), groups.overdue.map { it.id })
    }

    @Test fun `timed tasks come first in time order, then priority, then project order`() {
        val groups = groupToday(
            listOf(
                task("p4", "2026-10-26", priority = 4, order = "a"),
                task("at10", "2026-10-26", time = "10:00"),
                task("p1", "2026-10-26", priority = 1),
                task("at0730", "2026-10-26", time = "07:30"),
                task("p4-second", "2026-10-26", priority = 4, order = "b"),
            ),
            today,
        )
        assertEquals(listOf("at0730", "at10", "p1", "p4", "p4-second"), groups.today.map { it.id })
    }

    @Test fun `today follows the account's time zone, not the phone's`() {
        // For part of every day the date in Auckland differs from the phone's.
        assertEquals(todayIn("Pacific/Auckland"), java.time.ZonedDateTime.now(java.time.ZoneId.of("Pacific/Auckland")).toLocalDate())
        assertEquals(LocalDate.now(), todayIn("Not/AZone"))
    }
}

class TodayKeepTest {
    @Test fun `tasks completed in this visit stay in place when kept`() {
        val today = LocalDate.of(2026, 10, 26)
        val tasks = listOf(
            TaskEntity("a", "p", null, null, "a", "", 4, "2026-10-26", "08:00", null, null, null, isCompleted = true, order = "a", updatedAt = "", labelIds = emptyList(), commentCount = 0),
            TaskEntity("b", "p", null, null, "b", "", 4, "2026-10-26", "09:00", null, null, null, isCompleted = false, order = "a", updatedAt = "", labelIds = emptyList(), commentCount = 0),
        )
        assertEquals(listOf("b"), groupToday(tasks, today).today.map { it.id })
        assertEquals(listOf("a", "b"), groupToday(tasks, today, keep = setOf("a")).today.map { it.id })
    }
}
