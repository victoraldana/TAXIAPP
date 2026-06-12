import { query } from '../db/pool.js';
import crypto from 'crypto';

// ============================================================
// MODO DESARROLLO: omitir validación OTP real
// En producción, cambiar DEV_MODE a false y conectar un
// proveedor SMS/Email real (Twilio, SendGrid, etc.)
// ============================================================
// DEV_MODE: en Railway setea DEV_MODE=false para producción
const DEV_MODE = process.env.DEV_MODE !== 'false';
const DEV_OTP  = '0000'; // Código maestro en modo desarrollo

/**
 * Genera y guarda un código OTP (6 dígitos) para el target dado.
 * En DEV_MODE no envía nada y el código siempre es DEV_OTP.
 */
export const sendOtp = async (req, res) => {
  const { target, type } = req.body; // type: 'phone' | 'email'

  if (!target || !['phone', 'email'].includes(type)) {
    return res.status(400).json({
      success: false,
      message: 'Parámetros inválidos. Se requiere target y type (phone|email).',
      code: 'INVALID_PARAMS',
    });
  }

  try {
    // Invalidar OTPs anteriores para el mismo target/tipo
    await query(
      `UPDATE otp_codes SET is_used = TRUE
       WHERE target = $1 AND type = $2 AND is_used = FALSE`,
      [target, type]
    );

    // Generar código (en dev siempre mostramos el código en la respuesta)
    const code = DEV_MODE ? DEV_OTP : String(Math.floor(100000 + Math.random() * 900000));
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutos

    await query(
      `INSERT INTO otp_codes (target, type, code, expires_at)
       VALUES ($1, $2, $3, $4)`,
      [target, type, code, expiresAt]
    );

    if (DEV_MODE) {
      console.log(`[DEV] OTP para ${type} ${target}: ${code}`);
      return res.status(200).json({
        success: true,
        message: `[MODO DESARROLLO] Código OTP enviado. Usa el código: ${DEV_OTP}`,
        dev_code: DEV_OTP,
        dev_mode: true,
      });
    }

    // TODO: Aquí conectar Twilio / SendGrid en producción
    if (type === 'phone') {
      // await sendSms(target, `Tu código TaxiApp es: ${code}`);
    } else {
      // await sendEmail(target, 'Verifica tu correo', `Tu código es: ${code}`);
    }

    return res.status(200).json({
      success: true,
      message: `Código enviado al ${type === 'phone' ? 'teléfono' : 'correo'} indicado.`,
    });
  } catch (error) {
    console.error('❌ Error en sendOtp:', error);
    return res.status(500).json({
      success: false,
      message: 'Error al enviar el código OTP.',
      code: 'INTERNAL_ERROR',
    });
  }
};

/**
 * Verifica un código OTP.
 * En DEV_MODE acepta el código "0000" siempre.
 */
export const verifyOtp = async (req, res) => {
  const { target, type, code } = req.body;

  if (!target || !type || !code) {
    return res.status(400).json({
      success: false,
      message: 'Se requieren target, type y code.',
      code: 'INVALID_PARAMS',
    });
  }

  try {
    // En DEV_MODE el código maestro siempre es válido
    if (DEV_MODE && code === DEV_OTP) {
      return res.status(200).json({
        success: true,
        message: 'Código verificado correctamente (modo desarrollo).',
        dev_mode: true,
      });
    }

    const result = await query(
      `SELECT id FROM otp_codes
       WHERE target = $1 AND type = $2 AND code = $3
         AND is_used = FALSE AND expires_at > NOW()
       ORDER BY created_at DESC
       LIMIT 1`,
      [target, type, code]
    );

    if (result.rows.length === 0) {
      return res.status(400).json({
        success: false,
        message: 'Código inválido o expirado.',
        code: 'INVALID_OTP',
      });
    }

    // Marcar como usado
    await query(
      'UPDATE otp_codes SET is_used = TRUE WHERE id = $1',
      [result.rows[0].id]
    );

    return res.status(200).json({
      success: true,
      message: 'Código verificado correctamente.',
    });
  } catch (error) {
    console.error('❌ Error en verifyOtp:', error);
    return res.status(500).json({
      success: false,
      message: 'Error al verificar el código.',
      code: 'INTERNAL_ERROR',
    });
  }
};
