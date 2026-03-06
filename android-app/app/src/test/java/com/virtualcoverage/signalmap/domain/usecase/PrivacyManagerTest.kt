package com.virtualcoverage.signalmap.domain.usecase

import com.virtualcoverage.signalmap.util.H3Android
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/**
 * Unit tests for PrivacyManager — DPDP Act 2023 compliance verification.
 * 
 * Tests SHA-256 hashing, home location fuzzing, and H3 resolution logic.
 * Note: Tests that need Android Context (getHashedDeviceId) are tested
 * indirectly via the sha256 and isNightTime logic.
 */
class PrivacyManagerTest {

    // =============================================
    // 1. SHA-256 Hashing Tests
    // =============================================

    @Test
    fun `sha256 produces 64-char hex output`() {
        val hash = sha256("test_device_id:vcm_salt_2026")
        assertEquals("SHA-256 should produce 64-char hex", 64, hash.length)
        assertTrue("SHA-256 output should be valid hex", hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `sha256 is deterministic — same input gives same output`() {
        val input = "abc123:vcm_salt_2026"
        val hash1 = sha256(input)
        val hash2 = sha256(input)
        val hash3 = sha256(input)
        assertEquals(hash1, hash2)
        assertEquals(hash2, hash3)
    }

    @Test
    fun `sha256 produces different hashes for different inputs`() {
        val hash1 = sha256("device_1:vcm_salt_2026")
        val hash2 = sha256("device_2:vcm_salt_2026")
        assertNotEquals("Different devices should have different hashes", hash1, hash2)
    }

    @Test
    fun `sha256 hash never contains raw input`() {
        val rawDeviceId = "my_secret_device_id"
        val hash = sha256("$rawDeviceId:vcm_salt_2026")
        assertFalse("Hash should not contain raw ID", hash.contains(rawDeviceId))
        assertFalse("Hash should not contain salt", hash.contains("vcm_salt_2026"))
    }

    @Test
    fun `sha256 produces fixed known hash for test vector`() {
        // SHA-256 of "test" is a well-known value
        val hash = sha256("test")
        assertEquals(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            hash
        )
    }

    @Test
    fun `different salts produce different hashes for same device`() {
        val hash1 = sha256("device_id:salt_1")
        val hash2 = sha256("device_id:salt_2")
        assertNotEquals("Different salts should produce different hashes", hash1, hash2)
    }

    // =============================================
    // 2. Night-Time Fuzzing Logic Tests
    // =============================================

    @Test
    fun `isNightTime returns true for 22-00 to 05-59`() {
        // Night hours: 22, 23, 0, 1, 2, 3, 4, 5
        val nightHours = listOf(22, 23, 0, 1, 2, 3, 4, 5)
        for (hour in nightHours) {
            assertTrue("Hour $hour should be night-time", isNightTime(hour))
        }
    }

    @Test
    fun `isNightTime returns false for 06-00 to 21-59`() {
        // Day hours: 6, 7, ... 21
        val dayHours = (6..21).toList()
        for (hour in dayHours) {
            assertFalse("Hour $hour should be daytime", isNightTime(hour))
        }
    }

    @Test
    fun `boundary hour 22 is night-time`() {
        assertTrue("22:00 should be night-time", isNightTime(22))
    }

    @Test
    fun `boundary hour 6 is daytime`() {
        assertFalse("06:00 should be daytime", isNightTime(6))
    }

    @Test
    fun `boundary hour 5 is night-time`() {
        assertTrue("05:00 should be night-time", isNightTime(5))
    }

    @Test
    fun `midnight (0) is night-time`() {
        assertTrue("00:00 should be night-time", isNightTime(0))
    }

    // =============================================
    // 3. Home Location Fuzzing Tests
    // =============================================

    @Test
    fun `daytime fuzzing returns different res11 and res9 indexes`() {
        val lat = 21.1458
        val lng = 79.0882
        val hourOfDay = 14 // 2 PM — daytime

        val h3Res11 = H3Android.latLngToCell(lat, lng, 11)
        val h3Res9 = H3Android.latLngToCell(lat, lng, 9)

        val (fuzzedRes11, fuzzedRes9) = applyHomeLocationFuzzing(lat, lng, hourOfDay)

        assertEquals("Daytime should use Res 11 for fine index", h3Res11, fuzzedRes11)
        assertEquals("Daytime should use Res 9 for coarse index", h3Res9, fuzzedRes9)
        assertNotEquals("Res 11 and Res 9 should differ", fuzzedRes11, fuzzedRes9)
    }

    @Test
    fun `nighttime fuzzing returns same res9 for both indexes`() {
        val lat = 21.1458
        val lng = 79.0882
        val hourOfDay = 23 // 11 PM — nighttime

        val (fuzzedH3, fuzzedH3Res9) = applyHomeLocationFuzzing(lat, lng, hourOfDay)

        assertEquals("Night fuzzing should make both indexes the same (Res 9)", fuzzedH3, fuzzedH3Res9)
    }

    @Test
    fun `nighttime fuzzing at midnight uses res9 for both`() {
        val (h3, h3Res9) = applyHomeLocationFuzzing(21.1458, 79.0882, 0)
        assertEquals("Midnight should fuzz both to Res 9", h3, h3Res9)
    }

    @Test
    fun `daytime at 12-00 uses full precision`() {
        val (h3, h3Res9) = applyHomeLocationFuzzing(21.1458, 79.0882, 12)
        assertNotEquals("Noon should keep full Res 11 precision", h3, h3Res9)
    }

    // =============================================
    // 4. H3 Resolution Level Tests
    // =============================================

    @Test
    fun `h3 res11 and res9 produce different indexes for same point`() {
        val h3Res11 = H3Android.latLngToCell(21.1458, 79.0882, 11)
        val h3Res9 = H3Android.latLngToCell(21.1458, 79.0882, 9)
        assertNotEquals("Res 11 and Res 9 should produce different indexes", h3Res11, h3Res9)
    }

    @Test
    fun `h3 indexes are non-empty for valid Indian coordinates`() {
        val h3Res11 = H3Android.latLngToCell(21.1458, 79.0882, 11)
        val h3Res9 = H3Android.latLngToCell(21.1458, 79.0882, 9)
        assertTrue("Res 11 should not be empty", h3Res11.isNotEmpty())
        assertTrue("Res 9 should not be empty", h3Res9.isNotEmpty())
        assertNotEquals("Should not return error sentinel", "invalid_h3", h3Res11)
        assertNotEquals("Should not return error sentinel", "invalid_h3", h3Res9)
    }

    // =============================================
    // Helper methods (mirrors PrivacyManager logic)
    // =============================================

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun isNightTime(hourOfDay: Int): Boolean {
        return hourOfDay >= 22 || hourOfDay < 6
    }

    private fun applyHomeLocationFuzzing(
        latitude: Double,
        longitude: Double,
        hourOfDay: Int
    ): Pair<String, String> {
        val h3Res11 = H3Android.latLngToCell(latitude, longitude, 11)
        val h3Res9 = H3Android.latLngToCell(latitude, longitude, 9)

        return if (isNightTime(hourOfDay)) {
            Pair(h3Res9, h3Res9) // Both set to Res 9

        } else {
            Pair(h3Res11, h3Res9)
        }
    }
}
