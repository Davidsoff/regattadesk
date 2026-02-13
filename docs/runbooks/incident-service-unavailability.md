# Incident Runbook: Service Unavailability

**Runbook ID:** RB-001  
**Category:** Incident Response  
**Severity:** P1 (Critical)  
**Last Updated:** 2026-02-13

## Symptoms

- Backend health checks failing (`/q/health/ready` returns non-200)
- API endpoints returning 502/503/504 errors
- Users unable to access the application
- Monitoring alerts firing: `BackendDown`, `ServiceUnavailable`
- Database connection errors in logs
- Container restarts or crashes

## Impact

**Business Impact:**
- Complete service outage
- Users cannot access public results
- Staff unable to manage regatta operations
- Operators cannot record timings

**Technical Impact:**
- No API availability
- Data not being persisted
- SSE connections disconnected

## Initial Triage

### Step 1: Verify the Issue

```bash
# Check service health
curl -v http://localhost/q/health/ready

# Check all service status
docker compose ps

# Quick check all containers
docker ps -a | grep regattadesk
```

**Expected Output:**
- Health check should return 200 OK with JSON body
- All services should show "Up" status
- Containers should not be in "Restarting" or "Exited" state

### Step 2: Identify Failed Component

```bash
# Check backend logs
docker compose logs --tail=100 backend

# Check database logs
docker compose logs --tail=100 postgres

# Check Traefik logs
docker compose logs --tail=100 traefik

# Check Authelia logs
docker compose logs --tail=100 authelia
```

**Look for:**
- Exception stack traces
- Connection refused errors
- Out of memory errors
- Database connection failures
- Authentication errors

### Step 3: Check Resource Utilization

```bash
# Check Docker stats
docker stats --no-stream

# Check host resources
free -h
df -h
```

**Watch for:**
- Memory usage > 90%
- Disk space < 10% free
- CPU throttling

## Resolution Steps

### Scenario A: Backend Container Down

**Symptoms:** Backend container shows "Exited" or frequent restarts

**Resolution:**

1. Check backend logs for errors:
   ```bash
   docker compose logs --tail=200 backend | less
   ```

2. Check backend health endpoint directly:
   ```bash
   docker compose exec backend wget -O- http://localhost:8080/q/health/ready
   ```

3. Restart backend service:
   ```bash
   docker compose restart backend
   ```

4. Wait 60 seconds for startup probe to pass:
   ```bash
   watch -n 2 'curl -s http://localhost/q/health/ready'
   ```

5. If restart fails, check configuration:
   ```bash
   docker compose config | grep -A 20 backend
   ```

6. Check for missing environment variables:
   ```bash
   docker compose exec backend env | grep -E "QUARKUS|POSTGRES|MINIO"
   ```

### Scenario B: Database Connection Failures

**Symptoms:** Backend logs show "Connection refused" or "Could not connect to database"

**Resolution:**

1. Verify database is running:
   ```bash
   docker compose ps postgres
   ```

2. Check database health:
   ```bash
   docker compose exec postgres pg_isready -U regattadesk
   ```

3. Check database logs:
   ```bash
   docker compose logs --tail=100 postgres
   ```

4. Test database connection manually:
   ```bash
   docker compose exec postgres psql -U regattadesk -d regattadesk -c "SELECT 1;"
   ```

5. If database is down, restart it:
   ```bash
   docker compose restart postgres
   ```

6. Wait for database to be healthy (check health status):
   ```bash
   docker compose ps postgres
   # Should show "(healthy)"
   ```

7. Restart backend after database is healthy:
   ```bash
   docker compose restart backend
   ```

### Scenario C: Traefik Routing Issues

**Symptoms:** Traefik is up, backend is healthy, but requests return 502/504

**Resolution:**

1. Check Traefik logs:
   ```bash
   docker compose logs --tail=100 traefik
   ```

2. Verify Traefik can reach backend:
   ```bash
   docker compose exec traefik wget -O- http://backend:8080/q/health/ready
   ```

3. Check Traefik configuration:
   ```bash
   docker compose exec traefik cat /etc/traefik/dynamic/dynamic.yml
   ```

4. Verify network connectivity:
   ```bash
   docker network ls | grep regattadesk
   docker network inspect regattadesk-edge
   docker network inspect regattadesk-internal
   ```

5. Restart Traefik:
   ```bash
   docker compose restart traefik
   ```

### Scenario D: Complete Stack Failure

**Symptoms:** All services down or not responding

**Resolution:**

1. Stop all services:
   ```bash
   docker compose down
   ```

2. Check for port conflicts:
   ```bash
   sudo netstat -tulpn | grep -E ":(80|443|5432|9000|9091|8080)\s"
   ```

3. Check disk space:
   ```bash
   df -h
   # Ensure / has > 10% free space
   ```

4. Check Docker daemon:
   ```bash
   sudo systemctl status docker
   ```

5. Start services with verbose output:
   ```bash
   docker compose up -d
   docker compose logs -f
   ```

6. Monitor service startup:
   ```bash
   watch -n 2 'docker compose ps'
   ```

7. Wait for all health checks to pass (may take 2-3 minutes)

### Scenario E: Out of Memory (OOM)

**Symptoms:** Containers killed with exit code 137, "OOMKilled" in docker ps

**Resolution:**

1. Identify OOM-killed containers:
   ```bash
   docker ps -a --filter "exited=137"
   ```

2. Check memory limits:
   ```bash
   docker stats --no-stream
   ```

3. Increase memory limits in docker-compose.yml (if needed):
   ```yaml
   backend:
     deploy:
       resources:
         limits:
           memory: 2G
   ```

4. Restart affected services:
   ```bash
   docker compose up -d
   ```

5. Monitor memory usage:
   ```bash
   watch -n 5 'docker stats --no-stream'
   ```

## Validation

After resolution, validate the fix:

### 1. Health Checks

```bash
# Backend health
curl -v http://localhost/q/health/ready
# Expected: 200 OK

# Custom health endpoint
curl -v http://localhost/api/health
# Expected: 200 OK with {"status": "UP"}

# Readiness probe
curl -v http://localhost/q/health/live
# Expected: 200 OK
```

### 2. Service Status

```bash
docker compose ps
# All services should show "Up (healthy)"
```

### 3. Functional Test

```bash
# Run smoke test
cd /home/runner/work/regattadesk/regattadesk/infra/compose
./smoke-test.sh
```

### 4. Monitor Dashboards

- Open Grafana: http://localhost/grafana
- Check "Backend API Health" dashboard
- Verify:
  - ✅ Service status shows "UP"
  - ✅ Request rate > 0
  - ✅ Error rate < 1%
  - ✅ Response time < 500ms (p95)

### 5. End-to-End Test

```bash
# Test public endpoint
curl http://localhost/api/health

# Test metrics endpoint
curl http://localhost/q/metrics | grep "http_server_requests_seconds_count"
```

## Prevention

### Short-term (Immediate)

1. Set up resource monitoring alerts
2. Configure container resource limits
3. Enable automatic container restart policies
4. Set up log aggregation for better debugging

### Long-term (Strategic)

1. Implement horizontal scaling for backend
2. Add database replication for high availability
3. Configure load balancing across multiple backend instances
4. Implement circuit breakers for external dependencies
5. Add automated recovery procedures
6. Set up chaos engineering tests

### Monitoring Improvements

Add alerts for:
- Container restart count > 3 in 5 minutes
- Health check failures > 2 consecutive
- Memory usage > 85% for 5 minutes
- Database connection pool exhaustion

## Escalation Criteria

Escalate to senior engineer if:

- ⏰ Issue not resolved within **15 minutes**
- 🔄 Services repeatedly crash after restart (> 3 times)
- 💾 Data corruption suspected
- 🔒 Security incident suspected
- 📊 Resource constraints cannot be resolved immediately
- ❓ Root cause unclear after initial triage

## Escalation Procedure

1. Gather diagnostic information:
   ```bash
   # Save logs
   docker compose logs > incident-logs-$(date +%Y%m%d-%H%M%S).txt
   
   # Save service status
   docker compose ps > incident-status-$(date +%Y%m%d-%H%M%S).txt
   
   # Save resource usage
   docker stats --no-stream > incident-resources-$(date +%Y%m%d-%H%M%S).txt
   ```

2. Contact senior engineer via PagerDuty
3. Share incident details and diagnostic files
4. Continue monitoring while waiting for support

## Post-Incident Actions

### Immediate (Within 1 hour)

- [ ] Update incident ticket with resolution
- [ ] Notify stakeholders of resolution
- [ ] Verify monitoring alerts have cleared
- [ ] Document root cause

### Follow-up (Within 24 hours)

- [ ] Review logs for warning signs
- [ ] Update runbook if new scenarios discovered
- [ ] Check for similar issues in other environments
- [ ] Create technical debt tickets for prevention work

### Long-term (Within 1 week)

- [ ] Schedule postmortem meeting
- [ ] Create action items from postmortem
- [ ] Update monitoring and alerting
- [ ] Test runbook procedures in staging

## Related Runbooks

- [RB-004: Database Issues](./incident-database-failures.md)
- [RB-002: Authentication Failures](./incident-authentication-failures.md)
- [RB-005: Deployment and Rollback](./procedure-deployment-rollback.md)

## Additional Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Quarkus Health Check Guide](https://quarkus.io/guides/smallrye-health)
- [Observability Documentation](../../infra/compose/OBSERVABILITY.md)
- [PostgreSQL Troubleshooting](https://www.postgresql.org/docs/current/runtime.html)
