# Platform-Delivery Domain — Overview

## What This Domain Owns

- The canonical runtime composition in `infra/compose`, including Traefik, Authelia, PostgreSQL, MinIO, backend, and frontend services.
- Build, packaging, and dependency baselines defined by `apps/backend/pom.xml`, `apps/frontend/package.json`, and the top-level `Makefile`.
- Delivery-time environment shape, including dev overlays, observability overlays, and local-vs-production TLS posture.

## Boundaries

- It does not own business rules for regattas, draw, results, operator capture, or finance.
- It provides auth and storage infrastructure but does not own identity policy or operator workflows.
- It supports observability and security tooling, but the policies and gates themselves are documented under `operability`.

## Key Files

| File | Purpose |
|:-----|:--------|
| `infra/compose/docker-compose.yml` | Canonical stack definition for v0.1 runtime dependencies. |
| `infra/compose/docker-compose.dev.yml` | Development-only host exposure overlay. |
| `infra/compose/docker-compose.observability.yml` | Observability services attached to the stack. |
| `apps/backend/pom.xml` | Backend runtime, plugin, and test profile baseline. |
| `apps/frontend/package.json` | Frontend build, test, and generated API client entrypoints. |
| `Makefile` | Project-wide install, build, test, lint, and dev commands. |
| `docs/DEVELOPER_SETUP.md` | First-run environment bootstrap guide. |

## Current State

- The repository already has a working Quarkus backend and Vue frontend plus Docker Compose definitions for the full v0.1 stack.
- Platform docs are ahead of most ad hoc README files and already describe DB-only Authelia, MinIO-backed line-scan storage, and local TLS.
- CI and dependency governance are partially documented in `docs/` and workflow files, so this domain is primarily documentation consolidation rather than new code discovery.
