import { body, validationResult } from 'express-validator';

/**
 * Manejador centralizado de errores de validación
 */
export const handleValidationErrors = (req, res, next) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    return res.status(400).json({
      success: false,
      message: 'Datos de entrada inválidos.',
      code: 'VALIDATION_ERROR',
      errors: errors.array().map((e) => ({
        field: e.path,
        message: e.msg,
      })),
    });
  }
  next();
};

// ========================
// Validaciones de registro
// ========================
export const validateRegister = [
  body('full_name')
    .trim()
    .notEmpty().withMessage('El nombre completo es requerido.')
    .isLength({ min: 3, max: 150 }).withMessage('El nombre debe tener entre 3 y 150 caracteres.'),

  body('email')
    .trim()
    .notEmpty().withMessage('El correo electrónico es requerido.')
    .isEmail().withMessage('Formato de correo electrónico inválido.')
    .normalizeEmail(),

  body('password')
    .notEmpty().withMessage('La contraseña es requerida.')
    .isLength({ min: 8 }).withMessage('La contraseña debe tener al menos 8 caracteres.')
    .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/)
    .withMessage('La contraseña debe contener al menos una mayúscula, una minúscula y un número.'),

  body('phone')
    .optional()
    .isMobilePhone().withMessage('Formato de teléfono inválido.'),

  body('role')
    .notEmpty().withMessage('El rol es requerido.')
    .isIn(['client', 'driver']).withMessage('El rol debe ser "client" o "driver".'),

  handleValidationErrors,
];

// =======================
// Validaciones de login
// =======================
export const validateLogin = [
  body('email')
    .trim()
    .notEmpty().withMessage('El correo electrónico es requerido.')
    .isEmail().withMessage('Formato de correo electrónico inválido.')
    .normalizeEmail(),

  body('password')
    .notEmpty().withMessage('La contraseña es requerida.'),

  handleValidationErrors,
];

// ============================
// Validación de refresh token
// ============================
export const validateRefreshToken = [
  body('refresh_token')
    .notEmpty().withMessage('El refresh token es requerido.'),

  handleValidationErrors,
];
