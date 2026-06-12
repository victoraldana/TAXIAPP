/**
 * Migración de base de datos.
 * Lee el schema.sql y lo aplica en la base de datos configurada en DATABASE_URL.
 *
 * Uso manual:   node src/db/migrate.js
 * En Railway:   Se ejecuta automáticamente desde server.js al arrancar.
 */
import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import pg from 'pg';
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
  });

  const client = await pool.connect();
  try {
    console.log('🚕 Ejecutando migración de base de datos...');
    const schemaPath = resolve(__dirname, '../../../database/schema.sql');
    const sql = readFileSync(schemaPath, 'utf8');
    await client.query(sql);
    console.log('✅ Migración completada.');
  } finally {
    client.release();
    await pool.end();
  }
}

// Ejecución directa: node src/db/migrate.js
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  runMigration()
    .then(() => {
      console.log('🚕 Schema aplicado correctamente.');
      process.exit(0);
    })
    .catch((err) => {
      console.error('❌ Error en migración:', err.message);
      process.exit(1);
    });
}
