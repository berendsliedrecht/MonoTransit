package com.berend.transit.ui

import com.berend.transit.api.LatLon
import com.berend.transit.api.WalkStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkRouteTest {

    private fun segment(street: String?, vararg points: LatLon, meters: Double = 100.0) =
        WalkSegment(street, meters, points.toList())

    private val origin = LatLon(52.0, 5.0)
    private fun north(from: LatLon = origin) = LatLon(from.lat + 0.001, from.lon)
    private fun east(from: LatLon) = LatLon(from.lat, from.lon + 0.001)
    private fun west(from: LatLon) = LatLon(from.lat, from.lon - 0.001)

    @Test
    fun `consecutive steps on the same street collapse`() {
        val segments = collapseSteps(
            listOf(
                WalkStep(distance = 20.0, streetName = "Nieuwegracht"),
                WalkStep(distance = 30.0, streetName = "Nieuwegracht"),
                WalkStep(distance = 10.0, streetName = ""),
                WalkStep(distance = 5.0, streetName = null),
                WalkStep(distance = 40.0, streetName = "Trans"),
            ),
        )
        assertEquals(listOf("Nieuwegracht", null, "Trans"), segments.map { it.street })
        assertEquals(listOf(50.0, 15.0, 40.0), segments.map { it.meters })
    }

    @Test
    fun `turns derive from the bearing change at segment boundaries`() {
        val up = north()
        val instructions = instructionsFrom(
            listOf(
                segment("A", origin, up), // heading north
                segment("B", up, east(up)), // then east: right
                segment("C", east(up), west(east(up))), // reverse west: sharp either side
                segment("D", west(east(up)), north(west(east(up)))), // back north: right
            ),
        )
        assertEquals(Turn.Continue, instructions[0].turn)
        assertEquals(Turn.Right, instructions[1].turn)
        assertTrue(instructions[2].turn == Turn.SharpLeft || instructions[2].turn == Turn.SharpRight)
        assertEquals(Turn.Right, instructions[3].turn)
    }

    @Test
    fun `segments without usable geometry fall back to continue`() {
        val instructions = instructionsFrom(
            listOf(
                segment("A", origin, north()),
                segment("B"), // no points
                segment("C", origin, origin), // no distinct points
            ),
        )
        assertEquals(listOf(Turn.Continue, Turn.Continue, Turn.Continue), instructions.map { it.turn })
    }

    @Test
    fun `haversine distance is close for short hops`() {
        // 0.001 degrees of latitude is about 111 m anywhere on earth
        val d = haversineMeters(origin, north())
        assertTrue("was $d", d > 110 && d < 112)
    }

    @Test
    fun `nearest segment follows the fix along the route`() {
        val up = north()
        val segments = listOf(
            segment("A", origin, up),
            segment("B", up, east(up)),
        )
        assertEquals(0, nearestSegmentIndex(segments, LatLon(origin.lat + 0.0002, origin.lon)))
        assertEquals(1, nearestSegmentIndex(segments, LatLon(up.lat, up.lon + 0.0009)))
    }

    @Test
    fun `distance labels round for readability`() {
        assertEquals("8 m", distanceLabel(8))
        assertEquals("98 m", distanceLabel(98))
        assertEquals("240 m", distanceLabel(243))
        assertEquals("250 m", distanceLabel(247))
        assertEquals("1.0 km", distanceLabel(995))
        assertEquals("1.4 km", distanceLabel(1380))
    }
}
