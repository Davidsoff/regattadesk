# Security: Reduced Port Exposure - Implementation Summary

## Overview
This change implements security best practices by removing unnecessary host port exposure from internal services in the Docker Compose stack. By default, internal services are now only accessible within Docker networks, significantly reducing the attack surface.

## Changes Made

### 1. Docker Compose Configuration Updates

#### docker-compose.yml
- **PostgreSQL**: Changed from `ports` to `expose` (port 5432)
  - Service remains accessible to other containers via internal network
  - No longer exposed to host by default
  
- **MinIO**: Changed from `ports` to `expose` (ports 9000, 9001)
  - S3 API and Console remain accessible to internal services
  - No longer exposed to host by default

#### docker-compose.observability.yml
- **Jaeger**: Changed from `ports` to `expose` (ports 16686, 4317, 4318)
  - Tracing UI and OTLP receivers accessible internally
  - No longer exposed to host by default
  
- **Prometheus**: Changed from `ports` to `expose` (port 9090)
  - Metrics collection works within network
  - No longer exposed to host by default

### 2. Development Mode Overlay Files

Created two new overlay files for opt-in development access:

#### docker-compose.dev.yml
Provides host access to base services:
- PostgreSQL: `localhost:5432`
- MinIO API: `localhost:9000`
- MinIO Console: `localhost:9001`

Usage:
```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
```

#### docker-compose.observability.dev.yml
Provides host access to observability services:
- Jaeger UI: `localhost:16686`
- OTLP gRPC: `localhost:4317`
- OTLP HTTP: `localhost:4318`
- Prometheus: `localhost:9090`

Usage:
```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml -f docker-compose.observability.dev.yml up
```

### 3. Documentation Updates

#### README.md
- Added "Security: Port Exposure" section explaining the new behavior
- Updated "Prerequisites" to reflect reduced port requirements
- Updated "Quick Start" with both secure and development modes
- Updated "Service Details" to clarify internal vs host access
- Updated "Observability" section with correct dev overlay usage
- Updated all service endpoint documentation

#### .env.example
- Added comments explaining when port variables are used
- Documented that port variables only apply with dev overlay files

### 4. Test Updates

#### smoke-test.sh
- Updated MinIO console test to be conditional
- Skips MinIO console test when not exposed (secure default)
- Provides helpful message about enabling dev mode

#### observability-smoke-test.sh
- Detects dev mode automatically
- Conditionally tests Prometheus and Jaeger only when exposed
- Provides clear messaging about secure defaults

#### security-validation-test.sh (NEW)
Comprehensive validation script that ensures:
- Internal services have NO host ports in secure mode
- Internal services have proper `expose` directives
- Dev overlays correctly add host ports when used
- All compose configurations are valid

## Security Impact

### Attack Surface Reduction
**Before**: 7 ports exposed to host by default
- 5432 (PostgreSQL)
- 9000, 9001 (MinIO)
- 16686, 4317, 4318 (Jaeger)
- 9090 (Prometheus)

**After**: 0 internal service ports exposed by default
- Only Traefik remains exposed (80, 443, 8080) - required for application access
- All internal services isolated to Docker networks
- Dev mode must be explicitly enabled for direct access

### Benefits
1. **Reduced exposure**: Internal services cannot be directly accessed from host
2. **Network isolation**: Services communicate only within defined Docker networks
3. **Explicit opt-in**: Developers must consciously enable dev mode
4. **Production ready**: Secure defaults suitable for production deployment
5. **Clear separation**: Dev overlays clearly marked as development-only

## Backward Compatibility

### Breaking Changes
⚠️ **Direct host access to internal services requires dev overlay files**

Users who previously accessed services directly will need to update their workflow:

```bash
# OLD (no longer works by default)
docker compose up
psql -h localhost -U regattadesk

# NEW (secure mode - use docker exec)
docker compose up
docker compose exec postgres psql -U regattadesk

# OR NEW (dev mode - direct access)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
psql -h localhost -U regattadesk
```

### Migration Path
1. Review if direct host access is needed
2. If needed for development, use dev overlay files
3. For production, use secure defaults (no change needed)
4. Update scripts/documentation to use `docker compose exec` or dev overlays

## Testing

### Validation Results
✅ All security validation tests pass:
- PostgreSQL not exposed by default
- MinIO not exposed by default
- Jaeger not exposed by default
- Prometheus not exposed by default
- Dev overlays correctly expose services
- All compose configurations valid

### Test Commands
```bash
# Validate security configuration
./security-validation-test.sh

# Test base stack
docker compose config --quiet

# Test with dev mode
docker compose -f docker-compose.yml -f docker-compose.dev.yml config --quiet

# Test observability with dev mode
docker compose -f docker-compose.yml -f docker-compose.observability.yml -f docker-compose.observability.dev.yml config --quiet
```

## References

### Files Changed
- `infra/compose/docker-compose.yml`
- `infra/compose/docker-compose.observability.yml`
- `infra/compose/docker-compose.dev.yml` (NEW)
- `infra/compose/docker-compose.observability.dev.yml` (NEW)
- `infra/compose/.env.example`
- `infra/compose/README.md`
- `infra/compose/smoke-test.sh`
- `infra/compose/observability-smoke-test.sh`
- `infra/compose/security-validation-test.sh` (NEW)

### Related Documentation
- [BC01 Platform Spec](../../pdd/implementation/bc01-platform-and-delivery.md)
- [BC09 Security](../../pdd/implementation/bc09-operability-hardening-quality.md)
- [Docker Compose Best Practices](https://docs.docker.com/compose/production/)

## Acceptance Criteria

✅ Internal services are not host-exposed by default
✅ Any host-exposed ports are explicitly justified and opt-in
✅ Smoke tests confirm normal stack behavior under reduced exposure defaults
✅ Documentation explains secure defaults and dev mode
✅ Configuration validation passes for all modes
