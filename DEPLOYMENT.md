# Deployment Guide
User Management & IAM Service - Production Deployment
## Pre-Deployment Security Checklist
### Security Configuration
- [ ] Generate strong JWT secret: `openssl rand -base64 32` (32+ bytes)
- [ ] Set unique database password (not default 'postgres')
- [ ] Enable Redis authentication and encryption
- [ ] Configure TLS for database connection (if not on internal network)
- [ ] HTTPS reverse proxy configured with valid certificate
- [ ] X-Forwarded-For and X-Forwarded-Proto headers set at reverse proxy
- [ ] Firewall rules restrict database/Redis to internal IPs only
### Configuration Verification
- [ ] All required environment variables set (see Environment Variables section)
- [ ] Rate limit thresholds appropriate for expected traffic
- [ ] Flyway migrations executable by database user
- [ ] Database connection pool settings match workload
- [ ] Redis connection timeout reasonable (2000ms recommended)
- [ ] Application profiles set correctly (prod for production)
### Testing & Validation
- [ ] Full test suite passes: `./mvnw verify -Pit`
- [ ] Code coverage >= 80%: `./mvnw clean test`
- [ ] Load test passes: `ab -n 10000 -c 100 https://domain/api/v1/health`
- [ ] Token revocation verified (password change → old token rejected)
- [ ] Rate limiting tested (100 requests/min → 429 response)
- [ ] HTTPS enforcement verified (HTTP redirects to HTTPS)
### Monitoring & Alerting
- [ ] Prometheus scrape endpoint configured (`/actuator/prometheus`)
- [ ] Alert rules configured for:
  - Failed login attempts spike (sudden increase)
  - Rate limit rejections (potential DDoS)
  - Token validation failures (potential attack)
  - Database connection pool exhaustion
  - Redis connection failures
  - Application startup failures
### Infrastructure
- [ ] Database backup strategy defined and tested
- [ ] Log aggregation configured (ELK, Splunk, etc.)
- [ ] Metrics collection working (Prometheus)
- [ ] Health check endpoints verified
---
## Environment Variables
### Required (Production)
```bash
# JWT Configuration
JWT_SECRET_KEY                          # 32+ byte base64 string (openssl rand -base64 32)
JWT_KID                                 # Key ID, default: "current"
JWT_EXPIRATION_MS                       # 900000 (15 minutes, in milliseconds)
JWT_REFRESH_MS                          # 604800000 (7 days, in milliseconds)
# Database
SPRING_DATASOURCE_URL                   # jdbc:postgresql://host:port/database
SPRING_DATASOURCE_PASSWORD              # Strong database password (minimum 20 chars)
DB_HOST                                 # Database hostname/IP
DB_PORT                                 # Database port (5432)
DB_NAME                                 # Database name
DB_USERNAME                             # Database user (not 'postgres')
# Redis
SPRING_DATA_REDIS_HOST                  # Redis hostname/IP
SPRING_DATA_REDIS_PORT                  # Redis port (6379)
SPRING_DATA_REDIS_PASSWORD              # Redis password
REDIS_HOST                              # Alias for SPRING_DATA_REDIS_HOST
REDIS_PORT                              # Alias for SPRING_DATA_REDIS_PORT
# Spring Boot Configuration
SPRING_PROFILES_ACTIVE                  # 'prod' for production
SERVER_PORT                             # Application port (8080)
```
### Optional (Production, with defaults)
```bash
# Rate Limiting
RATE_LIMIT_BACKEND                      # 'redis' or 'local' (default: redis)
RATE_LIMIT_CAPACITY                     # General rate limit (default: 100 req/min)
RATE_LIMIT_REFILL_TOKENS                # Tokens to refill (default: 100)
RATE_LIMIT_REFILL_SECONDS               # Refill interval (default: 60)
RATE_LIMIT_AUTH_CAPACITY                # Auth endpoint limit (default: 10 req/min)
RATE_LIMIT_AUTH_REFILL_TOKENS           # Auth tokens to refill (default: 10)
RATE_LIMIT_AUTH_REFILL_SECONDS          # Auth refill interval (default: 60)
TRUSTED_PROXY_CIDRS                     # Comma-separated CIDR ranges
# Authentication
AUTH_MAX_FAILED_ATTEMPTS                # Account lock threshold (default: 5)
AUTH_LOCK_DURATION_MINUTES              # Lockout duration (default: 15)
# Logging
LOGGING_LEVEL_ROOT                      # Root logger level (WARN for production)
LOGGING_LEVEL_COM_COMPANY_USERMANAGEMENT # Application logger level (INFO)
```
---
## Docker Deployment
### Build Image
```bash
# Build application JAR
./mvnw clean package -DskipTests
# Build Docker image
docker build -t iam-service:latest .
```
### Push to Registry
```bash
# Tag image
docker tag iam-service:latest myregistry.azurecr.io/iam-service:1.0.0
# Push to registry
docker push myregistry.azurecr.io/iam-service:1.0.0
```
### Run Container
```bash
docker run -d \
  --name iam-service \
  --restart unless-stopped \
  -p 8080:8080 \
  -e JWT_SECRET_KEY="$(openssl rand -base64 32)" \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/userdb" \
  -e SPRING_DATASOURCE_PASSWORD="strong_password_here" \
  -e SPRING_DATA_REDIS_HOST="redis" \
  -e SPRING_DATA_REDIS_PORT="6379" \
  -e SPRING_PROFILES_ACTIVE="prod" \
  myregistry.azurecr.io/iam-service:1.0.0
```
### Docker Compose (Staging)
```bash
docker-compose -f docker-compose.prod.yml up -d
```
---
## Kubernetes Deployment
### 1. Create Namespace
```bash
kubectl create namespace iam-service
```
### 2. Create Secrets
```bash
kubectl create secret generic iam-secrets \
  --from-literal=JWT_SECRET_KEY="$(openssl rand -base64 32)" \
  --from-literal=DB_PASSWORD="strong_password_here" \
  --from-literal=REDIS_PASSWORD="redis_password_here" \
  -n iam-service
```
### 3. Create ConfigMap
```bash
kubectl create configmap iam-config \
  --from-literal=LOG_LEVEL="INFO" \
  --from-literal=SPRING_DATASOURCE_URL="jdbc:postgresql://postgres.iam-service:5432/iam" \
  --from-literal=SPRING_DATA_REDIS_HOST="redis.iam-service" \
  -n iam-service
```
### 4. Deploy Application
```bash
kubectl apply -f k8s/deployment.yaml -n iam-service
kubectl apply -f k8s/service.yaml -n iam-service
kubectl apply -f k8s/hpa.yaml -n iam-service  # Optional: auto-scaling
```
### Deployment YAML Example
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: iam-service
  namespace: iam-service
  labels:
    app: iam-service
    version: v1
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: iam-service
  template:
    metadata:
      labels:
        app: iam-service
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/api/v1/actuator/prometheus"
    spec:
      serviceAccountName: default
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
      containers:
      - name: iam-service
        image: myregistry.azurecr.io/iam-service:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: JWT_SECRET_KEY
          valueFrom:
            secretKeyRef:
              name: iam-secrets
              key: JWT_SECRET_KEY
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: iam-secrets
              key: DB_PASSWORD
        - name: SPRING_DATA_REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: iam-secrets
              key: REDIS_PASSWORD
        envFrom:
        - configMapRef:
            name: iam-config
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /api/v1/actuator/health/liveness
            port: 8080
            scheme: HTTP
          initialDelaySeconds: 45
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /api/v1/actuator/health/readiness
            port: 8080
            scheme: HTTP
          initialDelaySeconds: 20
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        securityContext:
          runAsNonRoot: true
          allowPrivilegeEscalation: false
          capabilities:
            drop:
            - ALL
          readOnlyRootFilesystem: true
        volumeMounts:
        - name: tmp
          mountPath: /tmp
      volumes:
      - name: tmp
        emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: iam-service
  namespace: iam-service
  labels:
    app: iam-service
spec:
  type: ClusterIP
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: iam-service
```
---
## Post-Deployment Verification
### 1. Health Check
```bash
curl -i https://your-domain/api/v1/actuator/health
# Expected: 200 OK
```
### 2. Metrics Available
```bash
curl -s https://your-domain/api/v1/actuator/prometheus | head -20
# Expected: Prometheus metrics output
```
### 3. API Documentation
```bash
curl -s https://your-domain/api/v1/swagger-ui.html | head -10
# Expected: Swagger UI HTML
```
### 4. Test Authentication Flow
```bash
# Register user
curl -X POST https://your-domain/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName":"John",
    "lastName":"Doe",
    "email":"test@example.com",
    "password":"SecurePass123!"
  }'
# Expected: 201 Created with auth response
# Login
curl -X POST https://your-domain/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email":"test@example.com",
    "password":"SecurePass123!"
  }'
# Expected: 200 OK with tokens
```
### 5. Verify Password Complexity
```bash
curl -X POST https://your-domain/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName":"Jane",
    "lastName":"Doe",
    "email":"weak@example.com",
    "password":"weak"
  }'
# Expected: 400 Bad Request (password too weak)
```
### 6. Verify HTTPS Redirect
```bash
curl -i http://your-domain/api/v1/users/me
# Expected: 301 Moved Permanently with https URL
```
---
## Rollback Plan
### Docker Rollback
```bash
# Stop current container
docker stop iam-service
# Run previous version
docker run -d \
  --name iam-service-previous \
  ...previous-environment...
  myregistry.azurecr.io/iam-service:previous-tag
```
### Kubernetes Rollback
```bash
# Check rollout history
kubectl rollout history deployment/iam-service -n iam-service
# Rollback to previous version
kubectl rollout undo deployment/iam-service -n iam-service
# Monitor rollback
kubectl rollout status deployment/iam-service -n iam-service
```
### Database Considerations
- ✅ Flyway migrations are forward-only
- ✅ No rollback needed for schema changes
- ⚠️ Data written by new version may need cleanup
- 📋 Document any manual data cleanup steps
---
## Performance Tuning
### Database Connection Pool
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20         # Adjust based on load
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 120000
```
### Redis Connection
```yaml
spring:
  data:
    redis:
      timeout: 2000ms
      jedis:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
```
### JVM Tuning
```bash
export JAVA_OPTS="-Xms1g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -XX:+UseStringDeduplication"
```
---
## Monitoring & Alerting
### Key Metrics to Monitor
```
auth.requests.total                   # Total authentication attempts
auth.failures.total                   # Failed login attempts
rate_limit.rejections.total           # Rate limit violations
tokens.revoked.total                  # Token revocations
audit.log.entries.total               # Audit log entries
http.requests.latency                 # Request latency
database.connections.active           # Active DB connections
redis.connections.active              # Active Redis connections
```
### Alert Rules (Prometheus)
```yaml
- alert: HighFailedLoginRate
  expr: rate(auth.failures.total[5m]) > 10
  for: 5m
  annotations:
    summary: "High failed login rate detected"
    description: "{{ $value }} failed logins per second"
- alert: RateLimitSpike
  expr: rate(rate_limit.rejections.total[1m]) > 100
  for: 1m
  annotations:
    summary: "DDoS or traffic spike detected"
    description: "{{ $value }} rate limit rejections per second"
- alert: DatabaseConnectionPool
  expr: database.connections.active > 18
  for: 2m
  annotations:
    summary: "Database connection pool near exhaustion"
    description: "{{ $value }} of 20 connections in use"
```
---
## Troubleshooting
### Application won't start
```bash
# Check logs
docker logs iam-service
# or
kubectl logs -f deployment/iam-service -n iam-service
# Common issues:
# - JWT_SECRET_KEY not set or invalid (must be base64, 32+ bytes)
# - Database connection failed (check credentials, firewall)
# - Redis unavailable (app should fall back to local cache)
```
### High response latency
```bash
# Check database connection pool
curl https://domain/api/v1/actuator/metrics/sql.PooledDatabase.Connections
# Check Redis connectivity
redis-cli -h redis-host PING
# Review JVM metrics
curl https://domain/api/v1/actuator/metrics
```
### Failed logins spike
```bash
# Check audit logs
SELECT COUNT(*) FROM audit_log WHERE action='LOGIN_FAILED' 
  AND created_at > NOW() - INTERVAL '5 minutes';
# Check for brute force attack
SELECT ip_address, COUNT(*) FROM audit_log 
  WHERE action='LOGIN_FAILED' AND created_at > NOW() - INTERVAL '1 hour'
  GROUP BY ip_address ORDER BY COUNT(*) DESC LIMIT 10;
```
---
Created by Software Engineer Daniel Maina.  
**Last Updated**: April 23, 2026
