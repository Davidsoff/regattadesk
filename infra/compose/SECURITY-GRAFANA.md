# Grafana Security Configuration

## Overview

This document describes the security measures implemented to protect the Grafana observability dashboard in RegattaDesk.

## Security Improvements Implemented

### 1. Removed Default Credentials Fallback

**Problem:** Grafana was configured with insecure default credentials (`admin/admin`) when environment variables were not set.

**Solution:** Removed the default fallback using Docker Compose required variable syntax:
```yaml
environment:
  GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:?GRAFANA_ADMIN_USER must be set in .env}
  GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:?GRAFANA_ADMIN_PASSWORD must be set in .env}
```

**Impact:**
- Grafana will fail to start if credentials are not explicitly configured
- Prevents accidental deployment with default credentials
- Forces administrators to set strong, unique passwords

### 2. Authelia SSO Protection

**Problem:** Grafana was publicly accessible without authentication at `/grafana` endpoint.

**Solution:** Added Authelia ForwardAuth middleware to the Traefik route:
```yaml
labels:
  - "traefik.http.routers.grafana.middlewares=authelia@file"
```

**Impact:**
- All requests to Grafana are intercepted by Authelia for authentication
- Users must authenticate with valid Authelia credentials before accessing Grafana
- Provides centralized authentication and authorization
- Supports role-based access control through Authelia groups

## Authentication Flow

1. **User accesses Grafana** at `http://localhost/grafana`
2. **Traefik intercepts request** and forwards to Authelia for verification
3. **Authelia checks session**:
   - If not authenticated → Redirect to Authelia login page
   - If authenticated → Forward identity headers to Grafana
4. **Grafana receives request** with authenticated user context
5. **User prompted for Grafana credentials** (first time only)
6. **Grafana dashboard accessible** after both authentication layers

## Required Configuration

### Environment Variables

Add to your `.env` file:

```bash
# Grafana Admin Credentials (Required)
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=your_strong_secure_password_here
```

### Password Requirements

For production deployments, Grafana admin passwords should:
- Be at least 16 characters long
- Include uppercase, lowercase, numbers, and special characters
- Be unique (not reused from other services)
- Be stored securely (e.g., secrets manager, encrypted vault)

### Authelia Configuration

Ensure Authelia is properly configured in `authelia/configuration.yml`:
- Access control rules are defined
- User groups are assigned correctly
- Session configuration is secure

## Testing Security

### 1. Test Required Environment Variables

```bash
# Create test env without Grafana credentials
cd infra/compose
cat > /tmp/test.env << EOF
DOMAIN=localhost.local
POSTGRES_PASSWORD=test
# ... other required vars but NOT Grafana creds
EOF

# Attempt to start - should fail
docker compose --env-file /tmp/test.env -f docker-compose.yml -f docker-compose.observability.yml config

# Expected: Error message about missing GRAFANA_ADMIN_USER or GRAFANA_ADMIN_PASSWORD
```

### 2. Test Authelia Protection

```bash
# Start the full stack with observability
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d

# Attempt to access Grafana without authentication
curl -I http://localhost/grafana

# Expected: 302 redirect to Authelia login page
```

### 3. Test Authenticated Access

```bash
# After authenticating with Authelia in a browser:
# 1. Navigate to http://localhost.local/grafana
# 2. Should be redirected to Authelia login (if not already logged in)
# 3. After Authelia login, should see Grafana login prompt
# 4. Use GRAFANA_ADMIN_USER and GRAFANA_ADMIN_PASSWORD to log in
# 5. Should successfully access Grafana dashboard
```

## Security Best Practices

### Development Environment

1. **Use strong passwords** even in development
2. **Never commit** `.env` files or credentials to version control
3. **Rotate credentials** periodically
4. **Limit access** to development environments

### Production Environment

1. **Use secrets management** (e.g., HashiCorp Vault, AWS Secrets Manager)
2. **Enable HTTPS** with proper TLS certificates
3. **Configure Grafana OAuth/SSO** for additional authentication layer
4. **Implement network segmentation** to limit Grafana access
5. **Enable audit logging** in both Grafana and Authelia
6. **Regular security updates** for all components
7. **Monitor authentication failures** for potential attacks

## Additional Hardening Options

### 1. Restrict Authelia Groups

In `authelia/configuration.yml`, restrict Grafana access to specific groups:

```yaml
access_control:
  rules:
    - domain:
        - "localhost.local"
      policy: one_factor
      resources:
        - "^/grafana(/.*)?$"
      subject:
        - ["group:super_admin"]
        - ["group:regatta_admin"]
```

### 2. Configure Grafana Role Mapping

Map Authelia groups to Grafana roles using Grafana's OAuth integration or header-based auth:

```ini
[auth.proxy]
enabled = true
header_name = Remote-User
header_property = username
auto_sign_up = true
sync_ttl = 60
whitelist = 127.0.0.1, ::1, authelia
headers = Email:Remote-Email Name:Remote-Name Groups:Remote-Groups
```

### 3. Network Isolation

Remove Grafana from the `regattadesk-edge` network to make it completely internal:

```yaml
networks:
  - regattadesk-internal
# Remove regattadesk-edge
```

Then access via VPN or bastion host only.

### 4. Read-Only Access

For most users, consider creating Grafana users with Viewer role only:
- Admin: Full access (super_admin group)
- Editor: Dashboard editing (regatta_admin group)
- Viewer: Read-only access (all other staff groups)

## Monitoring and Auditing

### Log Authentication Events

Monitor these log sources for security events:

1. **Authelia logs:**
   ```bash
   docker compose logs authelia | grep -i "authentication\|failed\|blocked"
   ```

2. **Grafana logs:**
   ```bash
   docker compose logs grafana | grep -i "login\|authentication\|failed"
   ```

3. **Traefik access logs:**
   ```bash
   docker compose logs traefik | grep "/grafana"
   ```

### Alert on Suspicious Activity

Consider implementing alerts for:
- Multiple failed authentication attempts
- Access from unexpected IP addresses
- Privilege escalation attempts
- Configuration changes

## Incident Response

If credentials are compromised:

1. **Immediately change** Grafana admin password via environment variable
2. **Restart** Grafana service with new credentials
3. **Review** Grafana audit logs for unauthorized access
4. **Check** for unauthorized dashboard modifications
5. **Rotate** Authelia session secrets if needed
6. **Investigate** how credentials were compromised

## Compliance Considerations

This security configuration addresses:

- **OWASP Top 10:** 
  - A01:2021 – Broken Access Control (Authelia protection)
  - A07:2021 – Identification and Authentication Failures (No default credentials)

- **CIS Benchmarks:**
  - Remove default credentials
  - Implement authentication for management interfaces
  - Use centralized authentication

## References

- [Grafana Security Documentation](https://grafana.com/docs/grafana/latest/setup-grafana/configure-security/)
- [Authelia Access Control](https://www.authelia.com/configuration/security/access-control/)
- [Traefik ForwardAuth Middleware](https://doc.traefik.io/traefik/middlewares/http/forwardauth/)
- [Docker Compose Environment Variable Syntax](https://docs.docker.com/compose/environment-variables/set-environment-variables/)

## Support

For questions or issues related to Grafana security:
1. Review this document and [OBSERVABILITY.md](./OBSERVABILITY.md)
2. Check Grafana, Authelia, and Traefik logs
3. Verify environment variables are correctly set
4. Ensure Authelia users and groups are properly configured

## Changelog

### 2026-02-14
- ✅ Removed default credentials fallback (`admin/admin`)
- ✅ Added required environment variable validation
- ✅ Implemented Authelia ForwardAuth protection
- ✅ Updated documentation and configuration examples
- ✅ Added comprehensive security documentation
