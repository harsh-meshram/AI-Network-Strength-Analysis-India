package com.virtualcoverage.signalmap.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for H3Android — pure-Kotlin H3 hexagonal indexing.
 * Tests determinism, edge cases, roundtrip consistency, and Indian locations.
 */
class H3AndroidTest {

    // =============================================
    // 1. Basic latLngToCell Tests
    // =============================================

    @Test
    fun `latLngToCell produces 16-char hex string`() {
        val h3 = H3Android.latLngToCell(21.1458, 79.0882, 11)
        assertEquals("H3 index should be 16 characters", 16, h3.length)
        assertTrue("H3 index should be valid hex", h3.matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun `latLngToCell is deterministic — same input gives same output`() {
        val lat = 21.1458
        val lng = 79.0882
        val h3a = H3Android.latLngToCell(lat, lng, 11)
        val h3b = H3Android.latLngToCell(lat, lng, 11)
        val h3c = H3Android.latLngToCell(lat, lng, 11)
        assertEquals("Same input should produce same H3 index", h3a, h3b)
        assertEquals("Same input should produce same H3 index", h3b, h3c)
    }

    @Test
    fun `different resolutions produce different indexes for same point`() {
        val lat = 21.1458
        val lng = 79.0882
        val h3Res9 = H3Android.latLngToCell(lat, lng, 9)
        val h3Res11 = H3Android.latLngToCell(lat, lng, 11)
        assertNotEquals("Res 9 and Res 11 should differ", h3Res9, h3Res11)
    }

    @Test
    fun `nearby points at res 9 may share same hex cell`() {
        // Two points ~10 meters apart should be in the same Res 9 cell (~0.1 km²)
        val h3a = H3Android.latLngToCell(21.14580, 79.08820, 9)
        val h3b = H3Android.latLngToCell(21.14581, 79.08821, 9)
        assertEquals("Nearby points should share Res 9 cell", h3a, h3b)
    }

    @Test
    fun `distant points produce different hex cells`() {
        val nagpur = H3Android.latLngToCell(21.1458, 79.0882, 9)
        val mumbai = H3Android.latLngToCell(19.0760, 72.8777, 9)
        assertNotEquals("Nagpur and Mumbai should have different cells", nagpur, mumbai)
    }

    // =============================================
    // 2. Indian City Coordinates
    // =============================================

    @Test
    fun `major Indian cities produce valid H3 indexes`() {
        val cities = mapOf(
            "Nagpur" to Pair(21.1458, 79.0882),
            "Mumbai" to Pair(19.0760, 72.8777),
            "Delhi" to Pair(28.6139, 77.2090),
            "Bangalore" to Pair(12.9716, 77.5946),
            "Chennai" to Pair(13.0827, 80.2707),
            "Kolkata" to Pair(22.5726, 88.3639),
            "Hyderabad" to Pair(17.3850, 78.4867),
            "Pune" to Pair(18.5204, 73.8567)
        )

        for ((city, coords) in cities) {
            val h3 = H3Android.latLngToCell(coords.first, coords.second, 11)
            assertEquals("$city H3 should be 16 chars", 16, h3.length)
            assertTrue("$city H3 should be valid hex", h3.matches(Regex("[0-9a-f]{16}")))
        }
    }

    @Test
    fun `all Indian cities produce unique H3 indexes`() {
        val cities = listOf(
            Pair(21.1458, 79.0882),   // Nagpur
            Pair(19.0760, 72.8777),   // Mumbai
            Pair(28.6139, 77.2090),   // Delhi
            Pair(12.9716, 77.5946),   // Bangalore
            Pair(13.0827, 80.2707),   // Chennai
            Pair(22.5726, 88.3639),   // Kolkata
        )

        val indexes = cities.map { H3Android.latLngToCell(it.first, it.second, 9) }
        assertEquals("All cities should have unique H3 indexes", indexes.size, indexes.toSet().size)
    }

    // =============================================
    // 3. Edge Cases
    // =============================================

    @Test
    fun `equator point produces valid H3`() {
        val h3 = H3Android.latLngToCell(0.0, 0.0, 11)
        assertEquals(16, h3.length)
        assertTrue(h3.matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun `max positive coordinates produce valid H3`() {
        val h3 = H3Android.latLngToCell(89.0, 179.0, 11)
        assertEquals(16, h3.length)
    }

    @Test
    fun `max negative coordinates produce valid H3`() {
        val h3 = H3Android.latLngToCell(-89.0, -179.0, 11)
        assertEquals(16, h3.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `latitude above 90 throws exception`() {
        H3Android.latLngToCell(91.0, 0.0, 11)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `latitude below minus 90 throws exception`() {
        H3Android.latLngToCell(-91.0, 0.0, 11)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `longitude above 180 throws exception`() {
        H3Android.latLngToCell(0.0, 181.0, 11)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resolution above 15 throws exception`() {
        H3Android.latLngToCell(0.0, 0.0, 16)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resolution below 0 throws exception`() {
        H3Android.latLngToCell(0.0, 0.0, -1)
    }

    // =============================================
    // 4. cellToBoundary Tests
    // =============================================

    @Test
    fun `cellToBoundary returns 6 vertices for valid H3 index`() {
        val h3 = H3Android.latLngToCell(21.1458, 79.0882, 11)
        val boundary = H3Android.cellToBoundary(h3)
        assertEquals("Hexagon should have 6 vertices", 6, boundary.size)
    }

    @Test
    fun `cellToBoundary vertices have valid coordinates`() {
        val h3 = H3Android.latLngToCell(21.1458, 79.0882, 11)
        val boundary = H3Android.cellToBoundary(h3)

        for ((i, point) in boundary.withIndex()) {
            assertTrue("Vertex $i lat should be in [-90, 90]", point.first in -90.0..90.0)
            assertTrue("Vertex $i lng should be in [-360, 360]", point.second in -360.0..360.0)
        }
    }

    @Test
    fun `cellToBoundary returns empty list for invalid H3 index`() {
        val boundary = H3Android.cellToBoundary("invalid_hex_str")
        assertTrue("Invalid H3 should return empty boundary", boundary.isEmpty())
    }

    // =============================================
    // 5. Roundtrip Tests
    // =============================================

    @Test
    fun `roundtrip — cellToLatLng center is near original point`() {
        val originalLat = 21.1458
        val originalLng = 79.0882
        val h3 = H3Android.latLngToCell(originalLat, originalLng, 11)
        val center = H3Android.cellToLatLng(h3)

        assertNotNull("Cell center should not be null", center)
        center!!
        // At Res 11, hex edge is ~25m, so center should be within ~50m (~0.001 degrees)
        assertTrue("Center lat should be near original", 
            Math.abs(center.first - originalLat) < 0.01)
        assertTrue("Center lng should be near original", 
            Math.abs(center.second - originalLng) < 0.01)
    }

    @Test
    fun `cellToLatLng returns null for invalid H3`() {
        val center = H3Android.cellToLatLng("not_valid_hex")
        assertNull("Invalid H3 should return null", center)
    }

    // =============================================
    // 6. Resolution Encoding Tests
    // =============================================

    @Test
    fun `all valid resolutions produce H3 indexes`() {
        for (res in 0..15) {
            val h3 = H3Android.latLngToCell(21.1458, 79.0882, res)
            assertEquals("Resolution $res should produce 16-char hex", 16, h3.length)
        }
    }
}
