# Platform-Delivery Domain — Architecture

## Key Patterns

- Docker Compose is the canonical runtime for both local development and production-like deployment in v0.1.
- The backend is packaged with Quarkus and Jib; the frontend is built with Vite and served via Nginx.
- Traefik is the single edge entrypoint and terminates TLS before routing to frontend, backend, and Authelia.
- Dev and observability concerns are layered through compose overrides instead of divergent base stacks.

## Invariants

- The stack must include all core runtime dependencies in-repo: backend, frontend, PostgreSQL, Traefik, Authelia, and MinIO.
- Internal services should remain private by default; development-only exposure belongs in overlay files.
- Authelia remains DB-only in v0.1, with no Redis dependency introduced through the platform layer.
- Build and test entrypoints must remain reproducible through `make`, Maven, and npm scripts.

## Design Decisions

- The repository uses compose overlays instead of separate stacks so production and local workflows stay structurally aligned.
- Local TLS is handled by Traefik with self-signed certificates, while production targets ACME through the same edge component.
- Dependency versions are pinned in build manifests so BC01 and BC09 can enforce governance and vulnerability scanning without external coordination.
