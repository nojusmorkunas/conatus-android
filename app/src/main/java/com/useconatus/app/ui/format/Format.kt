package com.useconatus.app.ui.format

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.useconatus.app.ui.theme.ConatusColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** The date formats an account can pick on the server. */
val DATE_FORMATS = listOf("yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "dd.MM.yyyy")

/** The account's date format, one of [DATE_FORMATS], and its first day of the week. */
data class DatePrefs(val format: String, val weekStart: DayOfWeek)

val LocalDatePrefs = staticCompositionLocalOf { DatePrefs(DATE_FORMATS[0], DayOfWeek.MONDAY) }

/** The account's [format] if this app knows it, else the one closest to the phone's own short date. */
fun dateFormatFor(format: String?, locale: Locale): String {
    if (format in DATE_FORMATS) return format!!
    val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
    return when {
        pattern.startsWith("y") -> "yyyy-MM-dd"
        pattern.startsWith("M") -> "MM/dd/yyyy"
        '.' in pattern -> "dd.MM.yyyy"
        else -> "dd/MM/yyyy"
    }
}

/** [date] in the account's [format]: "30.11.2026". */
fun formatDate(date: LocalDate, format: String): String = date.format(DateTimeFormatter.ofPattern(format, Locale.ROOT))

/** "Today", "Tomorrow", a weekday in the coming week, or the date in [format]. */
fun dueLabel(date: LocalDate, today: LocalDate, locale: Locale, format: String): String = when {
    date == today -> "Today"
    date == today.plusDays(1) -> "Tomorrow"
    date > today && date < today.plusDays(7) -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    else -> formatDate(date, format)
}

/** For days gone by, such as completions: "Today", "Yesterday", a weekday in the past week, or the date in [format]. */
fun pastDateLabel(date: LocalDate, today: LocalDate, locale: Locale, format: String): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    date < today && date > today.minusDays(7) -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    else -> formatDate(date, format)
}

/** A deadline reads back from the past once it has gone by, and forward until then. */
fun deadlineLabel(date: LocalDate, today: LocalDate, locale: Locale, format: String): String =
    if (date < today) pastDateLabel(date, today, locale, format) else dueLabel(date, today, locale, format)

/** A shorter date for Index's right-hand column: weekdays abbreviated, otherwise as [dueLabel]. */
fun shortDueLabel(date: LocalDate, today: LocalDate, locale: Locale, format: String): String =
    if (date > today.plusDays(1) && date < today.plusDays(7)) date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) else dueLabel(date, today, locale, format)

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
