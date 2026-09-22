package com.berend.transit.ui

import com.berend.transit.api.Itinerary
import com.berend.transit.api.PlanLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDetailRowsTest {

    private fun transit(name: String) = PlanLeg(mode = "BUS", routeShortName = name, duration = 600)
    private fun walk(seconds: Long) = PlanLeg(mode = "WALK", duration = seconds)

    @Test
    fun `transit legs flatten to header and two stop rows`() {
        val rows = legRows(Itinerary(legs = listOf(transit("1"))))
        assertEquals(3, rows.size)
        assertTrue(rows[0] is HeaderRow)
        assertTrue(rows[1] is StopRow)
        assertTrue(rows[2] is StopRow)
    }

    @Test
    fun `divider follows every transit leg except the last`() {
        val rows = legRows(Itinerary(legs = listOf(transit("1"), walk(120), transit("2"), transit("3"))))
        val dividers = rows.filterIsInstance<StopRow>().map { it.dividerAfter }
        assertEquals(listOf(false, true, false, true, false, false), dividers)
    }

    @Test
    fun `walks under a minute are dropped`() {
        val rows = legRows(Itinerary(legs = listOf(walk(59), transit("1"), walk(60))))
        assertEquals(listOf(WalkRow(1)), rows.filterIsInstance<WalkRow>())
    }
}
