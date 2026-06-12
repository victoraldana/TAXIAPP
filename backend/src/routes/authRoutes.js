import express from 'express';
import rateLimit from 'express-rate-limit';
import {
  register,
  login,
  loginByPhone,
  refreshToken,
  logout,
  getMe,
} from '../controllers/authController.js';
import { sendOtp, verifyOtp } from '../controllers/otpController.js';
import {
  validateRefreshToken,
} from '../middleware/validators.js';
import { authenticate } from '../middleware/auth.js';

const router = express.Router();

// Rate limiter específico para autenticación (más estricto)
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutos
  max: 20,
  message: {
    success: false,
    message: 'Demasiados intentos. Por favor espera 15 minutos.',
    code: 'TOO_MANY_REQUESTS',
  },
  standardHeaders: true,
  legacyHeaders: false,
  skipSuccessfulRequests: true,
});

// ======================
// Rutas OTP
// ======================

/**
 * @route  POST /api/auth/otp/send
 * @desc   Enviar código OTP a teléfono o email
 * @body   { target, type: 'phone'|'email' }
 */
router.post('/otp/send', authLimiter, sendOtp);

/**
 * @route  POST /api/auth/otp/verify
 * @desc   Verificar código OTP (en DEV_MODE el código "0000" siempre es válido)
 * @body   { target, type: 'phone'|'email', code }
 */
router.post('/otp/verify', authLimiter, verifyOtp);

// ======================
// Rutas públicas
// ======================

/**
 * @route  POST /api/auth/register
 * @desc   Registrar nuevo usuario (flujo por teléfono + KYC)
 * @body   { phone, password, full_name, cedula, birth_date, email,
 *           selfie_url, id_doc_url, role }
 */
router.post('/register', authLimiter, register);

/**
 * @route  POST /api/auth/login
 * @desc   Iniciar sesión con email y contraseña (compatibilidad)
 * @body   { email, password }
 */
router.post('/login', authLimiter, login);

/**
 * @route  POST /api/auth/login/phone
 * @desc   Iniciar sesión con teléfono y contraseña (flujo principal)
 * @body   { phone, password }
 */
router.post('/login/phone', authLimiter, loginByPhone);

/**
 * @route  POST /api/auth/refresh
 * @desc   Renovar access token usando refresh token
 * @body   { refresh_token }
 */
router.post('/refresh', validateRefreshToken, refreshToken);

/**
 * @route  POST /api/auth/logout
 * @desc   Cerrar sesión (revocar refresh token)
 * @body   { refresh_token? }
 */
router.post('/logout', logout);

// ======================
// Rutas protegidas
// ======================

/**
 * @route  GET /api/auth/me
 * @desc   Obtener perfil del usuario autenticado
 * @header Authorization: Bearer <access_token>
 */
router.get('/me', authenticate, getMe);

export default router;
