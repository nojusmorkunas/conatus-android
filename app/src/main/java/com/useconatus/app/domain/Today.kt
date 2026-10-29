package com.useconatus.app.domain

import com.useconatus.app.data.db.TaskEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

data class TodayGroups(val overdue: List<TaskEntity>, val today: List<TaskEntity>)

/**
 * Splits open, dated tasks into overdue and due today. Overdue runs oldest first; both groups put
 * timed tasks in time order, then the rest by priority, then by their order in the project.
 */
fun groupToday(tasks: List<TaskEntity>, today: LocalDate, keep: Set<String> = emptySet()): TodayGroups {
    // [keep] holds tasks completed in this visit that should stay in place, struck through (Index).
    val dated = tasks.filter { !it.isCompleted || it.id in keep }.mapNotNull { task -> task.dueDate?.let(::parseDate)?.let { task to it } }
    val byTime = compareBy<TaskEntity>({ it.dueTime == null }, { it.dueTime }, { it.priority }, { it.order })
    val overdue = dated.filter { it.second < today }.sortedWith(compareBy<Pair<TaskEntity, LocalDate>> { it.second }.thenBy(byTime) { it.first })
    val dueToday = dated.filter { it.second == today }.map { it.first }.sortedWith(byTime)
    return TodayGroups(overdue.map { it.first }, dueToday)
}

/** Today in the user's Conatus time zone, which can differ from the phone's. */
fun todayIn(timezone: String?): LocalDate =
    runCatching { LocalDate.now(ZoneId.of(timezone ?: "")) }.getOrElse { LocalDate.now() }

private fun parseDate(value: String): LocalDate? = try {
    LocalDate.parse(value)
} catch (_: DateTimeParseException) {
    null
}
