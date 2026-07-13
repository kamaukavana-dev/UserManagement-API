# Security Policy
## Reporting Security Vulnerabilities
**Do not** open public GitHub issues for security vulnerabilities.
Instead, email **kavana.daniel1@gmail.com** with:
- Vulnerability description
- Steps to reproduce (if applicable)
- Potential impact assessment
- Suggested fix (optional)
We will:
- Acknowledge receipt within 24 hours
- Provide an estimated patch timeline
- Work with you on disclosure coordination
## Security Features
### Authentication & Authorization
- **JWT Tokens**: HS256-signed tokens with 15-minute default TTL
- **Password Hashing**: BCrypt with work factor 12 (iterative key expansion)
- **Role-Based Access Control (RBAC)**: User roles enforcement at endpoint level
- **Token Revocation**: Version-based mechanism for immediate invalidation on password/role change
- **Account Lockout**: Automatic after 5 failed login attempts, 15-minute lockout (configurable)
### Attack Prevention
- **Rate Limiting**: Bucket4j + Redis backend with per-IP and per-user limits
  - Auth endpoints: 100 req/min per IP, 50 per user
  - Other endpoints: 1000 req/min per IP
- **Brute-Force Protection**: Account locking after configurable failed attempts
- **XSS Prevention**: Input validation and output encoding on all endpoints
- **CSRF Protection**: Stateless JWT (not session-based); no session fixation risk
- **HTTPS Enforcement**: Production profile redirects HTTP → HTTPS (reverse proxy aware)
- **Security Headers**: HSTS, CSP, X-Frame-Options, X-Content-Type-Options
### Audit & Compliance
- **Immutable Audit Log**: Database-backed, tamper-resistant event trail
- **IP Tracking**: X-Forwarded-For aware; captures actual client IP
- **User Agent Capture**: Headers preserved for anomaly detection
- **Token Version Tracking**: Detects unauthorized token usage after revocation
- **Timestamps**: All events recorded in UTC
## Security Boundaries
This service is a **basic JWT authentication microservice**, NOT a full-featured identity provider.
### What We Provide ✅
- Secure user registration and password management
- JWT-based authentication and authorization
- Role-based access control
- Password complexity enforcement (12+ chars, uppercase, number, special char)
- Rate limiting and brute-force protection
- Comprehensive audit logging
- HTTPS enforcement
### What We Do NOT Provide ❌
- **OAuth2/OIDC**: Use a dedicated IdP (Keycloak, Auth0, Okta, Ping)
- **Multi-factor authentication (MFA)**: Implement at reverse proxy or IdP layer
- **Social login**: Requires external identity provider
- **Federated SSO/SAML**: Use SAML bridge or federation layer if needed
- **Advanced consent management**: Complex permissions require custom implementation
- **Device trust / location-based security**: Implement at reverse proxy
- **Session encryption**: Stateless JWT only; implement TLS at infrastructure layer
## Dependencies & Vulnerability Scanning
### Current Dependency Versions
- **Spring Boot**: 3.2.5
- **Spring Security**: Included in Spring Boot 3.2.5
- **JJWT**: 0.12.5 (latest security patches)
- **Bucket4j**: 8.9.0
- **BCrypt**: Included in Spring Security
- **Flyway**: 10.20.1
### Vulnerability Management
- Maven Dependency Check enabled (can be run: `./mvnw dependency-check:check`)
- Regular Spring Security updates monitored
- OWASP scanning in CI/CD pipeline
- Dependencies kept current (no version locks beyond 1 year behind)
## Password Requirements
Enforced via `@ValidPassword` annotation on registration and password change endpoints.
### Rules
- **Minimum**: 12 characters
- **Uppercase**: At least 1 letter (A-Z)
- **Numeric**: At least 1 digit (0-9)
- **Special Character**: At least 1 from set (@$!%*?&_#)
- **Regex Pattern**: `^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_#])(?=.{12,}).*$`
### Rationale
- **12 characters**: Provides ~80-bit entropy with full character set
- **Mixed types**: Prevents dictionary attacks
- **Special characters**: Exponentially increases search space
- **Reference**: OWASP Password Storage Cheat Sheet
## Token Management
### Access Tokens
- **Default TTL**: 15 minutes
- **Algorithm**: HS256 (HMAC with SHA-256)
- **Claims**: subject, roles, token version, issued at, expiration
### Refresh Tokens
- **Default TTL**: 7 days
- **Stored**: Database with version tracking
- **Revocation**: Automatic via token version increment
### Token Revocation Triggers
1. Password change (user or admin)
2. Role change
3. Account lockout
4. Explicit logout request
5. Admin token revocation (future feature)
## Rate Limiting
### Redis-Backed (Production)
- **Backend**: Redis with Bucket4j
- **Per-IP**: Separate buckets for each IP address
- **Per-User**: Additional buckets for authenticated users
- **Fallback**: Automatic failover to local Caffeine cache if Redis unavailable
### Rate Limit Buckets
- **Auth endpoints**: 100 requests/minute per IP, 50 per user
- **Other endpoints**: 1000 requests/minute per IP
- **Trusted proxies**: Can pass through without counting (configurable CIDR ranges)
## HTTPS Enforcement
### Production Profile
- **Enabled**: Yes (via `app.security.https-enforcement.enabled=true`)
- **Behavior**: HTTP requests redirect to HTTPS (301)
- **Exceptions**: `/actuator` and `/health` endpoints allowed over HTTP
- **Reverse Proxy Aware**: Checks `X-Forwarded-Proto` header
### Dev/Test Profiles
- **Enabled**: No (disabled for easier local testing)
- **HTTP Fallback**: Allowed on all endpoints
## Known Vulnerabilities & Mitigations
| Vulnerability | Mitigation | Status |
|---|---|---|
| Session Fixation | Stateless JWT; no session storage | ✅ Not vulnerable |
| Token Leakage | HTTPS enforcement, short TTL (15 min) | ✅ Mitigated |
| Weak Passwords | 12-char min, complexity, BCrypt | ✅ Mitigated |
| Brute Force | Account lockout (5 attempts), rate limiting | ✅ Mitigated |
| CSRF | Stateless JWT, explicit auth header | ✅ Not vulnerable |
| SQL Injection | Spring Data JPA, parameterized queries | ✅ Not vulnerable |
| XSS | Input validation, output encoding | ✅ Mitigated |
| Privilege Escalation | RBAC, immutable JWT claims | ✅ Mitigated |
## Compliance & Standards
This service follows:
- ✅ OWASP Password Storage Cheat Sheet
- ✅ OWASP Authentication Cheat Sheet
- ✅ OWASP Authorization Cheat Sheet
- ✅ NIST Guidelines on password policies
- ✅ RFC 7519 (JWT standard)
- ✅ Industry best practices for rate limiting
## Deployment Security Checklist
Before production deployment, verify:
1. **TLS Termination**: Reverse proxy with valid certificate
2. **JWT Secret**: 32+ bytes, generated via `openssl rand -base64 32`
3. **Database**: Strong password, not default credentials
4. **Redis**: Authentication enabled, password protected
5. **Network**: Database/Redis on internal network only
6. **Environment Variables**: All secrets in secure vault
7. **Logs**: No sensitive data logged
8. **Backups**: Encrypted and access-controlled
9. **Monitoring**: Alerts on failed logins, rate limit spikes
10. **Incident Response**: Team prepared with documented runbook
---
**Contact**: Daniel1@gmail.com  
**Last Updated**: April 23, 2026  
**Status**: Security Policy Finalized  
Created by Software Engineer Daniel Maina.
