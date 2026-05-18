-- Add login lockout / optimistic locking columns
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

-- Remove the bootstrap admin from the production schema path.
DELETE FROM users
WHERE email = 'admin@company.com';

-- Help queries that filter active users.
CREATE INDEX IF NOT EXISTS idx_users_enabled ON users (enabled);

-- Full-text-ish search support for keyword lookups.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_users_first_name_trgm
    ON users USING GIN (LOWER(first_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_last_name_trgm
    ON users USING GIN (LOWER(last_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_email_trgm
    ON users USING GIN (LOWER(email) gin_trgm_ops);
