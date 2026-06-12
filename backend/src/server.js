import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';

import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

import authRoutes from './routes/authRoutes.js';
import adminRoutes from './routes/adminRoutes.js';
import { runMigration } from './db/migrate.js';

const __dirname = dirname(fileURLToPath(import.meta.url));

dotenv.config();

const app  = express();
const PORT = process.env.PORT || 3000;

// Estado de la migración (para reportarlo en /health)
let migrationStatus = 'pending';
let migrationError  = null;

// =============================================================
// MIDDLEWARES
// =============================================================

app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc:  ["'self'", "'unsafe-inline'", "https://fonts.googleapis.com"],
      styleSrc:   ["'self'", "'unsafe-inline'", "https://fonts.googleapis.com", "https://fonts.gstatic.com"],
      fontSrc:    ["'self'", "https://fonts.gstatic.com"],
      connectSrc: ["'self'", "https://taxiapp-production-1a53.up.railway.app"],
      imgSrc:     ["'self'", "data:", "https:"],
    },
  },
}));

app.use(cors({
  origin: (origin, cb) => cb(null, true),
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
  credentials: true,
}));

app.use(rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 15 * 60 * 1000,
  max:      parseInt(process.env.RATE_LIMIT_MAX_REQUESTS) || 200,
  message:  { success: false, message: 'Demasiadas solicitudes.', code: 'RATE_LIMIT_EXCEEDED' },
  standardHeaders: true,
  legacyHeaders: false,
}));

app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// =============================================================
// RUTAS DE SISTEMA
// =============================================================

app.get('/', (_req, res) => res.json({
  success: true,
  message: '🚕 TaxiApp API',
  version: '2.0.0',
  endpoints: {
    health:     'GET  /health',
    migrate:    'GET  /migrate  ← forzar migración',
    otpSend:    'POST /api/auth/otp/send',
    otpVerify:  'POST /api/auth/otp/verify',
    register:   'POST /api/auth/register',
    loginPhone: 'POST /api/auth/login/phone',
    loginEmail: 'POST /api/auth/login',
    refresh:    'POST /api/auth/refresh',
    logout:     'POST /api/auth/logout',
    me:         'GET  /api/auth/me',
  },
}));

app.get('/health', (_req, res) => res.json({
  success: true,
  status: 'OK',
  version: '2.0.0',
  timestamp: new Date().toISOString(),
  environment: process.env.NODE_ENV || 'development',
  dev_mode: process.env.DEV_MODE !== 'false',
  db_migration: migrationStatus,
  ...(migrationError && { migration_error: migrationError }),
}));

// Endpoint para forzar la migración desde el navegador (Railway)
app.get('/migrate', async (_req, res) => {
  try {
    migrationStatus = 'running';
    migrationError  = null;
    await runMigration();
    migrationStatus = 'ok';
    res.json({ success: true, message: 'Migración ejecutada correctamente ✅' });
  } catch (err) {
    migrationStatus = 'failed';
    migrationError  = err.message;
    res.status(500).json({
      success: false,
      message: 'Error en migración',
      error: err.message,
    });
  }
});

// =============================================================
// ADMIN PANEL (archivos estáticos)
// Carpeta: backend/public/admin (dentro del Root Directory de Railway)
// =============================================================
app.use('/admin', express.static(join(__dirname, '../public/admin')));

// =============================================================
// RUTAS API
// =============================================================

app.use('/api/auth',  authRoutes);
app.use('/api/admin', adminRoutes);

// 404
app.use('*', (req, res) => res.status(404).json({
  success: false,
  message: `Ruta no encontrada: ${req.method} ${req.originalUrl}`,
  code: 'NOT_FOUND',
}));

// Error global
app.use((err, _req, res, _next) => {
  console.error('❌ Error no manejado:', err);
  res.status(500).json({
    success: false,
    message: 'Error interno del servidor.',
    code: 'INTERNAL_ERROR',
    detail: err.message, // siempre mostramos el detalle para diagnóstico
  });
});

// =============================================================
// ARRANQUE
// =============================================================
async function start() {
  // Levantar servidor primero (Railway necesita que escuche antes del healthcheck)
  app.listen(PORT, '0.0.0.0', () => {
    console.log('');
    console.log('🚕 ================================');
    console.log('   TaxiApp API v2.0 - Railway Ready');
    console.log('🚕 ================================');
    console.log(`🌍 Entorno:  ${process.env.NODE_ENV || 'development'}`);
    console.log(`🚀 Puerto:   ${PORT}`);
    console.log(`🔧 Dev Mode: ${process.env.DEV_MODE !== 'false' ? 'ON (OTP = 0000)' : 'OFF'}`);
    console.log('🚕 ================================');
    console.log('');
  });

  // Migración después del arranque
  try {
    await runMigration();
    migrationStatus = 'ok';
  } catch (err) {
    migrationStatus = 'failed';
    migrationError  = err.message;
    console.error('❌ Migración falló:', err.message);
    console.error('👉 Visita /migrate para reintentarlo manualmente');
  }
}

start();

export default app;
