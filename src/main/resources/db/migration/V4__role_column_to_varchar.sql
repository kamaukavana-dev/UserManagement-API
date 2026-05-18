-- Migrate role column from enum type to VARCHAR for portability
ALTER TABLE users
    ALTER COLUMN role TYPE VARCHAR(50)
    USING role::text,
    ALTER COLUMN role SET DEFAULT 'ROLE_USER';

-- Drop the old enum type if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
        DROP TYPE user_role;
    END IF;
END$$;
