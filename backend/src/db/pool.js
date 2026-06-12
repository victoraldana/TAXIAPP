import pg from 'pg';
import dotenv from 'dotenv';

dotenv.config();

const { Pool } = pg;

if (!process.env.DATABASE_URL) {
  console.error('❌ DATABASE_URL no está definida. Configúrala en Railway > Variables.');
  process.exit(1);
}

/**
 * Pool de conexiones PostgreSQL.
 * Railway y la mayoría de proveedores cloud requieren SSL con
 * rejectUnauthorized: false porque usan certificados auto-firmados.
 */
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: { rejectUnauthorized: false },
  max: 10,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 10000,
});

pool.on('connect', () => {
  console.log('✅ Conexión a PostgreSQL establecida');
});

pool.on('error', (err) => {
  // Loguear pero NO hacer process.exit — Railway reiniciará si es necesario
  console.error('❌ Error en el pool de PostgreSQL:', err.message);
});

/**
 * Ejecuta una query parametrizada
 */
export const query = async (text, params) => {
  try {
    return await pool.query(text, params);
  } catch (error) {
    console.error('❌ Error en query:', error.message, '\nSQL:', text);
    throw error;
  }
};

/**
 * Obtiene un cliente del pool (para transacciones manuales)
 */
export const getClient = () => pool.connect();

/**
 * Ejecuta múltiples queries dentro de una transacción atómica.
 * Hace ROLLBACK automático si el callback lanza un error.
 */
export const withTransaction = async (callback) => {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await callback(client);
    await client.query('COMMIT');
    return result;
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
};

export default pool;
