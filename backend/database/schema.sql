-- ============================================================
-- TaxiApp - Schema PostgreSQL para Railway
-- ============================================================
-- Instrucciones:
--   1. En Railway: agrega un servicio PostgreSQL al proyecto.
--   2. Ve a "Data" > "Query" y pega este script completo.
--   3. Haz clic en "Run" para crear todas las tablas.
--
-- También puedes ejecutarlo desde tu máquina con:
--   DATABASE_URL=<tu_url_railway> node src/db/migrate.js
-- ============================================================

-- ============================================================
-- EXTENSIONES
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- TABLA: roles
-- Define los tipos de usuario en la plataforma
-- ============================================================
CREATE TABLE IF NOT EXISTS roles (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

-- Roles base
INSERT INTO roles (name, description) VALUES
    ('client',  'Usuario que solicita viajes'),
    ('driver',  'Conductor que acepta y realiza viajes'),
    ('admin',   'Administrador del sistema')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- TABLA: users
-- Usuarios registrados en la plataforma
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id             INTEGER      NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,

    -- Autenticación
    email               VARCHAR(255) UNIQUE,
    password_hash       VARCHAR(255),

    -- Datos personales
    full_name           VARCHAR(150),
    phone               VARCHAR(20)  UNIQUE,    -- Teléfono único (login principal)
    cedula              VARCHAR(20),            -- Número de cédula/DNI
    birth_date          DATE,                   -- Fecha de nacimiento
    avatar_url          TEXT,

    -- KYC (Know Your Customer)
    selfie_url          TEXT,                   -- Foto selfie del usuario
    id_doc_url          TEXT,                   -- Foto del documento de identidad
    kyc_status          VARCHAR(20) DEFAULT 'pending',  -- pending | submitted | approved | rejected

    -- Estado de la cuenta
    is_active           BOOLEAN      DEFAULT TRUE,
    is_verified         BOOLEAN      DEFAULT FALSE,
    phone_verified_at   TIMESTAMPTZ,
    email_verified_at   TIMESTAMPTZ,

    -- Seguridad / sesión
    last_login_at       TIMESTAMPTZ,
    failed_attempts     INTEGER      DEFAULT 0,
    locked_until        TIMESTAMPTZ,

    -- Timestamps
    created_at          TIMESTAMPTZ  DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email     ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone     ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_role_id   ON users(role_id);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active);

-- ============================================================
-- TABLA: driver_profiles
-- Información adicional exclusiva de conductores
-- ============================================================
CREATE TABLE IF NOT EXISTS driver_profiles (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,

    -- Vehículo
    vehicle_make     VARCHAR(80),            -- Marca  (Toyota, Chevrolet…)
    vehicle_model    VARCHAR(80),            -- Modelo (Corolla, Spark…)
    vehicle_year     SMALLINT,
    vehicle_plate    VARCHAR(20) UNIQUE,
    vehicle_color    VARCHAR(40),
    vehicle_type     VARCHAR(30)  DEFAULT 'sedan',  -- sedan | suv | van | moto

    -- Documentos
    license_number   VARCHAR(50),
    license_expiry   DATE,

    -- Estado operativo
    is_available     BOOLEAN      DEFAULT FALSE,
    is_approved      BOOLEAN      DEFAULT FALSE,     -- Aprobado por admin
    rating           DECIMAL(3,2) DEFAULT 5.00,
    total_trips      INTEGER      DEFAULT 0,

    -- Ubicación actual
    current_lat      DECIMAL(10,8),
    current_lng      DECIMAL(11,8),
    last_location_at TIMESTAMPTZ,

    created_at       TIMESTAMPTZ  DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_driver_user_id      ON driver_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_driver_is_available ON driver_profiles(is_available);
CREATE INDEX IF NOT EXISTS idx_driver_location     ON driver_profiles(current_lat, current_lng);

-- ============================================================
-- TABLA: client_profiles
-- Información adicional de clientes
-- ============================================================
CREATE TABLE IF NOT EXISTS client_profiles (
    id                UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,

    -- Preferencias
    preferred_payment VARCHAR(30)  DEFAULT 'cash',  -- cash | card | wallet
    home_address      TEXT,
    work_address      TEXT,

    -- Historial
    rating            DECIMAL(3,2) DEFAULT 5.00,
    total_trips       INTEGER      DEFAULT 0,

    created_at        TIMESTAMPTZ  DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_user_id ON client_profiles(user_id);

-- ============================================================
-- TABLA: refresh_tokens
-- Manejo seguro de refresh tokens JWT (almacenamos el hash)
-- ============================================================
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

-- ============================================================
-- TABLA: trips
-- Ciclo de vida completo de cada viaje
-- ============================================================
CREATE TABLE IF NOT EXISTS trips (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_id       UUID         NOT NULL REFERENCES users(id),
    driver_id       UUID         REFERENCES users(id),

    -- Origen
    origin_address  TEXT         NOT NULL,
    origin_lat      DECIMAL(10,8) NOT NULL,
    origin_lng      DECIMAL(11,8) NOT NULL,

    -- Destino
    dest_address    TEXT         NOT NULL,
    dest_lat        DECIMAL(10,8) NOT NULL,
    dest_lng        DECIMAL(11,8) NOT NULL,

    -- Estado
    -- pending → accepted → on_route → arrived → in_progress → completed | cancelled
    status          VARCHAR(30)  DEFAULT 'pending',

    -- Económico
    estimated_fare  DECIMAL(10,2),
    final_fare      DECIMAL(10,2),
    payment_method  VARCHAR(30)  DEFAULT 'cash',
    payment_status  VARCHAR(30)  DEFAULT 'pending',

    -- Distancia y tiempo
    distance_km     DECIMAL(8,2),
    duration_min    INTEGER,

    -- Ratings
    client_rating   SMALLINT     CHECK (client_rating BETWEEN 1 AND 5),
    driver_rating   SMALLINT     CHECK (driver_rating BETWEEN 1 AND 5),
    client_comment  TEXT,
    driver_comment  TEXT,

    -- Timestamps del ciclo de vida
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
CREATE INDEX IF NOT EXISTS idx_trips_created   ON trips(created_at DESC);

-- ============================================================
-- FUNCIÓN + TRIGGERS: auto-actualizar updated_at
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

CREATE OR REPLACE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER trg_driver_profiles_updated_at
    BEFORE UPDATE ON driver_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER trg_client_profiles_updated_at
    BEFORE UPDATE ON client_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER trg_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- VISTA: users_with_roles
-- Consulta conveniente para obtener usuario + rol en una sola query
-- ============================================================
CREATE OR REPLACE VIEW users_with_roles AS
SELECT
    u.id,
    u.email,
    u.full_name,
    u.phone,
    u.avatar_url,
    u.is_active,
    u.is_verified,
    u.last_login_at,
    u.created_at,
    r.name   AS role,
    r.id     AS role_id
FROM users u
JOIN roles r ON u.role_id = r.id;

-- ============================================================
-- TABLA: otp_codes
-- Códigos OTP de verificación (teléfono y email)
-- ============================================================
CREATE TABLE IF NOT EXISTS otp_codes (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    target      VARCHAR(255) NOT NULL,   -- teléfono o email
    type        VARCHAR(20)  NOT NULL,   -- 'phone' | 'email'
    code        VARCHAR(10)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    is_used     BOOLEAN      DEFAULT FALSE,
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_otp_target ON otp_codes(target, type);

-- ============================================================
-- JOB DE LIMPIEZA: eliminar refresh tokens expirados
-- (ejecutar manualmente o con pg_cron si está disponible en Railway)
-- ============================================================
-- DELETE FROM refresh_tokens
-- WHERE expires_at < NOW() OR is_revoked = TRUE;

-- ============================================================
-- USUARIO DE PRUEBA (modo desarrollo)
-- Teléfono: 042412345678 | Clave: 1212
-- ============================================================
DO $$
DECLARE
    v_role_id INTEGER;
    v_user_id UUID;
BEGIN
    SELECT id INTO v_role_id FROM roles WHERE name = 'client';

    IF NOT EXISTS (SELECT 1 FROM users WHERE phone = '042412345678') THEN
        INSERT INTO users (
            role_id, phone, password_hash, full_name,
            email, is_active, is_verified, phone_verified_at, kyc_status
        ) VALUES (
            v_role_id,
            '042412345678',
            crypt('1212', gen_salt('bf', 10)),
            'Usuario Prueba',
            'prueba@taxiapp.dev',
            TRUE,
            TRUE,
            NOW(),
            'approved'
        ) RETURNING id INTO v_user_id;

        INSERT INTO client_profiles (user_id) VALUES (v_user_id);
    END IF;
END;
$$;

SELECT 'TaxiApp schema Railway creado correctamente ✓' AS resultado;
