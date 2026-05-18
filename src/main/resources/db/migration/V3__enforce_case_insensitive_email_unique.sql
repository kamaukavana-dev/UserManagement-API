-- Ensure case-insensitive uniqueness on email
-- Functional index on lower(email) prevents duplicates differing only by case
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_lower ON users ((lower(email)));
