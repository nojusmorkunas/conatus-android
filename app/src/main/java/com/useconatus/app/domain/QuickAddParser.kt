package com.useconatus.app.domain

import java.time.LocalDate

// A port of the server's lib/parser (date-phrases.ts, time-phrases.ts and quick-add.ts), so quick
// add reads the same text the same way with or without a server. Keep the two in step: the tests
// mirror the server's own test cases. Dates are ISO strings, as on the server.

data class QuickAddParse(
    val content: String,
    val projectName: String? = null,
    val labelNames: List<String> = emptyList(),
    val priority: Int = 4,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val recurrence: String? = null,
    val deadlineDate: String? = null,
    val durationMinutes: Int? = null,
    /** Wall-clock date and time ("2026-07-15T09:00") in the phone's time zone. */
    val reminderAt: String? = null,
)

enum class TokenKind { Project, Label, Priority, Date, Time, Recurrence, Deadline, Duration, Reminder }

/** A recognised phrase: [start] to [end] in the input, for highlighting. */
data class QuickAddToken(val kind: TokenKind, val start: Int, val end: Int, val value: String)

data class QuickAddResult(val parsed: QuickAddParse, val tokens: List<QuickAddToken>)

data class DateMatch(val date: String, val length: Int)
data class TimeMatch(val time: String?, val length: Int)
data class DurationMatch(val minutes: Int?, val length: Int)

private fun List<String>.at(i: Int) = getOrNull(i) ?: ""

// Dates

private val MONTHS = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

private fun iso(date: LocalDate?): String? = date?.takeIf { it.year in 1..9999 }?.toString()

private fun validDate(year: Int, month: Int, day: Int): String? = iso(runCatching { LocalDate.of(year, month, day) }.getOrNull())

private fun addDays(date: String, n: Long): String? = iso(runCatching { LocalDate.parse(date).plusDays(n) }.getOrNull())

private fun addMonths(date: String, n: Long): String? = iso(runCatching { LocalDate.parse(date).plusMonths(n) }.getOrNull())

private fun result(date: String?, length: Int) = date?.let { DateMatch(it, length) }

private fun monthIndex(word: String) = if (word == "sept") 8 else MONTHS.indexOfFirst { it == word || (word.length == 3 && it.startsWith(word)) }

private fun nextCalendarDate(today: String, month: Int, day: Int, year: Int? = null): String? {
    if (year != null) return validDate(year, month, day)
    val currentYear = today.take(4).toInt()
    val current = validDate(currentYear, month, day)
    if (current != null && current >= today) return current
    return validDate(currentYear + 1, month, day)
}

private fun Double.isSafeInteger() = this % 1.0 == 0.0 && kotlin.math.abs(this) <= 9007199254740991.0

private fun relative(words: List<String>, i: Int, today: String): DateMatch? {
    val prefixed = words.at(i) == "in"
    val start = i + if (prefixed) 1 else 0
    for (size in listOf(2, 1)) {
        val n = numberValue(words.drop(start).take(size).joinToString(" "))
        val unit = Regex("^(day|week|month|year)s?$").find(words.at(start + size))
        if (n == null || !n.isSafeInteger() || n < 0 || unit == null) continue
        var length = size + 1 + if (prefixed) 1 else 0
        if (words.at(i + length) == "from" && words.at(i + length + 1) == "now") length += 2 else if (!prefixed) continue
        val count = n.toLong()
        return result(
            when (unit.groupValues[1]) {
                "day" -> addDays(today, count)
                "week" -> addDays(today, count * 7)
                "year" -> addMonths(today, count * 12)
                else -> addMonths(today, count)
            },
            length,
        )
    }
    return null
}

/** Calendar dates only; a time such as {noon} must never turn into a date. */
fun matchDate(source: List<String>, i: Int, today: String): DateMatch? {
    val words = source.map { cleanWord(it).lowercase() }
    val w = words.at(i)
    if (w.isEmpty()) return null
    if (w == "on") {
        if (words.at(i + 1) == "on") return null
        return matchDate(words, i + 1, today)?.let { it.copy(length = it.length + 1) }
    }
    if (w == "the") {
        // "the 15th" and "on the first" mean the next such day of a month.
        for (size in listOf(2, 1)) {
            if (i + 1 + size > words.size) continue
            val day = ordinalValue(words.subList(i + 1, i + 1 + size).joinToString(" ")) ?: continue
            val year = today.take(4).toInt()
            val month = today.substring(5, 7).toInt()
            for (offset in 0 until 3) {
                val total = year * 12 + month - 1 + offset
                val date = validDate(total / 12, total % 12 + 1, day)
                if (date != null && date >= today) return DateMatch(date, size + 1)
            }
        }
        return null
    }
    if (w == "today" || w == "tod") return DateMatch(today, 1)
    if (w == "tomorrow" || w == "tmr") return result(addDays(today, 1), 1)
    if (words.drop(i).take(3).joinToString(" ") == "day after tomorrow") return result(addDays(today, 2), 3)
    val weekday = LocalDate.parse(today).dayOfWeek.value % 7
    val day = weekdayIndex(w)
    if (day >= 0) return result(addDays(today, ((day - weekday + 7) % 7).let { if (it == 0) 7 else it }.toLong()), 1)
    if (w in listOf("this", "coming", "upcoming")) {
        val nextDay = weekdayIndex(words.at(i + 1))
        if (nextDay >= 0) return result(addDays(today, ((nextDay - weekday + 7) % 7).let { if (it == 0 && w != "this") 7 else it }.toLong()), 2)
    }
    if (w == "next") {
        val next = words.at(i + 1)
        if (next == "month" || next == "year") return result(addMonths(today, if (next == "year") 12 else 1), 2)
        val diff = 7 - (weekday + 6) % 7
        if (next == "week") return result(addDays(today, diff.toLong()), 2)
        val nextDay = weekdayIndex(next)
        if (nextDay >= 0) return result(addDays(today, (diff + (nextDay + 6) % 7).toLong()), 2)
    }
    relative(words, i, today)?.let { return it }
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(w)?.let { return result(validDate(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()), 1) }
    // Numeric dates are day first.
    Regex("^(\\d{1,2})[/.](\\d{1,2})$").find(w)?.let { return result(nextCalendarDate(today, it.groupValues[2].toInt(), it.groupValues[1].toInt()), 1) }
    val monthFirst = monthIndex(w)
    val dayWord = if (monthFirst >= 0) words.at(i + 1) else w
    val month = if (monthFirst >= 0) monthFirst else monthIndex(words.at(i + 1))
    val monthDay = if (Regex("^\\d{1,2}$").matches(dayWord)) dayWord.toInt() else ordinalValue(dayWord) ?: 0
    if (month >= 0 && monthDay != 0) {
        val yearWord = words.at(i + 2)
        val explicitYear = if (Regex("^\\d{4}$").matches(yearWord)) yearWord.toInt() else null
        return result(nextCalendarDate(today, month + 1, monthDay, explicitYear), if (explicitYear == null) 2 else 3)
    }
    return null
}

// Times and durations

fun matchTime(words: List<String>, i: Int): TimeMatch? {
    val prefixed = words.at(i) == "at"
    val p = if (prefixed) 1 else 0
    val word = words.at(i + p)
    if (word == "noon" || word == "midnight") return TimeMatch(if (word == "noon") "12:00" else "00:00", p + 1)
    val after = words.at(i + p + 1)
    val suffix = if (Regex("^[ap]\\.?m$").matches(after)) after else ""
    if (prefixed && suffix.isEmpty() && Regex("^[ap]$").matches(after) && Regex("^\\d{1,2}(?::\\d{2})?$").matches(word)) return TimeMatch(null, 3)
    if (!prefixed && suffix.isEmpty() && !Regex("\\d(?::\\d{2}|[ap]\\.?m)$").containsMatchIn(word)) return null
    val match = Regex("^(\\d{1,2})(?::(\\d{2}))?([ap]\\.?m)?$").find(word + suffix) ?: return null
    val length = p + 1 + if (suffix.isNotEmpty()) 1 else 0
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
    val half = match.groupValues[3]
    if (minute > 59 || (if (half.isNotEmpty()) hour !in 1..12 else hour > 23)) return TimeMatch(null, length)
    if (half.isNotEmpty()) hour = hour % 12 + if (half[0] == 'p') 12 else 0
    return TimeMatch(hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0'), length)
}

private val DURATION_PART = Regex("^(.+?)\\s*(hours?|hrs?|h|minutes?|mins?|m)(?=$|[\\s\\d])")

private fun durationValue(text: String): Double? {
    var rest = text.replace(Regex("^(an?|one) hour and a half$"), "1.5 hours")
    var minutes = 0.0
    var previousUnit = ""
    repeat(2) {
        val match = DURATION_PART.find(rest) ?: return null
        val amount = numberValue(match.groupValues[1]) ?: return null
        val unit = if (match.groupValues[2].startsWith("h")) "h" else "m"
        if (previousUnit.isNotEmpty() && (previousUnit != "h" || unit != "m")) return null
        minutes += amount * if (unit == "h") 60 else 1
        previousUnit = unit
        rest = rest.substring(match.value.length).trim().replace(Regex("^and\\s+"), "")
        if (rest.isEmpty()) return minutes
    }
    return null
}

fun matchDuration(words: List<String>, i: Int): DurationMatch? {
    if (words.at(i) != "for") return null
    var length = minOf(11, words.size - i)
    while (length >= 2) {
        val minutes = durationValue(words.subList(i + 1, i + length).joinToString(" "))
        if (minutes == null) {
            length--
            continue
        }
        // While typing, "for 1h 30" is unfinished, not a one-hour duration.
        val next = words.at(i + length)
        val continues = next.isNotEmpty() && (
            numberValue(next) != null ||
                Regex("^(?:\\d+(?:\\.\\d+)?)?(?:hours?|hrs?|h|minutes?|mins?|m)$").matches(next) ||
                (next == "and" && numberValue(words.at(i + length + 1)) != null)
            )
        if (continues) return DurationMatch(null, length + 1 + if (next == "and" && i + length + 1 < words.size) 1 else 0)
        return DurationMatch(if (minutes % 1.0 == 0.0 && minutes > 0 && minutes <= 1440) minutes.toInt() else null, length)
    }
    return null
}

// Quick add

private val DAY_PARTS = mapOf("morning" to "09:00", "afternoon" to "15:00", "evening" to "18:00", "night" to "18:00", "tonight" to "18:00")

private data class Span(val value: String?, val length: Int)
private data class DayPart(val time: String, val length: Int, val today: Boolean)

private fun matchDeadline(words: List<String>, i: Int, today: String): Span? {
    if (words[i].startsWith("{")) {
        val end = (i until words.size).firstOrNull { words[it].endsWith("}") } ?: -1
        val length = (if (end < 0) words.size else end + 1) - i
        val joined = words.subList(i, i + length).joinToString(" ")
        val inner = (if (joined.length >= 2) joined.substring(1, joined.length - 1) else "").trim().split(Regex("\\s+"))
        val match = if (end >= 0) matchDate(inner, 0, today) else null
        return Span(if (match != null && match.length == inner.size) match.date else null, length)
    }
    if (words[i] != "deadline") return null
    val skip = if (words.at(i + 1) == "on" || words.at(i + 1) == "by") 2 else 1
    val match = matchDate(words, i + skip, today) ?: return null
    return Span(match.date, skip + match.length)
}

private val EVERY_SOURCE = Regex("^every![.,!?;:]*$", RegexOption.IGNORE_CASE)
private val SCHEDULE_WORD = Regex("^(every!?|other|the|last|of|and|day|days|week|weeks|weekday|weekdays|weekend|month|months|year|years)$")

// Try the whole repeat before date matching sees a weekday inside it. Keep unfinished or
// unsupported schedules as text instead of saving a shorter, different one.
private fun matchRecurrence(source: List<String>, words: List<String>, i: Int, today: String): Span? {
    val w = words[i]
    val head = Regex("^every!?$").matches(w)
    val alias = Regex("^(daily|weekly|monthly|yearly|annually|fortnightly)$").matches(w)
    val inverted = (w == "the" || ordinalValue(w) != null) && Regex("\\bof every!? month\\b").containsMatchIn(words.drop(i).take(9).joinToString(" "))
    val monthlyStart = w == "the" && ordinalValue(words.at(i + 1)) != null && weekdayIndex(words.at(i + 2)) >= 0
    if (!head && !alias && !inverted && !monthlyStart) return null
    var length = minOf(24, words.size - i)
    while (length >= 1) {
        val phrase = source.subList(i, i + length)
            .joinToString(" ") { if (EVERY_SOURCE.matches(it)) "every!" else it.replace(Regex("[.!?;:]+$"), "") }
            .replace(Regex(",+$"), "")
        val rule = parseRecurrence(phrase)
        if (rule == null) {
            length--
            continue
        }
        val next = words.at(i + length)
        val continuation = next == "and" || next == "of" || weekdayIndex(next) >= 0 ||
            (source[i + length - 1].endsWith(",") && next.isNotEmpty() && !Regex("^(at|for|due|deadline|p[1-4]|#|@)").containsMatchIn(next) && matchDate(words, i + length, today) == null)
        if (!continuation) return Span(rule, length)
        break
    }
    length = 1
    while (i + length < words.size && length < 24) {
        val next = words[i + length]
        val afterConnector = words[i + length - 1] == "and" || words[i + length - 1] == "of"
        val scheduleWord = SCHEDULE_WORD.matches(next) || numberValue(next) != null || ordinalValue(next) != null ||
            Regex("^\\d+(st|nd|rd|th)$").matches(next) || next.split(",").all { weekdayIndex(it) >= 0 } ||
            WEEKDAY_NAMES.any { next.length >= 2 && it.startsWith(next) }
        if (!afterConnector && !scheduleWord) break
        length++
    }
    return Span(null, length)
}

private fun dayPart(words: List<String>, i: Int, afterDate: Boolean): DayPart? {
    if (words.at(i) == "tonight") return DayPart(DAY_PARTS.getValue("tonight"), 1, !afterDate)
    if ((words.at(i) == "this" || words.at(i) == "at") && DAY_PARTS.containsKey(words.at(i + 1))) {
        return DayPart(DAY_PARTS.getValue(words.at(i + 1)), 2, words.at(i) == "this" || !afterDate)
    }
    return if (afterDate && DAY_PARTS.containsKey(words.at(i))) DayPart(DAY_PARTS.getValue(words.at(i)), 1, false) else null
}

private fun matchReminder(words: List<String>, i: Int, today: String): Span? {
    if (words.at(i) != "remind" || words.at(i + 1) != "me") return null
    var cursor = i + 2
    var date: String? = null
    var time: String? = null
    var defaultTime = false
    for (part in 0 until 3) {
        val dateMatch = if (date != null) null else matchDate(words, cursor, today)
        if (dateMatch != null) {
            date = dateMatch.date
            cursor += dateMatch.length
            continue
        }
        val clock = if (time != null && !defaultTime) null else matchTime(words, cursor)
        if (clock?.time != null) {
            time = clock.time
            defaultTime = false
            cursor += clock.length
            continue
        }
        val period = if (time != null) null else dayPart(words, cursor, date != null)
        if (period != null) {
            time = period.time
            defaultTime = true
            date = date ?: today
            cursor += period.length
            continue
        }
        if (clock != null || words.at(cursor) == "at" || words.at(cursor) == "around") {
            return Span(null, cursor - i + (clock?.length ?: minOf(2, words.size - cursor)))
        }
        break
    }
    if (date == null && time == null) return null
    if (words.at(cursor) == "to") cursor++
    return Span((date ?: today) + "T" + (time ?: "09:00"), cursor - i)
}

fun parseQuickAdd(input: String, today: String, projectNames: List<String>? = null, labelNames: List<String>? = null): QuickAddParse =
    parseQuickAddPreview(input, today, projectNames, labelNames).parsed

/**
 * One pass gives both the fields to save and the exact ranges to highlight. Without
 * [projectNames] or [labelNames], any #word or @word counts, as on the server's API.
 */
fun parseQuickAddPreview(input: String, today: String, projectNames: List<String>? = null, labelNames: List<String>? = null): QuickAddResult {
    val matches = Regex("\\S+").findAll(input).toList()
    val source = matches.map { it.value }
    val words = source.map { cleanWord(it).lowercase() }
    val consumed = mutableSetOf<Int>()
    val tokens = mutableListOf<QuickAddToken>()
    var parsed = QuickAddParse(content = "")

    fun consume(index: Int, length: Int, kind: TokenKind, value: String) {
        for (k in 0 until length) consumed += index + k
        tokens += QuickAddToken(kind, matches[index].range.first, matches[index + length - 1].range.last + 1, value)
    }

    fun reference(index: Int, names: List<String>?): Pair<String, Int>? {
        if (names == null) return cleanWord(source[index]).drop(1) to 1
        for (name in names.sortedByDescending { it.length }) {
            val parts = name.split(Regex("\\s+"))
            val phrase = source.drop(index).take(parts.size).joinToString(" ").drop(1)
            // Exact matching first keeps punctuation that belongs to a name.
            val wanted = parts.joinToString(" ").lowercase()
            if (phrase.lowercase() == wanted || cleanWord(phrase).lowercase() == wanted) return name to parts.size
        }
        return null
    }

    var dateEnd = -1
    var defaultTime = false
    val leadingReminder = words.at(0) == "remind" && words.at(1) == "me" && words.at(2) == "to"
    var i = if (leadingReminder) 3 else 0
    while (i < words.size) {
        val w = words[i]
        val deadline = matchDeadline(words, i, today)
        if (deadline != null) {
            if (parsed.deadlineDate == null && deadline.value != null) {
                parsed = parsed.copy(deadlineDate = deadline.value)
                consume(i, deadline.length, TokenKind.Deadline, deadline.value)
            }
            i += deadline.length
            continue
        }
        val reminder = matchReminder(words, i, today)
        if (reminder != null) {
            if (parsed.reminderAt == null && reminder.value != null) {
                parsed = parsed.copy(reminderAt = reminder.value)
                consume(i, reminder.length, TokenKind.Reminder, reminder.value)
            }
            i += reminder.length
            continue
        }
        if (w.length > 1 && (w[0] == '#' || w[0] == '@')) {
            val project = w[0] == '#'
            val match = reference(i, if (project) projectNames else labelNames)
            if (match != null && match.first.isNotEmpty()) {
                val (name, length) = match
                if (project) {
                    parsed = parsed.copy(projectName = name)
                } else if (parsed.labelNames.none { it.equals(name, ignoreCase = true) }) {
                    parsed = parsed.copy(labelNames = parsed.labelNames + name)
                }
                consume(i, length, if (project) TokenKind.Project else TokenKind.Label, name)
                i += length
                continue
            }
            i++
            continue
        }
        Regex("^p([1-4])$").find(w)?.let { priority ->
            parsed = parsed.copy(priority = priority.groupValues[1].toInt())
            consume(i, 1, TokenKind.Priority, priority.groupValues[1])
            i++
            continue
        }
        val duration = matchDuration(words, i)
        if (duration != null) {
            if (parsed.durationMinutes == null && duration.minutes != null) {
                parsed = parsed.copy(durationMinutes = duration.minutes)
                consume(i, duration.length, TokenKind.Duration, duration.minutes.toString())
            }
            i += duration.length
            continue
        }
        // A half-typed decimal duration must not fall through as a day.month date.
        if (w == "for" && numberValue(words.at(i + 1)) != null) {
            i += 2
            continue
        }
        if (w == "around") {
            // Fuzzy times need an AM or PM decision; don't pull a bare time out of them.
            val clock = matchTime(words, i + 1)
            if (clock != null || numberValue(words.at(i + 1)) != null) i += clock?.length ?: 1
            i++
            continue
        }
        val repeat = matchRecurrence(source, words, i, today)
        if (repeat != null) {
            if (parsed.dueDate == null && repeat.value != null) {
                parsed = parsed.copy(recurrence = repeat.value, dueDate = firstOccurrence(repeat.value, today))
                consume(i, repeat.length, TokenKind.Recurrence, repeat.value)
                dateEnd = i + repeat.length
            }
            i += repeat.length
            continue
        }
        val clock = matchTime(words, i)
        if (clock != null) {
            if ((parsed.dueTime == null || defaultTime) && clock.time != null) {
                parsed = parsed.copy(dueTime = clock.time)
                defaultTime = false
                consume(i, clock.length, TokenKind.Time, clock.time)
            }
            i += clock.length
            continue
        }
        if (w == "at" && numberValue(words.at(i + 1)) != null) {
            i += 2
            continue
        }
        val period = dayPart(words, i, dateEnd == i)
        if (period != null && (parsed.dueTime == null || defaultTime) && (!period.today || parsed.dueDate == null)) {
            parsed = parsed.copy(dueTime = period.time, dueDate = if (period.today) today else parsed.dueDate)
            defaultTime = true
            consume(i, period.length, if (period.today) TokenKind.Date else TokenKind.Time, if (period.today) today else period.time)
            i += period.length
            continue
        }
        if (parsed.dueDate == null) {
            val skip = when (w) {
                "due" -> if (words.at(i + 1) == "by") 2 else 1
                "by" -> 1
                else -> 0
            }
            val match = matchDate(words, i + skip, today)
            if (match != null) {
                parsed = parsed.copy(dueDate = match.date)
                consume(i, skip + match.length, TokenKind.Date, match.date)
                dateEnd = i + skip + match.length
                i += skip + match.length
                continue
            }
        }
        i++
    }

    if (leadingReminder && (parsed.dueDate != null || parsed.dueTime != null) && parsed.recurrence == null) {
        val at = (parsed.dueDate ?: today) + "T" + (parsed.dueTime ?: "09:00")
        tokens.replaceAll { if (it.kind == TokenKind.Date || it.kind == TokenKind.Time) it.copy(kind = TokenKind.Reminder, value = at) else it }
        consume(0, 3, TokenKind.Reminder, at)
        parsed = parsed.copy(reminderAt = at, dueDate = null, dueTime = null)
    }
    if (parsed.dueTime != null && parsed.dueDate == null) parsed = parsed.copy(dueDate = today)
    val content = source.filterIndexed { index, _ -> index !in consumed }.joinToString(" ")
    if (content.isEmpty()) return QuickAddResult(QuickAddParse(content = source.joinToString(" ")), emptyList())
    return QuickAddResult(parsed.copy(content = content), tokens.sortedBy { it.start })
}
