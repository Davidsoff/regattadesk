# Operability Domain — Interfaces

## Contracts

- `GET /api/health` plus Quarkus health endpoints expose runtime health signals.
- The top-level `make test`, `make lint`, and related backend or frontend commands expose the verification contract used across domains.
- Runbooks under `docs/user/runbooks/` and validation scripts under `infra/compose/*.sh` expose the operational contract for incident response, hardening, and smoke checks.
- Version-controlled load scenarios and thresholds under `apps/backend/performance/` expose the performance-gate contract.

## Integration Points

- Every other domain is subject to the testing and quality requirements documented here.
- `platform-delivery` provides the runtime substrate that these checks validate.
- `identity-access` and `public-delivery` are especially coupled to the security and performance runbooks maintained here.