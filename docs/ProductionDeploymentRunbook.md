# Production Deployment Runbook

## Overview

This runbook covers deploying, operating, and recovering the ATM Banking System in production.
The system consists of a Spring Boot backend, React/nginx frontend, MySQL 8, Redis 7,
Prometheus, and Grafana — all orchestrated by Docker Compose.

---

## 1. Environment Variables

All secrets must be supplied as environment variables. Copy `.env.example` to `.env` and fill in
every value before the first deploy.

| Variable | Required | Description |
|----------|----------|-------------|
| `JWT_SECRET` | ✅ | Base-64 JWT signing key (≥ 32 bytes). Generate: `openssl rand -base64 64` |
| `DB_URL` | ✅ | JDBC URL e.g. `jdbc:mysql://mysql:3306/banking_db?serverTimezone=UTC` |
| `DB_USERNAME` | ✅ | MySQL application user (not root) |
| `DB_PASSWORD` | ✅ | MySQL application password |
| `MYSQL_ROOT_PASSWORD` | ✅ | MySQL root password |
| `MYSQL_DATABASE` | ✅ | Database name (default: `banking_db`) |
| `MYSQL_USER` | ✅ | Same as `DB_USERNAME` |
| `MYSQL_PASSWORD` | ✅ | Same as `DB_PASSWORD` |
| `APP_BASE_URL` | ✅ | Public frontend URL e.g. `https://bank.example.com` |
| `JWT_ACCESS_EXPIRATION` | ⬜ | Access token TTL in ms (default: 900000 = 15 min) |
| `JWT_REFRESH_EXPIRATION` | ⬜ | Refresh token TTL in ms (default: 604800000 = 7 days) |
| `SEED_DEMO_DATA` | ⬜ | Set `false` in production (default: `true`) |
| `GRAFANA_ADMIN_USER` | ⬜ | Grafana admin user (default: `admin`) |
| `GRAFANA_ADMIN_PASSWORD` | ⬜ | Grafana admin password (change from default) |
| `REDIS_HOST` | ⬜ | Redis hostname (default: `redis`) |
| `REDIS_RATE_LIMIT_ENABLED` | ⬜ | Set `true` to use Redis rate limiting (default: `false`) |

---

## 2. First-time Deploy

```bash
# 1. Clone the repository
git clone https://github.com/your-org/atm-banking-system.git
cd atm-banking-system

# 2. Configure secrets
cp .env.example .env
# Edit .env — fill in all REQUIRED variables

# 3. Build and start all services
docker compose --env-file .env up --build -d

# 4. Verify all services are healthy
docker compose ps

# Expected output: all services show "(healthy)" status
```

---

## 3. Database Migration Process

Flyway manages all schema changes. Migrations run automatically on backend startup.

```bash
# Check migration status (while backend container is running)
docker compose exec backend java -jar app.jar --spring.flyway.validate-on-migrate=true

# View applied migrations in MySQL
docker compose exec mysql mysql -u banking -p banking_db \
  -e "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;"
```

### Adding a new migration

1. Create `backend/src/main/resources/db/migration/V{N}__description.sql`
2. Never modify existing migration files
3. Test on staging before production
4. Deploy: `docker compose up --build -d backend`

### Emergency: repair failed migration

```bash
# Only if a migration partially ran and failed:
docker compose exec mysql mysql -u root -p banking_db \
  -e "UPDATE flyway_schema_history SET success=1 WHERE success=0;"
# Then fix the migration SQL and redeploy
```

---

## 4. Backup / Restore

See [BackupRecovery.md](BackupRecovery.md) for full procedures.

**Quick backup:**
```bash
docker compose exec mysql mysqldump \
  --single-transaction --routines --triggers \
  -u root -p"${MYSQL_ROOT_PASSWORD}" banking_db \
  | gzip > "backup_$(date +%Y%m%d_%H%M%S).sql.gz"
```

**Quick restore:**
```bash
gunzip -c backup_YYYYMMDD_HHMMSS.sql.gz \
  | docker compose exec -T mysql mysql \
    -u root -p"${MYSQL_ROOT_PASSWORD}" banking_db
```

---

## 5. Monitoring Endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /actuator/health` | Public | Docker/k8s health check |
| `GET /actuator/health/liveness` | Public | Liveness probe |
| `GET /actuator/health/readiness` | Public | Readiness probe |
| `GET /actuator/info` | Public | App version info |
| `GET /actuator/metrics` | Requires JWT | JVM, HTTP, DB metrics |
| `GET /actuator/prometheus` | Public (internal) | Prometheus scrape endpoint |
| `http://host:9090` | None | Prometheus UI |
| `http://host:3000` | Grafana login | Grafana dashboards |

### Import a Grafana dashboard

1. Log in to Grafana (`http://host:3000`, default: admin/admin — change this)
2. Go to **Dashboards → Import**
3. Enter dashboard ID **4701** (JVM Micrometer) and click **Load**
4. Select the provisioned **Prometheus** data source
5. Click **Import**

Recommended dashboard IDs:
- `4701` — JVM (Micrometer)
- `10280` — Spring Boot Statistics

---

## 6. Log Review

### Container logs (real-time)

```bash
# All services
docker compose logs -f

# Backend only (last 200 lines)
docker compose logs backend --tail=200 -f

# Filter for authentication events
docker compose logs backend | grep -E '"action":"(LOGIN|LOGOUT|REFRESH)"'

# Filter for errors
docker compose logs backend | grep '"level":"ERROR"'
```

### Structured log fields (non-dev profile)

Logs are JSON in non-dev profiles. Key fields:

| Field | Description |
|-------|-------------|
| `timestamp` | ISO-8601 UTC |
| `level` | INFO / WARN / ERROR |
| `logger` | Java class name |
| `message` | Human-readable description |
| `stack_trace` | Exception trace (on errors) |

---

## 7. Rolling Update / Redeploy

```bash
# Pull latest code
git pull origin main

# Rebuild and restart only the changed service (zero-downtime for stateless backend)
docker compose up --build -d backend

# Verify backend is healthy before frontend restarts
docker compose ps backend

# Restart frontend after backend is healthy
docker compose up --build -d frontend
```

---

## 8. Rollback Procedure

```bash
# 1. Identify the previous image tag or commit
git log --oneline -10

# 2. Check out the previous commit
git checkout <previous-commit-sha>

# 3. Rebuild and redeploy
docker compose up --build -d backend frontend

# 4. If the rollback involved a schema change, restore from backup first
# (see Section 4 — schema rollbacks are manual)
```

---

## 9. Incident Response Checklist

- [ ] Check service health: `docker compose ps`
- [ ] Check recent logs: `docker compose logs backend --tail=500`
- [ ] Check MySQL connectivity: `docker compose exec mysql mysqladmin ping -u root -p`
- [ ] Check Redis: `docker compose exec redis redis-cli ping`
- [ ] Verify Prometheus is scraping: `http://host:9090/targets`
- [ ] Check disk space: `df -h`
- [ ] If suspicious auth activity: review audit_logs table for failed logins
- [ ] If token theft suspected: revoke all sessions via `DELETE /api/v1/sessions`
- [ ] Page the on-call engineer if RTO > 30 minutes

---

## 10. Security Checklist

Pre-production gate — verify all items before go-live:

- [ ] `JWT_SECRET` generated with `openssl rand -base64 64` (not the dev placeholder)
- [ ] `DB_PASSWORD` is strong (≥ 20 characters, random)
- [ ] `MYSQL_ROOT_PASSWORD` is strong and different from `DB_PASSWORD`
- [ ] `GRAFANA_ADMIN_PASSWORD` changed from the default `admin`
- [ ] `SEED_DEMO_DATA=false` (no demo users in production)
- [ ] TLS termination configured at the load balancer / reverse proxy
- [ ] Ports 9090 (Prometheus) and 3000 (Grafana) are NOT publicly accessible (firewall)
- [ ] Port 3306 (MySQL) and 6379 (Redis) are NOT publicly accessible
- [ ] Docker socket is NOT exposed to application containers
- [ ] Trivy scan passing with no CRITICAL/HIGH unfixed CVEs
- [ ] Backup and restore procedure tested on staging
- [ ] `GET /actuator/health` returns `{"status":"UP"}` from outside the container
