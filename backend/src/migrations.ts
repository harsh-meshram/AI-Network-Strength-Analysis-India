import { query } from './db';

/**
 * Database migration: Creates all tables, indexes, and extensions.
 * 
 * Schema design:
 * - PostGIS for geospatial queries (ST_MakePoint, geography type)
 * - H3 hex indexes stored as TEXT for spatial aggregation
 * - Partitioned by time for efficient time-range queries
 * - Comprehensive indexes for carrier, network type, and spatial lookups
 */
export async function runMigrations(): Promise<void> {
    console.log('🔄 Running database migrations...');

    // 1. Enable PostGIS extension
    await query(`CREATE EXTENSION IF NOT EXISTS postgis;`);
    console.log('  ✅ PostGIS extension enabled');

    // 2. Create signal_measurements table
    await query(`
    CREATE TABLE IF NOT EXISTS signal_measurements (
      id              BIGSERIAL PRIMARY KEY,
      
      -- Timestamp
      timestamp       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      
      -- Carrier & Network
      carrier_name    TEXT NOT NULL,
      network_type    TEXT NOT NULL,  -- '5G_SA', '5G_NSA', 'LTE', 'GSM', 'WCDMA'
      
      -- Signal Metrics (nullable since different networks report different metrics)
      rsrp            INTEGER,        -- 4G LTE Reference Signal Received Power (dBm)
      rsrq            INTEGER,        -- 4G LTE Reference Signal Received Quality (dB)
      sinr            INTEGER,        -- 4G LTE Signal-to-Interference-plus-Noise Ratio (dB)
      ss_rsrp         INTEGER,        -- 5G NR SS Reference Signal Received Power (dBm)
      ss_sinr         INTEGER,        -- 5G NR SS Signal-to-Interference-plus-Noise Ratio (dB)
      dbm             INTEGER,        -- 2G/3G signal strength (dBm)
      asu_level       INTEGER,        -- 2G/3G Arbitrary Strength Unit
      
      -- Cell Identity
      pci             INTEGER,        -- Physical Cell Identity (4G/5G)
      tac             INTEGER,        -- Tracking Area Code
      ci              BIGINT,         -- Cell Identity (2G/3G)
      nci             BIGINT,         -- NR Cell Identity (5G)
      lac             INTEGER,        -- Location Area Code (2G/3G)
      
      -- H3 Spatial Indexing
      h3_index        TEXT NOT NULL,  -- H3 hex at resolution 11 (room-level, ~25m)
      h3_index_res9   TEXT NOT NULL,  -- H3 hex at resolution 9 (neighborhood, ~175m)
      
      -- Location (PostGIS geography point)
      latitude        DOUBLE PRECISION NOT NULL,
      longitude       DOUBLE PRECISION NOT NULL,
      location        GEOGRAPHY(POINT, 4326),  -- PostGIS geography for spatial queries
      
      -- Device (anonymized)
      hashed_device_id TEXT NOT NULL,  -- SHA-256 hashed device ID
      device_model    TEXT,
      android_version INTEGER,
      
      -- Metadata
      sim_slot        INTEGER DEFAULT 0,
      is_indoor       BOOLEAN DEFAULT FALSE,
      speed_kmh       REAL,
      
      -- Ingestion tracking
      batch_id        UUID,
      ingested_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);
    console.log('  ✅ signal_measurements table created');

    // 3. Automatically populate PostGIS location from lat/lng via trigger
    await query(`
    CREATE OR REPLACE FUNCTION set_location_point()
    RETURNS TRIGGER AS $$
    BEGIN
      NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
      RETURN NEW;
    END;
    $$ LANGUAGE plpgsql;
  `);

    await query(`
    DROP TRIGGER IF EXISTS trg_set_location ON signal_measurements;
    CREATE TRIGGER trg_set_location
      BEFORE INSERT ON signal_measurements
      FOR EACH ROW
      EXECUTE FUNCTION set_location_point();
  `);
    console.log('  ✅ PostGIS location trigger created');

    // 4. Create indexes for common query patterns
    // H3 index for spatial aggregation (most important for heatmap)
    await query(`
    CREATE INDEX IF NOT EXISTS idx_signal_h3_res9 
    ON signal_measurements (h3_index_res9);
  `);

    await query(`
    CREATE INDEX IF NOT EXISTS idx_signal_h3_res11 
    ON signal_measurements (h3_index);
  `);

    // Carrier + time for carrier-specific queries
    await query(`
    CREATE INDEX IF NOT EXISTS idx_signal_carrier_time 
    ON signal_measurements (carrier_name, timestamp DESC);
  `);

    // Network type for filtering by technology
    await query(`
    CREATE INDEX IF NOT EXISTS idx_signal_network_type 
    ON signal_measurements (network_type);
  `);

    // PostGIS spatial index on geography column
    await query(`
    CREATE INDEX IF NOT EXISTS idx_signal_location_gist 
    ON signal_measurements USING GIST (location);
  `);

    // Time-based index for recent queries
    await query(`
    CREATE INDEX IF NOT EXISTS idx_signal_timestamp 
    ON signal_measurements (timestamp DESC);
  `);

    // Device for per-device analytics
    await query(`
    CREATE INDEX IF NOT EXISTS idx_signal_device 
    ON signal_measurements (hashed_device_id);
  `);

    console.log('  ✅ Database indexes created');

    // 5. Create aggregation view for heatmap rendering
    await query(`
    CREATE OR REPLACE VIEW heatmap_h3_summary AS
    SELECT 
      h3_index_res9,
      carrier_name,
      network_type,
      COUNT(*) as measurement_count,
      ROUND(AVG(COALESCE(rsrp, ss_rsrp, dbm))::numeric, 1) as avg_rsrp,
      MIN(COALESCE(rsrp, ss_rsrp, dbm)) as min_rsrp,
      MAX(COALESCE(rsrp, ss_rsrp, dbm)) as max_rsrp,
      ROUND(AVG(COALESCE(sinr, ss_sinr))::numeric, 1) as avg_sinr,
      MIN(timestamp) as first_seen,
      MAX(timestamp) as last_seen,
      ROUND(AVG(latitude)::numeric, 6) as center_lat,
      ROUND(AVG(longitude)::numeric, 6) as center_lng
    FROM signal_measurements
    GROUP BY h3_index_res9, carrier_name, network_type;
  `);
    console.log('  ✅ heatmap_h3_summary view created');

    // 6. Create materialized view for fast dashboard queries
    await query(`
    CREATE MATERIALIZED VIEW IF NOT EXISTS signal_stats AS
    SELECT 
      carrier_name,
      network_type,
      COUNT(*) as total_measurements,
      COUNT(DISTINCT hashed_device_id) as unique_devices,
      COUNT(DISTINCT h3_index_res9) as covered_hexagons,
      ROUND(AVG(COALESCE(rsrp, ss_rsrp, dbm))::numeric, 1) as avg_signal,
      MIN(timestamp) as earliest,
      MAX(timestamp) as latest
    FROM signal_measurements
    GROUP BY carrier_name, network_type;
  `);

    await query(`
    CREATE UNIQUE INDEX IF NOT EXISTS idx_signal_stats_carrier_network
    ON signal_stats (carrier_name, network_type);
  `);
    console.log('  ✅ signal_stats materialized view created');

    console.log('✅ All migrations completed successfully!');
}
