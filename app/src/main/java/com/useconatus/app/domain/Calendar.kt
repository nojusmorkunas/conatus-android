package com.useconatus.app.domain

import com.useconatus.app.data.db.TaskEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// A port of the server's lib/calendar.ts: a repeating task shows on every date it falls on in
// the calendar, as a preview of the same task. Dates are ISO strings, as on the server.

/** One date a task shows on. [projected] is a future repeat rather than the stored due date. */
data class Occurrence(val task: TaskEntity, val date: String, val projected: Boolean)

fun calendarOccurrences(tasks: List<TaskEntity>, start: String, end: String): List<Occurrence> = tasks.flatMap { task ->
    val due = task.dueDate ?: return@flatMap emptyList()
    if (due > end) return@flatMap emptyList()
    val out = mutableListOf<Occurrence>()
    if (due >= start) out += Occurrence(task, due, projected = false)
    // Previews of completion-relative repeats assume each one is completed on its day.
    val rule = task.recurrence?.let(::parseRecurrence)?.replace(Regex("^every!"), "every") ?: return@flatMap out
    val last = task.recurrenceEndDate?.takeIf { it < end } ?: end
    var floor = if (due >= start) due else LocalDate.parse(start).minusDays(1).toString()
    while (floor < last) {
        // Always from the original anchor, so 31 January doesn't drift to 28 March.
        val next = runCatching { nextOccurrence(rule, due, floor) }.getOrNull() ?: break
        if (next <= floor || next > last) break
        out += Occurrence(task, next, projected = true)
        floor = next
    }
    out
}

/** The first cell of a month grid: the [weekStart] day on or before the 1st. */
fun monthGridStart(month: YearMonth, weekStart: DayOfWeek): LocalDate {
    val first = month.atDay(1)
    return first.minusDays(((first.dayOfWeek.value - weekStart.value + 7) % 7).toLong())
}

/** The server's weekStart, 0 for Sunday to 6 for Saturday, as a [DayOfWeek]. */
fun weekStartOf(value: Int): DayOfWeek = if (value == 0) DayOfWeek.SUNDAY else DayOfWeek.of(value.coerceIn(1, 6))
