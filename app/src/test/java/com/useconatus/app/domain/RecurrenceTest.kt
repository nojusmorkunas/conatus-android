package com.useconatus.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The server's lib/recurrence tests, ported: both sides must move repeating tasks on alike.
// 2026-07-14 is a Tuesday.
class RecurrenceTest {
    private val cases = listOf(
        "every day" to "every day", "Every Day" to "every day", "every week" to "every week", "every month" to "every month",
        "every year" to "every year", "every days" to "every day", "every 1 day" to "every day", "every 2 weeks" to "every 2 weeks",
        "every 3 day" to "every 3 days", "every 30 days" to "every 30 days", "every monday" to "every monday", "every Mon" to "every monday",
        "every sun" to "every sunday", "every 2 years" to "every 2 years", "every 10 Years" to "every 10 years", "every 0 days" to null,
        "every" to null, "daily" to "every day", "everyday" to null, "every! day" to "every! day", "Every! 3 Weeks" to "every! 3 weeks",
        "every! monday" to "every! monday", "every! weekday" to "every! weekday", "every! last day" to "every! last day",
        "every! 15th" to "every! 15th", "every! other tue" to "every! other tuesday", "every!" to null, "every! garbage" to null,
        "every! 0 days" to null, "every other day" to "every 2 days", "every Other Week" to "every 2 weeks",
        "every other months" to "every 2 months", "every other monday" to "every other monday", "every other Mon" to "every other monday",
        "every other year" to "every 2 years", "every other" to null, "every weekday" to "every weekday", "every weekdays" to "every weekday",
        "every last day" to "every last day", "every last days" to "every last day", "every last" to null, "every 1st" to "every 1st",
        "every 2nd" to "every 2nd", "every 3rd" to "every 3rd", "every 15th" to "every 15th", "every 21st" to "every 21st",
        "every 22nd" to "every 22nd", "every 31st" to "every 31st", "every 0th" to null, "every 32nd" to null, "every 31th" to null,
        "every 3st" to null, "every 15" to null, "weekly" to "every week", "monthly" to "every month", "yearly" to "every year",
        "fortnightly" to "every 2 weeks", "biweekly" to null, "twice a week" to null, "every fortnight" to "every 2 weeks",
        "every two weeks" to "every 2 weeks", "every twenty-one days" to "every 21 days", "every weekend" to "every saturday, sunday",
        "every Monday and Wednesday" to "every monday, wednesday", "every Mon,Wed,Fri" to "every monday, wednesday, friday",
        "every Fri, Mon, and Wed" to "every monday, wednesday, friday", "every monday, monday" to "every monday",
        "every! Sun and Sat" to "every! saturday, sunday", "every Mon and" to null, "every Mon and someday" to null,
        "every 2nd Friday" to "every 2nd friday", "every fifth Monday" to "every 5th monday", "every last Fri" to "every last friday",
        "the first Friday of every month" to "every 1st friday", "the first Friday of every! month" to "every! 1st friday",
        "1st of every month" to "every 1st", "every month on the twenty-first" to "every 21st", "every 6th Friday" to null,
        "every 2th Friday" to null, "every twenty first nonsense" to null, "every 999999999999999 days" to null,
    )

    @Test fun `parses rules like the server`() {
        for ((input, expected) in cases) assertEquals(input, expected, parseRecurrence(input))
    }

    @Test fun `canonical rules parse to themselves`() {
        for (canonical in cases.mapNotNull { it.second }) assertEquals(canonical, parseRecurrence(canonical))
    }

    private fun next(rule: String, from: String, today: String) = nextOccurrence(rule, from, today)

    @Test fun `steps and catches up`() {
        val today = "2026-07-14"
        assertEquals("2026-07-15", next("every day", today, today))
        assertEquals("2026-07-15", next("every day", "2026-07-07", today))
        assertEquals("2026-07-16", next("every 3 days", "2026-07-07", today))
        assertEquals("2026-07-21", next("every week", today, today))
        assertEquals("2026-07-20", next("every monday", "2026-07-06", today))
        assertEquals("2026-07-21", next("every tuesday", today, today))
        assertEquals("2026-07-29", next("every wednesday", "2026-07-22", today))
        assertEquals("2026-02-28", next("every month", "2026-01-31", "2026-01-31"))
        assertEquals("2026-03-31", next("every month", "2026-01-31", "2026-03-01"))
        assertEquals("2025-02-28", next("every year", "2024-02-29", "2024-02-29"))
    }

    @Test fun `every! counts from the completion day`() {
        val today = "2026-07-14"
        assertEquals("2026-07-17", next("every! 3 days", "2026-07-07", today))
        assertEquals("2026-07-15", next("every! day", today, today))
        assertEquals("2026-07-28", next("every! 2 weeks", "2026-06-01", today))
        assertEquals("2026-07-20", next("every! monday", "2026-07-27", today))
        assertEquals("2026-08-03", next("every monday", "2026-07-27", today))
        assertEquals("2026-07-20", next("every! monday", "2026-01-01", "2026-07-13"))
    }

    @Test fun weekdays() {
        val today = "2026-07-14"
        assertEquals("2026-07-15", next("every weekday", today, today))
        assertEquals("2026-07-20", next("every weekday", "2026-07-17", "2026-07-17"))
        assertEquals("2026-07-20", next("every weekday", "2026-07-18", "2026-07-18"))
        assertEquals("2026-07-20", next("every weekday", "2026-07-01", "2026-07-17"))
        assertEquals("2026-07-27", next("every weekday", "2026-07-24", today))
        assertEquals("2026-07-15", next("every! weekday", "2026-07-24", today))
    }

    @Test fun `every other weekday keeps its fortnight`() {
        assertEquals("2026-07-20", next("every other monday", "2026-07-06", "2026-07-06"))
        assertEquals("2026-07-27", next("every other monday", "2026-07-13", "2026-07-14"))
        assertEquals("2026-08-17", next("every other monday", "2026-07-06", "2026-08-04"))
        assertEquals("2026-08-10", next("every monday", "2026-07-06", "2026-08-04"))
        assertEquals("2026-07-13", next("every other monday", "2026-07-08", "2026-07-08"))
        assertEquals("2026-08-10", next("every other monday", "2026-07-27", "2026-07-14"))
        assertEquals("2026-07-20", next("every! other monday", "2026-01-05", "2026-07-14"))
    }

    @Test fun `month ends and month days`() {
        assertEquals("2026-02-28", next("every last day", "2026-01-31", "2026-01-31"))
        assertEquals("2028-02-29", next("every last day", "2028-01-31", "2028-02-01"))
        assertEquals("2026-07-31", next("every last day", "2026-06-30", "2026-06-30"))
        assertEquals("2027-01-31", next("every last day", "2026-12-31", "2026-12-31"))
        assertEquals("2026-07-31", next("every last day", "2026-07-10", "2026-07-10"))
        assertEquals("2026-07-31", next("every last day", "2026-01-31", "2026-07-14"))
        assertEquals("2026-08-15", next("every 15th", "2026-07-15", "2026-07-15"))
        assertEquals("2026-07-15", next("every 15th", "2026-05-15", "2026-07-14"))
        assertEquals("2026-08-01", next("every 1st", "2026-07-01", "2026-07-01"))
        assertEquals("2026-02-28", next("every 31st", "2026-01-31", "2026-01-31"))
        assertEquals("2026-03-31", next("every 31st", "2026-01-31", "2026-02-28"))
        assertEquals("2026-03-31", next("every 31st", "2026-01-31", "2026-03-01"))
        assertEquals("2026-08-15", next("every 15th", "2026-07-20", "2026-07-20"))
        assertEquals("2027-01-15", next("every 15th", "2026-12-15", "2026-12-15"))
        assertEquals("2026-07-15", next("every! 15th", "2025-01-15", "2026-07-14"))
    }

    @Test fun `multi-year intervals`() {
        assertEquals("2028-07-14", next("every 2 years", "2026-07-14", "2026-07-14"))
        assertEquals("2028-02-29", next("every 2 years", "2020-02-29", "2026-07-14"))
    }

    @Test fun `end dates are inclusive`() {
        assertEquals("2026-07-21", nextOccurrenceWithinEnd("every week", "2026-07-14", "2026-07-14", "2026-07-21"))
        assertNull(nextOccurrenceWithinEnd("every week", "2026-07-21", "2026-07-21", "2026-07-21"))
        assertEquals("2026-09-11", nextOccurrenceWithinEnd("every 2nd friday", "2026-08-14", "2026-08-14", "2026-09-11"))
        assertNull(nextOccurrenceWithinEnd("every 2nd friday", "2026-09-11", "2026-09-11", "2026-09-11"))
    }

    @Test fun `first occurrences`() {
        val today = "2026-07-14"
        assertEquals(today, firstOccurrence("every day", today))
        assertEquals(today, firstOccurrence("every 3 weeks", today))
        assertEquals(today, firstOccurrence("every! month", today))
        assertEquals("2026-07-17", firstOccurrence("every friday", today))
        assertEquals("2026-07-21", firstOccurrence("every tuesday", today))
        assertEquals("2026-07-20", firstOccurrence("every 20th", today))
        assertEquals("2026-07-15", firstOccurrence("every weekday", today))
    }

    @Test fun `weekday lists and monthly weekdays`() = listOf(
        listOf("every monday, wednesday, friday", "2026-07-13", "2026-07-14", "2026-07-15"),
        listOf("every monday, wednesday, friday", "2026-07-17", "2026-07-17", "2026-07-20"),
        listOf("every saturday, sunday", "2026-07-18", "2026-07-18", "2026-07-19"),
        listOf("every saturday, sunday", "2026-07-19", "2026-07-19", "2026-07-25"),
        listOf("every 2nd friday", "2026-07-10", "2026-07-14", "2026-08-14"),
        listOf("every 2nd friday", "2026-08-14", "2026-08-14", "2026-09-11"),
        listOf("every 1st friday", "2026-07-03", "2026-08-07", "2026-09-04"),
        listOf("every last friday", "2026-01-30", "2026-01-30", "2026-02-27"),
        listOf("every last friday", "2028-01-28", "2028-01-28", "2028-02-25"),
        listOf("every 5th monday", "2026-06-29", "2026-06-29", "2026-08-31"),
        listOf("every 5th monday", "2026-08-31", "2026-08-31", "2026-11-30"),
        listOf("every 1st friday", "2020-01-03", "2026-07-14", "2026-08-07"),
        listOf("every! 2nd friday", "2026-12-11", "2026-07-14", "2026-08-14"),
        listOf("every! monday, wednesday", "2026-12-14", "2026-07-14", "2026-07-15"),
    ).forEach { (rule, from, today, expected) -> assertEquals("$rule from $from on $today", expected, next(rule, from, today)) }

    @Test(expected = IllegalArgumentException::class) fun `rejects unsupported rules`() {
        next("every garbage", "2026-07-14", "2026-07-14")
    }
}
