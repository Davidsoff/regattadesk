# Operational Procedure: Deployment and Rollback

**Runbook ID:** RB-005  
**Category:** Operational Procedure  
**Last Updated:** 2026-02-13

## Overview

This runbook covers standard deployment procedures, emergency rollback procedures, and zero-downtime deployment strategies for RegattaDesk v0.1.

## Standard Deployment Process

### Prerequisites

Before starting deployment:

- [ ] All tests passing in CI/CD
- [ ] Code review approved and merged
- [ ] Deployment window scheduled and communicated
- [ ] Backup completed
- [ ] Rollback plan reviewed
- [ ] On-call engineer identified
- [ ] Stakeholders notified

### Pre-Deployment Checklist

```bash
# 1. Verify current system health
cd /home/runner/work/regattadesk/regattadesk/infra/compose
./smoke-test.sh

# 2. Check resource utilization
docker stats --no-stream

# 3. Backup database
docker compose exec postgres pg_dump -U regattadesk regattadesk > backup-$(date +%Y%m%d-%H%M%S).sql

# 4. Save current configuration
docker compose config > config-backup-$(date +%Y%m%d-%H%M%S).yml

# 5. Document current versions
docker compose images > versions-before-$(date +%Y%m%d-%H%M%S).txt
```

### Standard Deployment Steps

#### Method 1: Rolling Update (Recommended)

```bash
# 1. Pull latest code
cd /home/runner/work/regattadesk/regattadesk
git fetch origin
git checkout main
git pull origin main

# 2. Review changes
git log --oneline -10

# 3. Build new images (backend)
cd apps/backend
./mvnw clean package -Dquarkus.container-image.build=true

# 4. Build new images (frontend - if changed)
cd ../frontend
# Note: Frontend build currently has known issues, skip if not changed

# 5. Update services one at a time
cd ../../infra/compose

# Update backend with zero downtime
docker compose up -d --no-deps backend

# Wait for health check
sleep 30
curl -f http://localhost/q/health/ready || echo "Health check failed!"

# 6. Update frontend (if changed)
docker compose up -d --no-deps frontend

# 7. Verify all services
docker compose ps
```

#### Method 2: Full Stack Update

```bash
# 1. Stop all services
cd /home/runner/work/regattadesk/regattadesk/infra/compose
docker compose down

# 2. Pull new images or rebuild
# (Backend and frontend builds as above)

# 3. Start all services
docker compose up -d

# 4. Monitor startup
docker compose logs -f

# Wait for all health checks to pass (2-3 minutes)
watch -n 2 'docker compose ps'
```

### Post-Deployment Validation

```bash
# 1. Run smoke tests
cd /home/runner/work/regattadesk/regattadesk/infra/compose
./smoke-test.sh

# 2. Run edge hardening tests
./edge-hardening-test.sh

# 3. Run observability tests
./observability-smoke-test.sh

# 4. Test authentication flow
./edge-auth-test.sh

# 5. Check health endpoints
curl http://localhost/q/health/ready
curl http://localhost/api/health

# 6. Verify metrics are being collected
curl http://localhost/q/metrics | grep "http_server_requests"

# 7. Check Grafana dashboards
# Open http://localhost/grafana
# Verify "Backend API Health" dashboard shows data

# 8. Verify logs are clean
docker compose logs --tail=100 backend | grep -i error
docker compose logs --tail=100 frontend | grep -i error
```

### Post-Deployment Checklist

- [ ] All services running and healthy
- [ ] Health checks passing
- [ ] Smoke tests passing
- [ ] Monitoring dashboards show normal metrics
- [ ] No error spikes in logs
- [ ] User-facing features tested
- [ ] Stakeholders notified of completion
- [ ] Deployment documented in change log

## Emergency Rollback Procedures

### When to Rollback

Immediately rollback if:

- ❌ Health checks failing after deployment
- ❌ Error rate > 10% for > 2 minutes
- ❌ Critical functionality broken
- ❌ Data corruption detected
- ❌ Security vulnerability introduced
- ❌ Performance degradation > 50%

### Rollback Decision Matrix

| Severity | Symptoms | Action | Timeline |
|----------|----------|--------|----------|
| P1 - Critical | Service down, data loss | Immediate rollback | < 5 minutes |
| P2 - High | High error rate, degraded performance | Rollback if not fixed in 10 minutes | < 15 minutes |
| P3 - Medium | Non-critical feature broken | Assess and decide | < 30 minutes |
| P4 - Low | Minor UI issues, logging errors | Fix forward | Next deployment |

### Rollback Steps

#### Quick Rollback (Docker Images)

```bash
# 1. Stop current services
cd /home/runner/work/regattadesk/regattadesk/infra/compose
docker compose down

# 2. Check available images
docker images | grep regattadesk

# 3. Update .env or docker-compose.yml to use previous version
# Edit BACKEND_IMAGE to point to previous tag
nano .env
# Change: BACKEND_IMAGE=regattadesk-backend:0.1.0-SNAPSHOT
# To: BACKEND_IMAGE=regattadesk-backend:0.1.0-SNAPSHOT-previous

# 4. Start services with previous version
docker compose up -d

# 5. Monitor startup
docker compose logs -f
```

#### Git-Based Rollback

```bash
# 1. Identify commit to rollback to
cd /home/runner/work/regattadesk/regattadesk
git log --oneline -20

# 2. Create rollback branch
git checkout -b rollback-$(date +%Y%m%d-%H%M%S)

# 3. Revert to previous commit
git revert <commit-hash>
# OR hard reset (use with caution)
git reset --hard <previous-commit-hash>

# 4. Rebuild and deploy
cd apps/backend
./mvnw clean package -Dquarkus.container-image.build=true

cd ../../infra/compose
docker compose up -d --no-deps backend

# 5. Validate rollback
./smoke-test.sh
```

#### Database Rollback

**⚠️ WARNING: Database rollbacks are risky and should be last resort**

```bash
# 1. Stop application services
cd /home/runner/work/regattadesk/regattadesk/infra/compose
docker compose stop backend

# 2. Create safety backup of current state
docker compose exec postgres pg_dump -U regattadesk regattadesk > current-state-$(date +%Y%m%d-%H%M%S).sql

# 3. Restore from backup
docker compose exec -T postgres psql -U regattadesk regattadesk < backup-YYYYMMDD-HHMMSS.sql

# 4. Verify restoration
docker compose exec postgres psql -U regattadesk -d regattadesk -c "\dt"

# 5. Restart services
docker compose start backend

# 6. Validate
./smoke-test.sh
```

### Post-Rollback Actions

```bash
# 1. Verify system is stable
cd /home/runner/work/regattadesk/regattadesk/infra/compose
./smoke-test.sh

# 2. Document rollback reason
cat > rollback-notes-$(date +%Y%m%d-%H%M%S).txt << EOF
Rollback Date: $(date)
Rolled back from: [commit/version]
Rolled back to: [commit/version]
Reason: [detailed reason]
Impact: [what was affected]
Actions taken: [steps performed]
EOF

# 3. Notify stakeholders
# Send notification via communication channels

# 4. Create incident ticket
# Document in issue tracker

# 5. Schedule postmortem
# Plan meeting within 24-48 hours
```

## Configuration Updates

### Environment Variable Changes

```bash
# 1. Update .env file
cd /home/runner/work/regattadesk/regattadesk/infra/compose
nano .env

# 2. Restart affected services
docker compose up -d --no-deps backend

# 3. Verify new configuration
docker compose exec backend env | grep [VARIABLE_NAME]

# 4. Test functionality
./smoke-test.sh
```

### Traefik Configuration Changes

```bash
# 1. Edit dynamic configuration
nano traefik/dynamic.yml

# 2. Validate YAML syntax
docker compose exec traefik cat /etc/traefik/dynamic/dynamic.yml

# 3. Traefik reloads automatically (watch logs)
docker compose logs -f traefik

# 4. Test routing
./edge-hardening-test.sh
```

### Authelia Configuration Changes

```bash
# 1. Edit configuration
nano authelia/configuration.yml

# 2. Validate YAML syntax
cat authelia/configuration.yml

# 3. Restart Authelia
docker compose restart authelia

# 4. Wait for health check
sleep 30
curl http://localhost/auth/api/health

# 5. Test authentication
./edge-auth-test.sh
```

## Zero-Downtime Deployment Strategy

### For Backend Services

```bash
# 1. Scale up to 2 instances
docker compose up -d --scale backend=2

# 2. Wait for new instance to be healthy
sleep 60

# 3. Update configuration for rolling update
# Use docker-compose.yml with rolling update strategy

# 4. Verify both instances are serving traffic
curl http://localhost/q/health/ready
# Request should hit load-balanced instances

# 5. Scale back to 1 after validation
docker compose up -d --scale backend=1
```

### For Database Migrations

```bash
# 1. Run migration in transaction
docker compose exec postgres psql -U regattadesk -d regattadesk << EOF
BEGIN;
-- Run migration SQL
ALTER TABLE entries ADD COLUMN new_field VARCHAR(255);
-- Verify
SELECT * FROM entries LIMIT 1;
COMMIT;
EOF

# 2. If migration fails, transaction auto-rolls back

# 3. Verify migration success
docker compose exec postgres psql -U regattadesk -d regattadesk -c "\d entries"

# 4. Deploy application code that uses new schema
docker compose up -d --no-deps backend
```

## Troubleshooting Deployment Issues

### Build Failures

```bash
# Backend build fails
cd apps/backend
./mvnw clean package -X  # Run with debug output

# Check for:
# - Dependency resolution issues
# - Test failures
# - Resource errors

# Frontend build fails (known issue)
cd apps/frontend
npm ci
npm run build
# If fails, check BUILD_ISSUES.md
```

### Container Won't Start

```bash
# Check logs
docker compose logs backend

# Common issues:
# - Missing environment variables
# - Port conflicts
# - Volume mount issues
# - Resource constraints

# Verify configuration
docker compose config

# Check resources
docker stats --no-stream
```

### Health Checks Failing

```bash
# Test health endpoint directly
docker compose exec backend wget -O- http://localhost:8080/q/health/ready

# Check startup logs
docker compose logs backend | grep "started in"

# Verify dependencies are ready
docker compose exec backend wget -O- http://postgres:5432
docker compose exec backend wget -O- http://minio:9000
```

## Maintenance Windows

### Scheduled Maintenance

**Recommended maintenance window:** Sunday 02:00-04:00 UTC (low traffic)

**Pre-maintenance:**
- [ ] Announce maintenance 48 hours in advance
- [ ] Create maintenance banner on website
- [ ] Prepare rollback plan
- [ ] Verify backups are current

**During maintenance:**
- [ ] Set maintenance mode (if available)
- [ ] Perform deployment/updates
- [ ] Run validation tests
- [ ] Monitor for issues

**Post-maintenance:**
- [ ] Remove maintenance mode
- [ ] Announce completion
- [ ] Monitor for 1 hour
- [ ] Document changes

### Emergency Maintenance

For critical security updates or critical bugs:

1. **Immediate notification** (< 30 minutes notice acceptable)
2. **Fast-track deployment** (skip optional validation steps)
3. **Continuous monitoring** during and after
4. **Extended monitoring period** (4+ hours)
5. **Detailed postmortem** within 24 hours

## Deployment Checklist Template

```markdown
## Deployment: [Date] [Version/Ticket]

### Pre-Deployment
- [ ] CI/CD passing
- [ ] Code review approved
- [ ] Backup completed: [filename]
- [ ] Stakeholders notified
- [ ] Maintenance window scheduled

### Deployment
- [ ] Services stopped/scaled appropriately
- [ ] New version deployed
- [ ] Health checks passing
- [ ] Configuration updated

### Validation
- [ ] Smoke tests passed
- [ ] Edge hardening tests passed
- [ ] Authentication working
- [ ] Monitoring dashboards normal
- [ ] User acceptance test completed

### Post-Deployment
- [ ] Stakeholders notified
- [ ] Documentation updated
- [ ] Monitoring for issues (1 hour)
- [ ] Incident: None / [Link to incident]
```

## Related Runbooks

- [RB-001: Service Unavailability](./incident-service-unavailability.md)
- [RB-008: Configuration Management](./procedure-configuration-management.md)
- [RB-006: Log Analysis](./procedure-log-analysis.md)

## Additional Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [CI/CD Implementation Summary](../ci-cd-implementation-summary.md)
- [Developer Setup Guide](../DEVELOPER_SETUP.md)
- [Implementation Plan](../../pdd/implementation/plan.md)
