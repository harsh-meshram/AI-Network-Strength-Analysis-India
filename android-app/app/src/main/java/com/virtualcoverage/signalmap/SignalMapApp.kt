package com.virtualcoverage.signalmap

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

/**
 * Application class for VirtualCoverageMap
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection
 */
@HiltAndroidApp
class SignalMapApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Configure osmdroid
        Configuration.getInstance().apply {
            userAgentValue = packageName
            // Set cache location
            osmdroidBasePath = filesDir
            osmdroidTileCache = filesDir.resolve("osmdroid/tiles")
        }
    }
}
