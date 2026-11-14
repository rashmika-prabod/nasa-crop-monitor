package com.cropexpert.app.ui.location

import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.sin

fun parseBoundaryJson(boundaryJson: String?): List<LatLng> {
    if (boundaryJson.isNullOrBlank()) return emptyList()
    return try {
        val ring = JSONArray(boundaryJson).optJSONArray(0) ?: return emptyList()
        val points = mutableListOf<LatLng>()
        for (index in 0 until ring.length()) {
            val coordinate = ring.optJSONArray(index) ?: return emptyList()
            if (coordinate.length() != 2) return emptyList()
            val longitude = coordinate.optDouble(0, Double.NaN)
            val latitude = coordinate.optDouble(1, Double.NaN)
            if (!longitude.isFinite() || !latitude.isFinite() ||
                longitude !in -180.0..180.0 || latitude !in -90.0..90.0
            ) {
                return emptyList()
            }
            points.add(LatLng(latitude, longitude))
        }
        val openRing = if (points.size >= 2 && points.first() == points.last()) {
            points.dropLast(1)
        } else {
            points
        }
        openRing.takeIf { it.size >= 3 } ?: emptyList()
    } catch (_: org.json.JSONException) {
        emptyList()
    }
}

fun List<LatLng>.toBoundaryJson(): String? {
    if (size < 3) return null
    val ring = if (first() == last()) this else this + first()
    return ring.joinToString(prefix = "[[", postfix = "]]", separator = ",") {
        "[${it.longitude},${it.latitude}]"
    }
}

fun List<LatLng>.fieldCentroid(): LatLng {
    if (size < 3) {
        return LatLng(map { it.latitude }.average(), map { it.longitude }.average())
    }
    var twiceArea = 0.0
    var longitudeMoment = 0.0
    var latitudeMoment = 0.0
    for (index in indices) {
        val current = this[index]
        val next = this[(index + 1) % size]
        val cross = current.longitude * next.latitude - next.longitude * current.latitude
        twiceArea += cross
        longitudeMoment += (current.longitude + next.longitude) * cross
        latitudeMoment += (current.latitude + next.latitude) * cross
    }
    if (abs(twiceArea) < 1e-12) {
        return LatLng(map { it.latitude }.average(), map { it.longitude }.average())
    }
    return LatLng(
        latitudeMoment / (3 * twiceArea),
        longitudeMoment / (3 * twiceArea)
    )
}

fun List<LatLng>.areaSquareMeters(): Double {
    if (size < 3) return 0.0
    val radius = 6_371_008.8
    var total = 0.0
    for (index in indices) {
        val current = this[index]
        val next = this[(index + 1) % size]
        val deltaLongitude = Math.toRadians(next.longitude - current.longitude)
        total += deltaLongitude * (
            2 +
                sin(Math.toRadians(current.latitude)) +
                sin(Math.toRadians(next.latitude))
            )
    }
    return abs(total) * radius * radius / 2
}

fun List<LatLng>.hasSelfIntersection(): Boolean {
    if (size < 4) return false
    val edges = (0 until size - 1).map { this[it] to this[it + 1] } +
        (last() to first())
    for (firstEdge in edges.indices) {
        for (secondEdge in firstEdge + 1 until edges.size) {
            if (secondEdge == firstEdge + 1 ||
                (firstEdge == 0 && secondEdge == edges.lastIndex)
            ) {
                continue
            }
            if (segmentsIntersect(
                    edges[firstEdge].first,
                    edges[firstEdge].second,
                    edges[secondEdge].first,
                    edges[secondEdge].second
                )
            ) {
                return true
            }
        }
    }
    return false
}

fun LatLng.isInsideBoundary(points: List<LatLng>): Boolean {
    if (points.size < 3) return false
    var inside = false
    var previous = points.last()
    for (current in points) {
        val crossesLatitude = (current.latitude > latitude) != (previous.latitude > latitude)
        if (crossesLatitude) {
            val crossingLongitude = (previous.longitude - current.longitude) *
                (latitude - current.latitude) /
                (previous.latitude - current.latitude) + current.longitude
            if (longitude < crossingLongitude) inside = !inside
        }
        previous = current
    }
    return inside
}

private fun segmentsIntersect(a: LatLng, b: LatLng, c: LatLng, d: LatLng): Boolean {
    fun orientation(first: LatLng, second: LatLng, third: LatLng): Double =
        (second.longitude - first.longitude) * (third.latitude - first.latitude) -
            (second.latitude - first.latitude) * (third.longitude - first.longitude)

    fun onSegment(first: LatLng, second: LatLng, point: LatLng): Boolean =
        point.longitude in minOf(first.longitude, second.longitude)..maxOf(first.longitude, second.longitude) &&
            point.latitude in minOf(first.latitude, second.latitude)..maxOf(first.latitude, second.latitude)

    val o1 = orientation(a, b, c)
    val o2 = orientation(a, b, d)
    val o3 = orientation(c, d, a)
    val o4 = orientation(c, d, b)
    if ((o1 > 0 && o2 < 0 || o1 < 0 && o2 > 0) &&
        (o3 > 0 && o4 < 0 || o3 < 0 && o4 > 0)
    ) {
        return true
    }
    return (o1 == 0.0 && onSegment(a, b, c)) ||
        (o2 == 0.0 && onSegment(a, b, d)) ||
        (o3 == 0.0 && onSegment(c, d, a)) ||
        (o4 == 0.0 && onSegment(c, d, b))
}
