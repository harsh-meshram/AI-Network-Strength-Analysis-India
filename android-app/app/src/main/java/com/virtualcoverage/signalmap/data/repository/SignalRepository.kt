package com.virtualcoverage.signalmap.data.repository

import com.virtualcoverage.signalmap.data.local.dao.H3AggregateResult
import com.virtualcoverage.signalmap.data.local.dao.SignalMeasurementDao
import com.virtualcoverage.signalmap.data.local.entity.SignalMeasurementEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for signal measurements.
 * Abstracts local Room DB and remote API.
 * Currently offline-first: saves to Room, sync logic to be added.
 */
@Singleton
class SignalRepository @Inject constructor(
    private val dao: SignalMeasurementDao
) {
    suspend fun saveMeasurement(measurement: SignalMeasurementEntity): Long {
        return dao.insert(measurement)
    }

    suspend fun saveMeasurements(measurements: List<SignalMeasurementEntity>) {
        dao.insertAll(measurements)
    }

    suspend fun getUnsyncedMeasurements(): List<SignalMeasurementEntity> {
        return dao.getUnsyncedMeasurements()
    }

    suspend fun markAsSynced(ids: List<Long>) {
        dao.markAsSynced(ids)
    }

    fun getUnsyncedCount(): Flow<Int> = dao.getUnsyncedCount()
    fun getTotalCount(): Flow<Int> = dao.getTotalCount()
    fun getRecentMeasurements(): Flow<List<SignalMeasurementEntity>> = dao.getRecentMeasurements()

    suspend fun getLatestForCarrier(carrier: String): SignalMeasurementEntity? {
        return dao.getLatestForCarrier(carrier)
    }

    suspend fun getAllCarriers(): List<String> = dao.getAllCarriers()

    suspend fun getHeatmapData(
        minLat: Double, maxLat: Double,
        minLng: Double, maxLng: Double
    ): List<H3AggregateResult> {
        return dao.getHeatmapData(minLat, maxLat, minLng, maxLng)
    }

    /**
     * Cleanup old synced records (older than 7 days)
     */
    suspend fun cleanupOldRecords() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        dao.deleteSyncedBefore(sevenDaysAgo)
        dao.deleteFailedRecords(5)
    }
}
