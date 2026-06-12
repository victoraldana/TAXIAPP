import jwt from 'jsonwebtoken';

/**
 * Middleware de autenticación JWT
 * Verifica el token Bearer en el header Authorization
 */
export const authenticate = (req, res, next) => {
  const authHeader = req.headers['authorization'];

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({
      success: false,
      message: 'Token de autorización requerido.',
      code: 'TOKEN_MISSING',
    });
  }

  const token = authHeader.split(' ')[1];

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = decoded; // { sub: userId, role: 'client'|'driver'|'admin' }
    next();
  } catch (error) {
    if (error.name === 'TokenExpiredError') {
      return res.status(401).json({
        success: false,
        message: 'Token expirado. Usa el refresh token para obtener uno nuevo.',
        code: 'TOKEN_EXPIRED',
      });
    }
    return res.status(401).json({
      success: false,
      message: 'Token inválido.',
      code: 'TOKEN_INVALID',
    });
  }
};

/**
 * Middleware de autorización por rol
 * @param {...string} roles - Roles permitidos ('client', 'driver', 'admin')
 */
export const authorize = (...roles) => {
  return (req, res, next) => {
    if (!req.user) {
      return res.status(401).json({
        success: false,
        message: 'No autenticado.',
        code: 'NOT_AUTHENTICATED',
      });
    }

    if (!roles.includes(req.user.role)) {
      return res.status(403).json({
        success: false,
        message: `Acceso denegado. Se requiere el rol: ${roles.join(' o ')}.`,
        code: 'FORBIDDEN',
        required_roles: roles,
        current_role: req.user.role,
      });
    }

    next();
  };
};
