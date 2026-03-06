package com.virtualcoverage.signalmap.performance

import com.virtualcoverage.signalmap.data.local.entity.SignalMeasurementEntity
import com.virtualcoverage.signalmap.data.remote.SignalMeasurementDto
import com.virtualcoverage.signalmap.util.H3Android
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/**
 * Performance benchmark tests for the background signal collection service.
 * 
 * Ensures that critical operations complete within acceptable time limits
 * to avoid ANR (Application Not Responding) and excessive battery drain.
 * 
 * The service runs every 10 seconds and must complete all processing
 * well within that window.
 */
class PerformanceTest {

    // =============================================
    // 1. H3 Indexing Performance
    // =============================================

    @Test
    fun `H3 latLngToCell — 1000 conversions complete in under 1 second`() {
        val iterations = 1000
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            // Vary coordinates slightly to avoid caching effects
            val lat = 21.1458 + (i * 0.001)
            val lng = 79.0882 + (i * 0.001)
            H3Android.latLngToCell(
                lat.coerceIn(-90.0, 90.0),
                lng.coerceIn(-180.0, 180.0),
                11
            )
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        assertTrue(
            "1000 H3 conversions took ${elapsedMs}ms — should be < 1000ms",
            elapsedMs < 1000
        )
        println("✅ H3 latLngToCell: 1000 conversions in ${elapsedMs}ms (${elapsedMs.toFloat() / iterations}ms/op)")
    }

    @Test
    fun `H3 cellToBoundary — 1000 boundary computations in under 1 second`() {
        // Pre-generate some H3 indexes
        val indexes = (0 until 100).map { i ->
            H3Android.latLngToCell(
                (21.0 + i * 0.01).coerceIn(-90.0, 90.0),
                (79.0 + i * 0.01).coerceIn(-180.0, 180.0),
                11
            )
        }

        val iterations = 1000
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            H3Android.cellToBoundary(indexes[i % indexes.size])
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        assertTrue(
            "1000 boundary computations took ${elapsedMs}ms — should be < 1000ms",
            elapsedMs < 1000
        )
        println("✅ H3 cellToBoundary: 1000 ops in ${elapsedMs}ms")
    }

    // =============================================
    // 2. SHA-256 Hashing Performance
    // =============================================

    @Test
    fun `SHA-256 hashing — 1000 hashes complete in under 500ms`() {
        val iterations = 1000
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            sha256("device_${i}:vcm_salt_2026")
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        assertTrue(
            "1000 SHA-256 hashes took ${elapsedMs}ms — should be < 500ms",
            elapsedMs < 500
        )
        println("✅ SHA-256: 1000 hashes in ${elapsedMs}ms (${elapsedMs.toFloat() / iterations}ms/op)")
    }

    // =============================================
    // 3. DTO Mapping Performance
    // =============================================

    @Test
    fun `DTO mapping — 100 entity-to-DTO conversions in under 100ms`() {
        val entities = (0 until 100).map { i ->
            SignalMeasurementEntity(
                id = i.toLong(),
                timestamp = System.currentTimeMillis(),
                carrierName = "Jio",
                subscriptionId = 1,
                networkType = "4G_LTE",
                rsrp = -85,
                rsrq = -10,
                sinr = 15,
                pci = 120,
                tac = 4001,
                ci = 52345678L,
                h3Index = "8b3f2a4c1d5e6f7",
                h3IndexRes9 = "893f2a4c1d5",
                latitude = 21.1458 + (i * 0.001),
                longitude = 79.0882 + (i * 0.001),
                hashedDeviceId = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
                deviceModel = "realme RMX3430",
                androidVersion = 35,
                isSynced = false,
                syncAttempts = 0
            )
        }

        val startTime = System.nanoTime()

        val dtos = entities.map { entity ->
            SignalMeasurementDto(
                timestamp = entity.timestamp,
                carrier_name = entity.carrierName,
                network_type = entity.networkType,
                h3_index = entity.h3Index,
                h3_index_res9 = entity.h3IndexRes9,
                latitude = entity.latitude,
                longitude = entity.longitude,
                hashed_device_id = entity.hashedDeviceId,
                rsrp = entity.rsrp,
                rsrq = entity.rsrq,
                sinr = entity.sinr,
                ss_rsrp = entity.ssRsrp,
                ss_sinr = entity.ssSinr,
                dbm = entity.dbm,
                asu_level = entity.asuLevel,
                pci = entity.pci,
                tac = entity.tac,
                ci = entity.ci,
                nci = entity.ci,
                lac = entity.lac,
                device_model = entity.deviceModel,
                android_version = entity.androidVersion,
                sim_slot = entity.subscriptionId,
            )
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        assertEquals(100, dtos.size)
        assertTrue(
            "100 DTO mappings took ${elapsedMs}ms — should be < 100ms",
            elapsedMs < 100
        )
        println("✅ DTO mapping: 100 conversions in ${elapsedMs}ms")
    }

    // =============================================
    // 4. Combined Pipeline Performance
    // =============================================

    @Test
    fun `full pipeline — H3 + hash + DTO for single measurement in under 5ms`() {
        val iterations = 100
        var totalMs = 0L

        for (i in 0 until iterations) {
            val startTime = System.nanoTime()

            // Step 1: H3 indexing
            val lat = 21.1458 + (i * 0.001)
            val lng = 79.0882 + (i * 0.001)
            val h3Res11 = H3Android.latLngToCell(
                lat.coerceIn(-90.0, 90.0),
                lng.coerceIn(-180.0, 180.0),
                11
            )
            val h3Res9 = H3Android.latLngToCell(
                lat.coerceIn(-90.0, 90.0),
                lng.coerceIn(-180.0, 180.0),
                9
            )

            // Step 2: Device ID hashing
            val hashedId = sha256("device_$i:vcm_salt_2026")

            // Step 3: Create entity
            val entity = SignalMeasurementEntity(
                timestamp = System.currentTimeMillis(),
                carrierName = "Jio",
                subscriptionId = 1,
                networkType = "4G_LTE",
                rsrp = -85,
                h3Index = h3Res11,
                h3IndexRes9 = h3Res9,
                latitude = lat,
                longitude = lng,
                hashedDeviceId = hashedId,
                deviceModel = "TestDevice",
                androidVersion = 35
            )

            // Step 4: Map to DTO
            val dto = SignalMeasurementDto(
                timestamp = entity.timestamp,
                carrier_name = entity.carrierName,
                network_type = entity.networkType,
                h3_index = entity.h3Index,
                h3_index_res9 = entity.h3IndexRes9,
                latitude = entity.latitude,
                longitude = entity.longitude,
                hashed_device_id = entity.hashedDeviceId,
                rsrp = entity.rsrp,
                sim_slot = entity.subscriptionId,
            )

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            totalMs += elapsedMs
        }

        val avgMs = totalMs.toFloat() / iterations
        assertTrue(
            "Average pipeline time: ${avgMs}ms — should be < 5ms",
            avgMs < 5
        )
        println("✅ Full pipeline: avg ${avgMs}ms per measurement (${iterations} iterations)")
    }

    // =============================================
    // 5. Memory Estimation
    // =============================================

    @Test
    fun `SignalMeasurementEntity has reasonable field count`() {
        // Entity should have a manageable number of fields
        val fields = SignalMeasurementEntity::class.java.declaredFields
        assertTrue(
            "Entity has ${fields.size} fields — should be < 30",
            fields.size < 30
        )
    }

    @Test
    fun `SignalMeasurementDto has no unnecessary fields`() {
        val dtoFields = SignalMeasurementDto::class.java.declaredFields.size
        val entityFields = SignalMeasurementEntity::class.java.declaredFields.size
        
        // DTO should have fewer fields than Entity (no id, isSynced, syncAttempts)
        // But DTO adds nci, is_indoor, speed_kmh
        assertTrue(
            "DTO has $dtoFields fields, Entity has $entityFields — DTO should not bloat",
            dtoFields <= entityFields + 5
        )
    }

    // =============================================
    // Helper
    // =============================================

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
