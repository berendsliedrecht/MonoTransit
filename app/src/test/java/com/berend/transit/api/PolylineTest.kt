package com.berend.transit.api

import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineTest {

    // Example from the Google polyline encoding documentation.
    private val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    @Test
    fun `decodes the reference polyline at precision 5`() {
        val points = EncodedPolyline(points = encoded, precision = 5).decode()
        assertEquals(
            listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)),
            points,
        )
    }

    @Test
    fun `precision scales the decoded coordinates`() {
        val points = EncodedPolyline(points = encoded, precision = 6).decode()
        assertEquals(LatLon(3.85, -12.02), points.first())
    }

    @Test
    fun `missing points decode to an empty list`() {
        assertEquals(emptyList<LatLon>(), EncodedPolyline().decode())
    }

    @Test
    fun `truncated polyline keeps the complete points`() {
        // Second point's longitude cut off mid-token.
        val points = EncodedPolyline(points = "_p~iF~ps|U_ulL", precision = 5).decode()
        assertEquals(listOf(LatLon(38.5, -120.2)), points)
    }
}
