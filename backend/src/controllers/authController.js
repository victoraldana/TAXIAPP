import { query, withTransaction } from '../db/pool.js';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';

const BCRYPT_ROUNDS = 12;
// DEV_MODE: en Railway setea DEV_MODE=false para producción
const DEV_MODE = process.env.DEV_MODE !== 'false';

/**
 * Genera tokens JWT de acceso y refresh
 */
function generateTokens(userId, role) {
  const payload = { sub: userId, role };

  const accessToken = jwt.sign(payload, process.env.JWT_SECRET, {
    expiresIn: process.env.JWT_EXPIRES_IN || '7d',
  });

  const refreshToken = crypto.randomBytes(64).toString('hex');

  return { accessToken, refreshToken };
}

/**
 * Guarda el refresh token (hasheado) en la base de datos
 */
async function saveRefreshToken(userId, token, userAgent, ipAddress) {
  const tokenHash = crypto.createHash('sha256').update(token).digest('hex');
  const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000); // 30 días

  await query(
    `INSERT INTO refresh_tokens (user_id, token_hash, expires_at, user_agent, ip_address)
     VALUES ($1, $2, $3, $4, $5)`,
    [userId, tokenHash, expiresAt, userAgent, ipAddress]
  );

  return tokenHash;
}

// =============================================================
// LOGIN POR TELÉFONO + CONTRASEÑA
// =============================================================
export const loginByPhone = async (req, res) => {
  const { phone, password } = req.body;

  if (!phone || !password) {
    return res.status(400).json({
      success: false,
      message: 'Teléfono y contraseña son requeridos.',
      code: 'MISSING_FIELDS',
    });
  }

  try {
    const result = await query(
      `SELECT u.id, u.email, u.full_name, u.phone, u.avatar_url,
              u.password_hash, u.is_active, u.is_verified,
              u.failed_attempts, u.locked_until,
              r.name AS role, r.id AS role_id
       FROM users u
       JOIN roles r ON u.role_id = r.id
       WHERE u.phone = $1`,
      [phone.trim()]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({
        success: false,
        message: 'Teléfono o contraseña incorrectos.',
        code: 'INVALID_CREDENTIALS',
      });
    }

    const user = result.rows[0];

    // Verificar bloqueo
    if (user.locked_until && new Date(user.locked_until) > new Date()) {
      const minutesLeft = Math.ceil(
        (new Date(user.locked_until) - new Date()) / 60000
      );
      return res.status(423).json({
        success: false,
        message: `Cuenta bloqueada temporalmente. Inténtalo en ${minutesLeft} minutos.`,
        code: 'ACCOUNT_LOCKED',
      });
    }

    if (!user.is_active) {
      return res.status(403).json({
        success: false,
        message: 'Tu cuenta ha sido desactivada. Contacta al soporte.',
        code: 'ACCOUNT_INACTIVE',
      });
    }

    // En DEV_MODE la contraseña "1212" usa bcrypt pero también aceptamos bcrypt normal
    const passwordMatch = user.password_hash
      ? await bcrypt.compare(password, user.password_hash)
      : false;

    if (!passwordMatch) {
      // Intentos fallidos
      const failedAttempts = user.failed_attempts + 1;
      const lockUntil = failedAttempts >= 5
        ? new Date(Date.now() + 30 * 60 * 1000)
        : null;

      await query(
        `UPDATE users SET failed_attempts = $1, locked_until = $2 WHERE id = $3`,
        [failedAttempts, lockUntil, user.id]
      );

      const attemptsLeft = Math.max(0, 5 - failedAttempts);
      return res.status(401).json({
        success: false,
        message: attemptsLeft > 0
          ? `Teléfono o contraseña incorrectos. ${attemptsLeft} intentos restantes.`
          : 'Cuenta bloqueada por múltiples intentos fallidos.',
        code: 'INVALID_CREDENTIALS',
        attempts_left: attemptsLeft,
      });
    }

    // Login exitoso
    await query(
      `UPDATE users SET failed_attempts = 0, locked_until = NULL, last_login_at = NOW() WHERE id = $1`,
      [user.id]
    );

    const { accessToken, refreshToken } = generateTokens(user.id, user.role);
    await saveRefreshToken(user.id, refreshToken, req.headers['user-agent'], req.ip);

    // Perfil adicional
    let profile = null;
    if (user.role === 'driver') {
      const dp = await query(
        `SELECT vehicle_make, vehicle_model, vehicle_plate, vehicle_color,
                vehicle_type, is_available, is_approved, rating, total_trips,
                pago_movil_cedula, pago_movil_telefono, pago_movil_banco
         FROM driver_profiles WHERE user_id = $1`,
        [user.id]
      );
      profile = dp.rows[0] || null;
    } else if (user.role === 'client') {
      const cp = await query(
        `SELECT preferred_payment, rating, total_trips
         FROM client_profiles WHERE user_id = $1`,
        [user.id]
      );
      profile = cp.rows[0] || null;
    }

    return res.status(200).json({
      success: true,
      message: `Bienvenido${user.full_name ? ', ' + user.full_name : ''}!`,
      dev_mode: DEV_MODE,
      data: {
        user: {
          id: user.id,
          email: user.email,
          full_name: user.full_name,
          phone: user.phone,
          avatar_url: user.avatar_url,
          role: user.role,
          is_verified: user.is_verified,
          profile,
        },
        tokens: {
          access_token: accessToken,
          refresh_token: refreshToken,
          token_type: 'Bearer',
          expires_in: process.env.JWT_EXPIRES_IN || '7d',
        },
      },
    });
  } catch (error) {
    console.error('❌ Error en loginByPhone:', error);
    return res.status(500).json({
      success: false,
      message: 'Error interno del servidor. Inténtalo más tarde.',
      code: 'INTERNAL_ERROR',
    });
  }
};

// =============================================================
// REGISTRO POR PASOS (flujo telefónico)
// Body: { phone, password, full_name, cedula, birth_date,
//         email, selfie_url, id_doc_url, role }
// =============================================================
export const register = async (req, res) => {
  const {
    phone, password, full_name, cedula, birth_date,
    email, selfie_url, id_doc_url, role = 'client',
    pago_movil_cedula, pago_movil_telefono, pago_movil_banco,
  } = req.body;

  if (!phone) {
    return res.status(400).json({
      success: false,
      message: 'El número de teléfono es requerido.',
      code: 'MISSING_PHONE',
    });
  }

  try {
    // Verificar si el teléfono ya existe
    const existingPhone = await query('SELECT id FROM users WHERE phone = $1', [phone]);
    if (existingPhone.rows.length > 0) {
      return res.status(409).json({
        success: false,
        message: 'Ya existe una cuenta con este número de teléfono.',
        code: 'PHONE_ALREADY_EXISTS',
      });
    }

    // Verificar si el email ya existe (si se proporcionó)
    if (email) {
      const existingEmail = await query('SELECT id FROM users WHERE email = $1', [email.toLowerCase()]);
      if (existingEmail.rows.length > 0) {
        return res.status(409).json({
          success: false,
          message: 'Ya existe una cuenta con este correo electrónico.',
          code: 'EMAIL_ALREADY_EXISTS',
        });
      }
    }

    // Obtener role_id
    const roleResult = await query('SELECT id FROM roles WHERE name = $1', [role]);
    if (roleResult.rows.length === 0) {
      return res.status(400).json({
        success: false,
        message: 'Rol inválido.',
        code: 'INVALID_ROLE',
      });
    }
    const roleId = roleResult.rows[0].id;

    // Hashear contraseña
    const passwordHash = password ? await bcrypt.hash(password, BCRYPT_ROUNDS) : null;

    // Crear usuario y perfil en transacción
    const result = await withTransaction(async (client) => {
      const userResult = await client.query(
        `INSERT INTO users (
           role_id, phone, password_hash, full_name, email,
           cedula, birth_date, selfie_url, id_doc_url,
           is_active, is_verified, phone_verified_at,
           kyc_status
         ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,TRUE,TRUE,NOW(),$10)
         RETURNING id, email, full_name, phone, is_active, is_verified, kyc_status, created_at`,
        [
          roleId,
          phone,
          passwordHash,
          full_name || null,
          email ? email.toLowerCase() : null,
          cedula || null,
          birth_date || null,
          selfie_url || null,
          id_doc_url || null,
          selfie_url && id_doc_url ? 'submitted' : 'pending',
        ]
      );

      const newUser = userResult.rows[0];

      if (role === 'driver') {
        await client.query('INSERT INTO driver_profiles (user_id) VALUES ($1)', [newUser.id]);
        // Guardar datos de pago móvil si se proporcionaron
        if (pago_movil_cedula || pago_movil_telefono || pago_movil_banco) {
          await client.query(
            `UPDATE driver_profiles
             SET pago_movil_cedula = $1, pago_movil_telefono = $2, pago_movil_banco = $3
             WHERE user_id = $4`,
            [
              pago_movil_cedula || null,
              pago_movil_telefono || null,
              pago_movil_banco || null,
              newUser.id,
            ]
          );
        }
      } else {
        await client.query('INSERT INTO client_profiles (user_id) VALUES ($1)', [newUser.id]);
      }

      return newUser;
    });

    // Generar tokens
    const { accessToken, refreshToken } = generateTokens(result.id, role);
    await saveRefreshToken(result.id, refreshToken, req.headers['user-agent'], req.ip);

    return res.status(201).json({
      success: true,
      message: 'Cuenta creada exitosamente.',
      dev_mode: DEV_MODE,
      data: {
        user: {
          id: result.id,
          email: result.email,
          full_name: result.full_name,
          phone: result.phone,
          role,
          is_verified: result.is_verified,
          kyc_status: result.kyc_status,
        },
        tokens: {
          access_token: accessToken,
          refresh_token: refreshToken,
          token_type: 'Bearer',
          expires_in: process.env.JWT_EXPIRES_IN || '7d',
        },
      },
    });
  } catch (error) {
    console.error('❌ Error en register:', error);
    return res.status(500).json({
      success: false,
      message: 'Error interno del servidor. Inténtalo más tarde.',
      code: 'INTERNAL_ERROR',
    });
  }
};

// =============================================================
// LOGIN CLÁSICO POR EMAIL (compatibilidad)
// =============================================================
export const login = async (req, res) => {
  const { email, password } = req.body;

  try {
    const result = await query(
      `SELECT u.id, u.email, u.full_name, u.phone, u.avatar_url,
              u.password_hash, u.is_active, u.is_verified,
              u.failed_attempts, u.locked_until,
              r.name AS role, r.id AS role_id
       FROM users u
       JOIN roles r ON u.role_id = r.id
       WHERE u.email = $1`,
      [email.toLowerCase()]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({
        success: false,
        message: 'Correo o contraseña incorrectos.',
        code: 'INVALID_CREDENTIALS',
      });
    }

    const user = result.rows[0];

    if (user.locked_until && new Date(user.locked_until) > new Date()) {
      const minutesLeft = Math.ceil((new Date(user.locked_until) - new Date()) / 60000);
      return res.status(423).json({
        success: false,
        message: `Cuenta bloqueada temporalmente. Inténtalo en ${minutesLeft} minutos.`,
        code: 'ACCOUNT_LOCKED',
      });
    }

    if (!user.is_active) {
      return res.status(403).json({
        success: false,
        message: 'Tu cuenta ha sido desactivada. Contacta al soporte.',
        code: 'ACCOUNT_INACTIVE',
      });
    }

    const passwordMatch = await bcrypt.compare(password, user.password_hash);

    if (!passwordMatch) {
      const failedAttempts = user.failed_attempts + 1;
      const lockUntil = failedAttempts >= 5 ? new Date(Date.now() + 30 * 60 * 1000) : null;

      await query(
        `UPDATE users SET failed_attempts = $1, locked_until = $2 WHERE id = $3`,
        [failedAttempts, lockUntil, user.id]
      );

      const attemptsLeft = Math.max(0, 5 - failedAttempts);
      return res.status(401).json({
        success: false,
        message: attemptsLeft > 0
          ? `Correo o contraseña incorrectos. ${attemptsLeft} intentos restantes.`
          : 'Cuenta bloqueada por múltiples intentos fallidos.',
        code: 'INVALID_CREDENTIALS',
        attempts_left: attemptsLeft,
      });
    }

    await query(
      `UPDATE users SET failed_attempts = 0, locked_until = NULL, last_login_at = NOW() WHERE id = $1`,
      [user.id]
    );

    const { accessToken, refreshToken } = generateTokens(user.id, user.role);
    await saveRefreshToken(user.id, refreshToken, req.headers['user-agent'], req.ip);

    let profile = null;
    if (user.role === 'driver') {
      const dp = await query(
        `SELECT vehicle_make, vehicle_model, vehicle_plate, vehicle_color,
                vehicle_type, is_available, is_approved, rating, total_trips
         FROM driver_profiles WHERE user_id = $1`,
        [user.id]
      );
      profile = dp.rows[0] || null;
    } else if (user.role === 'client') {
      const cp = await query(
        `SELECT preferred_payment, rating, total_trips FROM client_profiles WHERE user_id = $1`,
        [user.id]
      );
      profile = cp.rows[0] || null;
    }

    return res.status(200).json({
      success: true,
      message: `Bienvenido, ${user.full_name}!`,
      data: {
        user: {
          id: user.id,
          email: user.email,
          full_name: user.full_name,
          phone: user.phone,
          avatar_url: user.avatar_url,
          role: user.role,
          is_verified: user.is_verified,
          profile,
        },
        tokens: {
          access_token: accessToken,
          refresh_token: refreshToken,
          token_type: 'Bearer',
          expires_in: process.env.JWT_EXPIRES_IN || '7d',
        },
      },
    });
  } catch (error) {
    console.error('❌ Error en login:', error);
    return res.status(500).json({
      success: false,
      message: 'Error interno del servidor. Inténtalo más tarde.',
      code: 'INTERNAL_ERROR',
    });
  }
};

// =============================================================
// REFRESH TOKEN
// =============================================================
export const refreshToken = async (req, res) => {
  const { refresh_token } = req.body;

  if (!refresh_token) {
    return res.status(400).json({ success: false, message: 'Refresh token requerido.', code: 'TOKEN_REQUIRED' });
  }

  try {
    const tokenHash = crypto.createHash('sha256').update(refresh_token).digest('hex');

    const result = await query(
      `SELECT rt.user_id, rt.expires_at, rt.is_revoked,
              u.is_active, r.name AS role
       FROM refresh_tokens rt
       JOIN users u ON rt.user_id = u.id
       JOIN roles r ON u.role_id = r.id
       WHERE rt.token_hash = $1`,
      [tokenHash]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({ success: false, message: 'Refresh token inválido.', code: 'INVALID_TOKEN' });
    }

    const tokenData = result.rows[0];

    if (tokenData.is_revoked) {
      return res.status(401).json({ success: false, message: 'Refresh token revocado.', code: 'TOKEN_REVOKED' });
    }

    if (new Date(tokenData.expires_at) < new Date()) {
      return res.status(401).json({ success: false, message: 'Refresh token expirado.', code: 'TOKEN_EXPIRED' });
    }

    if (!tokenData.is_active) {
      return res.status(403).json({ success: false, message: 'Tu cuenta ha sido desactivada.', code: 'ACCOUNT_INACTIVE' });
    }

    await query('UPDATE refresh_tokens SET is_revoked = TRUE WHERE token_hash = $1', [tokenHash]);

    const { accessToken, refreshToken: newRefreshToken } = generateTokens(tokenData.user_id, tokenData.role);
    await saveRefreshToken(tokenData.user_id, newRefreshToken, req.headers['user-agent'], req.ip);

    return res.status(200).json({
      success: true,
      message: 'Tokens renovados exitosamente.',
      data: {
        tokens: {
          access_token: accessToken,
          refresh_token: newRefreshToken,
          token_type: 'Bearer',
          expires_in: process.env.JWT_EXPIRES_IN || '7d',
        },
      },
    });
  } catch (error) {
    console.error('❌ Error en refreshToken:', error);
    return res.status(500).json({ success: false, message: 'Error interno del servidor.', code: 'INTERNAL_ERROR' });
  }
};

// =============================================================
// LOGOUT
// =============================================================
export const logout = async (req, res) => {
  const { refresh_token } = req.body;

  if (refresh_token) {
    try {
      const tokenHash = crypto.createHash('sha256').update(refresh_token).digest('hex');
      await query('UPDATE refresh_tokens SET is_revoked = TRUE WHERE token_hash = $1', [tokenHash]);
    } catch (error) {
      console.error('Error al revocar token:', error);
    }
  }

  return res.status(200).json({ success: true, message: 'Sesión cerrada exitosamente.' });
};

// =============================================================
// GET ME (perfil propio)
// =============================================================
export const getMe = async (req, res) => {
  try {
    const result = await query(
      `SELECT u.id, u.email, u.full_name, u.phone, u.avatar_url,
              u.is_active, u.is_verified, u.last_login_at, u.created_at,
              u.cedula, u.birth_date, u.kyc_status,
              r.name AS role
       FROM users u
       JOIN roles r ON u.role_id = r.id
       WHERE u.id = $1`,
      [req.user.sub]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ success: false, message: 'Usuario no encontrado.', code: 'USER_NOT_FOUND' });
    }

    const user = result.rows[0];

    let profile = null;
    if (user.role === 'driver') {
      const dp = await query('SELECT * FROM driver_profiles WHERE user_id = $1', [user.id]);
      profile = dp.rows[0] || null;
    } else if (user.role === 'client') {
      const cp = await query('SELECT * FROM client_profiles WHERE user_id = $1', [user.id]);
      profile = cp.rows[0] || null;
    }

    return res.status(200).json({
      success: true,
      data: { user: { ...user, profile } },
    });
  } catch (error) {
    console.error('❌ Error en getMe:', error);
    return res.status(500).json({ success: false, message: 'Error interno del servidor.', code: 'INTERNAL_ERROR' });
  }
};
