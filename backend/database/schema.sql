-- ============================================================
-- TaxiApp - Schema PostgreSQL para Railway
-- Versión compatible con PostgreSQL 13+ (Railway)
-- ============================================================

-- EXTENSIONES
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- ROLES
-- ============================================================
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

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id             INTEGER      NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,

    -- Autenticación
    email               VARCHAR(255) UNIQUE,
    password_hash       VARCHAR(255),

    -- Datos personales
    full_name           VARCHAR(150),
    phone               VARCHAR(20)  UNIQUE,
    cedula              VARCHAR(20),
    birth_date          DATE,
    avatar_url          TEXT,

    -- KYC
    selfie_url          TEXT,
    id_doc_url          TEXT,
    kyc_status          VARCHAR(20) DEFAULT 'pending',

    -- Estado
    is_active           BOOLEAN      DEFAULT TRUE,
    is_verified         BOOLEAN      DEFAULT FALSE,
    phone_verified_at   TIMESTAMPTZ,
    email_verified_at   TIMESTAMPTZ,

    -- Seguridad
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

-- ============================================================
-- DRIVER PROFILES
-- ============================================================
CREATE TABLE IF NOT EXISTS driver_profiles (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
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

-- ============================================================
-- CLIENT PROFILES
-- ============================================================
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

-- ============================================================
-- REFRESH TOKENS
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
-- TRIPS
-- ============================================================
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
CREATE INDEX IF NOT EXISTS idx_trips_created   ON trips(created_at DESC);

-- ============================================================
-- OTP CODES
-- ============================================================
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

-- ============================================================
-- FUNCIÓN updated_at
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers (DROP IF EXISTS primero para evitar error en re-ejecución)
DROP TRIGGER IF EXISTS trg_users_updated_at          ON users;
DROP TRIGGER IF EXISTS trg_driver_profiles_updated_at ON driver_profiles;
DROP TRIGGER IF EXISTS trg_client_profiles_updated_at ON client_profiles;
DROP TRIGGER IF EXISTS trg_trips_updated_at           ON trips;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_driver_profiles_updated_at
    BEFORE UPDATE ON driver_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_client_profiles_updated_at
    BEFORE UPDATE ON client_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- VISTA usuarios con rol
-- ============================================================
CREATE OR REPLACE VIEW users_with_roles AS
SELECT
    u.id, u.email, u.full_name, u.phone, u.avatar_url,
    u.is_active, u.is_verified, u.last_login_at, u.created_at,
    r.name AS role, r.id AS role_id
FROM users u
JOIN roles r ON u.role_id = r.id;
