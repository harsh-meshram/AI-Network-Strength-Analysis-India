package com.virtualcoverage.signalmap

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.virtualcoverage.signalmap.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmConfig
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application class for VirtualCoverageMap.
 * 
 * Initializes:
 * - Hilt dependency injection
 * - osmdroid map configuration
 * - WorkManager with HiltWorkerFactory for background sync
 * - Periodic SyncWorker (every 15 minutes on network)
 */
@HiltAndroidApp
class SignalMapApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Configure osmdroid
        OsmConfig.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = filesDir.resolve("osmdroid/tiles")
        }

        // Schedule periodic sync
        scheduleSyncWorker()
    }

    /**
     * Provide WorkManager configuration with Hilt worker factory.
     * This allows SyncWorker to receive injected dependencies.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    /**
     * Schedule the SyncWorker to run every 15 minutes.
     * Only runs when the device has network connectivity.
     * Uses KEEP policy so it doesn't duplicate if already scheduled.
     */
    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)  // Only sync when online
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES  // Minimum interval for periodic work
        )
            .setConstraints(constraints)
            .addTag(SyncWorker.TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,  // Don't replace existing schedule
            syncRequest
        )

        Log.d("SignalMapApp", "📅 SyncWorker scheduled (every 15 min, requires network)")
    }
}
