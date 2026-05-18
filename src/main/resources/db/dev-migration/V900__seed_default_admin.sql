INSERT INTO users (
    tenant_id,
    first_name,
    last_name,
    email,
    password,
    role,
    enabled,
    account_non_locked
)
SELECT
    'default',
    'System',
    'Admin',
    'admin@company.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCDBte08xyRnIFoCW7qVo.a',
    'ROLE_ADMIN',
    TRUE,
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE tenant_id = 'default'
      AND email = 'admin@company.com'
      AND enabled = TRUE
);
