package com.useconatus.app.domain

import com.useconatus.app.data.db.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// The server's lib/calendar tests, ported.
class CalendarTest {
    private val task = TaskEntity(
        id = "task-1", projectId = "p", sectionId = null, parentId = null, content = "x", description = "", priority = 4,
        dueDate = "2026-01-31", dueTime = "09:30", deadlineDate = null, recurrence = "every month", durationMinutes = null,
        isCompleted = false, order = "a0", updatedAt = "", labelIds = emptyList(), commentCount = 0,
    )

    private fun dates(t: TaskEntity, start: String, end: String) = calendarOccurrences(listOf(t), start, end).map { it.date }

    @Test fun `repeats from before the month keep month-end anchors`() {
        val result = calendarOccurrences(listOf(task), "2026-02-01", "2026-04-30")
        assertEquals(listOf("2026-02-28", "2026-03-31", "2026-04-30"), result.map { it.date })
        assertTrue(result.all { it.projected })
    }

    @Test fun `the due date shows once and previews stop at the end date`() = assertEquals(
        listOf("2026-01-31" to false, "2026-02-01" to true, "2026-02-02" to true),
        calendarOccurrences(listOf(task.copy(recurrence = "every day", recurrenceEndDate = "2026-02-02")), "2026-01-31", "2026-02-10").map { it.date to it.projected },
    )

    @Test fun `weekdays and fortnights`() {
        assertEquals(listOf("2026-09-07", "2026-09-08"), dates(task.copy(dueDate = "2026-09-04", recurrence = "every weekday"), "2026-09-05", "2026-09-08"))
        assertEquals(listOf("2026-09-14", "2026-09-28"), dates(task.copy(dueDate = "2026-08-03", recurrence = "every other monday"), "2026-09-01", "2026-09-30"))
    }

    @Test fun `completion-relative repeats assume on-time completion`() =
        assertEquals(listOf("2026-02-03", "2026-02-06"), dates(task.copy(recurrence = "every! 3 days"), "2026-02-01", "2026-02-08"))

    @Test fun `weekday lists and monthly weekdays`() {
        assertEquals(
            listOf("2026-07-15", "2026-07-17", "2026-07-20", "2026-07-22", "2026-07-24"),
            dates(task.copy(dueDate = "2026-07-13", recurrence = "every monday, wednesday, friday"), "2026-07-14", "2026-07-24"),
        )
        assertEquals(listOf("2026-07-10", "2026-08-14", "2026-09-11"), dates(task.copy(dueDate = "2026-06-12", recurrence = "every 2nd friday"), "2026-07-01", "2026-09-30"))
        assertEquals(listOf("2026-08-31", "2026-11-30"), dates(task.copy(dueDate = "2026-06-29", recurrence = "every 5th monday"), "2026-07-01", "2026-11-30"))
    }

    @Test fun `one-off and unsupported rules show only their stored date`() {
        for (rule in listOf(null, "unsupported rule")) {
            assertEquals(1, dates(task.copy(recurrence = rule), "2026-01-01", "2026-02-28").size)
            assertEquals(0, dates(task.copy(recurrence = rule), "2026-02-01", "2026-02-28").size)
        }
        assertEquals(0, dates(task.copy(dueDate = null), "2026-01-01", "2026-02-28").size)
    }

    @Test fun `month grids start on the week's first day`() {
        assertEquals(LocalDate.parse("2027-04-26"), monthGridStart(YearMonth.of(2027, 5), DayOfWeek.MONDAY))
        assertEquals(LocalDate.parse("2027-04-25"), monthGridStart(YearMonth.of(2027, 5), weekStartOf(0)))
    }
}
