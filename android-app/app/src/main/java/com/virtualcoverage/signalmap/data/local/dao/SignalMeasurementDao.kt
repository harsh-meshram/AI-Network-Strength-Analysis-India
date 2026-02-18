package com.virtualcoverage.signalmap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.virtualcoverage.signalmap.data.local.entity.SignalMeasurementEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for signal measurements - supports offline buffering and sync.
 */
@Dao
interface SignalMeasurementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: SignalMeasurementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(measurements: List<SignalMeasurementEntity>)

    // Get unsynced records for upload (batch of 100)
    @Query("SELECT * FROM signal_measurements WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT 100")
    suspend fun getUnsyncedMeasurements(): List<SignalMeasurementEntity>

    // Mark records as synced after successful upload
    @Query("UPDATE signal_measurements SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    // Increment sync attempts for failed uploads
    @Query("UPDATE signal_measurements SET syncAttempts = syncAttempts + 1 WHERE id IN (:ids)")
    suspend fun incrementSyncAttempts(ids: List<Long>)

    // Delete synced records older than given timestamp (cleanup)
    @Query("DELETE FROM signal_measurements WHERE isSynced = 1 AND timestamp < :beforeTimestamp")
    suspend fun deleteSyncedBefore(beforeTimestamp: Long)

    // Delete records that failed too many times
    @Query("DELETE FROM signal_measurements WHERE syncAttempts > :maxAttempts")
    suspend fun deleteFailedRecords(maxAttempts: Int = 5)

    // Get count of unsynced records
    @Query("SELECT COUNT(*) FROM signal_measurements WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    // Get total record count
    @Query("SELECT COUNT(*) FROM signal_measurements")
    fun getTotalCount(): Flow<Int>

    // Get latest measurements for display (grouped by carrier)
    @Query("SELECT * FROM signal_measurements WHERE carrierName = :carrier ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForCarrier(carrier: String): SignalMeasurementEntity?

    // Get all unique carriers
    @Query("SELECT DISTINCT carrierName FROM signal_measurements")
    suspend fun getAllCarriers(): List<String>

    // Get measurements for a specific H3 cell (for local map rendering)
    @Query("SELECT * FROM signal_measurements WHERE h3IndexRes9 = :h3Index ORDER BY timestamp DESC LIMIT 50")
    suspend fun getMeasurementsForH3(h3Index: String): List<SignalMeasurementEntity>

    // Get average RSRP per H3 cell for heatmap (local data only)
    @Query("""
        SELECT h3IndexRes9, AVG(COALESCE(rsrp, ssRsrp, dbm)) as avgRsrp, COUNT(*) as count
        FROM signal_measurements 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLng AND :maxLng
        GROUP BY h3IndexRes9
    """)
    suspend fun getHeatmapData(
        minLat: Double, maxLat: Double,
        minLng: Double, maxLng: Double
    ): List<H3AggregateResult>

    // Get recent measurements as Flow for live UI updates
    @Query("SELECT * FROM signal_measurements ORDER BY timestamp DESC LIMIT 20")
    fun getRecentMeasurements(): Flow<List<SignalMeasurementEntity>>
}

/**
 * Result class for aggregated H3 heatmap queries
 */
data class H3AggregateResult(
    val h3IndexRes9: String,
    val avgRsrp: Double,
    val count: Int
)
