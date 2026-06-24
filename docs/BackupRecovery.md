# Backup and Recovery Strategy

## Overview

This document defines the backup, restore, and disaster recovery procedures for the ATM Banking System's MySQL database. It should be reviewed and updated whenever the schema or deployment topology changes.

## Recovery Objectives

| Metric | Target | Notes |
|--------|--------|-------|
| **RPO** (Recovery Point Objective) | ≤ 1 hour | Maximum data loss window; achieved with hourly backups |
| **RTO** (Recovery Time Objective) | ≤ 4 hours | Time to restore service after a declared disaster |

## Backup Strategy

### Daily full backup

```bash
# Run nightly at 02:00 UTC (configure via cron or orchestrator)
mysqldump \
  --single-transaction \
  --routines \
  --triggers \
  --set-gtid-purged=OFF \
  -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
  | gzip > "/backups/banking_$(date +%Y%m%d_%H%M%S)_full.sql.gz"
```

`--single-transaction` takes a consistent snapshot without locking tables (InnoDB).

### Hourly incremental (binary log)

Enable binary logging in MySQL to support point-in-time recovery:

```ini
# /etc/mysql/conf.d/binlog.cnf
[mysqld]
log_bin           = /var/log/mysql/banking-binlog
binlog_format     = ROW
expire_logs_days  = 7
```

Sync binary logs to object storage hourly:
```bash
aws s3 sync /var/log/mysql/ s3://your-backup-bucket/binlogs/ --storage-class STANDARD_IA
```

### Retention policy

| Type | Retention |
|------|-----------|
| Daily full backups | 30 days |
| Weekly snapshots | 90 days |
| Monthly snapshots | 1 year |
| Binary logs | 7 days (rolling) |

### Off-site replication

- Sync nightly full backup archives to a geographically separate object store (S3, GCS, Azure Blob).
- For Docker deployments, mount the `atm-mysql-data` volume on a host that is snapshot by the cloud provider (e.g., AWS EBS snapshots).

## Restore Procedures

### Full restore from dump

```bash
# 1. Stop the application (prevent new writes)
docker compose stop backend frontend

# 2. Create a fresh database
mysql -h "$DB_HOST" -u root -p -e "DROP DATABASE IF EXISTS $DB_NAME; CREATE DATABASE $DB_NAME;"

# 3. Restore the full dump
gunzip -c /backups/banking_YYYYMMDD_HHMMSS_full.sql.gz \
  | mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME"

# 4. Verify row counts
mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "
  SELECT TABLE_NAME, TABLE_ROWS
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = '$DB_NAME';
"

# 5. Restart the application
docker compose start backend frontend
```

### Point-in-time recovery (PITR)

```bash
# Restore the last full backup, then replay binary logs up to the target time

# 1. Restore full backup (see above)

# 2. Apply binary logs from the backup timestamp to the target
mysqlbinlog \
  --start-datetime="2026-06-24 02:00:00" \
  --stop-datetime="2026-06-24 08:45:00" \
  /var/log/mysql/banking-binlog.* \
  | mysql -h "$DB_HOST" -u root -p"$MYSQL_ROOT_PASSWORD" "$DB_NAME"
```

## Disaster Recovery Scenarios

### Scenario 1 — Accidental data deletion

1. Identify the timestamp before the deletion from audit logs (`audit_logs.created_at`).
2. Perform PITR to 1 minute before that timestamp.
3. Export affected rows and merge into the live database.

### Scenario 2 — Database server failure

1. Restore the most recent full backup to a new MySQL instance (Docker or cloud RDS).
2. Apply binary logs for the missed period if available.
3. Update `DB_URL` environment variable and restart the backend container.

### Scenario 3 — Ransomware / complete environment compromise

1. Provision a clean environment from infrastructure-as-code.
2. Restore from the most recent off-site archive (never from the compromised environment).
3. Rotate all secrets (`JWT_SECRET`, DB passwords) before restarting.
4. Notify affected users if data exfiltration cannot be ruled out.

## Backup Verification

Run monthly restore drills to a staging environment:

```bash
# Restore last night's backup to staging
gunzip -c /backups/banking_latest_full.sql.gz \
  | mysql -h staging-db -u "$DB_USER" -p"$DB_PASSWORD" "${DB_NAME}_restore_test"

# Run a smoke test (login + balance check) against the staging environment.
# Document the RTO achieved.
```

## Schema Migrations

In production, schema changes must use a migration tool (Flyway or Liquibase) rather than `ddl-auto: update`. Store migration scripts in `backend/src/main/resources/db/migration/`.

## Contact and Escalation

| Role | Responsibility |
|------|---------------|
| On-call engineer | First responder — restore from backup, monitor recovery |
| Database administrator | PITR, replication setup, schema migration approval |
| Security lead | Incident response if data breach is suspected |
