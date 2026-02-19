import { Pool, PoolClient, QueryResult } from 'pg';
import { config } from './config';

/**
 * PostgreSQL connection pool with PostGIS support.
 * Uses pg connection pooling for optimal performance.
 */
const pool = new Pool(config.db);

pool.on('error', (err: Error) => {
    console.error('Unexpected error on idle client:', err);
    process.exit(-1);
});

pool.on('connect', () => {
    console.log('📗 New database connection established');
});

/**
 * Execute a parameterized query
 */
export async function query(text: string, params?: any[]): Promise<QueryResult> {
    const start = Date.now();
    const result = await pool.query(text, params);
    const duration = Date.now() - start;

    if (config.server.env === 'development') {
        console.log(`📊 Query (${duration}ms): ${text.substring(0, 80)}...`);
    }

    return result;
}

/**
 * Get a client from the pool for transactions
 */
export async function getClient(): Promise<PoolClient> {
    return pool.connect();
}

/**
 * Execute a transaction
 */
export async function transaction<T>(
    callback: (client: PoolClient) => Promise<T>
): Promise<T> {
    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        const result = await callback(client);
        await client.query('COMMIT');
        return result;
    } catch (e) {
        await client.query('ROLLBACK');
        throw e;
    } finally {
        client.release();
    }
}

/**
 * Check database connection health
 */
export async function healthCheck(): Promise<boolean> {
    try {
        const result = await pool.query('SELECT 1 as ok');
        return result.rows[0]?.ok === 1;
    } catch {
        return false;
    }
}

/**
 * Close database pool
 */
export async function closePool(): Promise<void> {
    await pool.end();
}

export default pool;
