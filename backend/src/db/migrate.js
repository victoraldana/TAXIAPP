/**
 * Migración de base de datos.
 * El schema SQL está embebido aquí directamente para evitar
 * problemas de rutas de archivos en Railway.
 */
import pg from 'pg';
import bcrypt from 'bcryptjs';
import dotenv from 'dotenv';

dotenv.config();

const { Pool } = pg;

// ============================================================
// SCHEMA SQL embebido (evita problemas de path en Railway)
// ============================================================
const SCHEMA_SQL = `
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS roles (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

INSERT INTO roles (name, description) VALUES
    ('client',  'Usuario que solicita viajes'),
    ('driver',  'Conductor que acepta y realiza viajes'),
    ('admin',   'Administrador del sistema')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE IF NOT EXISTS users (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id             INTEGER      NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    email               VARCHAR(255) UNIQUE,
    password_hash       VARCHAR(255),
    full_name           VARCHAR(150),
    phone               VARCHAR(20)  UNIQUE,
    cedula              VARCHAR(20),
    birth_date          DATE,
    avatar_url          TEXT,
    selfie_url          TEXT,
    id_doc_url          TEXT,
    kyc_status          VARCHAR(20) DEFAULT 'pending',
    is_active           BOOLEAN      DEFAULT TRUE,
    is_verified         BOOLEAN      DEFAULT FALSE,
    phone_verified_at   TIMESTAMPTZ,
    email_verified_at   TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    failed_attempts     INTEGER      DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email     ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone     ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_role_id   ON users(role_id);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active);

CREATE TABLE IF NOT EXISTS driver_profiles (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    unit_number      VARCHAR(20)  UNIQUE,
    vehicle_make     VARCHAR(80),
    vehicle_model    VARCHAR(80),
    vehicle_year     SMALLINT,
    vehicle_plate    VARCHAR(20) UNIQUE,
    vehicle_color    VARCHAR(40),
    vehicle_type     VARCHAR(30)  DEFAULT 'sedan',
    license_number   VARCHAR(50),
    license_expiry   DATE,
    is_available     BOOLEAN      DEFAULT FALSE,
    is_approved      BOOLEAN      DEFAULT FALSE,
    rating           DECIMAL(3,2) DEFAULT 5.00,
    total_trips      INTEGER      DEFAULT 0,
    current_lat      DECIMAL(10,8),
    current_lng      DECIMAL(11,8),
    last_location_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_driver_user_id      ON driver_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_driver_is_available ON driver_profiles(is_available);

CREATE TABLE IF NOT EXISTS client_profiles (
    id                UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    preferred_payment VARCHAR(30)  DEFAULT 'cash',
    home_address      TEXT,
    work_address      TEXT,
    rating            DECIMAL(3,2) DEFAULT 5.00,
    total_trips       INTEGER      DEFAULT 0,
    created_at        TIMESTAMPTZ  DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_user_id ON client_profiles(user_id);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    is_revoked  BOOLEAN      DEFAULT FALSE,
    user_agent  TEXT,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rt_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_rt_hash    ON refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_rt_expires ON refresh_tokens(expires_at);

CREATE TABLE IF NOT EXISTS trips (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_id       UUID         NOT NULL REFERENCES users(id),
    driver_id       UUID         REFERENCES users(id),
    origin_address  TEXT         NOT NULL,
    origin_lat      DECIMAL(10,8) NOT NULL,
    origin_lng      DECIMAL(11,8) NOT NULL,
    dest_address    TEXT         NOT NULL,
    dest_lat        DECIMAL(10,8) NOT NULL,
    dest_lng        DECIMAL(11,8) NOT NULL,
    status          VARCHAR(30)  DEFAULT 'pending',
    estimated_fare  DECIMAL(10,2),
    final_fare      DECIMAL(10,2),
    payment_method  VARCHAR(30)  DEFAULT 'cash',
    payment_status  VARCHAR(30)  DEFAULT 'pending',
    distance_km     DECIMAL(8,2),
    duration_min    INTEGER,
    client_rating   SMALLINT     CHECK (client_rating BETWEEN 1 AND 5),
    driver_rating   SMALLINT     CHECK (driver_rating BETWEEN 1 AND 5),
    client_comment  TEXT,
    driver_comment  TEXT,
    accepted_at     TIMESTAMPTZ,
    arrived_at      TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   TEXT,
    created_at      TIMESTAMPTZ  DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trips_client_id ON trips(client_id);
CREATE INDEX IF NOT EXISTS idx_trips_driver_id ON trips(driver_id);
CREATE INDEX IF NOT EXISTS idx_trips_status    ON trips(status);

CREATE TABLE IF NOT EXISTS otp_codes (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    target      VARCHAR(255) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    code        VARCHAR(10)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    is_used     BOOLEAN      DEFAULT FALSE,
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_otp_target ON otp_codes(target, type);

CREATE TABLE IF NOT EXISTS driver_queue (
    id             UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    driver_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    queue_position INTEGER     NOT NULL DEFAULT 0,
    is_active      BOOLEAN     DEFAULT TRUE,
    added_at       TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_queue_active   ON driver_queue(is_active, queue_position);
CREATE INDEX IF NOT EXISTS idx_queue_driver   ON driver_queue(driver_id);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated_at           ON users;
DROP TRIGGER IF EXISTS trg_driver_profiles_updated_at ON driver_profiles;
DROP TRIGGER IF EXISTS trg_client_profiles_updated_at ON client_profiles;
DROP TRIGGER IF EXISTS trg_trips_updated_at           ON trips;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_driver_profiles_updated_at
    BEFORE UPDATE ON driver_profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_client_profiles_updated_at
    BEFORE UPDATE ON client_profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_trips_updated_at
    BEFORE UPDATE ON trips FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Agregar columnas nuevas a tablas existentes (idempotente)
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS unit_number   VARCHAR(20);
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS current_lat   DOUBLE PRECISION;
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS current_lng   DOUBLE PRECISION;
CREATE UNIQUE INDEX IF NOT EXISTS idx_driver_unit ON driver_profiles(unit_number) WHERE unit_number IS NOT NULL;

-- Calificación del cliente hacia el conductor
ALTER TABLE trips ADD COLUMN IF NOT EXISTS client_rating  SMALLINT;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS client_comment TEXT;

CREATE TABLE IF NOT EXISTS driver_queue (
    id             UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    driver_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    queue_position INTEGER     NOT NULL DEFAULT 0,
    is_active      BOOLEAN     DEFAULT TRUE,
    added_at       TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_queue_active ON driver_queue(is_active, queue_position);
CREATE INDEX IF NOT EXISTS idx_queue_driver ON driver_queue(driver_id);
`;

// ============================================================
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

    // 1. Aplicar schema (embebido en este archivo)
    await client.query(SCHEMA_SQL);
    console.log('✅ Schema aplicado.');

    // 2. Sembrar usuario de prueba con bcrypt
    const testPhone = '042412345678';
    const existing = await client.query(
      'SELECT id FROM users WHERE phone = $1', [testPhone]
    );

    if (existing.rows.length === 0) {
      const roleRes = await client.query("SELECT id FROM roles WHERE name = 'client'");
      if (roleRes.rows.length > 0) {
        const roleId = roleRes.rows[0].id;
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
    throw err;
  } finally {
    client.release();
    await pool.end();
  }
}

// Ejecución directa: node src/db/migrate.js
import { fileURLToPath } from 'url';
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  runMigration()
    .then(() => process.exit(0))
    .catch(() => process.exit(1));
}
