package com.useconatus.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The server's lib/parser tests, ported: both parsers must read text the same way.
// 2026-07-14 is a Tuesday.
private const val TODAY = "2026-07-14"

private fun p(content: String) = QuickAddParse(content = content)

class QuickAddParserTest {
    private val cases = listOf(
        "Buy milk" to p("Buy milk"),
        "Buy milk #groceries" to p("Buy milk").copy(projectName = "groceries"),
        "task #a #b" to p("task").copy(projectName = "b"),
        "Buy milk @errand" to p("Buy milk").copy(labelNames = listOf("errand")),
        "x @a @b @a" to p("x").copy(labelNames = listOf("a", "b")),
        "Fix bug p1" to p("Fix bug").copy(priority = 1),
        "Fix bug P3" to p("Fix bug").copy(priority = 3),
        "p5 is not a priority" to p("p5 is not a priority"),
        "pay rent today" to p("pay rent").copy(dueDate = "2026-07-14"),
        "pay rent tod" to p("pay rent").copy(dueDate = "2026-07-14"),
        "pay rent Tomorrow" to p("pay rent").copy(dueDate = "2026-07-15"),
        "pay rent tmr" to p("pay rent").copy(dueDate = "2026-07-15"),
        "x in 3 days" to p("x").copy(dueDate = "2026-07-17"),
        "x in 1 day" to p("x").copy(dueDate = "2026-07-15"),
        "x wednesday" to p("x").copy(dueDate = "2026-07-15"),
        "x wed" to p("x").copy(dueDate = "2026-07-15"),
        "x tuesday" to p("x").copy(dueDate = "2026-07-21"),
        "x monday" to p("x").copy(dueDate = "2026-07-20"),
        "x next monday" to p("x").copy(dueDate = "2026-07-20"),
        "x next fri" to p("x").copy(dueDate = "2026-07-24"),
        "x next sunday" to p("x").copy(dueDate = "2026-07-26"),
        "x next week" to p("x").copy(dueDate = "2026-07-20"),
        "x 2026-12-31" to p("x").copy(dueDate = "2026-12-31"),
        "x 2026-02-31" to p("x 2026-02-31"),
        "x 20/7" to p("x").copy(dueDate = "2026-07-20"),
        "x 14/7" to p("x").copy(dueDate = "2026-07-14"),
        "x 1/7" to p("x").copy(dueDate = "2027-07-01"),
        "x 5.3" to p("x").copy(dueDate = "2027-03-05"),
        "x 29/2" to p("x 29/2"),
        "x 32/1" to p("x 32/1"),
        "x 30.11.2026" to p("x").copy(dueDate = "2026-11-30"), // with a year
        "x 1/7/2026" to p("x").copy(dueDate = "2026-07-01"), // a year keeps a past date
        "water plants every day" to p("water plants").copy(dueDate = "2026-07-14", recurrence = "every day"),
        "review every 2 weeks" to p("review").copy(dueDate = "2026-07-14", recurrence = "every 2 weeks"),
        "standup every Monday" to p("standup").copy(dueDate = "2026-07-20", recurrence = "every monday"),
        "retro every tue" to p("retro").copy(dueDate = "2026-07-21", recurrence = "every tuesday"),
        "rent every month at 9am" to p("rent").copy(dueDate = "2026-07-14", dueTime = "09:00", recurrence = "every month"),
        "gym every weekday" to p("gym").copy(dueDate = "2026-07-15", recurrence = "every weekday"),
        "review every! 3 days" to p("review").copy(dueDate = "2026-07-14", recurrence = "every! 3 days"),
        "sync every other monday" to p("sync").copy(dueDate = "2026-07-20", recurrence = "every other monday"),
        "do it every so often" to p("do it every so often"),
        "x every day tomorrow" to p("x tomorrow").copy(dueDate = "2026-07-14", recurrence = "every day"),
        "x tomorrow every day" to p("x every day").copy(dueDate = "2026-07-15"),
        "ship today tomorrow" to p("ship tomorrow").copy(dueDate = "2026-07-14"),
        "call mom tomorrow at 17:30" to p("call mom").copy(dueDate = "2026-07-15", dueTime = "17:30"),
        "call mom at 9 tomorrow" to p("call mom").copy(dueDate = "2026-07-15", dueTime = "09:00"),
        "call mom at 5pm" to p("call mom").copy(dueDate = "2026-07-14", dueTime = "17:00"),
        "call mom at 12am" to p("call mom").copy(dueDate = "2026-07-14", dueTime = "00:00"),
        "call mom at 12pm" to p("call mom").copy(dueDate = "2026-07-14", dueTime = "12:00"),
        "call mom at 9:15am" to p("call mom").copy(dueDate = "2026-07-14", dueTime = "09:15"),
        "meet at noon" to p("meet").copy(dueDate = TODAY, dueTime = "12:00"),
        "meet at 99" to p("meet at 99"),
        "meet at 13pm" to p("meet at 13pm"),
        "Buy milk #groceries @errand @home P2 tomorrow at 9am" to p("Buy milk").copy(
            projectName = "groceries", labelNames = listOf("errand", "home"), priority = 2, dueDate = "2026-07-15", dueTime = "09:00",
        ),
        "ship report {friday}" to p("ship report").copy(deadlineDate = "2026-07-17"),
        "ship report {next week}" to p("ship report").copy(deadlineDate = "2026-07-20"),
        "ship report {2026-08-01}" to p("ship report").copy(deadlineDate = "2026-08-01"),
        "ship report {nonsense}" to p("ship report {nonsense}"),
        "ship report {}" to p("ship report {}"),
        "ship report {friday} tomorrow p2" to p("ship report").copy(deadlineDate = "2026-07-17", dueDate = "2026-07-15", priority = 2),
        "ship report {friday} {monday}" to p("ship report {monday}").copy(deadlineDate = "2026-07-17"),
        "deep work for 2h" to p("deep work").copy(durationMinutes = 120),
        "deep work for 90m" to p("deep work").copy(durationMinutes = 90),
        "deep work for 1h30m" to p("deep work").copy(durationMinutes = 90),
        "deep work for 45min" to p("deep work").copy(durationMinutes = 45),
        "deep work 2h" to p("deep work 2h"),
        "wait for the bus" to p("wait for the bus"),
        "deep work tomorrow at 9 for 2h" to p("deep work").copy(dueDate = "2026-07-15", dueTime = "09:00", durationMinutes = 120),
        "#work p1 tomorrow" to p("#work p1 tomorrow"),
        "@waiting" to p("@waiting"),
        "# @ alone" to p("# @ alone"),
        "" to p(""),
        "   " to p(""),
        "!!! ??? %%" to p("!!! ??? %%"),
    )

    @Test fun `reads every case the server reads`() {
        for ((input, expected) in cases) assertEquals(input, expected, parseQuickAdd(input, TODAY))
    }

    @Test fun `numeric dates read month-first for the MM-dd-yyyy format`() {
        assertEquals("2027-03-09", parseQuickAdd("x 3/9", TODAY, dateFormat = "MM/dd/yyyy").dueDate)
        assertEquals("2026-11-30", parseQuickAdd("x 11/30/2026", TODAY, dateFormat = "MM/dd/yyyy").dueDate)
        assertEquals("2026-11-30", parseQuickAdd("x deadline 11/30", TODAY, dateFormat = "MM/dd/yyyy").deadlineDate)
        assertEquals("2026-09-03", parseQuickAdd("x 3/9", TODAY, dateFormat = "dd.MM.yyyy").dueDate)
    }

    @Test fun `weekdays roll over from another day`() {
        // 2026-07-17 is a Friday.
        assertEquals("2026-07-18", parseQuickAdd("x saturday", "2026-07-17").dueDate)
        assertEquals("2026-07-24", parseQuickAdd("x friday", "2026-07-17").dueDate)
        assertEquals("2026-07-20", parseQuickAdd("x next week", "2026-07-17").dueDate)
        assertEquals("2026-07-24", parseQuickAdd("x next friday", "2026-07-17").dueDate)
        assertEquals("2026-08-13", parseQuickAdd("x in 30 days", TODAY).dueDate)
    }

    @Test fun `highlights exact ranges without changing whitespace or emoji`() {
        val input = "  📦 Ship tomorrow\n  at 9am p2 #Home office @deep work {in 3 days} for 45m"
        val result = parseQuickAddPreview(input, TODAY, listOf("Home", "Home office"), listOf("deep work"))
        assertEquals(
            listOf(
                TokenKind.Date to "tomorrow", TokenKind.Time to "at 9am", TokenKind.Priority to "p2", TokenKind.Project to "#Home office",
                TokenKind.Label to "@deep work", TokenKind.Deadline to "{in 3 days}", TokenKind.Duration to "for 45m",
            ),
            result.tokens.map { it.kind to input.substring(it.start, it.end) },
        )
        assertEquals("📦 Ship", result.parsed.content)
        assertEquals("Home office", result.parsed.projectName)
        assertEquals("2026-07-17", result.parsed.deadlineDate)
    }

    @Test fun `follows a phrase as it is typed`() {
        assertEquals(emptyList<QuickAddToken>(), parseQuickAddPreview("Pay rent tomorro", TODAY).tokens)
        assertEquals("2026-07-15", parseQuickAddPreview("Pay rent tomorrow", TODAY).parsed.dueDate)
        assertNull(parseQuickAddPreview("Pay rent tomor", TODAY).parsed.dueDate)
    }

    @Test fun `only known projects and labels count when names are given`() {
        val result = parseQuickAddPreview("Read #Homeopathy @unknown #HOME @Errands", TODAY, listOf("Home"), listOf("errands"))
        assertEquals("Read #Homeopathy @unknown", result.parsed.content)
        assertEquals("Home", result.parsed.projectName)
        assertEquals(listOf("errands"), result.parsed.labelNames)
        assertEquals("Read #Home", parseQuickAddPreview("Read #Home", TODAY, emptyList()).parsed.content)
    }

    @Test fun `a title made only of details keeps them as text`() {
        val result = parseQuickAddPreview("tomorrow p1", TODAY)
        assertEquals(emptyList<QuickAddToken>(), result.tokens)
        assertEquals("tomorrow p1", result.parsed.content)
    }

    @Test fun `oversized phrases stay literal instead of throwing`() {
        assertEquals(emptyList<QuickAddToken>(), parseQuickAddPreview("Plan in 99999999999999999999 days for 999h", TODAY).tokens)
        assertEquals(1440, parseQuickAddPreview("Plan for 24h", TODAY).parsed.durationMinutes)
    }
}

class NaturalLanguageTest {
    private fun task(phrase: String) = parseQuickAdd("Task $phrase", TODAY)
    private val empty = p("Task")

    @Test fun clocks() = listOf(
        "at noon" to "12:00", "at midnight" to "00:00", "noon" to "12:00", "at 5 pm" to "17:00", "at 3:00 PM" to "15:00",
        "at 5 a.m." to "05:00", "at 5 p.m." to "17:00", "at 05:00pm" to "17:00", "5pm" to "17:00", "5 pm" to "17:00",
        "at 12 am" to "00:00", "at 12 p.m." to "12:00", "17:30" to "17:30", "at 5pm." to "17:00",
    ).forEach { (phrase, time) -> assertEquals(phrase, empty.copy(dueDate = TODAY, dueTime = time), task(phrase)) }

    @Test fun partsOfTheDay() = listOf(
        Triple("tomorrow morning", "2026-07-15", "09:00"), Triple("this morning", TODAY, "09:00"),
        Triple("this afternoon", TODAY, "15:00"), Triple("this evening", TODAY, "18:00"), Triple("tonight", TODAY, "18:00"),
        Triple("tomorrow evening", "2026-07-15", "18:00"), Triple("tomorrow morning at 10am", "2026-07-15", "10:00"),
    ).forEach { (phrase, date, time) -> assertEquals(phrase, empty.copy(dueDate = date, dueTime = time), task(phrase)) }

    @Test fun dates() = listOf(
        "in two days" to "2026-07-16", "in 2 weeks" to "2026-07-28", "next month" to "2026-08-14", "next year" to "2027-07-14",
        "in a week" to "2026-07-21", "a week from now" to "2026-07-21", "2 days from now" to "2026-07-16",
        "in twenty-one days" to "2026-08-04", "in twenty one days" to "2026-08-04", "in two months" to "2026-09-14",
        "September 15" to "2026-09-15", "Sep 15" to "2026-09-15", "15 Sep" to "2026-09-15", "15th September" to "2026-09-15",
        "September 15th" to "2026-09-15", "Sep. 15, 2027" to "2027-09-15", "on Friday" to "2026-07-17", "this Friday" to "2026-07-17",
        "coming Monday" to "2026-07-20", "upcoming Monday" to "2026-07-20", "on the 1st" to "2026-08-01", "the 15th" to "2026-07-15",
        "on the twenty-first" to "2026-07-21", "due Friday" to "2026-07-17", "due by Friday" to "2026-07-17", "by Friday" to "2026-07-17",
        "due on Friday" to "2026-07-17", "3/9" to "2026-09-03", "9/3" to "2027-03-09",
    ).forEach { (phrase, date) -> assertEquals(phrase, empty.copy(dueDate = date), task(phrase)) }

    @Test fun durations() = listOf(
        "for 2 hours" to 120, "for 90 minutes" to 90, "for 2 hrs" to 120, "for 30 mins" to 30, "for an hour" to 60,
        "for half an hour" to 30, "for 1h 30m" to 90, "for 1.5 hours" to 90, "for two hours" to 120,
        "for one hour and thirty minutes" to 90, "for 1h30m" to 90, "for an hour and a half" to 90, "for a quarter of an hour" to 15,
    ).forEach { (phrase, minutes) -> assertEquals(phrase, empty.copy(durationMinutes = minutes), task(phrase)) }

    @Test fun repeats() = listOf(
        Triple("daily", "every day", TODAY), Triple("weekly", "every week", TODAY), Triple("monthly", "every month", TODAY),
        Triple("yearly", "every year", TODAY), Triple("every fortnight", "every 2 weeks", TODAY),
        Triple("every weekend", "every saturday, sunday", "2026-07-18"),
        Triple("every Monday and Wednesday", "every monday, wednesday", "2026-07-15"),
        Triple("every Mon, Wed, Fri", "every monday, wednesday, friday", "2026-07-15"),
        Triple("every Mon,Wed,Fri", "every monday, wednesday, friday", "2026-07-15"),
        Triple("the first Friday of every month", "every 1st friday", "2026-08-07"),
        Triple("every 2nd Friday", "every 2nd friday", "2026-08-14"), Triple("every last friday", "every last friday", "2026-07-31"),
        Triple("1st of every month", "every 1st", "2026-08-01"), Triple("every month on the first Friday", "every 1st friday", "2026-08-07"),
        Triple("every! second Friday", "every! 2nd friday", "2026-08-14"), Triple("every! Mon and Wed", "every! monday, wednesday", "2026-07-15"),
    ).forEach { (phrase, rule, date) -> assertEquals(phrase, empty.copy(recurrence = rule, dueDate = date), task(phrase)) }

    @Test fun deadlines() = listOf(
        "deadline Friday" to "2026-07-17", "deadline by September 15" to "2026-09-15", "{in 2 weeks}" to "2026-07-28",
        "{next month}" to "2026-08-14", "{September 15th}" to "2026-09-15", "{in twenty one days}" to "2026-08-04",
        "{ next month }" to "2026-08-14",
    ).forEach { (phrase, date) -> assertEquals(phrase, empty.copy(deadlineDate = date), task(phrase)) }

    @Test fun reminders() = listOf(
        Triple("Remind me tomorrow to call mom", "call mom", "2026-07-15T09:00"),
        Triple("Remind me tomorrow at noon to call mom", "call mom", "2026-07-15T12:00"),
        Triple("Call mom remind me at 5 pm", "Call mom", "2026-07-14T17:00"),
        Triple("Remind me this afternoon to call mom", "call mom", "2026-07-14T15:00"),
        Triple("Remind me tomorrow morning at 10am to call mom", "call mom", "2026-07-15T10:00"),
        Triple("Remind me to call mom tomorrow at 5pm", "call mom", "2026-07-15T17:00"),
    ).forEach { (input, content, at) -> assertEquals(input, p(content).copy(reminderAt = at), parseQuickAdd(input, TODAY)) }

    @Test fun `ambiguous, invalid or unfinished phrases stay as text`() = listOf(
        "around 3", "around 3pm", "at 3ish", "biweekly", "twice a week", "at 13 pm", "at 25:30", "at 5 p", "at 1.5", "for 25.5 hours",
        "for 0.001 hours", "for 1h 30", "for 1.5 ho", "every second", "every 32nd Friday", "every 2th Friday", "every 6th Friday",
        "every 2nd Friday of", "every Monday and Wedn", "every Mon, Wed, F", "every Mon, Wed, nonsense", "{noon}", "{some Friday}",
        "{next month", "February 30", "Sep 31", "remind me tomorrow at 13 pm", "remind me tomorrow at 5 p", "remind me tomorrow around 3",
        "in 99999999999999999999 weeks", "every 9999999999999999999 days",
    ).forEach { phrase -> assertEquals(phrase, p("Task $phrase"), task(phrase)) }

    @Test fun `punctuation can't drop the date`() {
        val input = "Task tomorrow, at 5 pm. p1! #Home office, @deep work!"
        val result = parseQuickAddPreview(input, TODAY, listOf("Home office"), listOf("deep work"))
        assertEquals(
            empty.copy(dueDate = "2026-07-15", dueTime = "17:00", priority = 1, projectName = "Home office", labelNames = listOf("deep work")),
            result.parsed,
        )
        assertEquals(listOf("tomorrow,", "at 5 pm.", "p1!", "#Home office,", "@deep work!"), result.tokens.map { input.substring(it.start, it.end) })
    }

    @Test fun `keeps punctuation that belongs to names`() = assertEquals(
        p("Task!").copy(projectName = "Home!", labelNames = listOf("later?")),
        parseQuickAddPreview("Task! #Home! @later?", TODAY, listOf("Home", "Home!"), listOf("later?")).parsed,
    )

    @Test fun `month and year offsets clamp at month ends`() {
        assertEquals("2026-02-28", parseQuickAdd("Task next month", "2026-01-31").dueDate)
        assertEquals("2029-02-28", parseQuickAdd("Task next year", "2028-02-29").dueDate)
        assertEquals("2027-09-15", parseQuickAdd("Task September 15", "2026-10-01").dueDate)
        assertEquals("2026-05-31", parseQuickAdd("Task the 31st", "2026-04-30").dueDate)
    }
}
