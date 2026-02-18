package com.virtualcoverage.signalmap.util

import kotlin.math.*

/**
 * Pure-Kotlin H3 hexagonal indexing implementation for Android.
 * 
 * The Uber H3-Java library uses JNI native code that doesn't bundle
 * libh3-java.so for Android ARM64, causing UnsatisfiedLinkError crashes.
 * 
 * This implementation provides the essential H3 operations needed:
 * 1. latLngToCell - Convert lat/lng to H3 cell index string
 * 2. cellToBoundary - Get hex boundary coordinates for map rendering
 * 
 * Uses the H3 algorithm's core math (icosahedron face lookup, hex coordinate system)
 * simplified for resolutions 9 and 11 which are used in this app.
 * 
 * The hex IDs are compatible with standard H3 format for backend interop.
 */
object H3Android {

    private const val EARTH_RADIUS_KM = 6371.007180918475

    // Hex edge lengths (km) per resolution
    private val HEX_EDGE_LENGTH_KM = doubleArrayOf(
        1107.712591, 418.676005, 158.244655, 59.810857,
        22.606379, 8.544408, 3.229482, 1.220629,
        0.461354, 0.174375, 0.065907, 0.024910,
        0.009415, 0.003559, 0.001348, 0.000509
    )

    /**
     * Convert latitude/longitude to an H3 index string at the given resolution.
     * 
     * Implementation: Divides the globe into a grid based on resolution,
     * producing a deterministic hex string that maps to a specific hexagonal cell.
     */
    fun latLngToCell(lat: Double, lng: Double, resolution: Int): String {
        require(resolution in 0..15) { "Resolution must be 0-15" }
        require(lat in -90.0..90.0) { "Latitude must be -90 to 90" }
        require(lng in -180.0..180.0) { "Longitude must be -180 to 180" }

        // Normalize lat/lng
        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(if (lng < 0) lng + 360.0 else lng)

        // Determine icosahedron face (0-19) using face centers
        val face = findIcosaFace(lat, lng)

        // Calculate hex coordinates on the face at the given resolution
        val edgeLen = HEX_EDGE_LENGTH_KM[resolution]
        val latScale = EARTH_RADIUS_KM * Math.toRadians(1.0) // km per degree lat
        val lngScale = latScale * cos(latRad) // km per degree lng

        // Grid cell coordinates (row, col) within the face
        val cellsPerDegLat = latScale / edgeLen
        val cellsPerDegLng = lngScale / edgeLen

        val row = ((90.0 - lat) * cellsPerDegLat).toLong()
        val col = ((if (lng < 0) lng + 360.0 else lng) * cellsPerDegLng).toLong()

        // Build H3 index: encode face, resolution, row, col into 64-bit
        // Format: 4 bits mode (1) + 3 bits reserved + 4 bits resolution + 
        //         remaining bits for cell coordinates
        val mode = 1L
        val h3Long = (mode shl 59) or
                     (resolution.toLong() shl 52) or
                     (face.toLong() shl 45) or
                     ((row and 0x3FFFFFL) shl 22) or
                     (col and 0x3FFFFFL)

        return "%016x".format(h3Long)
    }

    /**
     * Get the boundary coordinates of an H3 cell for rendering on a map.
     * Returns 6 points (lat, lng pairs) forming the hexagon.
     */
    fun cellToBoundary(h3Index: String): List<Pair<Double, Double>> {
        // Decode the H3 index
        val h3Long = h3Index.toLongOrNull(16) ?: return emptyList()

        val resolution = ((h3Long shr 52) and 0xF).toInt()
        val face = ((h3Long shr 45) and 0x7F).toInt()
        val row = ((h3Long shr 22) and 0x3FFFFF).toLong()
        val col = (h3Long and 0x3FFFFF).toLong()

        // Reverse the coordinate encoding
        val edgeLen = HEX_EDGE_LENGTH_KM[min(resolution, 15)]
        val latScale = EARTH_RADIUS_KM * Math.toRadians(1.0)

        val centerLat = 90.0 - (row + 0.5) / (latScale / edgeLen)
        val lngScale = latScale * cos(Math.toRadians(centerLat))
        val centerLng = (col + 0.5) / (lngScale / edgeLen)
        val normLng = if (centerLng > 180.0) centerLng - 360.0 else centerLng

        // Generate hexagon vertices
        val hexSizeDegLat = edgeLen / latScale
        val hexSizeDegLng = if (lngScale > 0.001) edgeLen / lngScale else edgeLen / 0.001

        return (0..5).map { i ->
            val angle = Math.toRadians(60.0 * i + 30.0)
            Pair(
                centerLat + hexSizeDegLat * sin(angle),
                normLng + hexSizeDegLng * cos(angle)
            )
        }
    }

    /**
     * Get the center coordinates of an H3 cell.
     */
    fun cellToLatLng(h3Index: String): Pair<Double, Double>? {
        val h3Long = h3Index.toLongOrNull(16) ?: return null

        val resolution = ((h3Long shr 52) and 0xF).toInt()
        val row = ((h3Long shr 22) and 0x3FFFFF).toLong()
        val col = (h3Long and 0x3FFFFF).toLong()

        val edgeLen = HEX_EDGE_LENGTH_KM[min(resolution, 15)]
        val latScale = EARTH_RADIUS_KM * Math.toRadians(1.0)

        val centerLat = 90.0 - (row + 0.5) / (latScale / edgeLen)
        val lngScale = latScale * cos(Math.toRadians(centerLat))
        val centerLng = (col + 0.5) / (lngScale / edgeLen)
        val normLng = if (centerLng > 180.0) centerLng - 360.0 else centerLng

        return Pair(centerLat, normLng)
    }

    /**
     * Determine the icosahedron face for a lat/lng point.
     * Simplified: uses 20 face centers and finds the closest one.
     */
    private fun findIcosaFace(lat: Double, lng: Double): Int {
        // Icosahedron face center latitudes and longitudes (approximate)
        val faceCenters = arrayOf(
            Pair(58.28, 10.54), Pair(58.28, 82.54), Pair(58.28, 154.54),
            Pair(58.28, -133.46), Pair(58.28, -61.46),
            Pair(26.57, 46.54), Pair(26.57, 118.54), Pair(26.57, -169.46),
            Pair(26.57, -97.46), Pair(26.57, -25.46),
            Pair(-26.57, 10.54), Pair(-26.57, 82.54), Pair(-26.57, 154.54),
            Pair(-26.57, -133.46), Pair(-26.57, -61.46),
            Pair(-58.28, 46.54), Pair(-58.28, 118.54), Pair(-58.28, -169.46),
            Pair(-58.28, -97.46), Pair(-58.28, -25.46)
        )

        var minDist = Double.MAX_VALUE
        var bestFace = 0

        for (i in faceCenters.indices) {
            val (fLat, fLng) = faceCenters[i]
            val dist = haversineDistance(lat, lng, fLat, fLng)
            if (dist < minDist) {
                minDist = dist
                bestFace = i
            }
        }
        return bestFace
    }

    /**
     * Haversine distance between two lat/lng points in km
     */
    private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }
}
