import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import { config } from './config';
import { healthCheck, closePool } from './db';
import { runMigrations } from './migrations';
import signalRoutes from './routes/signal';

const app = express();

// ============================================
// Middleware
// ============================================

// Security headers
app.use(helmet());

// CORS (allow Android app to connect)
app.use(cors({
    origin: '*',  // In production, restrict to specific origins
    methods: ['GET', 'POST'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-API-Key'],
}));

// Body parsing (large payloads for batch ingestion)
app.use(express.json({ limit: '5mb' }));

// Rate limiting
const limiter = rateLimit({
    windowMs: config.rateLimit.windowMs,
    max: config.rateLimit.maxRequests,
    message: { error: 'Too many requests, please try again later' },
    standardHeaders: true,
    legacyHeaders: false,
});
app.use('/api/', limiter);

// Request logging in development
if (config.server.env === 'development') {
    app.use((req, _res, next) => {
        console.log(`📨 ${req.method} ${req.path}`);
        next();
    });
}

// ============================================
// Routes
// ============================================

// Health check endpoint
app.get('/health', async (_req, res) => {
    const dbOk = await healthCheck();
    const status = dbOk ? 'healthy' : 'unhealthy';
    res.status(dbOk ? 200 : 503).json({
        status,
        timestamp: new Date().toISOString(),
        database: dbOk ? 'connected' : 'disconnected',
        uptime: process.uptime(),
        version: '1.0.0',
    });
});

// API v1 routes
app.use('/api/v1', signalRoutes);

// 404 handler
app.use((_req, res) => {
    res.status(404).json({ error: 'Endpoint not found' });
});

// Error handler
app.use((err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    console.error('❌ Unhandled error:', err);
    res.status(500).json({
        error: 'Internal server error',
        message: config.server.env === 'development' ? err.message : undefined,
    });
});

// ============================================
// Server Startup
// ============================================

async function start() {
    try {
        console.log('╔══════════════════════════════════════════════╗');
        console.log('║   Virtual Coverage Map — Backend Server      ║');
        console.log('╚══════════════════════════════════════════════╝');
        console.log(`Environment: ${config.server.env}`);

        // Check database connection
        const dbOk = await healthCheck();
        if (!dbOk) {
            console.error('❌ Cannot connect to PostgreSQL database!');
            console.error('   Check DB_HOST, DB_PORT, DB_USER, DB_PASSWORD in .env');
            process.exit(1);
        }
        console.log('✅ PostgreSQL connected');

        // Run migrations
        await runMigrations();

        // Start HTTP server
        app.listen(config.server.port, () => {
            console.log('');
            console.log(`🚀 Server running at http://localhost:${config.server.port}`);
            console.log(`   Health:   http://localhost:${config.server.port}/health`);
            console.log(`   Ingest:   POST http://localhost:${config.server.port}/api/v1/ingest/signal`);
            console.log(`   Heatmap:  GET  http://localhost:${config.server.port}/api/v1/heatmap`);
            console.log(`   Stats:    GET  http://localhost:${config.server.port}/api/v1/stats`);
            console.log(`   Carriers: GET  http://localhost:${config.server.port}/api/v1/carriers`);
            console.log(`   Nearby:   GET  http://localhost:${config.server.port}/api/v1/nearby`);
            console.log('');
        });
    } catch (error) {
        console.error('❌ Failed to start server:', error);
        process.exit(1);
    }
}

// Graceful shutdown
process.on('SIGTERM', async () => {
    console.log('SIGTERM received, shutting down gracefully...');
    await closePool();
    process.exit(0);
});

process.on('SIGINT', async () => {
    console.log('SIGINT received, shutting down...');
    await closePool();
    process.exit(0);
});

start();

export default app;
