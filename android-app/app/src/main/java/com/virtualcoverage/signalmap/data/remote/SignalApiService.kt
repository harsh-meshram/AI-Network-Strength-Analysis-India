package com.virtualcoverage.signalmap.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API service for the Virtual Coverage Map backend.
 * Handles uploading signal measurements from the phone to the server.
 */
interface SignalApiService {

    @POST("/api/v1/ingest/signal")
    suspend fun ingestSignals(@Body request: BatchIngestRequest): Response<BatchIngestResponse>
}

/**
 * Request body for batch signal ingestion.
 * Matches the backend's BatchIngestSchema (Zod validation).
 */
data class BatchIngestRequest(
    val device_id: String,
    val measurements: List<SignalMeasurementDto>
)

/**
 * Individual signal measurement DTO for upload.
 * Field names use snake_case to match the backend API.
 */
data class SignalMeasurementDto(
    val timestamp: Long,
    val carrier_name: String,
    val network_type: String,
    val h3_index: String,
    val h3_index_res9: String,
    val latitude: Double,
    val longitude: Double,
    val hashed_device_id: String,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val ss_rsrp: Int? = null,
    val ss_sinr: Int? = null,
    val dbm: Int? = null,
    val asu_level: Int? = null,
    val pci: Int? = null,
    val tac: Int? = null,
    val ci: Long? = null,
    val nci: Long? = null,
    val lac: Int? = null,
    val device_model: String? = null,
    val android_version: Int? = null,
    val sim_slot: Int = 0,
    val is_indoor: Boolean = false,
    val speed_kmh: Float? = null
)

/**
 * Response from the backend after successful ingestion.
 */
data class BatchIngestResponse(
    val success: Boolean,
    val batch_id: String?,
    val inserted: Int?,
    val message: String?,
    val error: String?
)
