# Incident Runbook: Authentication and Session Failures

**Runbook ID:** RB-002  
**Category:** Incident Response  
**Severity:** P1-P2 (Critical to High)  
**Last Updated:** 2026-02-13

## Symptoms

- Users unable to log in to staff interface
- "Authentication failed" errors
- Session timeouts immediately after login
- 401/403 errors on authenticated endpoints
- Authelia service unavailable
- ForwardAuth middleware errors in Traefik logs
- Users redirected to login page repeatedly

## Impact

**Business Impact:**
- Staff unable to access management functions
- Operators cannot record race results
- No ability to manage regattas, entries, or results
- Public site may remain accessible (depends on scope)

**Technical Impact:**
- ForwardAuth middleware failures
- Session management broken
- Identity headers not forwarded
- Database authentication state inconsistent

## Initial Triage

### Step 1: Verify Authentication Service Status

```bash
# Check Authelia health
curl -v http://localhost/auth/api/health

# Check Authelia container status
docker compose ps authelia

# Check Authelia logs
docker compose logs --tail=100 authelia
```

**Expected Output:**
- Health check should return 200 OK
- Container status should show "Up (healthy)"
- Logs should not show connection errors

### Step 2: Test Authentication Flow

```bash
# Test ForwardAuth endpoint
curl -v http://localhost/api/v1/staff/regattas

# Check Traefik logs for ForwardAuth
docker compose logs --tail=50 traefik | grep -i "forward\|auth"
```

**Look for:**
- Redirect to `/auth/` (302 response)
- ForwardAuth verification errors
- Connection refused to Authelia
- Missing or malformed identity headers

### Step 3: Check Configuration

```bash
# Check Authelia configuration
docker compose exec authelia cat /config/configuration.yml

# Verify environment variables
docker compose config | grep -A 30 authelia

# Check Traefik dynamic config
docker compose exec traefik cat /etc/traefik/dynamic/dynamic.yml | grep -A 10 authelia
```

## Resolution Steps

### Scenario A: Authelia Service Down

**Symptoms:** Authelia container exited or not responding

**Resolution:**

1. Check Authelia logs for errors:
   ```bash
   docker compose logs --tail=200 authelia
   ```

2. Common error patterns:
   - **Database connection failed**: Check PostgreSQL availability
   - **Missing secrets**: Verify environment variables
   - **Configuration error**: Validate `configuration.yml`

3. Verify required environment variables:
   ```bash
   docker compose exec authelia env | grep AUTHELIA
   ```
   
   Required variables:
   - `AUTHELIA_JWT_SECRET`
   - `AUTHELIA_SESSION_SECRET`
   - `AUTHELIA_STORAGE_ENCRYPTION_KEY`
   - `AUTHELIA_STORAGE_POSTGRES_*` (host, port, database, username, password)

4. Restart Authelia:
   ```bash
   docker compose restart authelia
   ```

5. Wait for health check to pass (30 seconds):
   ```bash
   watch -n 2 'curl -s http://localhost/auth/api/health'
   ```

### Scenario B: Database Connection Failures

**Symptoms:** Authelia logs show PostgreSQL connection errors

**Resolution:**

1. Verify PostgreSQL is running:
   ```bash
   docker compose ps postgres
   ```

2. Check if Authelia database exists:
   ```bash
   docker compose exec postgres psql -U regattadesk -l | grep authelia
   ```

3. Test database connection:
   ```bash
   docker compose exec postgres psql -U regattadesk -d authelia -c "SELECT 1;"
   ```

4. If database doesn't exist, recreate it:
   ```bash
   docker compose exec postgres psql -U regattadesk -c "CREATE DATABASE authelia;"
   ```

5. Restart Authelia to reinitialize schema:
   ```bash
   docker compose restart authelia
   docker compose logs -f authelia
   # Watch for successful migration messages
   ```

### Scenario C: Session Management Issues

**Symptoms:** Users can log in but sessions expire immediately

**Resolution:**

1. Check session configuration in Authelia:
   ```bash
   docker compose exec authelia cat /config/configuration.yml | grep -A 10 "^session:"
   ```

2. Verify session secret is set:
   ```bash
   docker compose exec authelia env | grep AUTHELIA_SESSION_SECRET
   ```

3. Check for session storage errors in logs:
   ```bash
   docker compose logs authelia | grep -i "session"
   ```

4. Clear stale sessions from database:
   ```bash
   docker compose exec postgres psql -U regattadesk -d authelia -c "
   SELECT COUNT(*) FROM user_sessions WHERE expiration < NOW();
   "
   
   # Delete expired sessions
   docker compose exec postgres psql -U regattadesk -d authelia -c "
   DELETE FROM user_sessions WHERE expiration < NOW();
   "
   ```

5. Restart Authelia:
   ```bash
   docker compose restart authelia
   ```

### Scenario D: ForwardAuth Middleware Misconfiguration

**Symptoms:** Traefik logs show ForwardAuth errors, 502 errors on protected endpoints

**Resolution:**

1. Check Traefik configuration for ForwardAuth:
   ```bash
   docker compose exec traefik cat /etc/traefik/dynamic/dynamic.yml | grep -A 15 "authelia:"
   ```

2. Verify correct ForwardAuth address:
   ```yaml
   # Should be:
   address: http://authelia:9091/api/verify?rd=https://localhost.local
   ```

3. Check network connectivity from Traefik to Authelia:
   ```bash
   docker compose exec traefik wget -O- http://authelia:9091/api/health
   ```

4. Verify both services are on same network:
   ```bash
   docker network inspect regattadesk-edge | grep -E "authelia|traefik"
   ```

5. If configuration changed, reload Traefik:
   ```bash
   # Traefik watches for file changes automatically
   # Force reload by restarting
   docker compose restart traefik
   ```

### Scenario E: Missing Identity Headers

**Symptoms:** Backend receives requests but identity headers are missing

**Resolution:**

1. Check ForwardAuth configuration in `dynamic.yml`:
   ```yaml
   authResponseHeaders:
     - Remote-User
     - Remote-Groups
     - Remote-Name
     - Remote-Email
   ```

2. Test ForwardAuth response headers:
   ```bash
   # Authenticate first, then test with cookies
   curl -v -c cookies.txt http://localhost/auth/api/check
   curl -v -b cookies.txt http://localhost/api/v1/staff/regattas
   ```

3. Check backend logs for received headers:
   ```bash
   docker compose logs backend | grep -i "remote-"
   ```

4. Verify Traefik applies middleware to protected routes:
   ```bash
   docker compose config | grep -A 5 "backend-staff.middlewares"
   # Should include: authelia@docker
   ```

### Scenario F: User Account Issues

**Symptoms:** Specific users cannot log in, others can

**Resolution:**

1. Check if user exists in Authelia:
   ```bash
   docker compose exec postgres psql -U regattadesk -d authelia -c "
   SELECT username, email, disabled FROM users;
   "
   ```

2. Check if user is disabled:
   ```bash
   docker compose exec postgres psql -U regattadesk -d authelia -c "
   SELECT username, disabled FROM users WHERE username='<username>';
   "
   ```

3. Enable user if needed:
   ```bash
   docker compose exec postgres psql -U regattadesk -d authelia -c "
   UPDATE users SET disabled = false WHERE username='<username>';
   "
   ```

4. Check for failed authentication attempts:
   ```bash
   docker compose exec postgres psql -U regattadesk -d authelia -c "
   SELECT * FROM authentication_logs WHERE username='<username>' ORDER BY time DESC LIMIT 10;
   "
   ```

5. Reset user password (if needed, requires Authelia CLI or direct DB update):
   ```bash
   # Refer to Authelia documentation for password reset procedures
   # https://www.authelia.com/reference/guides/reset-passwords/
   ```

## Validation

After resolution, validate the fix:

### 1. Health Check

```bash
# Authelia health
curl -v http://localhost/auth/api/health
# Expected: 200 OK
```

### 2. Authentication Flow Test

```bash
# Test protected endpoint (should redirect to auth)
curl -v http://localhost/api/v1/staff/regattas
# Expected: 302 redirect to /auth/

# Use edge auth test script
cd infra/compose
./edge-auth-test.sh
```

### 3. End-to-End Login Test

1. Open browser to: http://localhost/api/v1/staff/regattas
2. Should redirect to Authelia login page
3. Log in with test credentials (see `.env` or users database)
4. Should redirect back to original URL
5. Should receive valid API response

### 4. Verify Identity Headers

```bash
# Check backend logs after successful login
docker compose logs backend | grep "Remote-User"
# Should show forwarded identity headers
```

### 5. Monitor Dashboards

- Open Grafana: http://localhost/grafana
- Check for authentication-related errors
- Verify request success rate > 95%

## Prevention

### Short-term (Immediate)

1. Set up monitoring for Authelia health
2. Add alerts for ForwardAuth failures
3. Document user management procedures
4. Create database backup schedule

### Long-term (Strategic)

1. Implement automated user provisioning
2. Add self-service password reset
3. Configure session persistence across restarts
4. Implement MFA for privileged accounts
5. Add authentication audit logging
6. Set up automated session cleanup

### Monitoring Improvements

Add alerts for:
- Authelia health check failures
- ForwardAuth error rate > 5%
- Failed authentication attempts > 10/minute
- Session database growth rate
- Database connection pool exhaustion

## Escalation Criteria

Escalate to senior engineer or security team if:

- ⏰ Issue not resolved within **20 minutes**
- 🔒 Unauthorized access suspected
- 💾 User data exposure suspected
- 🔐 Password database compromise suspected
- 📊 Mass authentication failures (> 50 users affected)
- ❓ Root cause involves security configuration

## Escalation Procedure

1. Gather diagnostic information:
   ```bash
   # Save logs
   docker compose logs authelia > authelia-logs-$(date +%Y%m%d-%H%M%S).txt
   docker compose logs traefik > traefik-logs-$(date +%Y%m%d-%H%M%S).txt
   
   # Save configuration (REDACT SECRETS before sharing!)
   docker compose config > compose-config-$(date +%Y%m%d-%H%M%S).yml
   # IMPORTANT: Remove secrets before sharing this file
   ```

2. Contact via PagerDuty:
   - Security incidents: Page security team
   - Service issues: Page senior engineer

3. If security incident:
   - Isolate affected services
   - Preserve logs for forensic analysis
   - Do not restart services until instructed

## Post-Incident Actions

### Immediate (Within 1 hour)

- [ ] Update incident ticket with resolution
- [ ] Verify all users can authenticate
- [ ] Check for unauthorized access during incident
- [ ] Document root cause

### Follow-up (Within 24 hours)

- [ ] Review authentication audit logs
- [ ] Update runbook if new scenarios discovered
- [ ] Test authentication in staging environment
- [ ] Verify backup procedures are working

### Long-term (Within 1 week)

- [ ] Schedule postmortem if P1 incident
- [ ] Review and update authentication configuration
- [ ] Test disaster recovery procedures
- [ ] Update security documentation

## Related Runbooks

- [RB-001: Service Unavailability](./incident-service-unavailability.md)
- RB-004: Database Issues (planned, runbook not yet published)
- RB-008: Configuration Management (planned, runbook not yet published)

## Additional Resources

- [Authelia Documentation](https://www.authelia.com/)
- [Traefik ForwardAuth](https://doc.traefik.io/traefik/middlewares/http/forwardauth/)
- [BC02-001 Implementation Status](../../infra/compose/BC02-001-STATUS.md)
- [Identity Forwarding](../IDENTITY_FORWARDING.md)
