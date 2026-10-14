package com.berend.transit.ui

import com.berend.transit.api.LatLon
import com.berend.transit.api.WalkStep
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class Turn { Continue, SlightLeft, Left, SharpLeft, SlightRight, Right, SharpRight }

internal data class WalkInstruction(val turn: Turn, val street: String?, val meters: Int)

/** Consecutive steps on the same street merged into one. */
internal data class WalkSegment(val street: String?, val meters: Double, val points: List<LatLon>)

internal fun collapseSteps(steps: List<WalkStep>): List<WalkSegment> {
    val segments = mutableListOf<WalkSegment>()
    for (step in steps) {
        val street = step.streetName?.takeIf { it.isNotBlank() }
        val points = step.polyline?.decode().orEmpty()
        val last = segments.lastOrNull()
        if (last != null && last.street == street) {
            segments[segments.lastIndex] = WalkSegment(street, last.meters + step.distance, last.points + points)
        } else {
            segments.add(WalkSegment(street, step.distance, points))
        }
    }
    return segments
}

internal fun instructionsFrom(segments: List<WalkSegment>): List<WalkInstruction> =
    segments.mapIndexed { index, segment ->
        val turn = if (index == 0) Turn.Continue else turnBetween(segments[index - 1].points, segment.points)
        WalkInstruction(turn, segment.street, segment.meters.roundToInt())
    }

// Transitous leaves relativeDirection at CONTINUE for nearly every step, so
// derive the turn from the bearing change at the segment boundary instead.
private fun turnBetween(previous: List<LatLon>, current: List<LatLon>): Turn {
    val incoming = lastBearing(previous) ?: return Turn.Continue
    val outgoing = firstBearing(current) ?: return Turn.Continue
    var delta = outgoing - incoming
    if (delta > 180) delta -= 360
    if (delta < -180) delta += 360
    val magnitude = abs(delta)
    return when {
        magnitude <= 35 -> Turn.Continue
        magnitude <= 70 -> if (delta > 0) Turn.SlightRight else Turn.SlightLeft
        magnitude <= 135 -> if (delta > 0) Turn.Right else Turn.Left
        else -> if (delta > 0) Turn.SharpRight else Turn.SharpLeft
    }
}

// Polylines repeat points (zero deltas), so scan for a distinct neighbour.
private fun firstBearing(points: List<LatLon>): Double? {
    val start = points.firstOrNull() ?: return null
    val next = points.firstOrNull { it != start } ?: return null
    return bearing(start, next)
}

private fun lastBearing(points: List<LatLon>): Double? {
    val end = points.lastOrNull() ?: return null
    val previous = points.lastOrNull { it != end } ?: return null
    return bearing(previous, end)
}

/** Initial bearing from a to b in degrees, 0 = north, 90 = east. */
private fun bearing(a: LatLon, b: LatLon): Double {
    val dLon = Math.toRadians(b.lon - a.lon)
    val latA = Math.toRadians(a.lat)
    val latB = Math.toRadians(b.lat)
    val y = sin(dLon) * cos(latB)
    val x = cos(latA) * sin(latB) - sin(latA) * cos(latB) * cos(dLon)
    return Math.toDegrees(atan2(y, x))
}

/** Great-circle distance in meters. */
internal fun haversineMeters(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * 6_371_000.0 * asin(sqrt(h))
}

/** Index of the segment whose geometry is closest to the fix, 0 when unknown. */
internal fun nearestSegmentIndex(segments: List<WalkSegment>, fix: LatLon): Int =
    segments.withIndex()
        .minByOrNull { (_, segment) -> segment.points.minOfOrNull { haversineMeters(it, fix) } ?: Double.MAX_VALUE }
        ?.index ?: 0

/** "80 m", "240 m" (nearest 10), "1.4 km" from a kilometre up. */
internal fun distanceLabel(meters: Int): String {
    val rounded = if (meters >= 100) (meters + 5) / 10 * 10 else meters
    return if (rounded >= 1000) String.format(Locale.ENGLISH, "%.1f km", rounded / 1000.0) else "$rounded m"
}
