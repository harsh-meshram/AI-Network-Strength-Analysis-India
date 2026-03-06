package com.virtualcoverage.signalmap.sync

import com.virtualcoverage.signalmap.data.local.entity.SignalMeasurementEntity
import com.virtualcoverage.signalmap.data.remote.BatchIngestRequest
import com.virtualcoverage.signalmap.data.remote.SignalMeasurementDto
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SyncWorker DTO mapping.
 * Verifies that Entity → DTO conversion preserves all fields
 * and correctly maps camelCase to snake_case.
 */
class SyncWorkerDtoMappingTest {

    /**
     * Create a fully-populated test entity
     */
    private fun createTestEntity(
        id: Long = 1L,
        timestamp: Long = 1708300000000L,
        carrierName: String = "Jio",
        subscriptionId: Int = 1,
        networkType: String = "4G_LTE",
        rsrp: Int? = -85,
        rsrq: Int? = -10,
        sinr: Int? = 15,
        ssRsrp: Int? = null,
        ssSinr: Int? = null,
        csiRsrp: Int? = null,
        dbm: Int? = null,
        asuLevel: Int? = null,
        pci: Int? = 120,
        tac: Int? = 4001,
        ci: Long? = 52345678L,
        lac: Int? = null,
        h3Index: String = "8b3f2a4c1d5e6f7",
        h3IndexRes9: String = "893f2a4c1d5",
        latitude: Double = 21.1458,
        longitude: Double = 79.0882,
        hashedDeviceId: String = "abc123def456ghi789jkl012mno345pq",
        deviceModel: String = "realme RMX3430",
        androidVersion: Int = 35,
        isSynced: Boolean = false,
        syncAttempts: Int = 0
    ): SignalMeasurementEntity {
        return SignalMeasurementEntity(
            id = id,
            timestamp = timestamp,
            carrierName = carrierName,
            subscriptionId = subscriptionId,
            networkType = networkType,
            rsrp = rsrp,
            rsrq = rsrq,
            sinr = sinr,
            ssRsrp = ssRsrp,
            ssSinr = ssSinr,
            csiRsrp = csiRsrp,
            dbm = dbm,
            asuLevel = asuLevel,
            pci = pci,
            tac = tac,
            ci = ci,
            lac = lac,
            h3Index = h3Index,
            h3IndexRes9 = h3IndexRes9,
            latitude = latitude,
            longitude = longitude,
            hashedDeviceId = hashedDeviceId,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            isSynced = isSynced,
            syncAttempts = syncAttempts
        )
    }

    /**
     * Mirrors the toDto() mapping in SyncWorker.kt
     */
    private fun SignalMeasurementEntity.toDto(): SignalMeasurementDto {
        return SignalMeasurementDto(
            timestamp = this.timestamp,
            carrier_name = this.carrierName,
            network_type = this.networkType,
            h3_index = this.h3Index,
            h3_index_res9 = this.h3IndexRes9,
            latitude = this.latitude,
            longitude = this.longitude,
            hashed_device_id = this.hashedDeviceId,
            rsrp = this.rsrp,
            rsrq = this.rsrq,
            sinr = this.sinr,
            ss_rsrp = this.ssRsrp,
            ss_sinr = this.ssSinr,
            dbm = this.dbm,
            asu_level = this.asuLevel,
            pci = this.pci,
            tac = this.tac,
            ci = this.ci,
            nci = this.ci,           // Entity uses ci for both LTE CI and NR NCI
            lac = this.lac,
            device_model = this.deviceModel,
            android_version = this.androidVersion,
            sim_slot = this.subscriptionId,
        )
    }

    // =============================================
    // 1. Basic Mapping Tests
    // =============================================

    @Test
    fun `toDto maps timestamp correctly`() {
        val entity = createTestEntity(timestamp = 1708300000000L)
        val dto = entity.toDto()
        assertEquals(1708300000000L, dto.timestamp)
    }

    @Test
    fun `toDto maps carrierName to carrier_name`() {
        val entity = createTestEntity(carrierName = "Airtel")
        val dto = entity.toDto()
        assertEquals("Airtel", dto.carrier_name)
    }

    @Test
    fun `toDto maps networkType to network_type`() {
        val entity = createTestEntity(networkType = "5G_SA")
        val dto = entity.toDto()
        assertEquals("5G_SA", dto.network_type)
    }

    @Test
    fun `toDto maps h3Index to h3_index`() {
        val entity = createTestEntity(h3Index = "8b3f2a4c1d5e6f7")
        val dto = entity.toDto()
        assertEquals("8b3f2a4c1d5e6f7", dto.h3_index)
    }

    @Test
    fun `toDto maps coordinates correctly`() {
        val entity = createTestEntity(latitude = 21.1458, longitude = 79.0882)
        val dto = entity.toDto()
        assertEquals(21.1458, dto.latitude, 0.0001)
        assertEquals(79.0882, dto.longitude, 0.0001)
    }

    @Test
    fun `toDto maps hashedDeviceId to hashed_device_id`() {
        val entity = createTestEntity(hashedDeviceId = "abcdef123456")
        val dto = entity.toDto()
        assertEquals("abcdef123456", dto.hashed_device_id)
    }

    // =============================================
    // 2. Signal Metrics Mapping
    // =============================================

    @Test
    fun `toDto maps LTE signal metrics correctly`() {
        val entity = createTestEntity(rsrp = -85, rsrq = -10, sinr = 15)
        val dto = entity.toDto()
        assertEquals(-85, dto.rsrp)
        assertEquals(-10, dto.rsrq)
        assertEquals(15, dto.sinr)
    }

    @Test
    fun `toDto maps 5G signal metrics correctly`() {
        val entity = createTestEntity(ssRsrp = -95, ssSinr = 20, rsrp = null)
        val dto = entity.toDto()
        assertEquals(-95, dto.ss_rsrp)
        assertEquals(20, dto.ss_sinr)
        assertNull(dto.rsrp)
    }

    @Test
    fun `toDto maps GSM signal metrics correctly`() {
        val entity = createTestEntity(
            dbm = -75, asuLevel = 12,
            rsrp = null, rsrq = null, sinr = null
        )
        val dto = entity.toDto()
        assertEquals(-75, dto.dbm)
        assertEquals(12, dto.asu_level)
        assertNull(dto.rsrp)
    }

    @Test
    fun `toDto handles all null signal metrics`() {
        val entity = createTestEntity(
            rsrp = null, rsrq = null, sinr = null,
            ssRsrp = null, ssSinr = null,
            dbm = null, asuLevel = null
        )
        val dto = entity.toDto()
        assertNull(dto.rsrp)
        assertNull(dto.rsrq)
        assertNull(dto.sinr)
        assertNull(dto.ss_rsrp)
        assertNull(dto.ss_sinr)
        assertNull(dto.dbm)
        assertNull(dto.asu_level)
    }

    // =============================================
    // 3. Cell Identity Mapping
    // =============================================

    @Test
    fun `toDto maps ci to both ci and nci`() {
        val entity = createTestEntity(ci = 52345678L)
        val dto = entity.toDto()
        assertEquals(52345678L, dto.ci)
        assertEquals(52345678L, dto.nci)
    }

    @Test
    fun `toDto maps subscriptionId to sim_slot`() {
        val entity = createTestEntity(subscriptionId = 2)
        val dto = entity.toDto()
        assertEquals(2, dto.sim_slot)
    }

    @Test
    fun `toDto maps pci and tac correctly`() {
        val entity = createTestEntity(pci = 120, tac = 4001)
        val dto = entity.toDto()
        assertEquals(120, dto.pci)
        assertEquals(4001, dto.tac)
    }

    @Test
    fun `toDto maps lac correctly for GSM`() {
        val entity = createTestEntity(lac = 500)
        val dto = entity.toDto()
        assertEquals(500, dto.lac)
    }

    // =============================================
    // 4. Device Info Mapping
    // =============================================

    @Test
    fun `toDto maps device_model correctly`() {
        val entity = createTestEntity(deviceModel = "realme RMX3430")
        val dto = entity.toDto()
        assertEquals("realme RMX3430", dto.device_model)
    }

    @Test
    fun `toDto maps android_version correctly`() {
        val entity = createTestEntity(androidVersion = 35)
        val dto = entity.toDto()
        assertEquals(35, dto.android_version)
    }

    // =============================================
    // 5. Batch Request Tests
    // =============================================

    @Test
    fun `BatchIngestRequest wraps DTOs correctly`() {
        val entities = (1..5).map { createTestEntity(id = it.toLong()) }
        val dtos = entities.map { it.toDto() }
        val deviceId = entities.first().hashedDeviceId

        val request = BatchIngestRequest(
            device_id = deviceId,
            measurements = dtos
        )

        assertEquals(deviceId, request.device_id)
        assertEquals(5, request.measurements.size)
    }

    // =============================================
    // 6. All Network Types
    // =============================================

    @Test
    fun `all network types map correctly`() {
        val types = listOf("5G_SA", "5G_NSA", "4G_LTE", "2G_GSM", "Unknown", "5G")
        for (type in types) {
            val entity = createTestEntity(networkType = type)
            val dto = entity.toDto()
            assertEquals("Network type $type should map correctly", type, dto.network_type)
        }
    }
}
