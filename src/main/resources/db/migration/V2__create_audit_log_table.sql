-- ============================================================
-- V2: Audit log table — tracks all sensitive user operations
-- ============================================================

CREATE TABLE IF NOT EXISTS audit_log
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    action      VARCHAR(100) NOT NULL,   -- e.g. 'USER_LOGIN', 'PASSWORD_CHANGE'
    entity_type VARCHAR(50),             -- e.g. 'USER'
    entity_id   BIGINT,
    ip_address  VARCHAR(45),             -- portable across Postgres/H2; stores IPv4/IPv6
    user_agent  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Foreign key kept outside the CREATE TABLE to avoid parser issues on some tooling
ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_audit_log_user_id    ON audit_log (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action     ON audit_log (action);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at DESC);
