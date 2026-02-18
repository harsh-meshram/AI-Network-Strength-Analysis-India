package com.virtualcoverage.signalmap.domain.usecase

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.virtualcoverage.signalmap.util.H3Android
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy Manager - DPDP Act 2023 Compliant
 * 
 * Handles:
 * 1. H3 hexagonal indexing (converts lat/lng to hex IDs)
 * 2. Device ID hashing (SHA-256, no raw IMEI/IMSI)
 * 3. Home location fuzzing (reduced precision during night hours)
 */
@Singleton
class PrivacyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val salt = "vcm_salt_2026" // In production, fetch from server

    companion object {
        private const val TAG = "PrivacyManager"
        const val H3_RESOLUTION_ROOM = 11     // ~0.003 km² (room-level precision)
        const val H3_RESOLUTION_NEIGHBORHOOD = 9  // ~0.1 km² (neighborhood-level)
        const val NIGHT_START_HOUR = 22  // 10 PM
        const val NIGHT_END_HOUR = 6     // 6 AM
    }

    /**
     * Convert lat/lng to H3 index at specified resolution
     */
    fun latLngToH3(latitude: Double, longitude: Double, resolution: Int): String {
        return try {
            H3Android.latLngToCell(latitude, longitude, resolution)
        } catch (e: Exception) {
            Log.e(TAG, "H3 conversion failed: ${e.message}")
            "invalid_h3"
        }
    }

    /**
     * Get H3 index at room-level resolution (Res 11)
     */
    fun getH3RoomLevel(latitude: Double, longitude: Double): String {
        return latLngToH3(latitude, longitude, H3_RESOLUTION_ROOM)
    }

    /**
     * Get H3 index at neighborhood resolution (Res 9)
     */
    fun getH3NeighborhoodLevel(latitude: Double, longitude: Double): String {
        return latLngToH3(latitude, longitude, H3_RESOLUTION_NEIGHBORHOOD)
    }

    /**
     * Get H3 hexagon boundary coordinates (for map rendering)
     */
    fun getH3Boundary(h3Index: String): List<Pair<Double, Double>> {
        return try {
            H3Android.cellToBoundary(h3Index)
        } catch (e: Exception) {
            Log.e(TAG, "H3 boundary failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Generate a privacy-safe hashed device ID using SHA-256.
     * Never transmits raw ANDROID_ID, IMEI, or IMSI.
     */
    fun getHashedDeviceId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"

        return sha256("$androidId:$salt")
    }

    /**
     * Apply home location fuzzing.
     * During nighttime (10 PM - 6 AM), snap location to neighborhood-level H3
     * to prevent home address inference.
     */
    fun applyHomeLocationFuzzing(
        latitude: Double,
        longitude: Double,
        hourOfDay: Int
    ): Pair<String, String> {
        val h3Res11 = getH3RoomLevel(latitude, longitude)
        val h3Res9 = getH3NeighborhoodLevel(latitude, longitude)

        return if (isNightTime(hourOfDay)) {
            // During night hours, use neighborhood precision only
            Log.d(TAG, "Night-time fuzzing applied (hour=$hourOfDay)")
            Pair(h3Res9, h3Res9) // Both set to Res 9 (lower precision)
        } else {
            // Daytime: use full room-level precision
            Pair(h3Res11, h3Res9)
        }
    }

    /**
     * Get device model name for analytics
     */
    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /**
     * Get Android API level
     */
    fun getAndroidVersion(): Int {
        return Build.VERSION.SDK_INT
    }

    // --- Private helpers ---

    private fun isNightTime(hourOfDay: Int): Boolean {
        return hourOfDay >= NIGHT_START_HOUR || hourOfDay < NIGHT_END_HOUR
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
