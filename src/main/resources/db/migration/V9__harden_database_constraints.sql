-- Enable CITEXT extension for case-insensitive email handling at the DB engine level
CREATE EXTENSION IF NOT EXISTS citext;

-- Update email columns to use CITEXT
ALTER TABLE users ALTER COLUMN email TYPE citext;

-- Drop the old constraint from V1 if it exists (names might vary)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_format;
ALTER TABLE users DROP CONSTRAINT IF EXISTS email_format_check;

-- Add a database-level format check for emails to prevent malformed data insertion
-- Using a more robust regex that handles subdomains correctly
ALTER TABLE users ADD CONSTRAINT email_format_check CHECK (email ~* '^[A-Za-z0-9._%+-]+@([A-Za-z0-9-]+\.)+[A-Za-z]{2,}$');

-- Add a check constraint to ensure first_name and last_name are not just empty spaces
ALTER TABLE users DROP CONSTRAINT IF EXISTS names_not_empty_check;
ALTER TABLE users ADD CONSTRAINT names_not_empty_check CHECK (length(trim(first_name)) > 0 AND length(trim(last_name)) > 0);
