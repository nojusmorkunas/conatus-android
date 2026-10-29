package com.useconatus.app.ui.format

import androidx.compose.ui.graphics.Color
import com.useconatus.app.ui.theme.ConatusColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "Today", "Tomorrow", "Yesterday", a weekday in the coming week, or "24 Oct" (with the year if it isn't this one). */
fun dueLabel(date: LocalDate, today: LocalDate, locale: Locale): String = when {
    date == today -> "Today"
    date == today.plusDays(1) -> "Tomorrow"
    date == today.minusDays(1) -> "Yesterday"
    date > today && date < today.plusDays(7) -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM", locale))
    else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
}

/** A shorter date for Index's right-hand column: weekdays abbreviated, otherwise as [dueLabel]. */
fun shortDueLabel(date: LocalDate, today: LocalDate, locale: Locale): String =
    if (date > today.plusDays(1) && date < today.plusDays(7)) date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) else dueLabel(date, today, locale)

/** "HH:mm" from the server, shown in the phone's 12 or 24-hour format. */
fun timeLabel(value: String, use24Hour: Boolean, locale: Locale): String {
    val time = runCatching { LocalTime.parse(value) }.getOrNull() ?: return value
    return time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))
}

fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

fun dueColor(date: LocalDate, today: LocalDate, colors: ConatusColors): Color = when {
    date < today -> colors.overdue
    date == today -> colors.today
    date == today.plusDays(1) -> colors.tomorrow
    else -> colors.muted
}
