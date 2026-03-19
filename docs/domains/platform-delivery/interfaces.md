# Platform-Delivery Domain — Interfaces

## Contracts

- Compose service contracts in `infra/compose/docker-compose*.yml` define hostnames, networks, and service availability assumptions for all other domains.
- The top-level `Makefile` exposes stable workflows: `make install`, `make build`, `make test`, `make backend-dev`, and `make frontend-dev`.
- Backend and frontend manifest files expose build and test profiles that other domains rely on for local verification and CI.

## Integration Points

- `identity-access` depends on Traefik and Authelia wiring from `infra/compose/authelia/` and `infra/compose/traefik/`.
- `operator-capture` depends on MinIO and PostgreSQL services defined by the compose stack.
- `public-delivery` depends on the edge routing, frontend container, and backend API exposure provided here.
- `operability` depends on compose overlays and build commands to run smoke tests, security checks, and observability components.
