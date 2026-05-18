-- ============================================================
-- V1: Initial schema — Users table
-- Author: Software Engineer Daniel Maina
-- Date: 2025-01-01
-- ============================================================

-- Core users table
CREATE TABLE users
(
    id                BIGSERIAL PRIMARY KEY,
    first_name        VARCHAR(50)  NOT NULL,
    last_name         VARCHAR(50)  NOT NULL,
    email             VARCHAR(100) NOT NULL,
    password          VARCHAR(255) NOT NULL,        -- BCrypt hash, always 60 chars
    role              VARCHAR(50)  NOT NULL DEFAULT 'ROLE_USER',
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT users_first_name_length CHECK (char_length(first_name) >= 2),
    CONSTRAINT users_last_name_length CHECK (char_length(last_name) >= 2)
);

-- Index on email — every login query hits this column
-- Without this index, every login does a full table scan
CREATE INDEX idx_users_email ON users (email);

-- Index on role — for filtering users by role (ADMIN dashboard queries)
CREATE INDEX idx_users_role ON users (role);

-- Index on created_at — for pagination sorted by creation time
CREATE INDEX idx_users_created_at ON users (created_at DESC);

-- ============================================================
-- Trigger: Auto-update `updated_at` on any row update
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
