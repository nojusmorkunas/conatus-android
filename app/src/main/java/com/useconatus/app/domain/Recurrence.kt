package com.useconatus.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// A port of the server's lib/parser/english.ts and lib/recurrence/index.ts, so the phone reads
// repeats and moves tasks on exactly as the server does. Keep the two in step: the tests mirror
// the server's own test cases. Dates are ISO strings ("2026-07-14"), as on the server.

val WEEKDAY_NAMES = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")

private val EVERY_BANG = Regex("^every![.,!?;:]*$", RegexOption.IGNORE_CASE)

/** Punctuation stays in source ranges and is removed only from matching copies. The ! in every! is syntax. */
fun cleanWord(word: String): String = if (EVERY_BANG.matches(word)) "every!" else word.replace(Regex("[.,!?;:]+$"), "")

/** 0 for Sunday to 6 for Saturday, or -1. */
fun weekdayIndex(word: String): Int {
    val normalized = cleanWord(word).lowercase()
    return WEEKDAY_NAMES.indexOfFirst { it == normalized || (normalized.length == 3 && it.startsWith(normalized)) }
}

private val SMALL = listOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
)
private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

fun numberValue(text: String): Double? {
    val value = text.lowercase().trim().replace("-", " ")
    if (Regex("^\\d+(?:\\.\\d+)?$").matches(value)) return value.toDouble().takeIf { it.isFinite() }
    if (value == "a" || value == "an") return 1.0
    if (Regex("^half(?: an?)?$").matches(value)) return 0.5
    if (Regex("^(?:a )?quarter(?: of an?)?$").matches(value)) return 0.25
    val small = SMALL.indexOf(value)
    if (small >= 0) return small.toDouble()
    val parts = value.split(" ")
    val ten = TENS.indexOf(parts[0])
    val ones = parts.getOrNull(1)
    val one = ones?.let { SMALL.indexOf(it) } ?: -1
    return if (ten >= 2 && parts.getOrNull(2).isNullOrEmpty() && (ones.isNullOrEmpty() || one in 1..9)) (ten * 10 + if (ones.isNullOrEmpty()) 0 else one).toDouble() else null
}

fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

private val ORDINALS = listOf(
    "", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh",
    "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth", "twentieth",
)

fun ordinalValue(text: String): Int? {
    val value = text.lowercase().replace("-", " ")
    Regex("^(\\d+)(st|nd|rd|th)$").find(value)?.let { numeric ->
        val n = numeric.groupValues[1].toIntOrNull() ?: return null
        return if (n in 1..31 && ordinal(n) == value) n else null
    }
    val direct = ORDINALS.indexOf(value)
    if (direct > 0) return direct
    if (value == "thirtieth") return 30
    val parts = value.split(" ")
    if (parts.size != 2) return null
    val one = ORDINALS.indexOf(parts[1])
    if (parts[0] == "twenty" && one in 1..9) return 20 + one
    return if (parts[0] == "thirty" && one == 1) 31 else null
}

private val ALIASES = mapOf(
    "daily" to "day", "weekly" to "week", "monthly" to "month", "yearly" to "year", "annually" to "year", "fortnightly" to "2 weeks",
)

private fun monthlyAnchor(text: String): String? {
    val words = text.replace(Regex("^the "), "").split(" ")
    val day = weekdayIndex(words.last())
    if (day >= 0) {
        val position = words.dropLast(1).joinToString(" ")
        if (position == "last") return "last ${WEEKDAY_NAMES[day]}"
        val nth = ordinalValue(position)
        return if (nth != null && nth <= 5) "${ordinal(nth)} ${WEEKDAY_NAMES[day]}" else null
    }
    if (Regex("^last days?$").matches(text)) return "last day"
    return ordinalValue(text.replace(Regex("^the "), ""))?.let(::ordinal)
}

private val UNIT = Regex("^(day|week|month|year)s?$")

private fun parseRuleBody(words: List<String>): String? {
    val text = words.joinToString(" ").replace(Regex("^the "), "").replace(Regex(" of (?:the )?month$"), "")
    // A bare "second" can be a time unit; it isn't enough to infer the 2nd of a month.
    val anchored = if (text == "second") null else monthlyAnchor(text)
    if (anchored != null) return anchored

    // A list is one rule, unique and Monday-first, never just its first weekday.
    if (text.contains(",") || Regex("\\band\\b").containsMatchIn(text)) {
        val days = text.split(Regex("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+")).map(::weekdayIndex)
        if (days.size < 2 || days.any { it < 0 }) return null
        return days.distinct().sortedBy { (it + 6) % 7 }.joinToString(", ") { WEEKDAY_NAMES[it] }
    }
    if (words.size == 1) {
        val word = words[0]
        val day = weekdayIndex(word)
        if (day >= 0) return WEEKDAY_NAMES[day]
        if (Regex("^weekdays?$").matches(word)) return "weekday"
        if (Regex("^weekends?$").matches(word)) return "saturday, sunday"
        if (word == "fortnight") return "2 weeks"
        return UNIT.find(word)?.groupValues?.get(1)
    }
    if (words.size == 2 && words[0] == "other") {
        val day = weekdayIndex(words[1])
        if (day >= 0) return "other ${WEEKDAY_NAMES[day]}"
        return UNIT.find(words[1])?.let { "2 ${it.groupValues[1]}s" }
    }
    val unit = UNIT.find(words.last()) ?: return null
    val n = numberValue(words.dropLast(1).joinToString(" ")) ?: return null
    if (n % 1.0 != 0.0 || n < 1 || n > 999) return null
    return if (n == 1.0) unit.groupValues[1] else "${n.toInt()} ${unit.groupValues[1]}s"
}

/**
 * "every <body>" or "every! <body>" as the canonical rule, or null. "every!" restarts the
 * schedule from the day the task is completed. Canonical rules parse to themselves.
 */
fun parseRecurrence(text: String): String? {
    val normalized = text.trim().lowercase().replace(Regex("[.!?;:]+$"), "").replace(Regex("\\s+"), " ")
    ALIASES[normalized]?.let { return "every $it" }
    Regex("^(?:the )?(.+?) of (every!?) month$").find(normalized)?.let { inverted ->
        return monthlyAnchor(inverted.groupValues[1])?.let { "${inverted.groupValues[2]} $it" }
    }
    Regex("^(every!?) month on (?:the )?(.+)$").find(normalized)?.let { onMonth ->
        return monthlyAnchor(onMonth.groupValues[2])?.let { "${onMonth.groupValues[1]} $it" }
    }
    val parts = normalized.split(" ")
    val head = parts[0]
    if ((head != "every" && head != "every!") || parts.size == 1) return null
    return parseRuleBody(parts.drop(1))?.let { "$head $it" }
}

private fun date(text: String) = LocalDate.parse(text)

/** 0 for Sunday to 6 for Saturday, like JavaScript's getUTCDay. */
private fun LocalDate.weekday() = dayOfWeek.value % 7

/**
 * The next due date: after [from] and never in the past, so an overdue task catches up instead
 * of replaying old dates. Month and year steps count from [from], so a clamped 28 February
 * doesn't move a 31st anchor for good. "every!" rules count from [today], the completion day.
 */
fun nextOccurrence(rule: String, from: String, today: String): String {
    val canonical = parseRecurrence(rule) ?: throw IllegalArgumentException("Unsupported recurrence: $rule")
    val words = canonical.split(" ")
    val start = date(if (words[0] == "every!") today else from)
    val now = date(today)
    val floor = maxOf(start, now)

    val monthlyWeekday = Regex("^(last|[1-5](?:st|nd|rd|th)) (\\w+)$").find(words.drop(1).joinToString(" "))
    val monthlyDay = monthlyWeekday?.let { weekdayIndex(it.groupValues[2]) } ?: -1
    if (monthlyWeekday != null && monthlyDay >= 0) {
        for (offset in 0L until 24L) {
            val month = floor.withDayOfMonth(1).plusMonths(offset)
            val last = month.withDayOfMonth(month.lengthOfMonth())
            val wanted = if (monthlyWeekday.groupValues[1] == "last") {
                last.dayOfMonth - (last.weekday() - monthlyDay + 7) % 7
            } else {
                1 + (monthlyDay - month.weekday() + 7) % 7 + (monthlyWeekday.groupValues[1].take(1).toInt() - 1) * 7
            }
            // A fifth weekday skips months that don't have one.
            if (wanted > last.dayOfMonth) continue
            val candidate = month.withDayOfMonth(wanted)
            if (candidate > floor) return candidate.toString()
        }
        throw IllegalStateException("Could not advance monthly weekday: $rule")
    }

    if (canonical.contains(",")) {
        val days = canonical.replace(Regex("^every!? "), "").split(", ").map(::weekdayIndex)
        return floor.plusDays(days.minOf { ((it - floor.weekday() + 7) % 7).takeIf { d -> d != 0 } ?: 7 }.toLong()).toString()
    }

    // "other <weekday>": a fortnight from an anchor on that weekday, keeping its parity when overdue.
    if (words[1] == "other") {
        val day = weekdayIndex(words[2])
        val diff = (day - start.weekday() + 7) % 7
        val first = start.plusDays((if (diff == 0) 14 else diff).toLong())
        val elapsed = ChronoUnit.DAYS.between(first, now)
        return first.plusDays(maxOf(0L, Math.floorDiv(elapsed, 14L) + 1) * 14).toString()
    }

    if (words[1] == "weekday") {
        var next = floor.plusDays(1)
        while (next.weekday() == 0 || next.weekday() == 6) next = next.plusDays(1)
        return next.toString()
    }

    // "last day" and "<Nth>": that day each month, clamped to the month's length.
    val nth = Regex("^(\\d+)(?:st|nd|rd|th)$").find(words[1])
    if (words[1] == "last" || nth != null) {
        val wanted = nth?.groupValues?.get(1)?.toInt() ?: 31
        var month = floor.withDayOfMonth(1)
        repeat(5000) {
            val candidate = month.withDayOfMonth(minOf(wanted, month.lengthOfMonth()))
            if (candidate > floor) return candidate.toString()
            month = month.plusMonths(1)
        }
        throw IllegalStateException("recurrence walk exceeded 5000 months: $rule")
    }

    val day = weekdayIndex(words[1])
    if (day >= 0) {
        val diff = (day - floor.weekday() + 7) % 7
        return floor.plusDays((if (diff == 0) 7 else diff).toLong()).toString()
    }

    val n = if (words.size == 3) words[1].toInt() else 1
    val unit = words.last().removeSuffix("s")
    val months = when (unit) { "month" -> n; "year" -> n * 12; else -> 0 }
    val days = when (unit) { "day" -> n; "week" -> n * 7; else -> 0 }
    // Jump close to the floor first, so a long-overdue daily task doesn't walk through old dates.
    val first = if (months > 0) {
        val elapsedMonths = (floor.year - start.year) * 12 + floor.monthValue - start.monthValue
        maxOf(1, elapsedMonths / months)
    } else {
        maxOf(1L, ChronoUnit.DAYS.between(start, floor) / days + 1).toInt()
    }
    var step = first
    while (true) {
        val candidate = if (months > 0) start.plusMonths(step.toLong() * months) else start.plusDays(step.toLong() * days)
        if (candidate > floor) return candidate.toString()
        step++
    }
}

/** End dates are inclusive. Null means the repeating task has had its last run. */
fun nextOccurrenceWithinEnd(rule: String, from: String, today: String, endDate: String?): String? {
    val next = nextOccurrence(rule, from, today)
    return if (endDate != null && next > endDate) null else next
}

/** The date a rule starts on when no date was given: today for intervals, else the next matching day. */
fun firstOccurrence(rule: String, today: String): String {
    val canonical = parseRecurrence(rule) ?: throw IllegalArgumentException("Unsupported recurrence: $rule")
    return if (Regex("^every!? (\\d+ )?(day|week|month|year)s?$").matches(canonical)) today else nextOccurrence(canonical, today, today)
}
