package com.useconatus.app.ui

import com.useconatus.app.ui.format.dueLabel
import com.useconatus.app.ui.format.durationLabel
import com.useconatus.app.ui.format.shortDueLabel
import com.useconatus.app.ui.format.timeLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class FormatTest {
    private val today = LocalDate.of(2026, 10, 26) // a Monday
    private val en = Locale.UK

    @Test fun `names nearby days`() {
        assertEquals("Today", dueLabel(today, today, en))
        assertEquals("Tomorrow", dueLabel(today.plusDays(1), today, en))
        assertEquals("Yesterday", dueLabel(today.minusDays(1), today, en))
        assertEquals("Wednesday", dueLabel(today.plusDays(2), today, en))
        assertEquals("Sunday", dueLabel(today.plusDays(6), today, en))
    }

    @Test fun `falls back to the date, with a year only when it differs`() {
        assertEquals("2 Nov", dueLabel(today.plusDays(7), today, en))
        assertEquals("24 Oct", dueLabel(today.minusDays(2), today, en))
        assertEquals("5 Jan 2027", dueLabel(LocalDate.of(2027, 1, 5), today, en))
    }

    @Test fun `the short form abbreviates weekdays but keeps Tomorrow whole`() {
        assertEquals("Tomorrow", shortDueLabel(today.plusDays(1), today, en))
        assertEquals("Wed", shortDueLabel(today.plusDays(2), today, en))
        assertEquals("2 Nov", shortDueLabel(today.plusDays(7), today, en))
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
