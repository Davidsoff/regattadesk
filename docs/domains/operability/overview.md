# Operability Domain — Overview

## What This Domain Owns

- Health and readiness signaling, metrics, tracing, performance gates, and operational dashboards.
- Security hardening guidance, validation scripts, and incident or operational runbooks.
- The cross-cutting testing strategy and CI quality gates that apply to all other domains.

## Boundaries

- It does not own business rules for regatta workflows, draw logic, operator capture, adjudication, or finance.
- It collaborates with platform-delivery on runtime composition, but it does not own the base stack definition.
- It defines and enforces cross-cutting quality and operational expectations rather than feature-specific product behavior.

## Key Files

| File | Purpose |
|:-----|:--------|
| `apps/backend/src/main/java/com/regattadesk/health/HealthResource.java` | App-specific health endpoint. |
| `apps/backend/src/main/java/com/regattadesk/performance/PerformanceGateEvaluator.java` | Backend performance gate evaluation. |
| `docs/core/TESTING_STRATEGY.md` | Test matrix and quality expectations. |
| `docs/core/CI_QUALITY_GATES.md` | CI enforcement and merge gate guidance. |
| `docs/core/EDGE_SECURITY.md` | Edge hardening and TLS posture. |
| `docs/user/runbooks/performance-gates.md` | Load and performance gate interpretation. |
| `infra/compose/observability-smoke-test.sh` | Observability validation script. |
| `infra/compose/security-validation-test.sh` | Security validation script. |

## Current State

- This domain already has substantial implementation and documentation across backend health and performance packages, compose scripts, and operational runbooks.
- Testing strategy and CI quality gate documents map naturally into this canonical domain view.
- Operability is the main cross-cutting domain in the repo and underpins all bounded contexts.
