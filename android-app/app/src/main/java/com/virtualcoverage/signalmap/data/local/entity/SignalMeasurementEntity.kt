package com.virtualcoverage.signalmap.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for persisting signal measurements locally.
 * Data is buffered here before upload to backend.
 * All location data is stored as H3 index for privacy.
 */
@Entity(
    tableName = "signal_measurements",
    indices = [
        Index(value = ["h3Index"]),
        Index(value = ["carrierName", "timestamp"]),
        Index(value = ["isSynced"])
    ]
)
data class SignalMeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Timestamp
    val timestamp: Long = System.currentTimeMillis(),

    // Carrier info
    val carrierName: String,
    val subscriptionId: Int,

    // Network type: 5G_SA, 5G_NSA, 4G_LTE, 3G, 2G
    val networkType: String,

    // Signal metrics (nullable since not all techs have all metrics)
    val rsrp: Int? = null,        // Reference Signal Received Power (dBm) - LTE/NR
    val rsrq: Int? = null,        // Reference Signal Received Quality (dB) - LTE
    val sinr: Int? = null,        // Signal-to-Interference-plus-Noise Ratio (dB)
    val ssRsrp: Int? = null,      // SS-RSRP (dBm) - 5G NR
    val ssSinr: Int? = null,      // SS-SINR (dB) - 5G NR
    val csiRsrp: Int? = null,     // CSI-RSRP (dBm) - 5G NR
    val dbm: Int? = null,         // Signal strength in dBm - GSM
    val asuLevel: Int? = null,    // ASU level - GSM

    // Cell identifiers
    val pci: Int? = null,         // Physical Cell ID
    val tac: Int? = null,         // Tracking Area Code
    val ci: Long? = null,         // Cell Identity (NCI for NR, CI for LTE, CID for GSM)
    val lac: Int? = null,         // Location Area Code (GSM)

    // Location (H3 indexed for privacy)
    val h3Index: String,          // H3 hex index at resolution 11 (room-level)
    val h3IndexRes9: String,      // H3 hex index at resolution 9 (neighborhood)
    val latitude: Double,         // Raw lat (only stored locally, stripped before upload)
    val longitude: Double,        // Raw lng (only stored locally, stripped before upload)

    // Device info (anonymized)
    val hashedDeviceId: String,   // SHA-256 hash of device ID
    val deviceModel: String,      // e.g. "Pixel 8 Pro"
    val androidVersion: Int,      // e.g. 34 (API level)

    // Sync status
    val isSynced: Boolean = false,
    val syncAttempts: Int = 0
)
