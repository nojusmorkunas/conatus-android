package com.useconatus.app.ui.task

import com.useconatus.app.data.sync.localTimeToMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderChoicesTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private fun millis(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private val early = millis("2026-10-28T06:00")

    @Test fun `a timed task offers its time and three before it`() {
        val choices = reminderChoices("2026-10-28", "14:00", zone, early, emptySet()) { it.toString() }
        assertEquals(listOf("At the time of the task", "10 minutes before", "30 minutes before", "1 hour before"), choices.map { it.label })
        assertEquals(millis("2026-10-28T13:00"), choices.last().at)
    }

    @Test fun `a task with only a date offers the morning of its day`() {
        val choices = reminderChoices("2026-10-29", null, zone, early, emptySet()) { it.toString() }
        assertEquals(listOf("On the day, at 09:00"), choices.map { it.label })
        assertEquals(millis("2026-10-29T09:00"), choices.single().at)
    }

    @Test fun `past and existing times are left out, and no date offers nothing`() {
        val now = millis("2026-10-28T13:40")
        val choices = reminderChoices("2026-10-28", "14:00", zone, now, setOf(millis("2026-10-28T13:50"))) { it.toString() }
        assertEquals(listOf("At the time of the task"), choices.map { it.label })
        assertEquals(emptyList<ReminderChoice>(), reminderChoices(null, null, zone, now, emptySet()) { it.toString() })
    }

    @Test fun `quick add's wall-clock time is read in the account's zone`() {
        assertEquals(millis("2026-10-28T09:00"), localTimeToMillis("2026-10-28T09:00", zone))
        assertNull(localTimeToMillis("tomorrow", zone))
    }
}
