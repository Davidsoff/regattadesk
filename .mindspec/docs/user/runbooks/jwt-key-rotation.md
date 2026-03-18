# JWT Key Rotation Runbook

## Overview

This runbook describes the operational procedures for rotating JWT signing keys for the RegattaDesk public anonymous session system. The system uses HS256 (HMAC-SHA256) symmetric key signatures with support for multiple active keys during rotation windows.

## Key Rotation Requirements

- **Minimum overlap**: 6 days between activation of new key and removal of old key
- **Token TTL**: 5 days (432,000 seconds)
- **Algorithm**: HS256 (HMAC-SHA256)
- **Key length**: Minimum 256 bits (32 bytes)
- **Active keys**: 2 keys active during overlap window

## Configuration Format

### Legacy Single-Key Configuration (Backward Compatible)

```properties
jwt.public.secret=${JWT_PUBLIC_SECRET}
jwt.public.kid=v1-2026-02
jwt.public.ttl-seconds=432000
jwt.public.refresh-window-percent=20
```

### Multi-Key Configuration (Recommended for Production)

```properties
# Key rotation configuration
jwt.public.keys.v1-2026-02.secret=${JWT_KEY_V1_SECRET}
jwt.public.keys.v1-2026-02.activated-at=2026-02-01T00:00:00Z

jwt.public.keys.v2-2026-02.secret=${JWT_KEY_V2_SECRET}
jwt.public.keys.v2-2026-02.activated-at=2026-02-15T00:00:00Z

# Legacy single-key config is ignored when multi-key config is present
jwt.public.ttl-seconds=432000
jwt.public.refresh-window-percent=20
```

## Standard Key Rotation Procedure

### Prerequisites

- Access to production configuration management system
- New secret key generated (minimum 256 bits, cryptographically secure)
- Deployment window scheduled
- Rollback plan prepared

### Rotation Timeline

```
Day 0: New key activated (v2)
       Both v1 and v2 active
       New tokens signed with v2
       Old tokens with v1 still valid

Day 6+: Safe to remove v1
        All tokens signed with v1 have expired (5-day TTL)
        v2 becomes sole active key
```

### Step-by-Step Procedure

#### Phase 1: Activate New Key (Day 0)

1. **Generate new secret key**
   ```bash
   # Generate 256-bit (32-byte) secure random key
   openssl rand -base64 32
   ```

2. **Add new key to configuration**
   
   Add the new key configuration while keeping the old key:
   
   ```properties
   # Old key (v1) - keep active
   jwt.public.keys.v1-2026-02.secret=${JWT_KEY_V1_SECRET}
   jwt.public.keys.v1-2026-02.activated-at=2026-02-01T00:00:00Z
   
   # New key (v2) - newly activated
   jwt.public.keys.v2-2026-02.secret=${JWT_KEY_V2_SECRET}
   jwt.public.keys.v2-2026-02.activated-at=2026-02-15T00:00:00Z
   ```

3. **Deploy configuration update**
   
   Deploy the configuration change to all backend instances. The system will:
   - Immediately start signing new tokens with v2
   - Continue accepting tokens signed with either v1 or v2

4. **Verify rotation**
   
   ```bash
   # Get a new session token
   curl -X POST https://your-domain.com/public/session -v -c cookies.txt
   
   # Extract and decode the token to verify kid=v2
   # Check JWT header contains: "kid":"v2-2026-02"
   ```

5. **Monitor logs**
   
   Check application logs for:
   ```
   INFO [com.regattadesk.jwt.ConfigBasedJwtKeyRegistry] Using key 'v2-2026-02' for signing new tokens
   INFO [com.regattadesk.jwt.JwtTokenService] JWT service initialized with 2 active keys
   ```

#### Phase 2: Remove Old Key (Day 6+)

1. **Verify 6+ days have passed since Phase 1**
   
   All tokens signed with v1 will have expired (5-day TTL + 1-day safety margin).

2. **Remove old key from configuration**
   
   ```properties
   # Remove v1 configuration entirely
   # Keep only v2
   jwt.public.keys.v2-2026-02.secret=${JWT_KEY_V2_SECRET}
   jwt.public.keys.v2-2026-02.activated-at=2026-02-15T00:00:00Z
   ```

3. **Deploy configuration update**

4. **Verify old key removed**
   
   Check application logs:
   ```
   INFO [com.regattadesk.jwt.JwtTokenService] JWT service initialized with 1 active keys
   ```

5. **Monitor for authentication errors**
   
   No increase in 401 errors should occur (all v1 tokens should be expired).

## Emergency Key Rotation (Security Incident)

If a key is compromised, immediate rotation is required:

### Emergency Procedure

1. **Immediately activate new key**
   
   Follow Phase 1 steps to activate new key alongside compromised key.

2. **Invalidate all existing sessions**
   
   Option A: Remove compromised key immediately (will cause 401 for valid sessions)
   
   ```properties
   # Only keep new secure key
   jwt.public.keys.v3-2026-02-emergency.secret=${JWT_KEY_V3_SECRET}
   jwt.public.keys.v3-2026-02-emergency.activated-at=2026-02-20T14:30:00Z
   ```
   
   Option B: Wait for normal token expiry (5 days) if risk is low

3. **Notify users via public channels**
   
   If immediate invalidation is chosen, users will need to:
   - Refresh their browser to trigger `/public/session` bootstrap
   - Automatic recovery via 401 → bootstrap flow

4. **Document incident**
   
   Record:
   - Date/time of compromise detection
   - Date/time of emergency rotation
   - Affected key IDs
   - Recovery actions taken

## Monitoring and Alerts

### Key Metrics to Monitor

1. **JWT validation failures**
   - Metric: `jwt_validation_failures_total`
   - Alert threshold: Increase > 10% over baseline

2. **Session creation rate**
   - Metric: `public_session_created_total`
   - Alert threshold: Spike > 3x normal rate (could indicate mass token invalidation)

3. **401 response rate**
   - Metric: `http_requests_total{status="401"}`
   - Alert threshold: Increase > 20% over baseline

4. **Active key count**
   - Expected: 1 (steady state) or 2 (during rotation)
   - Alert: > 2 keys active (configuration error)

### Log Patterns to Watch

```
# Expected during rotation
INFO JWT service initialized with 2 active keys

# Warning: Unknown key ID
WARN Rejected JWT token with unknown kid: v-old-key

# Error: Invalid signature
WARN Rejected JWT token with kid=v2 due to invalid signature
```

## Rollback Procedure

If new key causes issues:

1. **Re-add old key to configuration**
   
   Restore both keys to active state.

2. **Deploy rollback configuration**

3. **Investigate root cause**
   - Check key format/encoding
   - Verify activation timestamp format
   - Review application logs

4. **Fix issue and retry rotation**

## Testing Key Rotation

### Pre-Production Validation

Before rotating production keys, validate in staging:

1. **Run automated tests**
   ```bash
   cd apps/backend
   ./mvnw test -Dtest=JwtKeyRotationTest
   ./mvnw test -Dtest=PublicBootstrapFallbackIT
   ./mvnw test -Dtest=PublicSessionKeyRotationIT
   ```

2. **Manual validation**
   - Generate test tokens with old and new keys
   - Verify both validate successfully
   - Verify new tokens use newest key

3. **Load testing during rotation**
   - Simulate production traffic
   - Verify no authentication failures
   - Measure latency impact (should be negligible)

## Key Generation Best Practices

1. **Use cryptographically secure random number generator**
   ```bash
   # Good: OS-level secure random
   openssl rand -base64 32
   
   # Bad: Pseudo-random or predictable sources
   ```

2. **Minimum key length: 256 bits (32 bytes)**

3. **Store keys securely**
   - Use secrets management system (e.g., AWS Secrets Manager, HashiCorp Vault)
   - Never commit keys to version control
   - Restrict access to keys (principle of least privilege)

4. **Key naming convention**
   - Format: `v{sequence}-{year}-{month}[-{qualifier}]`
   - Examples: `v1-2026-02`, `v2-2026-02`, `v3-2026-02-emergency`

## Rotation Schedule

### Recommended Rotation Frequency

- **Standard rotation**: Every 3-6 months
- **Compliance-driven**: As required by security policies
- **Post-incident**: Immediately after compromise

### Planning Rotation Windows

- Low-traffic periods preferred (to minimize bootstrap overhead)
- Avoid rotation during:
  - Major race events
  - System maintenance windows
  - Holiday periods

## Troubleshooting

### Issue: Tokens signed with new key fail validation

**Symptoms**: 401 errors after activating new key

**Diagnosis**:
```bash
# Check application logs for verification errors
grep "Invalid JWT signature" app.log
```

**Resolution**:
1. Verify new key secret is correctly base64-encoded
2. Check activation timestamp format (ISO 8601: `YYYY-MM-DDTHH:MM:SSZ`)
3. Confirm configuration was properly deployed to all instances

### Issue: Old tokens rejected before expiry

**Symptoms**: Unexpected 401 errors before 6-day overlap completes

**Diagnosis**:
```bash
# Check if old key was prematurely removed
grep "Rejected JWT token with unknown kid" app.log
```

**Resolution**:
1. Re-add old key to configuration
2. Deploy immediately
3. Monitor for recovery

### Issue: Multiple keys accumulate in configuration

**Symptoms**: More than 2 active keys

**Diagnosis**:
```bash
# Check key registry initialization
grep "JWT service initialized with" app.log
```

**Resolution**:
1. Review configuration for stale keys
2. Remove keys older than 6 days past their successor's activation
3. Deploy cleaned configuration

## Security Considerations

1. **Key confidentiality**
   - Keys are symmetric (same key signs and verifies)
   - Compromise of any active key allows token forgery
   - Rotate immediately if confidentiality is breached

2. **Overlap window**
   - 6-day minimum ensures all tokens expire before old key removal
   - Extending overlap is safe (e.g., 7-10 days for extra safety)
   - Shortening overlap risks breaking valid sessions

3. **Audit trail**
   - Log all key rotation events
   - Record key IDs and activation times
   - Retain rotation history for compliance

## Compliance and Audit

### Documentation Requirements

For each rotation, document:
- Date and time of rotation
- Key IDs involved (old and new)
- Reason for rotation (scheduled vs. emergency)
- Personnel who performed rotation
- Validation results

### Audit Questions

- When was the last key rotation?
- How many keys are currently active?
- What is the overlap period?
- Are rotation logs available?
- Is the current rotation schedule compliant with policy?

## References

- BC02 Identity and Access: `.mindspec/docs/specs/pdd-v0.1/implementation/bc02-identity-and-access.md`
- Implementation Plan Step 6: `.mindspec/docs/specs/pdd-v0.1/implementation/plan.md`
- JWT Configuration: `apps/backend/src/main/resources/application.properties`
- Key Registry Implementation: `apps/backend/src/main/java/com/regattadesk/jwt/ConfigBasedJwtKeyRegistry.java`
