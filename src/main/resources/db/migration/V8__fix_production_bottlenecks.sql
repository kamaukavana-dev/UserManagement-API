-- Add tenant_id for multi-tenancy support
ALTER TABLE users ADD COLUMN tenant_id VARCHAR(50) DEFAULT 'default' NOT NULL;
ALTER TABLE audit_log ADD COLUMN tenant_id VARCHAR(50) DEFAULT 'default' NOT NULL;
ALTER TABLE refresh_tokens ADD COLUMN tenant_id VARCHAR(50) DEFAULT 'default' NOT NULL;

-- Drop old unique constraint and create a composite one per tenant
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_unique;
-- Create a partial unique index to allow multiple soft-deleted users with the same email
CREATE UNIQUE INDEX idx_users_tenant_email_enabled ON users (tenant_id, email) WHERE (enabled = true);

-- B-Tree indexes for critical search queries to prevent DB bottlenecks
CREATE INDEX idx_users_tenant_email ON users(tenant_id, email);
CREATE INDEX idx_users_tenant_role ON users(tenant_id, role);
CREATE INDEX idx_audit_log_tenant ON audit_log(tenant_id);
CREATE INDEX idx_refresh_tokens_tenant ON refresh_tokens(tenant_id);
