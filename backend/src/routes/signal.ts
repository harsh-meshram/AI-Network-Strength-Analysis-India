import { Router, Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { query, transaction } from '../db';
import { BatchIngestSchema, HeatmapQuerySchema, StatsQuerySchema } from '../schemas';

const router = Router();

/**
 * POST /api/v1/ingest/signal
 * 
 * Batch ingest signal measurements from mobile devices.
 * Accepts up to 500 measurements per request.
 * Each measurement is validated with Zod and inserted into PostgreSQL.
 * PostGIS geography point is auto-populated via database trigger.
 */
router.post('/ingest/signal', async (req: Request, res: Response): Promise<void> => {
    try {
        // Validate request body
        const parsed = BatchIngestSchema.safeParse(req.body);
        if (!parsed.success) {
            res.status(400).json({
                error: 'Validation failed',
                details: parsed.error.flatten().fieldErrors,
            });
            return;
        }

        const { measurements, device_id } = parsed.data;
        const batchId = uuidv4();
        let insertedCount = 0;

        // Batch insert using a transaction
        await transaction(async (client) => {
            const insertSQL = `
        INSERT INTO signal_measurements (
          timestamp, carrier_name, network_type,
          rsrp, rsrq, sinr, ss_rsrp, ss_sinr, dbm, asu_level,
          pci, tac, ci, nci, lac,
          h3_index, h3_index_res9,
          latitude, longitude,
          hashed_device_id, device_model, android_version,
          sim_slot, is_indoor, speed_kmh,
          batch_id
        ) VALUES (
          to_timestamp($1 / 1000.0), $2, $3,
          $4, $5, $6, $7, $8, $9, $10,
          $11, $12, $13, $14, $15,
          $16, $17,
          $18, $19,
          $20, $21, $22,
          $23, $24, $25,
          $26
        )
      `;

            for (const m of measurements) {
                await client.query(insertSQL, [
                    m.timestamp,
                    m.carrier_name,
                    m.network_type,
                    m.rsrp ?? null,
                    m.rsrq ?? null,
                    m.sinr ?? null,
                    m.ss_rsrp ?? null,
                    m.ss_sinr ?? null,
                    m.dbm ?? null,
                    m.asu_level ?? null,
                    m.pci ?? null,
                    m.tac ?? null,
                    m.ci ?? null,
                    m.nci ?? null,
                    m.lac ?? null,
                    m.h3_index,
                    m.h3_index_res9,
                    m.latitude,
                    m.longitude,
                    m.hashed_device_id,
                    m.device_model ?? null,
                    m.android_version ?? null,
                    m.sim_slot ?? 0,
                    m.is_indoor ?? false,
                    m.speed_kmh ?? null,
                    batchId,
                ]);
                insertedCount++;
            }
        });

        console.log(`📥 Ingested ${insertedCount} measurements from device ${device_id.substring(0, 8)}... (batch: ${batchId})`);

        res.status(201).json({
            success: true,
            batch_id: batchId,
            inserted: insertedCount,
            message: `Successfully ingested ${insertedCount} signal measurements`,
        });

    } catch (error: any) {
        console.error('❌ Ingest error:', error.message);
        res.status(500).json({
            error: 'Failed to ingest measurements',
            message: error.message,
        });
    }
});

/**
 * GET /api/v1/heatmap
 * 
 * Get aggregated signal data for heatmap rendering.
 * Groups by H3 resolution-9 hexagons within a bounding box.
 * Supports carrier and network type filtering.
 */
router.get('/heatmap', async (req: Request, res: Response): Promise<void> => {
    try {
        const parsed = HeatmapQuerySchema.safeParse(req.query);
        if (!parsed.success) {
            res.status(400).json({
                error: 'Invalid query parameters',
                details: parsed.error.flatten().fieldErrors,
            });
            return;
        }

        const { min_lat, max_lat, min_lng, max_lng, carrier, network_type, hours } = parsed.data;

        let sql = `
      SELECT 
        h3_index_res9,
        COUNT(*) as measurement_count,
        ROUND(AVG(COALESCE(rsrp, ss_rsrp, dbm))::numeric, 1) as avg_rsrp,
        MIN(COALESCE(rsrp, ss_rsrp, dbm)) as min_rsrp,
        MAX(COALESCE(rsrp, ss_rsrp, dbm)) as max_rsrp,
        ROUND(AVG(COALESCE(sinr, ss_sinr))::numeric, 1) as avg_sinr,
        ROUND(AVG(latitude)::numeric, 6) as center_lat,
        ROUND(AVG(longitude)::numeric, 6) as center_lng,
        MODE() WITHIN GROUP (ORDER BY carrier_name) as primary_carrier,
        MODE() WITHIN GROUP (ORDER BY network_type) as primary_network
      FROM signal_measurements
      WHERE latitude BETWEEN $1 AND $2
        AND longitude BETWEEN $3 AND $4
        AND timestamp > NOW() - INTERVAL '1 hour' * $5
    `;

        const params: any[] = [min_lat, max_lat, min_lng, max_lng, hours];
        let paramIndex = 6;

        if (carrier) {
            sql += ` AND carrier_name = $${paramIndex}`;
            params.push(carrier);
            paramIndex++;
        }

        if (network_type) {
            sql += ` AND network_type = $${paramIndex}`;
            params.push(network_type);
            paramIndex++;
        }

        sql += `
      GROUP BY h3_index_res9
      ORDER BY measurement_count DESC
      LIMIT 2000
    `;

        const result = await query(sql, params);

        res.json({
            hexagons: result.rows,
            count: result.rows.length,
            bounds: { min_lat, max_lat, min_lng, max_lng },
            filters: { carrier: carrier || 'all', network_type: network_type || 'all', hours },
        });

    } catch (error: any) {
        console.error('❌ Heatmap query error:', error.message);
        res.status(500).json({ error: 'Failed to fetch heatmap data' });
    }
});

/**
 * GET /api/v1/stats
 * 
 * Get overall signal statistics (carrier coverage, device count, etc.)
 */
router.get('/stats', async (req: Request, res: Response): Promise<void> => {
    try {
        const parsed = StatsQuerySchema.safeParse(req.query);
        if (!parsed.success) {
            res.status(400).json({ error: 'Invalid query parameters' });
            return;
        }

        let sql = `
      SELECT 
        carrier_name,
        network_type,
        COUNT(*) as total_measurements,
        COUNT(DISTINCT hashed_device_id) as unique_devices,
        COUNT(DISTINCT h3_index_res9) as covered_hexagons,
        ROUND(AVG(COALESCE(rsrp, ss_rsrp, dbm))::numeric, 1) as avg_signal,
        MIN(timestamp) as earliest_measurement,
        MAX(timestamp) as latest_measurement
      FROM signal_measurements
    `;

        const params: any[] = [];
        if (parsed.data.carrier) {
            sql += ` WHERE carrier_name = $1`;
            params.push(parsed.data.carrier);
        }

        sql += ` GROUP BY carrier_name, network_type ORDER BY total_measurements DESC`;

        const result = await query(sql, params);

        // Total summary
        const totalResult = await query(`
      SELECT 
        COUNT(*) as total,
        COUNT(DISTINCT hashed_device_id) as devices,
        COUNT(DISTINCT h3_index_res9) as hexagons
      FROM signal_measurements
    `);

        res.json({
            carriers: result.rows,
            summary: totalResult.rows[0],
        });
    } catch (error: any) {
        console.error('❌ Stats error:', error.message);
        res.status(500).json({ error: 'Failed to fetch stats' });
    }
});

/**
 * GET /api/v1/carriers
 * 
 * List all carriers with measurement counts
 */
router.get('/carriers', async (_req: Request, res: Response): Promise<void> => {
    try {
        const result = await query(`
      SELECT 
        carrier_name,
        COUNT(*) as measurement_count,
        COUNT(DISTINCT network_type) as network_types,
        array_agg(DISTINCT network_type) as networks
      FROM signal_measurements
      GROUP BY carrier_name
      ORDER BY measurement_count DESC
    `);

        res.json({ carriers: result.rows });
    } catch (error: any) {
        res.status(500).json({ error: 'Failed to fetch carriers' });
    }
});

/**
 * GET /api/v1/hexagon/:h3Index
 * 
 * Get detailed measurements for a specific H3 hexagon
 */
router.get('/hexagon/:h3Index', async (req: Request, res: Response): Promise<void> => {
    try {
        const { h3Index } = req.params;

        const result = await query(`
      SELECT 
        timestamp, carrier_name, network_type,
        COALESCE(rsrp, ss_rsrp, dbm) as signal_strength,
        COALESCE(sinr, ss_sinr) as sinr_value,
        pci, tac, device_model, android_version,
        latitude, longitude
      FROM signal_measurements
      WHERE h3_index_res9 = $1
      ORDER BY timestamp DESC
      LIMIT 100
    `, [h3Index]);

        // Aggregate stats for this hex
        const statsResult = await query(`
      SELECT 
        COUNT(*) as total,
        ROUND(AVG(COALESCE(rsrp, ss_rsrp, dbm))::numeric, 1) as avg_rsrp,
        MIN(COALESCE(rsrp, ss_rsrp, dbm)) as worst_signal,
        MAX(COALESCE(rsrp, ss_rsrp, dbm)) as best_signal,
        COUNT(DISTINCT carrier_name) as carrier_count,
        COUNT(DISTINCT hashed_device_id) as device_count
      FROM signal_measurements
      WHERE h3_index_res9 = $1
    `, [h3Index]);

        res.json({
            h3_index: h3Index,
            measurements: result.rows,
            stats: statsResult.rows[0],
        });
    } catch (error: any) {
        res.status(500).json({ error: 'Failed to fetch hexagon data' });
    }
});

/**
 * GET /api/v1/nearby
 * 
 * Find measurements near a point using PostGIS ST_DWithin
 */
router.get('/nearby', async (req: Request, res: Response): Promise<void> => {
    try {
        const lat = parseFloat(req.query.lat as string);
        const lng = parseFloat(req.query.lng as string);
        const radiusMeters = parseInt(req.query.radius as string) || 500;

        if (isNaN(lat) || isNaN(lng)) {
            res.status(400).json({ error: 'lat and lng are required' });
            return;
        }

        const result = await query(`
      SELECT 
        h3_index_res9,
        carrier_name,
        network_type,
        ROUND(AVG(COALESCE(rsrp, ss_rsrp, dbm))::numeric, 1) as avg_signal,
        COUNT(*) as count,
        ROUND(ST_Distance(
          location,
          ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography
        )::numeric, 0) as distance_meters
      FROM signal_measurements
      WHERE ST_DWithin(
        location,
        ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography,
        $3
      )
      GROUP BY h3_index_res9, carrier_name, network_type
      ORDER BY distance_meters ASC
      LIMIT 100
    `, [lat, lng, radiusMeters]);

        res.json({
            center: { lat, lng },
            radius_meters: radiusMeters,
            results: result.rows,
        });
    } catch (error: any) {
        res.status(500).json({ error: 'Failed to fetch nearby data' });
    }
});

export default router;
