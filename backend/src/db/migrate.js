/**
 * Migración de base de datos.
 * Aplica schema.sql y siembra datos iniciales (usuario de prueba).
 *
 * Uso manual:   node src/db/migrate.js
 * Automático:   llamado desde server.js al arrancar
 */
import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import pg from 'pg';
import bcrypt from 'bcryptjs';
import dotenv from 'dotenv';

dotenv.config();

const __dirname = dirname(fileURLToPath(import.meta.url));
const { Pool } = pg;

export async function runMigration() {
  if (!process.env.DATABASE_URL) {
    throw new Error('DATABASE_URL no está definida.');
  }

  const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false },
    connectionTimeoutMillis: 15000,
  });

  const client = await pool.connect();
  try {
    console.log('🚕 Ejecutando migración...');

    // 1. Aplicar schema.sql
    // Desde src/db/ subimos 2 niveles para llegar a la raíz del backend
    const schemaPath = resolve(__dirname, '../../database/schema.sql');
    console.log('📄 Schema path:', schemaPath);
    const sql = readFileSync(schemaPath, 'utf8');
    await client.query(sql);
    console.log('✅ Schema aplicado.');

    // 2. Sembrar usuario de prueba con bcrypt (no pgcrypto)
    const testPhone = '042412345678';
    const existing = await client.query(
      'SELECT id FROM users WHERE phone = $1',
      [testPhone]
    );

    if (existing.rows.length === 0) {
      const roleRes = await client.query("SELECT id FROM roles WHERE name = 'client'");
      if (roleRes.rows.length > 0) {
        const roleId = roleRes.rows[0].id;
        // bcrypt hash de "1212"
        const passwordHash = await bcrypt.hash('1212', 10);

        const userRes = await client.query(
          `INSERT INTO users (
             role_id, phone, password_hash, full_name, email,
             is_active, is_verified, phone_verified_at, kyc_status
           ) VALUES ($1,$2,$3,$4,$5,TRUE,TRUE,NOW(),'approved')
           RETURNING id`,
          [roleId, testPhone, passwordHash, 'Usuario Prueba', 'prueba@taxiapp.dev']
        );

        const userId = userRes.rows[0].id;
        await client.query('INSERT INTO client_profiles (user_id) VALUES ($1)', [userId]);
        console.log('✅ Usuario de prueba creado: 042412345678 / 1212');
      }
    } else {
      console.log('ℹ️  Usuario de prueba ya existe.');
    }

    console.log('✅ Migración completada.');
  } catch (err) {
    console.error('❌ Error en migración:', err.message);
    console.error(err.stack);
    throw err; // re-lanzar para que server.js lo registre
  } finally {
    client.release();
    await pool.end();
  }
}

// Ejecución directa: node src/db/migrate.js
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  runMigration()
    .then(() => process.exit(0))
    .catch(() => process.exit(1));
}
