import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';

import authRoutes from './routes/authRoutes.js';
import { runMigration } from './db/migrate.js';

dotenv.config();

const app  = express();
const PORT = process.env.PORT || 3000;

// =============================================================
// SEGURIDAD
// =============================================================

app.use(helmet());

// CORS: permite apps móviles (sin origin) y cualquier origin configurado
app.use(cors({
  origin: (origin, cb) => cb(null, true), // móviles no envían origin
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
  credentials: true,
}));

// Rate limit global
app.use(rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 15 * 60 * 1000,
  max:      parseInt(process.env.RATE_LIMIT_MAX_REQUESTS) || 200,
  message:  { success: false, message: 'Demasiadas solicitudes. Inténtalo más tarde.', code: 'RATE_LIMIT_EXCEEDED' },
  standardHeaders: true,
  legacyHeaders: false,
}));

app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// =============================================================
// RUTAS
// =============================================================

app.get('/', (_req, res) => res.json({
  success: true,
  message: '🚕 TaxiApp API',
  version: '2.0.0',
  endpoints: {
    health:       'GET  /health',
    otpSend:      'POST /api/auth/otp/send',
    otpVerify:    'POST /api/auth/otp/verify',
    register:     'POST /api/auth/register',
    loginPhone:   'POST /api/auth/login/phone',
    loginEmail:   'POST /api/auth/login',
    refresh:      'POST /api/auth/refresh',
    logout:       'POST /api/auth/logout',
    me:           'GET  /api/auth/me',
  },
}));

app.get('/health', (_req, res) => res.json({
  success: true,
  status: 'OK',
  version: '2.0.0',
  timestamp: new Date().toISOString(),
  environment: process.env.NODE_ENV || 'development',
  dev_mode: process.env.DEV_MODE !== 'false',
}));

app.use('/api/auth', authRoutes);

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
    ...(process.env.NODE_ENV !== 'production' && { detail: err.message }),
  });
});

// =============================================================
// ARRANQUE: migrar DB → levantar servidor
// =============================================================
async function start() {
  try {
    await runMigration();
  } catch (err) {
    // Si la migración falla (ej. schema ya existe), loguear y continuar
    console.warn('⚠️  Migración omitida o con advertencia:', err.message);
  }

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
}

start();

export default app;
