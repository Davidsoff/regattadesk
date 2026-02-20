# Incident Runbook: Public Endpoint Performance Issues

**Runbook ID:** RB-003  
**Category:** Incident Response  
**Severity:** P2-P3 (High to Medium)  
**Last Updated:** 2026-02-13

## Symptoms

- Slow response times on public endpoints (> 2 seconds)
- High latency in Grafana dashboards
- 504 Gateway Timeout errors
- SSE connections dropping frequently
- Rate limiting triggered unexpectedly
- Public results page loading slowly
- Monitoring alerts: `HighLatency`, `HighErrorRate`

## Impact

**Business Impact:**
- Poor user experience for public visitors
- Regatta results not accessible in timely manner
- Reputation damage during events
- Spectators unable to view live results

**Technical Impact:**
- Backend resources under stress
- Potential cascade failures
- Cache effectiveness reduced
- Database query performance degraded

## Initial Triage

### Step 1: Verify Performance Degradation

```bash
# Check response times
time curl -w "@-" -o /dev/null -s http://localhost/api/health << 'EOF'
time_namelookup:  %{time_namelookup}s\n
time_connect:  %{time_connect}s\n
time_appconnect:  %{time_appconnect}s\n
time_pretransfer:  %{time_pretransfer}s\n
time_starttransfer:  %{time_starttransfer}s\n
time_total:  %{time_total}s\n
EOF

# Check multiple endpoints
for endpoint in /api/health /q/health/ready /q/metrics; do
  echo "Testing $endpoint"
  curl -w "Response time: %{time_total}s\n" -o /dev/null -s http://localhost$endpoint
done
```

**Expected Response Times:**
- Health endpoints: < 100ms
- Public API: < 500ms
- Complex queries: < 1000ms

### Step 2: Check System Resources

```bash
# Check Docker container resources
docker stats --no-stream

# Check backend metrics
curl -s http://localhost/q/metrics | grep -E "cpu|memory|http_server"

# Check database connections
docker compose exec postgres psql -U regattadesk -d regattadesk -c "
SELECT count(*) as active_connections,
       max_connections::int - count(*) as available_connections
FROM pg_stat_activity
CROSS JOIN (SELECT setting::int AS max_connections FROM pg_settings WHERE name = 'max_connections') s;
"
```

### Step 3: Check Traffic Patterns

```bash
# Check request rates in Prometheus
# Open http://localhost:9090
# Query: rate(http_server_requests_seconds_count[5m])

# Check Traefik logs for rate limiting
docker compose logs --tail=100 traefik | grep -i "429\|rate"

# Check backend logs for slow queries
docker compose logs --tail=100 backend | grep -i "slow\|timeout"
```

## Resolution Steps

### Scenario A: High CPU Usage

**Symptoms:** Backend container using > 80% CPU

**Resolution:**

1. Identify CPU-intensive operations:
   ```bash
   # Check backend logs for heavy operations
   docker compose logs backend | grep -E "query|request" | tail -50
   
   # Check JVM metrics
   curl -s http://localhost/q/metrics | grep "jvm_threads\|process_cpu"
   ```

2. Check for inefficient queries:
   ```bash
   # Check PostgreSQL slow queries (if slow query log enabled)
   docker compose exec postgres psql -U regattadesk -d regattadesk -c "
   SELECT pid, now() - pg_stat_activity.query_start AS duration, query
   FROM pg_stat_activity
   WHERE state = 'active' AND now() - pg_stat_activity.query_start > interval '5 seconds'
   ORDER BY duration DESC;
   "
   ```

3. Identify request hotspots:
   ```bash
   # Check most requested endpoints
   docker compose logs backend | grep "GET\|POST" | awk '{print $7}' | sort | uniq -c | sort -rn | head -20
   ```

4. Temporary mitigation - scale backend:
   ```bash
   # Add more backend instances (if resources allow)
   docker compose up -d --scale backend=2
   ```

5. Long-term fix - optimize queries or add caching

### Scenario B: Memory Pressure

**Symptoms:** Backend using > 90% of allocated memory, OOM risk

**Resolution:**

1. Check memory usage:
   ```bash
   docker stats --no-stream | grep backend
   
   # Check JVM heap usage
   curl -s http://localhost/q/metrics | grep "jvm_memory"
   ```

2. Check for memory leaks:
   ```bash
   # Look for growing memory usage over time
   # Compare with earlier metrics
   docker compose logs backend | grep -i "memory\|heap\|gc"
   ```

3. Restart backend to clear memory (temporary fix):
   ```bash
   docker compose restart backend
   sleep 30
   curl http://localhost/q/health/ready
   ```

4. Increase memory limits (if host has capacity):
   ```bash
   # Edit docker-compose.yml
   nano docker-compose.yml
   
   # Add under backend service:
   # deploy:
   #   resources:
   #     limits:
   #       memory: 2G
   
   docker compose up -d --no-deps backend
   ```

5. Tune JVM heap settings:
   ```bash
   # Add to backend environment in docker-compose.yml:
   # JAVA_OPTS: "-Xms512m -Xmx1536m"
   ```

### Scenario C: Database Connection Pool Exhaustion

**Symptoms:** "No available connections" errors, slow queries

**Resolution:**

1. Check active connections:
   ```bash
   docker compose exec postgres psql -U regattadesk -d regattadesk -c "
   SELECT count(*), state, wait_event_type
   FROM pg_stat_activity
   WHERE datname = 'regattadesk'
   GROUP BY state, wait_event_type;
   "
   ```

2. Identify long-running transactions:
   ```bash
   docker compose exec postgres psql -U regattadesk -d regattadesk -c "
   SELECT pid, now() - xact_start AS duration, state, query
   FROM pg_stat_activity
   WHERE datname = 'regattadesk' AND state <> 'idle'
   ORDER BY duration DESC
   LIMIT 10;
   "
   ```

3. Terminate stuck connections (if safe):
   ```bash
   # Carefully terminate specific PID
   docker compose exec postgres psql -U regattadesk -d regattadesk -c "
   SELECT pg_terminate_backend([PID]);
   "
   ```

4. Increase connection pool size:
   ```bash
   # Add to backend environment in docker-compose.yml:
   # QUARKUS_DATASOURCE_JDBC_MAX_SIZE: 20
   
   docker compose restart backend
   ```

5. Tune PostgreSQL max_connections:
   ```bash
   # Check current setting
   docker compose exec postgres psql -U regattadesk -c "SHOW max_connections;"
   
   # Increase if needed (requires PostgreSQL restart)
   # Edit postgres configuration or use command-line flag
   ```

### Scenario D: Rate Limiting Too Aggressive

**Symptoms:** Legitimate traffic getting 429 errors

**Resolution:**

1. Check rate limit hits:
   ```bash
   docker compose logs traefik | grep "429" | tail -50
   ```

2. Analyze traffic patterns:
   ```bash
   # Count requests per endpoint
   docker compose logs traefik | grep "GET\|POST" | awk '{print $10}' | sort | uniq -c | sort -rn
   ```

3. Adjust rate limits in `traefik/dynamic.yml`:
   ```yaml
   rate-limit-public:
     rateLimit:
       average: 200  # Increase from 100
       burst: 400    # Increase from 200
       period: 1s
   ```

4. Reload Traefik (automatic with file changes):
   ```bash
   # Verify config is valid
   docker compose exec traefik cat /etc/traefik/dynamic/dynamic.yml
   
   # Monitor logs for reload
   docker compose logs -f traefik
   ```

5. Test new limits:
   ```bash
   cd infra/compose
   ./edge-hardening-test.sh
   ```

### Scenario E: SSE Connection Failures

**Symptoms:** Server-Sent Events disconnecting, clients unable to receive live updates

**Resolution:**

1. Check SSE endpoint health:
   ```bash
   # Test SSE connection
   curl -N -H "Accept: text/event-stream" http://localhost/api/v1/public/regattas/[regatta-id]/events
   ```

2. Check timeout settings:
   ```bash
   # Review timeout configuration in traefik/dynamic.yml
   docker compose exec traefik cat /etc/traefik/dynamic/dynamic.yml | grep -A 5 "timeout"
   ```

3. Increase timeout for SSE endpoints by using a dedicated serversTransport:
   ```yaml
   # In traefik/dynamic.yml
   http:
     serversTransports:
       sse-transport:
         forwardingTimeouts:
           responseHeaderTimeout: 0  # Disable for SSE
           idleConnTimeout: 300s     # 5 minutes idle
   ```

4. Monitor active SSE connections:
   ```bash
   # Check backend logs for SSE connections
   docker compose logs backend | grep -i "sse\|event-stream"
   ```

5. Test SSE reconnection:
   ```bash
   # Client should automatically reconnect
   # Verify with browser dev tools or curl
   ```

### Scenario F: Slow Database Queries

**Symptoms:** Database queries taking > 1 second, high database CPU

**Resolution:**

1. Identify slow queries:
   ```bash
   docker compose exec postgres psql -U regattadesk -d regattadesk -c "
   SELECT pid, now() - pg_stat_activity.query_start AS duration, query
   FROM pg_stat_activity
   WHERE state = 'active'
   ORDER BY duration DESC
   LIMIT 10;
   "
   ```

2. Check for missing indexes:
   ```bash
   docker compose exec postgres psql -U regattadesk -d regattadesk -c "
   SELECT schemaname, tablename, indexname
   FROM pg_indexes
   WHERE schemaname = 'public'
   ORDER BY tablename;
   "
   ```

3. Analyze query execution plan:
   ```bash
   docker compose exec postgres psql -U regattadesk -d regattadesk -c "
   EXPLAIN ANALYZE [your slow query];
   "
   ```

4. Add missing indexes (if identified):
   ```bash
   docker compose exec postgres psql -U regattadesk -d regattadesk -c "
   CREATE INDEX CONCURRENTLY idx_entries_regatta_id ON entries(regatta_id);
   "
   ```

5. Run VACUUM ANALYZE:
   ```bash
   docker compose exec postgres psql -U regattadesk -d regattadesk -c "
   VACUUM ANALYZE;
   "
   ```

## Validation

After resolution:

### 1. Performance Metrics

```bash
# Check response times
time curl -o /dev/null -s http://localhost/api/health
# Should be < 100ms

# Check p95 latency in Grafana
# Open http://localhost/grafana
# Dashboard: Backend API Health
# Verify p95 latency < 500ms
```

### 2. Resource Utilization

```bash
# Check resources are within normal range
docker stats --no-stream
# CPU < 60%, Memory < 70%
```

### 3. Error Rates

```bash
# Check error rate
curl -s http://localhost/q/metrics | grep "http_server_requests_seconds_count" | grep "status=\"500\""
# Should be minimal or zero
```

### 4. End-to-End Tests

```bash
cd infra/compose
./smoke-test.sh
./edge-hardening-test.sh
```

## Prevention

### Short-term

1. Set up performance monitoring alerts
2. Implement query timeout limits
3. Add connection pool monitoring
4. Configure appropriate rate limits

### Long-term

1. Implement caching layer (Redis)
2. Add database read replicas
3. Optimize slow queries
4. Implement CDN for static assets
5. Add horizontal scaling
6. Performance testing in CI/CD

### Monitoring Improvements

Add alerts for:
- Response time p95 > 1 second for 5 minutes
- Database query time > 500ms
- Connection pool usage > 80%
- Rate limit hits > 100/minute
- SSE connection drops > 10/minute

## Escalation Criteria

Escalate if:

- ⏰ Performance not improved after **30 minutes**
- 📊 Resource limits exhausted
- 💾 Database optimization needed
- 🔧 Code changes required
- ❓ Root cause unclear

## Post-Incident Actions

### Immediate

- [ ] Document performance baseline
- [ ] Update monitoring thresholds
- [ ] Review rate limits

### Follow-up

- [ ] Analyze query patterns
- [ ] Identify optimization opportunities
- [ ] Update capacity planning
- [ ] Schedule performance testing

### Long-term

- [ ] Implement caching strategy
- [ ] Optimize database schema
- [ ] Add performance tests to CI
- [ ] Create capacity planning model

## Related Runbooks

- [RB-001: Service Unavailability](./incident-service-unavailability.md)
- RB-004: Database Issues (planned, runbook not yet published)
- RB-006: Log Analysis (planned, runbook not yet published)

## Additional Resources

- [Observability Documentation](../../infra/compose/OBSERVABILITY.md)
- [PostgreSQL Performance Tuning](https://www.postgresql.org/docs/current/performance-tips.html)
- [Quarkus Performance Guide](https://quarkus.io/guides/performance-measure)
