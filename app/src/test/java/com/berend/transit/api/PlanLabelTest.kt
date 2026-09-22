package com.berend.transit.api

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PlanLabelTest {

    private val today = LocalDate.of(2026, 9, 22)
    private fun at(date: LocalDate, hour: Int, minute: Int): OffsetDateTime =
        date.atTime(hour, minute).atOffset(ZoneOffset.UTC)

    @Test
    fun `today shows clock only`() {
        assertEquals("08:15", at(today, 8, 15).asPlanLabel(today))
    }

    @Test
    fun `tomorrow shows day label with clock`() {
        assertEquals("Tomorrow 08:15", at(today.plusDays(1), 8, 15).asPlanLabel(today))
    }

    @Test
    fun `other days show day and date with clock`() {
        assertEquals("Fri 25 Sep 17:05", at(today.plusDays(3), 17, 5).asPlanLabel(today))
    }
}
