package com.virtualcoverage.signalmap.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthNr
import android.telephony.NetworkRegistrationInfo
import android.telephony.ServiceState
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.virtualcoverage.signalmap.R

/**
 * Foreground Service for collecting mobile network signal strength data
 * Supports dual-SIM devices and collects RSRP, SINR, and other signal metrics
 */
class SignalCollectionService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val signalCollectionRunnable = Runnable {
        collectSignalData()
        scheduleNextCollection()
    }

    companion object {
        private const val TAG = "SignalCollectionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "signal_collection_channel"
        private const val COLLECTION_INTERVAL_MS = 10_000L // 10 seconds
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Setup location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                currentLocation = locationResult.lastLocation
                Log.d(TAG, "Location updated: ${currentLocation?.latitude}, ${currentLocation?.longitude}")
            }
        }
        
        // Start location updates
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        // Create notification channel for Android 8+
        createNotificationChannel()
        
        // Start as foreground service
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        // Start signal collection
        scheduleNextCollection()
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        handler.removeCallbacks(signalCollectionRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Signal Collection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Collecting mobile network signal data"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Virtual Coverage Map")
            .setContentText("Collecting signal data...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10_000L // 10 seconds
        ).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun scheduleNextCollection() {
        handler.postDelayed(signalCollectionRunnable, COLLECTION_INTERVAL_MS)
    }

    /**
     * Main signal collection logic - handles dual SIM and extracts signal metrics
     */
    private fun collectSignalData() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Phone state permission not granted")
            return
        }

        Log.d(TAG, "=== Starting signal collection ===")
        
        // Get SubscriptionManager for dual-SIM support
        val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList ?: emptyList()
        
        if (activeSubscriptions.isEmpty()) {
            Log.w(TAG, "No active SIM cards found")
            return
        }

        // Iterate through each active SIM
        for (subInfo in activeSubscriptions) {
            val subId = subInfo.subscriptionId
            val carrierName = subInfo.carrierName?.toString() ?: "Unknown"
            
            Log.d(TAG, "Processing SIM: $carrierName (SubId: $subId)")
            
            // Create TelephonyManager for this specific subscription
            val telephonyManager = (getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .createForSubscriptionId(subId)
            
            // Collect cell info for this SIM
            val cellInfoList = telephonyManager.allCellInfo
            
            if (cellInfoList.isNullOrEmpty()) {
                Log.w(TAG, "No cell info available for $carrierName")
                continue
            }

            // Process each cell tower
            for (cellInfo in cellInfoList) {
                if (!cellInfo.isRegistered) continue // Only process registered cells
                
                when (cellInfo) {
                    is CellInfoNr -> processNrCell(cellInfo, carrierName, subId, telephonyManager)
                    is CellInfoLte -> processLteCell(cellInfo, carrierName, subId)
                    is CellInfoGsm -> processGsmCell(cellInfo, carrierName, subId)
                    else -> Log.d(TAG, "Unsupported cell type: ${cellInfo.javaClass.simpleName}")
                }
            }
        }
        
        Log.d(TAG, "=== Signal collection complete ===")
    }

    /**
     * Process 5G NR cell info
     */
    private fun processNrCell(
        cellInfo: CellInfoNr,
        carrierName: String,
        subId: Int,
        telephonyManager: TelephonyManager
    ) {
        // Cast to NR-specific types for access to 5G metrics
        val signalStrength = cellInfo.cellSignalStrength as? CellSignalStrengthNr
        val cellIdentity = cellInfo.cellIdentity as? CellIdentityNr
        
        if (signalStrength == null || cellIdentity == null) {
            Log.w(TAG, "Failed to cast NR cell info types")
            return
        }
        
        // Extract signal metrics
        val ssRsrp = signalStrength.ssRsrp // Reference Signal Received Power
        val ssSinr = signalStrength.ssSinr // Signal-to-Interference-plus-Noise Ratio
        val csiRsrp = signalStrength.csiRsrp
        
        // Extract cell identifiers
        val pci = cellIdentity.pci // Physical Cell ID
        val tac = cellIdentity.tac // Tracking Area Code
        val nci = cellIdentity.nci // NR Cell Identity
        
        // Determine if it's 5G SA or NSA
        val networkType = detect5GType(telephonyManager)
        
        Log.d(TAG, """
            |5G NR Signal Data:
            |  Carrier: $carrierName
            |  Network: $networkType
            |  SS-RSRP: $ssRsrp dBm
            |  SS-SINR: $ssSinr dB
            |  CSI-RSRP: $csiRsrp dBm
            |  PCI: $pci
            |  TAC: $tac
            |  NCI: $nci
            |  Location: ${currentLocation?.latitude}, ${currentLocation?.longitude}
        """.trimMargin())
        
        // TODO: Save to Room database
        // TODO: Upload to backend
    }

    /**
     * Process 4G LTE cell info
     */
    private fun processLteCell(cellInfo: CellInfoLte, carrierName: String, subId: Int) {
        val signalStrength = cellInfo.cellSignalStrength
        val cellIdentity = cellInfo.cellIdentity
        
        val rsrp = signalStrength.rsrp
        val rsrq = signalStrength.rsrq
        val sinr = signalStrength.rssnr
        
        val pci = cellIdentity.pci
        val tac = cellIdentity.tac
        val ci = cellIdentity.ci
        
        Log.d(TAG, """
            |4G LTE Signal Data:
            |  Carrier: $carrierName
            |  RSRP: $rsrp dBm
            |  RSRQ: $rsrq dB
            |  SINR: $sinr dB
            |  PCI: $pci
            |  TAC: $tac
            |  CI: $ci
            |  Location: ${currentLocation?.latitude}, ${currentLocation?.longitude}
        """.trimMargin())
        
        // TODO: Save to Room database
        // TODO: Upload to backend
    }

    /**
     * Process 2G/3G GSM cell info
     */
    private fun processGsmCell(cellInfo: CellInfoGsm, carrierName: String, subId: Int) {
        val signalStrength = cellInfo.cellSignalStrength
        val cellIdentity = cellInfo.cellIdentity
        
        val dbm = signalStrength.dbm
        val asuLevel = signalStrength.asuLevel
        
        val lac = cellIdentity.lac
        val cid = cellIdentity.cid
        
        Log.d(TAG, """
            |2G/3G GSM Signal Data:
            |  Carrier: $carrierName
            |  dBm: $dbm
            |  ASU: $asuLevel
            |  LAC: $lac
            |  CID: $cid
            |  Location: ${currentLocation?.latitude}, ${currentLocation?.longitude}
        """.trimMargin())
        
        // TODO: Save to Room database
        // TODO: Upload to backend
    }

    /**
     * Detect if 5G connection is Standalone (SA) or Non-Standalone (NSA)
     * Uses publicly available TelephonyManager APIs
     */
    @Suppress("MissingPermission")
    private fun detect5GType(telephonyManager: TelephonyManager): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ : Check data network type for NR
            val dataNetworkType = telephonyManager.dataNetworkType
            val voiceNetworkType = telephonyManager.voiceNetworkType
            
            return when {
                // If both voice and data are on NR, it's SA
                dataNetworkType == TelephonyManager.NETWORK_TYPE_NR -> {
                    if (voiceNetworkType == TelephonyManager.NETWORK_TYPE_NR) "5G_SA"
                    else "5G_NSA" // Data on NR but voice on LTE = NSA
                }
                // Fallback for LTE
                dataNetworkType == TelephonyManager.NETWORK_TYPE_LTE -> "4G_LTE"
                else -> "Unknown"
            }
        }
        // For Android 10 (API 29), CellInfoNr presence indicates 5G
        return "5G"
    }
}
