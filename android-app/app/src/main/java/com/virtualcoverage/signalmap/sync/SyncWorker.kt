package com.virtualcoverage.signalmap.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.virtualcoverage.signalmap.data.local.entity.SignalMeasurementEntity
import com.virtualcoverage.signalmap.data.remote.BatchIngestRequest
import com.virtualcoverage.signalmap.data.remote.SignalApiService
import com.virtualcoverage.signalmap.data.remote.SignalMeasurementDto
import com.virtualcoverage.signalmap.data.repository.SignalRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that syncs unsynced signal measurements
 * from the local Room database to the backend server.
 *
 * Scheduled to run periodically (every 15 minutes) when the device
 * has network connectivity. Uses WorkManager for reliable execution
 * even if the app is killed or device restarts.
 *
 * Flow:
 * 1. Query Room for unsynced measurements (batch of 100)
 * 2. Convert to DTOs matching backend API schema
 * 3. POST to /api/v1/ingest/signal
 * 4. On success: mark as synced in Room
 * 5. On failure: increment sync attempts
 * 6. Repeat until no more unsynced records
 * 7. Cleanup old synced records (>7 days)
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val signalRepository: SignalRepository,
    private val apiService: SignalApiService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "signal_sync_work"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "🔄 Starting sync...")

        var totalSynced = 0
        var batchCount = 0

        try {
            // Keep syncing batches until no more unsynced records
            while (true) {
                val unsynced = signalRepository.getUnsyncedMeasurements()
                if (unsynced.isEmpty()) {
                    Log.d(TAG, "✅ No more unsynced records. Total synced: $totalSynced")
                    break
                }

                batchCount++
                Log.d(TAG, "📦 Syncing batch #$batchCount (${unsynced.size} measurements)")

                // Convert Room entities to API DTOs
                val dtos = unsynced.map { it.toDto() }
                val deviceId = unsynced.first().hashedDeviceId

                val request = BatchIngestRequest(
                    device_id = deviceId,
                    measurements = dtos
                )

                try {
                    val response = apiService.ingestSignals(request)

                    if (response.isSuccessful && response.body()?.success == true) {
                        // Mark all in this batch as synced
                        val ids = unsynced.map { it.id }
                        signalRepository.markAsSynced(ids)
                        totalSynced += unsynced.size
                        Log.d(TAG, "✅ Batch #$batchCount synced: ${response.body()?.inserted} inserted (batch_id: ${response.body()?.batch_id})")
                    } else {
                        // Server rejected the batch
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        Log.e(TAG, "❌ Server rejected batch #$batchCount: ${response.code()} - $errorBody")
                        incrementAttempts(unsynced)
                        break  // Don't keep trying if server is rejecting
                    }
                } catch (e: Exception) {
                    // Network error (no internet, server down, timeout, etc.)
                    Log.e(TAG, "❌ Network error on batch #$batchCount: ${e.message}")
                    incrementAttempts(unsynced)
                    return Result.retry()  // WorkManager will retry with backoff
                }
            }

            // Cleanup old synced records to free storage
            signalRepository.cleanupOldRecords()
            Log.d(TAG, "🧹 Cleaned up old records")

            Log.d(TAG, "✅ Sync complete. Total synced: $totalSynced across $batchCount batches")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync failed: ${e.message}", e)
            return Result.retry()
        }
    }

    private suspend fun incrementAttempts(measurements: List<SignalMeasurementEntity>) {
        try {
            val ids = measurements.map { it.id }
            // Use DAO directly via repository pattern - increment attempts
            // For now, we just log. The records stay unsynced for next attempt.
            Log.w(TAG, "⚠️ Will retry ${ids.size} measurements on next sync")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update sync attempts: ${e.message}")
        }
    }

    /**
     * Convert a Room entity to the API DTO format.
     * Maps camelCase (Room) → snake_case (backend API).
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
            nci = this.ci,          // Entity uses ci for both LTE CI and NR NCI
            lac = this.lac,
            device_model = this.deviceModel,
            android_version = this.androidVersion,
            sim_slot = this.subscriptionId,
        )
    }
}
