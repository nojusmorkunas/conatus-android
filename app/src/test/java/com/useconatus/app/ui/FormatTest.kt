package com.useconatus.app.ui

import com.useconatus.app.ui.format.dateFormatFor
import com.useconatus.app.ui.format.deadlineLabel
import com.useconatus.app.ui.format.dueLabel
import com.useconatus.app.ui.format.durationLabel
import com.useconatus.app.ui.format.formatDate
import com.useconatus.app.ui.format.pastDateLabel
import com.useconatus.app.ui.format.shortDueLabel
import com.useconatus.app.ui.components.linkRanges
import com.useconatus.app.ui.components.tooLong
import com.useconatus.app.ui.format.timeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class FormatTest {
    private val today = LocalDate.of(2026, 10, 26) // a Monday
    private val en = Locale.UK
    private val dots = "dd.MM.yyyy"

    @Test fun `dates follow each of the account's formats`() {
        val date = LocalDate.of(2026, 3, 9)
        assertEquals("2026-03-09", formatDate(date, "yyyy-MM-dd"))
        assertEquals("03/09/2026", formatDate(date, "MM/dd/yyyy"))
        assertEquals("09/03/2026", formatDate(date, "dd/MM/yyyy"))
        assertEquals("09.03.2026", formatDate(date, "dd.MM.yyyy"))
        assertEquals("09.03.2026", formatDate(date, dateFormatFor("dd.MM.yyyy", Locale.US)))
    }

    @Test fun `without a known format the phone's own order decides`() {
        assertEquals("MM/dd/yyyy", dateFormatFor(null, Locale.US))
        assertEquals("dd/MM/yyyy", dateFormatFor(null, Locale.UK))
        assertEquals("dd.MM.yyyy", dateFormatFor(null, Locale.GERMANY))
        assertEquals("yyyy-MM-dd", dateFormatFor("d MMM", Locale.forLanguageTag("sv-SE")))
    }

    @Test fun `names the coming days`() {
        assertEquals("Today", dueLabel(today, today, en, dots))
        assertEquals("Tomorrow", dueLabel(today.plusDays(1), today, en, dots))
        assertEquals("Wednesday", dueLabel(today.plusDays(2), today, en, dots))
        assertEquals("Sunday", dueLabel(today.plusDays(6), today, en, dots))
    }

    @Test fun `any other day is the date in the account's format`() {
        assertEquals("02.11.2026", dueLabel(today.plusDays(7), today, en, dots))
        assertEquals("25.10.2026", dueLabel(today.minusDays(1), today, en, dots))
        assertEquals("01/05/2027", dueLabel(LocalDate.of(2027, 1, 5), today, en, "MM/dd/yyyy"))
    }

    @Test fun `days gone by name the past week`() {
        assertEquals("Today", pastDateLabel(today, today, en, dots))
        assertEquals("Yesterday", pastDateLabel(today.minusDays(1), today, en, dots))
        assertEquals("Tuesday", pastDateLabel(today.minusDays(6), today, en, dots))
        assertEquals("19.10.2026", pastDateLabel(today.minusDays(7), today, en, dots))
        assertEquals("27.10.2026", pastDateLabel(today.plusDays(1), today, en, dots))
    }

    @Test fun `deadlines read back once they've gone by`() {
        assertEquals("Yesterday", deadlineLabel(today.minusDays(1), today, en, dots))
        assertEquals("Tomorrow", deadlineLabel(today.plusDays(1), today, en, dots))
        assertEquals("16.11.2026", deadlineLabel(today.plusDays(21), today, en, dots))
    }

    @Test fun `the short form abbreviates weekdays but keeps Tomorrow whole`() {
        assertEquals("Tomorrow", shortDueLabel(today.plusDays(1), today, en, dots))
        assertEquals("Wed", shortDueLabel(today.plusDays(2), today, en, dots))
        assertEquals("02.11.2026", shortDueLabel(today.plusDays(7), today, en, dots))
    }

    @Test fun `times follow the phone's clock format`() {
        assertEquals("19:00", timeLabel("19:00", use24Hour = true, Locale.US))
        assertEquals("7:00 PM", timeLabel("19:00", use24Hour = false, Locale.US))
        assertEquals("not a time", timeLabel("not a time", use24Hour = true, Locale.US))
    }

    @Test fun `durations read like the web`() {
        assertEquals("20m", durationLabel(20))
        assertEquals("3h", durationLabel(180))
        assertEquals("1h 30m", durationLabel(90))
    }
}

class InputTest {
    @Test fun `text fits up to the limit, counted trimmed`() {
        assertNull(tooLong("  " + "a".repeat(10) + "  ", 10))
        assertEquals("1 character too long. The limit is 10.", tooLong("a".repeat(11), 10))
        assertEquals("5 characters too long. The limit is 10.", tooLong("a".repeat(15), 10))
    }

    @Test fun `finds web addresses without the punctuation after them`() {
        val text = "See https://example.com/a?b=1, or (http://x.org). Not ftp://y.org"
        assertEquals(listOf("https://example.com/a?b=1", "http://x.org"), linkRanges(text).map(text::substring))
        assertEquals(emptyList<IntRange>(), linkRanges("no links here"))
    }
}
