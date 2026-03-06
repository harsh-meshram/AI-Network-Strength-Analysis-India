import { z } from 'zod';

/**
 * Zod validation schemas for API request/response types.
 * Matches the Android app's SignalMeasurementEntity structure.
 */

// Single signal measurement from mobile device
export const SignalMeasurementSchema = z.object({
    // Required fields
    timestamp: z.number().int().positive(),            // Unix epoch ms
    carrier_name: z.string().min(1).max(100),
    network_type: z.string().min(1).max(20),  // e.g. '5G_SA', '5G_NSA', '4G_LTE', '2G_GSM', '5G', 'Unknown'
    h3_index: z.string().min(1).max(30),               // H3 res 11
    h3_index_res9: z.string().min(1).max(30),           // H3 res 9
    latitude: z.number().min(-90).max(90),
    longitude: z.number().min(-180).max(180),
    hashed_device_id: z.string().min(16).max(128),      // SHA-256 hex

    // Signal metrics (optional — different networks report different metrics)
    rsrp: z.number().int().nullable().optional(),
    rsrq: z.number().int().nullable().optional(),
    sinr: z.number().int().nullable().optional(),
    ss_rsrp: z.number().int().nullable().optional(),
    ss_sinr: z.number().int().nullable().optional(),
    dbm: z.number().int().nullable().optional(),
    asu_level: z.number().int().nullable().optional(),

    // Cell identity (optional)
    pci: z.number().int().min(0).max(1007).nullable().optional(),
    tac: z.number().int().min(0).nullable().optional(),
    ci: z.number().int().min(0).nullable().optional(),
    nci: z.number().int().min(0).nullable().optional(),
    lac: z.number().int().min(0).nullable().optional(),

    // Device info (optional)
    device_model: z.string().max(200).nullable().optional(),
    android_version: z.number().int().nullable().optional(),

    // Extra metadata (optional)
    sim_slot: z.number().int().min(0).max(3).optional().default(0),
    is_indoor: z.boolean().optional().default(false),
    speed_kmh: z.number().min(0).max(500).nullable().optional(),
});

// Batch ingestion request: array of measurements
export const BatchIngestSchema = z.object({
    measurements: z.array(SignalMeasurementSchema).min(1).max(500),
    device_id: z.string().min(16).max(128),  // Hashed device ID for batch tracking
});

// Heatmap query parameters
export const HeatmapQuerySchema = z.object({
    min_lat: z.coerce.number().min(-90).max(90),
    max_lat: z.coerce.number().min(-90).max(90),
    min_lng: z.coerce.number().min(-180).max(180),
    max_lng: z.coerce.number().min(-180).max(180),
    carrier: z.string().optional(),
    network_type: z.string().optional(),
    hours: z.coerce.number().int().min(1).max(8760).optional().default(168), // default 7 days
});

// Stats query
export const StatsQuerySchema = z.object({
    carrier: z.string().optional(),
});

// Response types
export type SignalMeasurement = z.infer<typeof SignalMeasurementSchema>;
export type BatchIngestRequest = z.infer<typeof BatchIngestSchema>;
export type HeatmapQuery = z.infer<typeof HeatmapQuerySchema>;
